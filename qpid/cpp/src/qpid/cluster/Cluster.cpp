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
#include "DumpClient.h"
#include "FailoverExchange.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/ClusterDumpRequestBody.h"
#include "qpid/framing/ClusterReadyBody.h"
#include "qpid/framing/ClusterConfigChangeBody.h"
#include "qpid/framing/ClusterDumpOfferBody.h"
#include "qpid/framing/ClusterShutdownBody.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/log/Statement.h"
#include "qpid/log/Helpers.h"
#include "qpid/sys/Thread.h"
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
namespace qmf = qmf::org::apache::qpid::cluster;

/**@file
   Threading notes:
   - Public functions may be called in local connection IO threads.
   see .h.
*/ 

struct ClusterDispatcher : public framing::AMQP_AllOperations::ClusterHandler {
    qpid::cluster::Cluster& cluster;
    MemberId member;
    Cluster::Lock& l;
    ClusterDispatcher(Cluster& c, const MemberId& id, Cluster::Lock& l_) : cluster(c), member(id), l(l_) {}

    void dumpRequest(const std::string& url) { cluster.dumpRequest(member, url, l); }
    void ready(const std::string& url) { cluster.ready(member, url, l); }
    void configChange(const std::string& addresses) { cluster.configChange(member, addresses, l); }
    void dumpOffer(uint64_t dumpee, const Uuid& id) { cluster.dumpOffer(member, dumpee, id, l); }
    void shutdown() { cluster.shutdown(member, l); }

    bool invoke(AMQBody& body) { return framing::invoke(*this, body).wasHandled(); }
};

Cluster::Cluster(const std::string& name_, const Url& url_, broker::Broker& b, bool quorum_, size_t readMax_, size_t writeEstimate_, size_t mcastMax) :
    broker(b),
    poller(b.getPoller()),
    cpg(*this),
    name(name_),
    myUrl(url_),
    myId(cpg.self()),
    readMax(readMax_),
    writeEstimate(writeEstimate_),
    cpgDispatchHandle(
        cpg,
        boost::bind(&Cluster::dispatch, this, _1), // read
        0,                                         // write
        boost::bind(&Cluster::disconnect, this, _1) // disconnect
    ),
    mcast(cpg, mcastMax, poller, boost::bind(&Cluster::leave, this)),
    mgmtObject(0),
    deliverQueue(boost::bind(&Cluster::delivered, this, _1), poller),
    state(INIT),
    lastSize(0),
    lastBroker(false)
{
    ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
    if (agent != 0){
        qmf::Package  packageInit(agent);
        mgmtObject = new qmf::Cluster (agent, this, &broker,name,myUrl.str());
        agent->addObject (mgmtObject);
        mgmtObject->set_status("JOINING");
    }
    broker.getKnownBrokers = boost::bind(&Cluster::getUrls, this);
    failoverExchange.reset(new FailoverExchange(this));
    cpgDispatchHandle.startWatch(poller);
    deliverQueue.start();
    QPID_LOG(notice, *this << " joining cluster " << name << " with url=" << myUrl);
    if (quorum_) quorum.init();
    cpg.join(name);
    broker.addFinalizer(boost::bind(&Cluster::brokerShutdown, this)); // Must be last for exception safety.
}

Cluster::~Cluster() {
    if (dumpThread.id()) dumpThread.join(); // Join the previous dumpthread.
}

void Cluster::insert(const boost::intrusive_ptr<Connection>& c) {
    connections.insert(c->getId(), c);
}

void Cluster::erase(ConnectionId id) {
    connections.erase(id);
}

std::vector<Url> Cluster::getUrls() const {
    Lock l(lock);
    return getUrls(l);
}

std::vector<Url> Cluster::getUrls(Lock&) const {
    return map.memberUrls();
} 

void Cluster::leave() { 
    Lock l(lock);
    leave(l);
}

void Cluster::leave(Lock&) { 
    if (state != LEFT) {
        state = LEFT;
        QPID_LOG(notice, *this << " leaving cluster " << name);
        if (mgmtObject!=0) mgmtObject->set_status("SHUTDOWN");
        if (!deliverQueue.isStopped()) deliverQueue.stop();
        try { cpg.leave(); }
        catch (const std::exception& e) {
            QPID_LOG(critical, *this << " error leaving process group: " << e.what());
        }
        try { broker.shutdown(); }
        catch (const std::exception& e) {
            QPID_LOG(critical, *this << " error during shutdown: " << e.what());
        }
    }
}

