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
#include "qpid/framing/ClusterReadyBody.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/log/Statement.h"
#include "qpid/memory.h"
#include "qpid/shared_ptr.h"
#include "qmf/org/apache/qpid/cluster/Package.h"

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
using namespace qpid::cluster;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::cluster;

struct ClusterOperations : public AMQP_AllOperations::ClusterHandler {
    Cluster& cluster;
    MemberId member;
    ClusterOperations(Cluster& c, const MemberId& id) : cluster(c), member(id) {}
    bool invoke(AMQFrame& f) { return framing::invoke(*this, *f.getBody()).wasHandled(); }

    void update(const FieldTable& members, uint64_t dumping) { cluster.update(members, dumping); }
    void dumpRequest(const std::string& url) { cluster.dumpRequest(member, url); }
    void ready(const std::string& url) { cluster.ready(member, url); }
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
    ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
    if (agent != 0){
        _qmf::Package  packageInit(agent);
        mgmtObject = new _qmf::Cluster (agent, this, &broker,name.str(),url.str());
        agent->addObject (mgmtObject);
		mgmtObject->set_status("JOINING");
		mgmtObject->set_clusterSize(1);
		
		// if first cluster up set new UUID to set_clusterID() else set UUID of cluster being joined.
    }
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

void Cluster::dispatch(sys::DispatchHandle& h) {
    cpg.dispatchAll();
    h.rewatch();
}

void Cluster::disconnect(sys::DispatchHandle& ) {
    // FIXME aconway 2008-09-11: this should be logged as critical,
    // when we provide admin option to shut down cluster and let
    // members leave cleanly.
    stopClusterNode();
}

void Cluster::configChange(
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address */*joined*/, int nJoined)
{
    Mutex::ScopedLock l(lock);
    // FIXME aconway 2008-09-15: use group terminology not cluster. Member not node.
    QPID_LOG(info, "Current cluster: " << AddrList(current, nCurrent));
    QPID_LOG_IF(info, nLeft, "Left the cluster: " << AddrList(left, nLeft));
    
    map.left(left, nLeft);
    if (find(left, left+nLeft, self) != left+nLeft) { 
        // I have left the group, this is the final config change.
        QPID_LOG(notice, self << " left cluster " << name.str());
        broker.shutdown();
        return;
    }

    if (state == START) {
        if (nCurrent == 1 && *current == self) { // First in cluster.
            // First in cluster
            QPID_LOG(notice, self << " first in cluster.");
            map.add(self, url);
            ready();
        }
        return;                 
    }

    if (state == DISCARD && !map.dumper) // try another dump request.
        mcastControl(ClusterDumpRequestBody(ProtocolVersion(), url.str()), 0);

    if (nJoined && map.sendUpdate(self))  // New members need update
        mcastControl(map.toControl(), 0);

    //update mgnt stats
	if (mgmtObject!=0){
	    mgmtObject->set_clusterSize(size()+1); // cluster size is other nodes +me
		// copy urls to fieldtable ? how is the ftable packed?
		//::qpid::framing::FieldTable val;
		//std::vector<Url> vectUrl = getUrls();
		//mgmtObject->set_members(val);
	}
}

void Cluster::update(const FieldTable& members, uint64_t dumper) {
    Mutex::ScopedLock l(lock);
    map.update(members, dumper);
    QPID_LOG(debug, "Cluster update: " << map);
    if (state == START) state = DISCARD; // Got first update.
    if (state == DISCARD && !map.dumper)
        mcastControl(ClusterDumpRequestBody(ProtocolVersion(), url.str()), 0);
}

void Cluster::dumpRequest(const MemberId& dumpee, const string& urlStr) {
    Mutex::ScopedLock l(lock);
    if (map.dumper) return;     // Dump already in progress, ignore.
    map.dumper = map.first();
    if (dumpee == self && state == DISCARD) { // My turn to receive a dump.
        QPID_LOG(info, self << " receiving state dump from " << map.dumper);
        // FIXME aconway 2008-09-15: RECEIVE DUMP
        // state = CATCHUP; 
        // stall();
        // When received
        mcastControl(ClusterReadyBody(ProtocolVersion(), url.str()), 0);
        ready();
    }
    else if (map.dumper == self && state == READY) { // My turn to send the dump
        QPID_LOG(info, self << " sending state dump to " << dumpee);
        // FIXME aconway 2008-09-15: stall & send brain dump - finish DumpClient.
        // state = DUMPING;
        // stall();
        (void)urlStr;       
        // When dump complete:
        assert(map.dumper == self);
        ClusterUpdateBody b = map.toControl();
        b.setDumper(0);
        mcastControl(b, 0);
        // NB: Don't modify my own map till self-delivery.
    }
}

void Cluster::ready(const MemberId& member, const std::string& url) {
    Mutex::ScopedLock l(lock);
    map.add(member, Url(url));
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
	if (mgmtObject!=0)
	    mgmtObject->set_status("STALLED");
}

void Cluster::ready() {
    // Called with lock held
    QPID_LOG(info, self << " ready with URL " << url);
    state = READY;
    connectionEventQueue.start(poller);
    // FIXME aconway 2008-09-15: stall/unstall map?
	if (mgmtObject!=0)
	    mgmtObject->set_status("ACTIVE");
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

ManagementObject* Cluster::GetManagementObject(void) const
{
   return (ManagementObject*) mgmtObject;
}

Manageable::status_t Cluster::ManagementMethod (uint32_t methodId, Args& /*args*/, string&)
{
  Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;
  QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

  switch (methodId)
  {
  case _qmf::Cluster::METHOD_STOPCLUSTERNODE:
      stopClusterNode();
      break;
  case _qmf::Cluster::METHOD_STOPFULLCLUSTER:
      stopFullCluster();
      break;
  }

  return status;
}    

void Cluster::stopClusterNode(void)
{
    QPID_LOG(notice, self << " disconnected from cluster " << name.str());
    broker.shutdown();
}

void Cluster::stopFullCluster(void)
{
}

}} // namespace qpid::cluster
