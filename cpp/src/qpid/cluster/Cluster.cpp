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
#include "qpid/framing/ClusterDumpRequestBody.h"
#include "qpid/framing/ClusterUpdateBody.h"
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
    bool invoke(AMQFrame& f) { return framing::invoke(*this, *f.getBody()).wasHandled(); }

    void dumpRequest(const std::string& u) { cluster.dumpRequest(member, u); }
    void update(const FieldTable& members,bool dumping) { cluster.update(members, dumping); }
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
    state(START)
{
    QPID_LOG(notice, self << " joining cluster " << name.str());
    broker.addFinalizer(boost::bind(&Cluster::shutdown, this));
    cpgDispatchHandle.startWatch(poller);
    cpg.join(name);
    
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

// FIXME aconway 2008-09-10: call leave from cluster admin command.
// Any other type of exit is caught in disconnect().
// 
void Cluster::leave() {
    QPID_LOG(notice, self << " leaving cluster " << name.str());
    cpg.leave(name);
    // Defer shut down to the final configChange when the group knows we've left.
}

void Cluster::mcastControl(const framing::AMQBody& body, Connection* cptr) {
    QPID_LOG(trace, "MCAST [" << self << "]: " << body);
    AMQFrame f(body);
    Event e(CONTROL, ConnectionId(self, cptr), f.size());
    Buffer buf(e);
    f.encode(buf);
    mcastEvent(e);
}

void Cluster::mcastBuffer(const char* data, size_t size, const ConnectionId& connection) {
    Event e(DATA, connection, size);
    memcpy(e.getData(), data, size);
    mcastEvent(e);
}

void Cluster::mcastEvent(const Event& e) {
    e.mcast(name, cpg);
}

size_t Cluster::size() const {
    Mutex::ScopedLock l(lock);
    return map.size();
}

std::vector<Url> Cluster::getUrls() const {
    Mutex::ScopedLock l(lock);
    return map.memberUrls();
} 

// FIXME aconway 2008-09-15: volatile for locked/unlocked functions.
boost::intrusive_ptr<Connection> Cluster::getConnection(const ConnectionId& id) {
    Mutex::ScopedLock l(lock);
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
        // Process cluster controls immediately 
        if (e.getConnectionId().getConnectionPtr() == 0)  { // Cluster control
            Buffer buf(e);
            AMQFrame frame;
            while (frame.decode(buf)) {
                QPID_LOG(trace, "DLVR [" << e.getConnectionId().getMember() << "]: " << *frame.getBody());
                if (!ClusterOperations(*this, e.getConnectionId().getMember()).invoke(frame))
                    throw Exception(QPID_MSG("Invalid cluster control"));
            }
        }
        else {
            // Process connection controls & data via the connectionEventQueue
            // unless we are in the DISCARD state, in which case ignore.
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
          case CPG_REASON_JOIN: reasonString =  " joined"; break;
          case CPG_REASON_LEAVE: reasonString =  " left";break;
          case CPG_REASON_NODEDOWN: reasonString =  " node-down";break;
          case CPG_REASON_NODEUP: reasonString =  " node-up";break;
          case CPG_REASON_PROCDOWN: reasonString =  " process-down";break;
          default: reasonString = " ";
        }
        qpid::cluster::MemberId member(*p);
        o << member << reasonString << ((p+1 < a.addrs+a.count) ? ", " : "");
    }
    return o;
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address */*joined*/, int nJoined)
{
    // FIXME aconway 2008-09-15: use group terminology not cluster. Member not node.
    QPID_LOG(notice, "Current cluster: " << AddrList(current, nCurrent));
    QPID_LOG_IF(notice, nLeft, "Left the cluster: " << AddrList(left, nLeft));
    if (find(left, left+nLeft, self) != left+nLeft) {
        // We have left the group, this is the final config change.
        QPID_LOG(notice, self << " left cluster " << name.str());
        broker.shutdown();
    }
    Mutex::ScopedLock l(lock);
    map.configChange(current, nCurrent);
    if (state == START && nCurrent == 1) {    // First in cluster
        assert(*current == self);
        assert(map.empty());
        QPID_LOG(notice, self << " first in cluster.");
        map.insert(self, url);
        ready();
    }
    else if (nJoined && self == map.first()) { // Send an update to new members.
        mcastControl(map.toControl(), 0);
    }
}

void Cluster::dispatch(sys::DispatchHandle& h) {
    cpg.dispatchAll();
    h.rewatch();
}

void Cluster::disconnect(sys::DispatchHandle& ) {
    // FIXME aconway 2008-09-11: this should be logged as critical,
    // when we provide admin option to shut down cluster and let
    // members leave cleanly.
    QPID_LOG(notice, self << " disconnected from cluster " << name.str());
    broker.shutdown();
}

void Cluster::update(const FieldTable& members, bool dumping) {
    Mutex::ScopedLock l(lock);
    map.update(members, dumping);
    QPID_LOG(info, "Cluster update:\n    " << map);
    if (state == START && dumping == false)  {
        state = DISCARD;
        mcastControl(ClusterDumpRequestBody(ProtocolVersion(), url.str()), 0);
    }
}

void Cluster::dumpRequest(const MemberId& m, const string& urlStr) {
    Mutex::ScopedLock l(lock);
    bool wasDumping = map.isDumping();
    map.setDumping(true);
    if (!wasDumping) {
        if (self == m) {        // My turn
            assert(state == DISCARD);
            // FIXME aconway 2008-09-15: RECEIVE DUMP
            // state = CATCHUP; 
            // stall();
            // When received
            map.insert(self, url);
            mcastControl(map.toControl(), 0);
            ready();
        }
        else if (state == READY && self == map.first()) { // Give the dump.
            QPID_LOG(info, self << " dumping to " << url);
            // FIXME aconway 2008-09-15: stall & send brain dump - finish DumpClient.
            // state = DUMPING;
            // stall();
            (void)urlStr;       
            // When dump complete:
            map.setDumping(false);
            mcastControl(map.toControl(), 0); 
        }
    }
}

broker::Broker& Cluster::getBroker(){ return broker; }

void Cluster::stall() {
    Mutex::ScopedLock l(lock);
    // Stop processing connection events. We still process config changes
    // and cluster controls in deliver()
    connectionEventQueue.stop();

    // FIXME aconway 2008-09-11: Flow control, we should slow down or
    // stop reading from local connections while stalled to avoid an
    // unbounded queue.
}

void Cluster::ready() {
    // Called with lock held
    QPID_LOG(info, self << " ready with URL " << url);
    state = READY;
    connectionEventQueue.start(poller);
    // FIXME aconway 2008-09-15: stall/unstall map?
}

// Called from Broker::~Broker when broker is shut down.  At this
// point we know the poller has stopped so no poller callbacks will be
// invoked. We must ensure that CPG has also shut down so no CPG
// callbacks will be invoked.
// 
void Cluster::shutdown() {
    QPID_LOG(notice, self << " shutting down.");
    try { cpg.shutdown(); }
    catch (const std::exception& e) { QPID_LOG(error, "During CPG shutdown: " << e.what()); }
    delete this;
}


}} // namespace qpid::cluster
