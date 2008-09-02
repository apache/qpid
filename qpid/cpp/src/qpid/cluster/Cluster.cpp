/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
n * You may obtain a copy of the License at
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
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ClusterJoinedBody.h"
#include "qpid/log/Statement.h"
#include "qpid/memory.h"
#include "qpid/shared_ptr.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/Invoker.h"

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

// Handle cluster controls from a given member.
struct ClusterOperations :  public framing::AMQP_AllOperations::ClusterHandler {
    Cluster& cluster;
    MemberId member;
    
    ClusterOperations(Cluster& c, const MemberId& m) : cluster(c), member(m) {}

    void joined(const std::string& url) {
        cluster.joined(member, url);
    }
};
    
ostream& operator <<(ostream& out, const Cluster& cluster) {
    return out << cluster.name.str() << "-" << cluster.self;
}

ostream& operator<<(ostream& out, const Cluster::UrlMap::value_type& m) {
    return out << m.first << " at " << m.second;
}

ostream& operator <<(ostream& out, const Cluster::UrlMap& urls) {
    ostream_iterator<Cluster::UrlMap::value_type> o(out, " ");
    copy(urls.begin(), urls.end(), o);
    return out;
}

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
    ),
    deliverQueue(boost::bind(&Cluster::deliverQueueCb, this, _1, _2)),
    mcastQueue(boost::bind(&Cluster::mcastQueueCb, this, _1, _2))
{
    broker->addFinalizer(boost::bind(&Cluster::leave, this));
    QPID_LOG(trace, "Node " << self << " joining cluster: " << name_);
    cpg.join(name);
    send(AMQFrame(in_place<ClusterJoinedBody>(ProtocolVersion(), url.str())), ConnectionId(self,0));

    // Start dispatching from the poller.
    cpgDispatchHandle.startWatch(poller);
    deliverQueue.start(poller);
    mcastQueue.start(poller);
}

Cluster::~Cluster() {}

void Cluster::leave() {
    Mutex::ScopedLock l(lock);
    if (!broker) return;                               // Already left.
    // Leave is called by from Broker destructor after the poller has
    // been shut down. No dispatches can occur.
    cpg.leave(name);
    // broker is set to 0 when the final config-change is delivered.
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

void Cluster::send(const AMQFrame& frame, const ConnectionId& id) {
    QPID_LOG(trace, "MCAST [" << id << "] " << frame);
    mcastQueue.push(Message(frame, id));
}

void Cluster::mcastQueueCb(const MessageQueue::iterator& begin,
                           const MessageQueue::iterator& end)
{
    // Static is OK because there is only one cluster allowed per
    // process and only one thread in mcastQueueCb at a time.
    static char buffer[64*1024]; // FIXME aconway 2008-07-04: buffer management.
    Buffer buf(buffer, sizeof(buffer));
    for (MessageQueue::iterator i = begin; i != end; ++i) {
        AMQFrame& frame =i->first;
        ConnectionId id =i->second;
        if (buf.available() < frame.size() + sizeof(uint64_t))
            break;
        frame.encode(buf);
        encodePtr(buf, id.second);
    }
    iovec iov = { buffer, buf.getPosition() };
    cpg.mcast(name, &iov, 1);
}

size_t Cluster::size() const {
    Mutex::ScopedLock l(lock);
    return urls.size();
}

std::vector<Url> Cluster::getUrls() const {
    Mutex::ScopedLock l(lock);
    std::vector<Url> result(urls.size());
    std::transform(urls.begin(), urls.end(), result.begin(), boost::bind(&UrlMap::value_type::second, _1));
    return result;        
}

boost::intrusive_ptr<Connection> Cluster::getConnection(const ConnectionId& id) {
    boost::intrusive_ptr<Connection> c = connections[id];
    if (!c && id.first != self) { // Shadow connection
        std::ostringstream os;
        os << id;
        c = connections[id] = new Connection(*this, shadowOut, os.str(), id);
    }
    assert(c);
    return c;
}

void Cluster::deliver(
    cpg_handle_t /*handle*/,
    cpg_name* /*group*/,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len)
{
    MemberId from(nodeid, pid);
    try {
        Buffer buf(static_cast<char*>(msg), msg_len);
        while (buf.available() > 0) {
            AMQFrame frame;
            if (!frame.decode(buf))  // Not enough data.
                throw Exception("Received incomplete cluster event.");
            Connection* cp;
            decodePtr(buf, cp);
            QPID_LOG(critical, "deliverQ.push " << frame);
            deliverQueue.push(Message(frame, ConnectionId(from, cp)));
        }
    }
    catch (const std::exception& e) {
        // FIXME aconway 2008-01-30: exception handling.
        QPID_LOG(critical, "Error in cluster deliver: " << e.what());
        assert(0);
        throw;
    }
}

void Cluster::deliverQueueCb(const MessageQueue::iterator& begin,
                             const MessageQueue::iterator& end)
{
    for (MessageQueue::iterator i = begin; i != end; ++i) {
        AMQFrame& frame(i->first);
        ConnectionId connectionId(i->second);
        try {
            QPID_LOG(trace, "DLVR [" << connectionId << "]: " << frame);
            if (!broker) {
                QPID_LOG(error, "Unexpected DLVR after leaving the cluster.");
                return;
            }
            if (connectionId.getConnectionPtr()) // Connection control
                getConnection(connectionId)->deliver(frame);
            else {              // Cluster control
                ClusterOperations cops(*this, connectionId.getMember());
                bool invoked = framing::invoke(cops, *frame.getBody()).wasHandled();
                assert(invoked);
            }
        }
        catch (const std::exception& e) {
            // FIXME aconway 2008-01-30: exception handling.
            QPID_LOG(critical, "Error in cluster deliverQueueCb: " << e.what());
            assert(0);
            throw;
        }
    }
}

void Cluster::joined(const MemberId& member, const string& url) {
    Mutex::ScopedLock l(lock);
    QPID_LOG(debug, member << " has URL " << url);
    urls[member] = url;
    lock.notifyAll();
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address */*current*/, int /*nCurrent*/,
    cpg_address *left, int nLeft,
    cpg_address *joined, int nJoined)
{
    QPID_LOG(debug, "Cluster change: " << std::make_pair(joined, nJoined) << std::make_pair(left, nLeft));
    Mutex::ScopedLock l(lock);
    // We add URLs to the map in joined() we don't keep track of pre-URL members yet.
    for (int l = 0; l < nLeft; ++l) urls.erase(left[l]);

    if (std::find(left, left+nLeft, self) != left+nLeft) {
        broker = 0;       // We have left the group, this is the final config change.
        QPID_LOG(debug, "Leaving cluster " << *this);
    }
    lock.notifyAll();     // Threads waiting for url changes.  
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

void Cluster::insert(const boost::intrusive_ptr<Connection>& c) {
    Mutex::ScopedLock l(lock);
    connections[c->getId()] = c;
}

void Cluster::erase(ConnectionId id) {
    Mutex::ScopedLock l(lock);
    connections.erase(id);
}

}} // namespace qpid::cluster