boost::intrusive_ptr<Connection> Cluster::getConnection(const ConnectionId& connectionId)  {
    boost::intrusive_ptr<Connection> cp = connections.find(connectionId);
    if (!cp && connectionId.getMember() != myId) { // New shadow connection
        std::ostringstream mgmtId;
        mgmtId << name << ":"  << connectionId;
        cp = new Connection(*this, shadowOut, mgmtId.str(), connectionId);
        connections.insert(connectionId, cp);
    }
    return cp;
}

void Cluster::deliver(          
    cpg_handle_t /*handle*/,
    cpg_name* /*group*/,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len) 
{
    Mutex::ScopedLock l(lock);
    MemberId from(nodeid, pid);
    framing::Buffer buf(static_cast<char*>(msg), msg_len);
    Event e(Event::decodeCopy(from, buf));
    if (from == myId) // Record self-deliveries for flow control.
        mcast.selfDeliver(e);
    deliver(e, l);
}

void Cluster::deliver(const Event& e, Lock&) {
    if (state == LEFT) return;
    QPID_LOG(trace, *this << " PUSH: " << e);
    deliverQueue.push(e);
}

// Entry point: called when deliverQueue has events to process.
void Cluster::delivered(PollableEventQueue::Queue& events) {
    try {
        for_each(events.begin(), events.end(), boost::bind(&Cluster::deliveredEvent, this, _1));
        events.clear();
    } catch (const std::exception& e) {
        QPID_LOG(critical, *this << " error in cluster delivery: " << e.what());
        leave();
    }
}

void Cluster::deliveredEvent(const Event& e) {
    Buffer buf(e);
    AMQFrame frame;
    if (e.isCluster())  {
        while (frame.decode(buf)) {
            QPID_LOG(trace, *this << " DLVR: " << e << " " << frame);
            Mutex::ScopedLock l(lock); // FIXME aconway 2008-12-11: lock scope too big?
            ClusterDispatcher dispatch(*this, e.getMemberId(), l);
            if (!framing::invoke(dispatch, *frame.getBody()).wasHandled())
                throw Exception(QPID_MSG("Invalid cluster control"));
        }
    }
    else {                      // e.isConnection()
        if (state == NEWBIE) {
            QPID_LOG(trace, *this << " DROP: " << e);
        }
        else {
            boost::intrusive_ptr<Connection> connection = getConnection(e.getConnectionId());
            if (!connection) return;
            if (e.getType() == CONTROL) {              
                while (frame.decode(buf)) {
                    QPID_LOG(trace, *this << " DLVR: " << e << " " << frame);
                    connection->delivered(frame);
                }
            }
            else  {
                QPID_LOG(trace, *this << " DLVR: " << e);
                connection->deliverBuffer(buf);
            }
        }
    }
}

struct AddrList {
    const cpg_address* addrs;
    int count;
    const char *prefix, *suffix;
    AddrList(const cpg_address* a, int n, const char* p="", const char* s="")
        : addrs(a), count(n), prefix(p), suffix(s) {}
};

ostream& operator<<(ostream& o, const AddrList& a) {
    if (!a.count) return o;
    o << a.prefix;
    for (const cpg_address* p = a.addrs; p < a.addrs+a.count; ++p) {
        const char* reasonString;
        switch (p->reason) {
          case CPG_REASON_JOIN: reasonString =  " (joined) "; break;
          case CPG_REASON_LEAVE: reasonString =  " (left) "; break;
          case CPG_REASON_NODEDOWN: reasonString =  " (node-down) "; break;
          case CPG_REASON_NODEUP: reasonString =  " (node-up) "; break;
          case CPG_REASON_PROCDOWN: reasonString =  " (process-down) "; break;
          default: reasonString = " ";
        }
        qpid::cluster::MemberId member(*p);
        o << member << reasonString;
    }
    return o << a.suffix;
}

// Entry point: called by IO to dispatch CPG events.
void Cluster::dispatch(sys::DispatchHandle& h) {
    try {
        cpg.dispatchAll();
        h.rewatch();
    } catch (const std::exception& e) {
        QPID_LOG(critical, *this << " error in cluster dispatch: " << e.what());
        leave();
    }
}

// Entry point: called if disconnected from  CPG.
void Cluster::disconnect(sys::DispatchHandle& ) {
    QPID_LOG(critical, *this << " error disconnected from cluster");
    try {
        broker.shutdown();
    } catch (const std::exception& e) {
        QPID_LOG(error, *this << " error in shutdown: " << e.what());
    }
}

