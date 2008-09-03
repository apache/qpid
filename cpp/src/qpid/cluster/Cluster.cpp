/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "Cluster.h"
#include "Connection.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Connection.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/ClusterUrlNoticeBody.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/log/Statement.h"
#include "qpid/memory.h"
#include "qpid/shared_ptr.h"

#include <boost/bind.hpp>
#include <boost/cast.hpp>
#include <boost/current_function.hpp>
#include <algorithm>
#include <iterator>
#include <map>

namespace qpid {
namespace cluster {
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;

struct ClusterOperations : public AMQP_AllOperations::ClusterHandler {
    Cluster& cluster;
    MemberId member;
    ClusterOperations(Cluster& c, const MemberId& id) : cluster(c), member(id) {}
    void urlNotice(const std::string& u) { cluster.urlNotice (member, u); }
    bool invoke(AMQFrame& f) { return framing::invoke(*this, *f.getBody()).wasHandled(); }
};

Cluster::Cluster(const std::string& name_, const Url& url_, broker::Broker& b) :
    broker(&b),
    poller(b.getPoller()),
    cpg(*this),
    name(name_),
    url(url_),
    self(cpg.self()),
    cpgDispatchHandle(cpg,
                      boost::bind(&Cluster::dispatch, this, _1), // read
                      0,                                         // write
                      boost::bind(&Cluster::disconnect, this, _1) // disconnect
    )
{
    broker->addFinalizer(boost::bind(&Cluster::leave, this));
    QPID_LOG(trace, "Joining cluster: " << name << " as " << self);
    cpg.join(name);
    mcastFrame(AMQFrame(in_place<ClusterUrlNoticeBody>(ProtocolVersion(), url.str())),
               ConnectionId(self,0));

    // Start dispatching from the poller.
    cpgDispatchHandle.startWatch(poller);
}

Cluster::~Cluster() {}

void Cluster::insert(const boost::intrusive_ptr<Connection>& c) {
    Mutex::ScopedLock l(lock);
    connections.insert(ConnectionMap::value_type(ConnectionId(self, c.get()), c));
}

void Cluster::erase(ConnectionId id) {
    Mutex::ScopedLock l(lock);
    connections.erase(id);
}

void Cluster::leave() {
    Mutex::ScopedLock l(lock);
    if (!broker) return;                               // Already left.
    // Leave is called by from Broker destructor after the poller has
    // been shut down. No dispatches can occur.

    QPID_LOG(debug, "Leaving cluster " << name.str());
    cpg.leave(name);
    // broker= is set to 0 when the final config-change is delivered.
    while(broker) {
        Mutex::ScopedUnlock u(lock);
        cpg.dispatchAll();
    }
    cpg.shutdown();
}

template <class T> void decodePtr(Buffer& buf, T*& ptr) {
    uint64_t value = buf.getLongLong();
    ptr = reinterpret_cast<T*>(value);
}

template <class T> void encodePtr(Buffer& buf, T* ptr) {
    uint64_t value = reinterpret_cast<uint64_t>(ptr);
    buf.putLongLong(value);
}

void Cluster::mcastFrame(const AMQFrame& frame, const ConnectionId& connection) {
    QPID_LOG(trace, "MCAST [" << connection << "] " << frame);
    // FIXME aconway 2008-09-02: restore queueing.
    Mutex::ScopedLock l(lock); // FIXME aconway 2008-09-02: review locking.
    static char buffer[64*1024]; // FIXME aconway 2008-07-04: buffer management or FrameEncoder.
    Buffer buf(buffer, sizeof(buffer));
    buf.putOctet(CONTROL);
    encodePtr(buf, connection.getConnectionPtr());
    frame.encode(buf);
    iovec iov = { buffer, buf.getPosition() };
    cpg.mcast(name, &iov, 1);
}

void Cluster::mcastBuffer(const char* data, size_t size, const ConnectionId& id) {
    // FIXME aconway 2008-09-02: does this need locking?
    Mutex::ScopedLock l(lock); // FIXME aconway 2008-09-02: review locking.
    char hdrbuf[1+sizeof(uint64_t)];
    Buffer buf(hdrbuf, sizeof(hdrbuf));
    buf.putOctet(DATA);
    encodePtr(buf, id.getConnectionPtr());
    iovec iov[] = { { hdrbuf, buf.getPosition() }, { const_cast<char*>(data), size } };
    cpg.mcast(name, iov, sizeof(iov)/sizeof(*iov));
}

size_t Cluster::size() const {
    Mutex::ScopedLock l(lock);
    return urls.size();
}

std::vector<Url> Cluster::getUrls() const {
    Mutex::ScopedLock l(lock);
    std::vector<Url> result(urls.size());
    std::transform(urls.begin(), urls.end(), result.begin(),
                   boost::bind(&UrlMap::value_type::second, _1));
    return result;        
}

boost::intrusive_ptr<Connection> Cluster::getConnection(const ConnectionId& id) {
    if (id.getMember() == self)
        return boost::intrusive_ptr<Connection>(id.getConnectionPtr());
    ConnectionMap::iterator i = connections.find(id);
    if (i == connections.end()) { // New shadow connection.
        assert(id.getMember() != self);
        std::ostringstream mgmtId;
        mgmtId << name << ":"  << id;
        ConnectionMap::value_type value(id, new Connection(*this, shadowOut, mgmtId.str(), id));
        i = connections.insert(value).first;
    }
    return i->second;
}

void Cluster::deliver(
    cpg_handle_t /*handle*/,
    cpg_name* /*group*/,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len)
{
    try {
        MemberId from(nodeid, pid);
        Buffer buf(static_cast<char*>(msg), msg_len);
        Connection* connection;
        uint8_t type = buf.getOctet();
        decodePtr(buf, connection);
        if (connection == 0)  { // Cluster controls
            AMQFrame frame;
            while (frame.decode(buf)) 
                if (!ClusterOperations(*this, from).invoke(frame))
                    throw Exception("Invalid cluster control");
        }
        else {                  // Connection data or control
            boost::intrusive_ptr<Connection> c =
                getConnection(ConnectionId(from, connection));
            if (type == DATA)
                c->deliverBuffer(buf);
            else {
                AMQFrame frame;
                while (frame.decode(buf))
                    c->deliver(frame);
            }
        }
    }
    catch (const std::exception& e) {
        // FIXME aconway 2008-01-30: exception handling.
        QPID_LOG(critical, "Error in cluster deliver: " << e.what());
        assert(0);
        throw;
    }
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address */*joined*/, int /*nJoined*/)
{
    QPID_LOG(debug, "Cluster change: "
             << std::make_pair(current, nCurrent)
             << std::make_pair(left, nLeft));

    Mutex::ScopedLock l(lock);
    for (int i = 0; i < nLeft; ++i) urls.erase(left[i]);
    // Add new members when their URL notice arraives.

    if (std::find(left, left+nLeft, self) != left+nLeft)
        broker = 0;       // We have left the group, this is the final config change.
    lock.notifyAll();     // Threads waiting for membership changes.
}

void Cluster::dispatch(sys::DispatchHandle& h) {
    cpg.dispatchAll();
    h.rewatch();
}

void Cluster::disconnect(sys::DispatchHandle& h) {
    h.stopWatch();
    QPID_LOG(critical, "Disconnected from cluster, shutting down");
    broker->shutdown();
}

void Cluster::urlNotice(const MemberId& m, const std::string& url) {
    urls.insert(UrlMap::value_type(m,Url(url)));
}

}} // namespace qpid::cluster



