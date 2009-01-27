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
#include "UpdateClient.h"
#include "FailoverExchange.h"
#include "ClusterQueueHandler.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/ClusterUpdateRequestBody.h"
#include "qpid/framing/ClusterReadyBody.h"
#include "qpid/framing/ClusterConfigChangeBody.h"
#include "qpid/framing/ClusterUpdateOfferBody.h"
#include "qpid/framing/ClusterShutdownBody.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/log/Statement.h"
#include "qpid/log/Helpers.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/LatencyMetric.h"
#include "qpid/memory.h"
#include "qpid/shared_ptr.h"
#include "qmf/org/apache/qpid/cluster/Package.h"
#include "qmf/org/apache/qpid/cluster/ArgsClusterStopClusterNode.h"

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
namespace _qmf = ::qmf::org::apache::qpid::cluster;

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

    void updateRequest(const std::string& url) { cluster.updateRequest(member, url, l); }
    void ready(const std::string& url) { cluster.ready(member, url, l); }
    void configChange(const std::string& addresses) { cluster.configChange(member, addresses, l); }
    void updateOffer(uint64_t updatee, const Uuid& id) { cluster.updateOffer(member, updatee, id, l); }
    void shutdown() { cluster.shutdown(member, l); }

    bool invoke(AMQBody& body) { return framing::invoke(*this, body).wasHandled(); }
};

Cluster::Cluster(const std::string& name_, const Url& url_, broker::Broker& b, bool quorum_, size_t readMax_, size_t writeEstimate_, size_t mcastMax) :
    broker(b),
    mgmtObject(0),
    poller(b.getPoller()),
    cpg(*this),
    name(name_),
    myUrl(url_),
    myId(cpg.self()),
    readMax(readMax_),
    writeEstimate(writeEstimate_),
    mcast(cpg, mcastMax, poller, boost::bind(&Cluster::leave, this)),
    dispatcher(cpg, poller, boost::bind(&Cluster::leave, this)),
    deliverEventQueue(ClusterQueueHandler<Event>(this, boost::bind(&Cluster::deliveredEvent, this, _1), "event queue"),  poller),
    deliverFrameQueue(ClusterQueueHandler<EventFrame>(this, boost::bind(&Cluster::deliveredFrame, this, _1), "frame queue"), poller),
    state(INIT),
    lastSize(0),
    lastBroker(false),
    sequence(0)
{
    mAgent = ManagementAgent::Singleton::getInstance();
    if (mAgent != 0){
        _qmf::Package  packageInit(mAgent);
        mgmtObject = new _qmf::Cluster (mAgent, this, &broker,name,myUrl.str());
        mAgent->addObject (mgmtObject);
        mgmtObject->set_status("JOINING");
    }
    broker.getKnownBrokers = boost::bind(&Cluster::getUrls, this);
    failoverExchange.reset(new FailoverExchange(this));
    dispatcher.start();
    deliverEventQueue.start();
    deliverFrameQueue.start();
    QPID_LOG(notice, *this << " joining cluster " << name << " with url=" << myUrl);
    if (quorum_) quorum.init();
    cpg.join(name);
    broker.addFinalizer(boost::bind(&Cluster::brokerShutdown, this)); // Must be last for exception safety.
}

Cluster::~Cluster() {
    if (updateThread.id()) updateThread.join(); // Join the previous updatethread.
}

void Cluster::insert(const boost::intrusive_ptr<Connection>& c) {
    connections.insert(c->getId(), c);
}

void Cluster::erase(ConnectionId id) {
    connections.erase(id);
}

std::vector<string> Cluster::getIds() const {
    Lock l(lock);
    return getIds(l);
}