void Cluster::configChange ( 
    cpg_handle_t /*handle*/,
    cpg_name */*group*/,
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address */*joined*/, int /*nJoined*/)
{
    Mutex::ScopedLock l(lock);
    QPID_LOG(debug, *this << " config change: " << AddrList(current, nCurrent) 
             << AddrList(left, nLeft, "( ", ")"));
    std::string addresses;
    for (cpg_address* p = current; p < current+nCurrent; ++p) 
        addresses.append(MemberId(*p).str());
    deliver(Event::control(ClusterConfigChangeBody(ProtocolVersion(), addresses), myId), l);
}

void Cluster::configChange(const MemberId&, const std::string& addresses, Lock& l) {
    bool memberChange = map.configChange(addresses);
    if (state == LEFT) return;
    
    if (!map.isAlive(myId)) {  // Final config change.
        leave(l);
        return;
    }

    if (state == INIT) {        // First configChange
        if (map.aliveCount() == 1) {
            setClusterId(true);
            // FIXME aconway 2008-12-11: Centralize transition to READY and associated actions eg mcast.release()
            state = READY;
            mcast.release();
            QPID_LOG(notice, *this << " first in cluster");
            if (mgmtObject!=0) mgmtObject->set_status("ACTIVE");
            map = ClusterMap(myId, myUrl, true);
            memberUpdate(l);
        }
        else {                  // Joining established group.
            state = NEWBIE;
            QPID_LOG(info, *this << " joining cluster: " << map);
            mcast.mcastControl(ClusterDumpRequestBody(ProtocolVersion(), myUrl.str()), myId);
        }
    }
    else if (state >= READY && memberChange)
        memberUpdate(l);
}




void Cluster::tryMakeOffer(const MemberId& id, Lock& ) {
    if (state == READY && map.isNewbie(id)) {
        state = OFFER;
        QPID_LOG(info, *this << " send dump-offer to " << id);
        mcast.mcastControl(ClusterDumpOfferBody(ProtocolVersion(), id, clusterId), myId);
    }
}

// Called from Broker::~Broker when broker is shut down.  At this
// point we know the poller has stopped so no poller callbacks will be
// invoked. We must ensure that CPG has also shut down so no CPG
// callbacks will be invoked.
// 
void Cluster::brokerShutdown()  {
    QPID_LOG(notice, *this << " shutting down ");
    if (state != LEFT) {
        try { cpg.shutdown(); }
        catch (const std::exception& e) {
            QPID_LOG(error, *this << " during shutdown: " << e.what());
        }
    }
    delete this;
}

void Cluster::dumpRequest(const MemberId& id, const std::string& url, Lock& l) {
    map.dumpRequest(id, url);
    tryMakeOffer(id, l);
}

void Cluster::ready(const MemberId& id, const std::string& url, Lock& l) {
    if (map.ready(id, Url(url))) 
        memberUpdate(l);
    if (state == CATCHUP && id == myId) {
        state = READY;
        mcast.release();
        QPID_LOG(notice, *this << " caught up, active cluster member");
        if (mgmtObject!=0) mgmtObject->set_status("ACTIVE");
        mcast.release();
    }
}

void Cluster::dumpOffer(const MemberId& dumper, uint64_t dumpeeInt, const Uuid& uuid, Lock& l) {
    if (state == LEFT) return;
    MemberId dumpee(dumpeeInt);
    boost::optional<Url> url = map.dumpOffer(dumper, dumpee);
    if (dumper == myId) {
        assert(state == OFFER);
        if (url) {              // My offer was first.
            dumpStart(dumpee, *url, l);
        }
        else {                  // Another offer was first.
            state = READY;
            mcast.release();
            QPID_LOG(info, *this << " cancelled dump offer to " << dumpee);
            tryMakeOffer(map.firstNewbie(), l); // Maybe make another offer.
        }
    }
    else if (dumpee == myId && url) {
        assert(state == NEWBIE);
        setClusterId(uuid);
        state = DUMPEE;
        QPID_LOG(info, *this << " receiving dump from " << dumper);
        deliverQueue.stop();
        checkDumpIn(l);
    }
}

void Cluster::dumpStart(const MemberId& dumpee, const Url& url, Lock&) {
    if (state == LEFT) return;
    assert(state == OFFER);
    state = DUMPER;
    QPID_LOG(info, *this << " stall for dump to " << dumpee << " at " << url);
    deliverQueue.stop();
    if (dumpThread.id()) dumpThread.join(); // Join the previous dumpthread.
    dumpThread = Thread(
        new DumpClient(myId, dumpee, url, broker, map, connections.values(),
                       boost::bind(&Cluster::dumpOutDone, this),
                       boost::bind(&Cluster::dumpOutError, this, _1)));
}

