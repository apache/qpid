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
#include <ostream>

namespace qpid {
namespace cluster {
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;

struct ClusterOperations : public AMQP_AllOperations::ClusterHandler {
    Cluster& cluster;
    MemberId member;
    ClusterOperations(Cluster& c, const MemberId& id) : cluster(c), member(id) {}
    void urlNotice(const std::string& u) { cluster.urlNotice(member, u); }
    void ready() { cluster.ready(member); }

    void members(const framing::FieldTable& , const framing::FieldTable& , const framing::FieldTable& ) {
        assert(0); // Not passed to cluster, used to start a brain dump over TCP.
    }

    bool invoke(AMQFrame& f) { return framing::invoke(*this, *f.getBody()).wasHandled(); }

    virtual void map(const FieldTable& ,const FieldTable& ,const FieldTable& ) {
        // FIXME aconway 2008-09-12: TODO
    }

};

Cluster::Cluster(const std::string& name_, const Url& url_, broker::Broker& b) :
    broker(b),
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
    connectionEventQueue(EventQueue::forEach(boost::bind(&Cluster::connectionEvent, this, _1))),
    state(DISCARD)
{
    QPID_LOG(notice, "Cluster member " << self << " joining cluster " << name.str());
    broker.addFinalizer(boost::bind(&Cluster::shutdown, this));
    cpg.join(name);

    connectionEventQueue.start(poller);
    cpgDispatchHandle.startWatch(poller);
}

Cluster::~Cluster() {
    QPID_LOG(debug, "~Cluster()");
}

void Cluster::insert(const boost::intrusive_ptr<Connection>& c) {
    Mutex::ScopedLock l(lock);
    connections.insert(ConnectionMap::value_type(ConnectionId(self, c.get()), c));
}

void Cluster::erase(ConnectionId id) {
    Mutex::ScopedLock l(lock);
    connections.erase(id);
}

// FIXME aconway 2008-09-10: leave is currently not called,
// It should be called if we are shut down by a cluster admin command.
// Any other type of exit is caught in disconnect().
// 
void Cluster::leave() {
    QPID_LOG(notice, "Cluster member " << self << " leaving cluster " << name.str());
    cpg.leave(name);
    // Cluster will shut down in configChange when the cluster knows we've left.
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
    Event e(CONTROL, connection, frame.size());
    Buffer buf(e);
    frame.encode(buf);
    mcastEvent(e);
}

void Cluster::mcastBuffer(const char* data, size_t size, const ConnectionId& connection) {
    QPID_LOG(trace, "MCAST [" << connection << "] " << size << "bytes of data");
    Event e(DATA, connection, size);
    memcpy(e.getData(), data, size);
    mcastEvent(e);
}

void Cluster::mcastEvent(const Event& e) {
    QPID_LOG(trace, "Multicasting: " << e);
    e.mcast(name, cpg);
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
        mgmtId << name.str() << ":"  << id;
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
        Event e = Event::delivered(from, msg, msg_len);
        QPID_LOG(trace, "Cluster deliver: " << e);

        // Process cluster controls immediately 
        if (e.getConnectionId().getConnectionPtr() == 0)  { // Cluster control
            Buffer buf(e);
            AMQFrame frame;
            while (frame.decode(buf)) 
                if (!ClusterOperations(*this, e.getConnectionId().getMember()).invoke(frame))
                    throw Exception("Invalid cluster control");
        }
        else { // Process connection controls & data via the connectionEventQueue.
            if (state != DISCARD) { 
                e.setConnection(getConnection(e.getConnectionId()));
                connectionEventQueue.push(e);
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

void Cluster::connectionEvent(const Event& e) {
    Buffer buf(e);
    assert(e.getConnection());
    if (e.getType() == DATA)
        e.getConnection()->deliverBuffer(buf);
    else {              // control
        AMQFrame frame;
        while (frame.decode(buf))
            e.getConnection()->deliver(frame);
    }
}

struct AddrList {
    const cpg_address* addrs;
    int count;
    AddrList(const cpg_address* a, int n) : addrs(a), count(n) {}
};

ostream& operator<<(ostream& o, const AddrList& a) {
    for (const cpg_address* p = a.addrs; p < a.addrs+a.count; ++p) {
        const char* reasonString;
        switch (p->reason) {
          case CPG_REASON_JOIN: reasonString =  " joined "; break;
          case CPG_REASON_LEAVE: reasonString =  " left ";break;
          case CPG_REASON_NODEDOWN: reasonString =  " node-down ";break;
          case CPG_REASON_NODEUP: reasonString =  " node-up ";break;
          case CPG_REASON_PROCDOWN: reasonString =  " process-down ";break;
          default: reasonString = " ";
        }
        qpid::cluster::MemberId member(*p);
        o << member << reasonString;
    }
    return o;
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address *joined, int nJoined)
{
    QPID_LOG(info, "Cluster of " << nCurrent << ": " << AddrList(current, nCurrent) << ".\n Changes: "
             << AddrList(joined, nJoined) << AddrList(left, nLeft));
    
    if (nJoined)                // Notfiy new members of my URL.
        mcastFrame(
            AMQFrame(in_place<ClusterUrlNoticeBody>(ProtocolVersion(), url.str())),
            ConnectionId(self,0));

    if (find(left, left+nLeft, self) != left+nLeft) {
        // We have left the group, this is the final config change.
        QPID_LOG(notice, "Cluster member " << self << " left cluster " << name.str());
         broker.shutdown();
    }
    Mutex::ScopedLock l(lock);
    for (int i = 0; i < nLeft; ++i) urls.erase(left[i]);
    // Add new members when their URL notice arraives.
    lock.notifyAll();     // Threads waiting for membership changes.
}

void Cluster::dispatch(sys::DispatchHandle& h) {
    cpg.dispatchAll();
    h.rewatch();
}

void Cluster::disconnect(sys::DispatchHandle& ) {
    // FIXME aconway 2008-09-11: this should be logged as critical,
    // when we provide admin option to shut down cluster and let
    // members leave cleanly.
    QPID_LOG(notice, "Cluster member " << self << " disconnected from cluster " << name.str());
    broker.shutdown();
}

void Cluster::urlNotice(const MemberId& m, const string& url) {
    //FIXME aconway 2008-09-12: Rdo join logic using ClusterMap. Implement xml map function also.
    //FIXME aconway 2008-09-11: Note multiple meanings of my own notice -
    //from DISCARD->STALL and from STALL->READY via map.

    QPID_LOG(info, "Cluster member " << m << " has URL " << url);
    // My brain dump is up to this point, stall till it is complete.
    if (m == self && state == DISCARD) 
        state = STALL;
    urls.insert(UrlMap::value_type(m,Url(url)));
}

void Cluster::ready(const MemberId& ) {
    // FIXME aconway 2008-09-08: TODO
}

// Called from Broker::~Broker when broker is shut down.  At this
// point we know the poller has stopped so no poller callbacks will be
// invoked. We must ensure that CPG has also shut down so no CPG
// callbacks will be invoked.
// 
void Cluster::shutdown() {
    QPID_LOG(notice, "Cluster member " << self << " shutting down.");
    try { cpg.shutdown(); }
    catch (const std::exception& e) { QPID_LOG(error, "During CPG shutdown: " << e.what()); }
    delete this;
}

broker::Broker& Cluster::getBroker(){ return broker; }

void Cluster::stall() {
    // Stop processing connection events. We still process config changes
    // and cluster controls in deliver()

    // FIXME aconway 2008-09-11: Flow control, we should slow down or
    // stop reading from local connections while stalled to avoid an
    // unbounded queue.
    connectionEventQueue.stop();
}

void Cluster::unStall() {
    connectionEventQueue.start(poller);
}

}} // namespace qpid::cluster