std::vector<string> Cluster::getIds(Lock&) const {
    return map.memberIds();
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
        try { cpg.leave(); }
        catch (const std::exception& e) {
            QPID_LOG(critical, *this << " error leaving process group: " << e.what());
        }
        connections.clear();
        try { broker.shutdown(); }
        catch (const std::exception& e) {
            QPID_LOG(critical, *this << " error during broker shutdown: " << e.what());
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
    e.setSequence(sequence++);
    if (from == myId)  // Record self-deliveries for flow control.
        mcast.selfDeliver(e);
    deliver(e, l);
}

void Cluster::deliver(const Event& e, Lock&) {
    if (state == LEFT) return;
    QPID_LATENCY_INIT(e);
    deliverEventQueue.push(e);
}

// Entry point: called when deliverEventQueue has events to process.
void Cluster::deliveredEvent(const Event& e) {
    QPID_LATENCY_RECORD("delivered event queue", e);
    Buffer buf(const_cast<char*>(e.getData()), e.getSize());
    boost::intrusive_ptr<Connection> connection;
    if (e.isConnection()) {
        if (state == JOINER) {
            QPID_LOG(trace, *this << " DROP: " << e);
            return;
        }
        connection = getConnection(e.getConnectionId());
        if (!connection) return;
    }
    if (e.getType() == CONTROL) {
        AMQFrame frame;
        while (frame.decode(buf)) {
            deliverFrameQueue.push(EventFrame(connection, e, frame));
        }
    }
    else if (e.getType() == DATA) { 
        connection->deliveredEvent(e, deliverFrameQueue);
    }
}

void Cluster::deliveredFrame(const EventFrame& e) {
    QPID_LOG(trace, *this << " DLVR: " << e);
    QPID_LATENCY_RECORD("delivered frame queue", e.frame);
    if (e.connection) {
        e.connection->deliveredFrame(e);
    }
    else {
        Mutex::ScopedLock l(lock); // FIXME aconway 2008-12-11: lock scope too big?
        ClusterDispatcher dispatch(*this, e.member, l);
        if (!framing::invoke(dispatch, *e.frame.getBody()).wasHandled())
            throw Exception(QPID_MSG("Invalid cluster control"));
    }
    QPID_LATENCY_RECORD("processed", e.frame);
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
            state = JOINER;
            QPID_LOG(info, *this << " joining cluster: " << map);
            mcast.mcastControl(ClusterUpdateRequestBody(ProtocolVersion(), myUrl.str()), myId);
            ClusterMap::Set members = map.getAlive();
            members.erase(myId);
            myElders = members;
            broker.getLinks().setPassive(true);
        }
    }
    else if (state >= READY && memberChange) {
        memberUpdate(l);
        myElders = ClusterMap::intersection(myElders, map.getAlive());
        if (myElders.empty()) {
            //assume we are oldest, reactive links if necessary
            broker.getLinks().setPassive(false);
        }
    }
}




void Cluster::tryMakeOffer(const MemberId& id, Lock& ) {
    if (state == READY && map.isJoiner(id)) {
        state = OFFER;
        QPID_LOG(info, *this << " send update-offer to " << id);
        mcast.mcastControl(ClusterUpdateOfferBody(ProtocolVersion(), id, clusterId), myId);
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
            QPID_LOG(error, *this << " shutting down CPG: " << e.what());
        }
    }
    delete this;
}