// Called in dump thread.
void Cluster::dumpInDone(const ClusterMap& m) {
    Lock l(lock);
    dumpedMap = m;
    checkDumpIn(l);
}

void Cluster::checkDumpIn(Lock& ) {
    if (state == LEFT) return;
    if (state == DUMPEE && dumpedMap) {
        map = *dumpedMap;
        mcast.mcastControl(ClusterReadyBody(ProtocolVersion(), myUrl.str()), myId);
        state = CATCHUP;
        QPID_LOG(info, *this << " received dump, starting catch-up");
        deliverQueue.start();
    }
}

void Cluster::dumpOutDone() {
    Monitor::ScopedLock l(lock);
    dumpOutDone(l);
}

void Cluster::dumpOutDone(Lock& l) {
    assert(state == DUMPER);
    state = READY;
    mcast.release();
    QPID_LOG(info, *this << " sent dump");
    deliverQueue.start();
    tryMakeOffer(map.firstNewbie(), l); // Try another offer
}

void Cluster::dumpOutError(const std::exception& e)  {
    Monitor::ScopedLock l(lock);
    QPID_LOG(error, *this << " error sending dump: " << e.what());    
    dumpOutDone(l);
}

void Cluster ::shutdown(const MemberId& id, Lock& l) {
    QPID_LOG(notice, *this << " received shutdown from " << id);
    leave(l);
}

ManagementObject* Cluster::GetManagementObject() const { return mgmtObject; }

Manageable::status_t Cluster::ManagementMethod (uint32_t methodId, Args&, string&) {
    Lock l(lock);
    QPID_LOG(debug, *this << " managementMethod [id=" << methodId << "]");
    switch (methodId) {
      case qmf::Cluster::METHOD_STOPCLUSTERNODE: stopClusterNode(l); break;
      case qmf::Cluster::METHOD_STOPFULLCLUSTER: stopFullCluster(l); break;
      default: return Manageable::STATUS_UNKNOWN_METHOD;
    }
    return Manageable::STATUS_OK;
}    

void Cluster::stopClusterNode(Lock& l) {
    QPID_LOG(notice, *this << " stopped by admin");
    leave(l);
}

void Cluster::stopFullCluster(Lock& ) {
    QPID_LOG(notice, *this << " shutting down cluster " << name);
    mcast.mcastControl(ClusterShutdownBody(), myId);
}

void Cluster::memberUpdate(Lock& l) {
    QPID_LOG(info, *this << " member update: " << map);
    std::vector<Url> urls = getUrls(l);
    size_t size = urls.size();
    failoverExchange->setUrls(urls);

    if (size == 1 && lastSize > 1 && state >= READY) { 
        QPID_LOG(info, *this << " last broker standing, update queue policies");
        lastBroker = true;
        broker.getQueues().updateQueueClusterState(true);
    }
    else if (size > 1 && lastBroker) {
        QPID_LOG(info, *this << " last broker standing joined by " << size-1 << " replicas, updating queue policies" << size);
        lastBroker = false;
        broker.getQueues().updateQueueClusterState(false);
    }
    lastSize = size;

    if (mgmtObject) {
        mgmtObject->set_clusterSize(size); 
        string urlstr;
        for(std::vector<Url>::iterator iter = urls.begin(); iter != urls.end(); iter++ ) {
            if (iter != urls.begin()) urlstr += "\n";
            urlstr += iter->str();
        }
        mgmtObject->set_members(urlstr);
    }

    // Close connections belonging to members that have now been excluded
    connections.update(myId, map);
}

std::ostream& operator<<(std::ostream& o, const Cluster& cluster) {
    static const char* STATE[] = { "INIT", "NEWBIE", "DUMPEE", "CATCHUP", "READY", "OFFER", "DUMPER", "LEFT" };
    return o << cluster.myId << "(" << STATE[cluster.state] << ")";
}

MemberId Cluster::getId() const {
    return myId;            // Immutable, no need to lock.
}

broker::Broker& Cluster::getBroker() const {
    return broker; // Immutable,  no need to lock.
}

void Cluster::checkQuorum() {
    if (!quorum.isQuorate()) {
        QPID_LOG(critical, *this << " disconnected from cluster quorum, shutting down");
        leave();
        throw Exception(QPID_MSG(*this << " disconnected from cluster quorum."));
    }
}

void Cluster::setClusterId(const Uuid& uuid) {
    clusterId = uuid;
    if (mgmtObject)
        mgmtObject->set_clusterID(clusterId.str());
    QPID_LOG(debug, *this << " cluster-id = " << clusterId);
}

}} // namespace qpid::cluster