void Cluster::updateRequest(const MemberId& id, const std::string& url, Lock& l) {
    map.updateRequest(id, url);
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

void Cluster::updateOffer(const MemberId& updater, uint64_t updateeInt, const Uuid& uuid, Lock& l) {
    if (state == LEFT) return;
    MemberId updatee(updateeInt);
    boost::optional<Url> url = map.updateOffer(updater, updatee);
    if (updater == myId) {
        assert(state == OFFER);
        if (url) {              // My offer was first.
            updateStart(updatee, *url, l);
        }
        else {                  // Another offer was first.
            state = READY;
            mcast.release();
            QPID_LOG(info, *this << " cancelled update offer to " << updatee);
            tryMakeOffer(map.firstJoiner(), l); // Maybe make another offer.
        }
    }
    else if (updatee == myId && url) {
        assert(state == JOINER);
        setClusterId(uuid);
        state = UPDATEE;
        QPID_LOG(info, *this << " receiving update from " << updater);
        deliverEventQueue.stop();
        checkUpdateIn(l);
    }
}

void Cluster::updateStart(const MemberId& updatee, const Url& url, Lock&) {
    if (state == LEFT) return;
    assert(state == OFFER);
    state = UPDATER;
    QPID_LOG(info, *this << " stall for update to " << updatee << " at " << url);
    deliverEventQueue.stop();
    if (updateThread.id()) updateThread.join(); // Join the previous updatethread.
    updateThread = Thread(
        new UpdateClient(myId, updatee, url, broker, map, connections.values(),
                       boost::bind(&Cluster::updateOutDone, this),
                       boost::bind(&Cluster::updateOutError, this, _1)));
}

// Called in update thread.
void Cluster::updateInDone(const ClusterMap& m) {
    Lock l(lock);
    updatedMap = m;
    checkUpdateIn(l);
}

void Cluster::checkUpdateIn(Lock& ) {
    if (state == LEFT) return;
    if (state == UPDATEE && updatedMap) {
        map = *updatedMap;
        mcast.mcastControl(ClusterReadyBody(ProtocolVersion(), myUrl.str()), myId);
        state = CATCHUP;
        QPID_LOG(info, *this << " received update, starting catch-up");
        deliverEventQueue.start();
    }
}

void Cluster::updateOutDone() {
    Monitor::ScopedLock l(lock);
    updateOutDone(l);
}

void Cluster::updateOutDone(Lock& l) {
    assert(state == UPDATER);
    state = READY;
    mcast.release();
    QPID_LOG(info, *this << " sent update");
    deliverEventQueue.start();
    tryMakeOffer(map.firstJoiner(), l); // Try another offer
}

void Cluster::updateOutError(const std::exception& e)  {
    Monitor::ScopedLock l(lock);
    QPID_LOG(error, *this << " error sending update: " << e.what());    
    updateOutDone(l);
}

void Cluster ::shutdown(const MemberId& id, Lock& l) {
    QPID_LOG(notice, *this << " received shutdown from " << id);
    leave(l);
}

ManagementObject* Cluster::GetManagementObject() const { return mgmtObject; }

Manageable::status_t Cluster::ManagementMethod (uint32_t methodId, Args& args, string&) {
    Lock l(lock);
    QPID_LOG(debug, *this << " managementMethod [id=" << methodId << "]");
    switch (methodId) {
    case _qmf::Cluster::METHOD_STOPCLUSTERNODE :
        {
            _qmf::ArgsClusterStopClusterNode& iargs = (_qmf::ArgsClusterStopClusterNode&) args;
            stringstream stream;
            stream << myId;
            if (iargs.i_brokerId == stream.str())
                stopClusterNode(l);
        }
        break;
    case _qmf::Cluster::METHOD_STOPFULLCLUSTER :
        stopFullCluster(l);
        break;
    default:
        return Manageable::STATUS_UNKNOWN_METHOD;
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
    std::vector<string> ids = getIds(l);
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

    //

    if (mgmtObject) {
        mgmtObject->set_clusterSize(size); 
        string urlstr;
        for(std::vector<Url>::iterator iter = urls.begin(); iter != urls.end(); iter++ ) {
            if (iter != urls.begin()) urlstr += ";";
            urlstr += iter->str();
        }
        string idstr;
        for(std::vector<string>::iterator iter = ids.begin(); iter != ids.end(); iter++ ) {
            if (iter != ids.begin()) idstr += ";";
            idstr += (*iter);
        }
        mgmtObject->set_members(urlstr);
        mgmtObject->set_memberIDs(idstr);
    }

    // Close connections belonging to members that have now been excluded
    connections.update(myId, map);
}

std::ostream& operator<<(std::ostream& o, const Cluster& cluster) {
    static const char* STATE[] = { "INIT", "JOINER", "UPDATEE", "CATCHUP", "READY", "OFFER", "UPDATER", "LEFT" };
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
    if (mgmtObject) {
        stringstream stream;
        stream << myId;
        mgmtObject->set_clusterID(clusterId.str());
        mgmtObject->set_memberID(stream.str());
    }
    QPID_LOG(debug, *this << " cluster-id = " << clusterId);
}

}} // namespace qpid::cluster
