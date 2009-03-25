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
#include "ClusterSettings.h"
#include "Connection.h"
#include "UpdateClient.h"
#include "FailoverExchange.h"
#include "UpdateExchange.h"

#include "qpid/assert.h"
#include "qmf/org/apache/qpid/cluster/ArgsClusterStopClusterNode.h"
#include "qmf/org/apache/qpid/cluster/Package.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/SessionState.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/ClusterConfigChangeBody.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/framing/ClusterReadyBody.h"
#include "qpid/framing/ClusterShutdownBody.h"
#include "qpid/framing/ClusterUpdateOfferBody.h"
#include "qpid/framing/ClusterUpdateRequestBody.h"
#include "qpid/log/Helpers.h"
#include "qpid/log/Statement.h"
#include "qpid/management/IdAllocator.h"
#include "qpid/management/ManagementBroker.h"
#include "qpid/memory.h"
#include "qpid/shared_ptr.h"
#include "qpid/sys/LatencyMetric.h"
#include "qpid/sys/Thread.h"

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

struct ClusterDispatcher : public framing::AMQP_AllOperations::ClusterHandler {
    qpid::cluster::Cluster& cluster;
    MemberId member;
    Cluster::Lock& l;
    ClusterDispatcher(Cluster& c, const MemberId& id, Cluster::Lock& l_) : cluster(c), member(id), l(l_) {}

    void updateRequest(const std::string& url) { cluster.updateRequest(member, url, l); }
    void ready(const std::string& url) { cluster.ready(member, url, l); }
    void configChange(const std::string& addresses) { cluster.configChange(member, addresses, l); }
    void updateOffer(uint64_t updatee, const Uuid& id) { cluster.updateOffer(member, updatee, id, l); }
    void messageExpired(uint64_t id) { cluster.messageExpired(member, id, l); }
    void shutdown() { cluster.shutdown(member, l); }

    bool invoke(AMQBody& body) { return framing::invoke(*this, body).wasHandled(); }
};

Cluster::Cluster(const ClusterSettings& set, broker::Broker& b) :
    settings(set), 
    broker(b),
    mgmtObject(0),
    poller(b.getPoller()),
    cpg(*this),
    name(settings.name),
    myUrl(settings.url.empty() ? Url() : Url(settings.url)),
    self(cpg.self()),
    readMax(settings.readMax),
    writeEstimate(settings.writeEstimate),
    expiryPolicy(new ExpiryPolicy(mcast, self, broker.getTimer())),
    mcast(cpg, poller, boost::bind(&Cluster::leave, this)),
    dispatcher(cpg, poller, boost::bind(&Cluster::leave, this)),
    deliverEventQueue(boost::bind(&Cluster::deliveredEvent, this, _1),
                      boost::bind(&Cluster::leave, this),
                      "Error decoding events",
                      poller),
    deliverFrameQueue(boost::bind(&Cluster::deliveredFrame, this, _1),
                      boost::bind(&Cluster::leave, this),
                      "Error delivering frames",
                      poller),
    initialized(false),
    decoder(boost::bind(&Cluster::deliverFrame, this, _1)),
    discarding(true),
    state(INIT),
    lastSize(0),
    lastBroker(false)
{
    mAgent = ManagementAgent::Singleton::getInstance();
    if (mAgent != 0){
        _qmf::Package  packageInit(mAgent);
        mgmtObject = new _qmf::Cluster (mAgent, this, &broker,name,myUrl.str());
        mAgent->addObject (mgmtObject);
        mgmtObject->set_status("JOINING");
    }

    // Failover exchange provides membership updates to clients.
    failoverExchange.reset(new FailoverExchange(this));
    broker.getExchanges().registerExchange(failoverExchange);

    // Update exchange is used during updates to replicate messages without modifying delivery-properties.exchange.
    broker.getExchanges().registerExchange(boost::shared_ptr<broker::Exchange>(new UpdateExchange(this)));

    if (settings.quorum) quorum.init();
    cpg.join(name);
    // pump the CPG dispatch manually till we get initialized. 
    while (!initialized)
        cpg.dispatchOne();
}

Cluster::~Cluster() {
    if (updateThread.id()) updateThread.join(); // Join the previous updatethread.
}

void Cluster::initialize() {
    if (myUrl.empty())
        myUrl = Url::getIpAddressesUrl(broker.getPort(broker::Broker::TCP_TRANSPORT));
    QPID_LOG(notice, *this << " joining cluster " << name << " with url=" << myUrl);
    broker.getKnownBrokers = boost::bind(&Cluster::getUrls, this);
    broker.setExpiryPolicy(expiryPolicy);
    dispatcher.start();
    deliverEventQueue.start();
    deliverFrameQueue.start();
    // Add finalizer last for exception safety.
    broker.addFinalizer(boost::bind(&Cluster::brokerShutdown, this)); 
}

// Called in connection thread to insert a client connection.
void Cluster::addLocalConnection(const boost::intrusive_ptr<Connection>& c) {
    localConnections.insert(c);
}

// Called in connection thread to insert an updated shadow connection.
void Cluster::addShadowConnection(const boost::intrusive_ptr<Connection>& c) {
    // Safe to use connections here because we're pre-catchup, either
    // discarding or stalled, so deliveredFrame is not processing any
    // connection events.
    assert(discarding);         
    connections.insert(ConnectionMap::value_type(c->getId(), c));
}

// Called by Connection::deliverClose() in deliverFrameQueue thread.
void Cluster::erase(const ConnectionId& id) {
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
        try { broker.shutdown(); }
        catch (const std::exception& e) {
            QPID_LOG(critical, *this << " error during broker shutdown: " << e.what());
        }
    }
}

// Deliver CPG message.
void Cluster::deliver(
    cpg_handle_t /*handle*/,
    cpg_name* /*group*/,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len) 
{
    MemberId from(nodeid, pid);
    framing::Buffer buf(static_cast<char*>(msg), msg_len);
    Event e(Event::decodeCopy(from, buf));
    if (from == self)  // Record self-deliveries for flow control.
        mcast.selfDeliver(e);
    deliverEvent(e);
}

void Cluster::deliverEvent(const Event& e) {
    deliverEventQueue.push(e);
}

void Cluster::deliverFrame(const EventFrame& e) {
    deliverFrameQueue.push(e);
}

// Handler for deliverEventQueue.
// This thread decodes frames from events.
void Cluster::deliveredEvent(const Event& e) {
        QPID_LOG(trace, *this << " DLVR: " << e);
    if (e.isCluster()) {
        EventFrame ef(e, e.getFrame());
        // Stop the deliverEventQueue on update offers.
        // This preserves the connection decoder fragments for an update.
        ClusterUpdateOfferBody* offer = dynamic_cast<ClusterUpdateOfferBody*>(ef.frame.getBody());
        if (offer)
            deliverEventQueue.stop();
        deliverFrame(ef);
    }
    else if(!discarding) {    
        if (e.isControl())
            deliverFrame(EventFrame(e, e.getFrame()));
        else
            decoder.decode(e, e.getData());
}
    else // Discard connection events if discarding is set.
        QPID_LOG(trace, *this << " DROP: " << e);
}

// Handler for deliverFrameQueue.
// This thread executes the main logic.
void Cluster::deliveredFrame(const EventFrame& e) {
    Mutex::ScopedLock l(lock);
    if (e.isCluster()) {
        QPID_LOG(trace, *this << " DLVR: " << e);
        ClusterDispatcher dispatch(*this, e.connectionId.getMember(), l);
        if (!framing::invoke(dispatch, *e.frame.getBody()).wasHandled())
            throw Exception(QPID_MSG("Invalid cluster control"));
    }
    else if (state >= CATCHUP) {
        QPID_LOG(trace, *this << " DLVR:  " << e);
        ConnectionPtr connection = getConnection(e.connectionId, l);
        if (connection)
            connection->deliveredFrame(e);
    }
    else // Drop connection frames while state < CATCHUP
        QPID_LOG(trace, *this << " DROP: " << e);        
}

// Called in deliverFrameQueue thread
ConnectionPtr Cluster::getConnection(const ConnectionId& id, Lock&) {
    ConnectionPtr cp;
    ConnectionMap::iterator i = connections.find(id);
    if (i != connections.end())
        cp = i->second;
    else {
        if(id.getMember() == self) 
            cp = localConnections.getErase(id);
        else {
            // New remote connection, create a shadow.
            std::ostringstream mgmtId;
            mgmtId << id;
            cp = new Connection(*this, shadowOut, mgmtId.str(), id);
        }
        if (cp)
            connections.insert(ConnectionMap::value_type(id, cp));
    }
    return cp;
}

Cluster::ConnectionVector Cluster::getConnections(Lock&) {
    ConnectionVector result(connections.size());
    std::transform(connections.begin(), connections.end(), result.begin(),
                   boost::bind(&ConnectionMap::value_type::second, _1));
    return result;
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
    if (state == INIT) {        // First config change.
        // Recover only if we are first in cluster.
        broker.setRecovery(nCurrent == 1);
        initialized = true;
    }
    QPID_LOG(debug, *this << " config change: " << AddrList(current, nCurrent) 
             << AddrList(left, nLeft, "( ", ")"));
    std::string addresses;
    for (cpg_address* p = current; p < current+nCurrent; ++p) 
        addresses.append(MemberId(*p).str());
    deliverEvent(Event::control(ClusterConfigChangeBody(ProtocolVersion(), addresses), self));
}

void Cluster::setReady(Lock&) {
    state = READY;
    if (mgmtObject!=0) mgmtObject->set_status("ACTIVE");
    mcast.release();
    broker.getQueueEvents().enable();
}

void Cluster::configChange(const MemberId&, const std::string& addresses, Lock& l) {
    bool memberChange = map.configChange(addresses);
    if (state == LEFT) return;
    
    if (!map.isAlive(self)) {  // Final config change.
        leave(l);
        return;
    }

    if (state == INIT) {        // First configChange
        if (map.aliveCount() == 1) {
            setClusterId(true, l);
            discarding = false;
            setReady(l);
            map = ClusterMap(self, myUrl, true);
            memberUpdate(l);
            QPID_LOG(notice, *this << " first in cluster");
        }
        else {                  // Joining established group.
            state = JOINER;
            QPID_LOG(info, *this << " joining cluster: " << map);
            mcast.mcastControl(ClusterUpdateRequestBody(ProtocolVersion(), myUrl.str()), self);
            elders = map.getAlive();
            elders.erase(self);
            broker.getLinks().setPassive(true);
            broker.getQueueEvents().disable();
        }
    } 
    else if (state >= CATCHUP && memberChange) {
        memberUpdate(l);
        elders = ClusterMap::intersection(elders, map.getAlive());
        if (elders.empty()) {
            //assume we are oldest, reactive links if necessary
            broker.getLinks().setPassive(false);
        }
    }
}

void Cluster::makeOffer(const MemberId& id, Lock& ) {
    if (state == READY && map.isJoiner(id)) {
        state = OFFER;
        QPID_LOG(info, *this << " send update-offer to " << id);
        mcast.mcastControl(ClusterUpdateOfferBody(ProtocolVersion(), id, clusterId), self);
    }
}

// Called from Broker::~Broker when broker is shut down.  At this
// point we know the poller has stopped so no poller callbacks will be
// invoked. We must ensure that CPG has also shut down so no CPG
// callbacks will be invoked.
// 
void Cluster::brokerShutdown()  {
    try { cpg.shutdown(); }
    catch (const std::exception& e) {
        QPID_LOG(error, *this << " shutting down CPG: " << e.what());
    }
    delete this;
}

void Cluster::updateRequest(const MemberId& id, const std::string& url, Lock& l) {
    map.updateRequest(id, url);
    makeOffer(id, l);
}

void Cluster::ready(const MemberId& id, const std::string& url, Lock& l) {
    if (map.ready(id, Url(url))) 
        memberUpdate(l);
    if (state == CATCHUP && id == self) {
        setReady(l);
        QPID_LOG(notice, *this << " caught up, active cluster member");
    }
}

void Cluster::updateOffer(const MemberId& updater, uint64_t updateeInt, const Uuid& uuid, Lock& l) {
    // NOTE: deliverEventQueue has been stopped at the update offer by
    // deliveredEvent in case an update is required.
    if (state == LEFT) return;
    MemberId updatee(updateeInt);
    boost::optional<Url> url = map.updateOffer(updater, updatee);
    if (updater == self) {
        assert(state == OFFER);
        if (url)               // My offer was first.
            updateStart(updatee, *url, l);
        else {                  // Another offer was first.
            deliverEventQueue.start(); // Don't need to update
            setReady(l);
            QPID_LOG(info, *this << " cancelled update offer to " << updatee);
            makeOffer(map.firstJoiner(), l); // Maybe make another offer.
        }
    }
    else if (updatee == self && url) {
        assert(state == JOINER);
        setClusterId(uuid, l);
        state = UPDATEE;
        QPID_LOG(info, *this << " receiving update from " << updater);
        checkUpdateIn(l);
    }
    else
        deliverEventQueue.start(); // Don't need to update
}

void Cluster::updateStart(const MemberId& updatee, const Url& url, Lock& l) {
    // NOTE: deliverEventQueue is already stopped at the stall point by deliveredEvent.
    if (state == LEFT) return;
    assert(state == OFFER);
    state = UPDATER;
    QPID_LOG(info, *this << " sending update to " << updatee << " at " << url);
    if (updateThread.id())
        updateThread.join(); // Join the previous updateThread to avoid leaks.
    client::ConnectionSettings cs;
    cs.username = settings.username;
    cs.password = settings.password;
    cs.mechanism = settings.mechanism;
    updateThread = Thread(
        new UpdateClient(self, updatee, url, broker, map, *expiryPolicy, getConnections(l), decoder,
                         boost::bind(&Cluster::updateOutDone, this),
                         boost::bind(&Cluster::updateOutError, this, _1),
                         cs));
}

// Called in update thread.
void Cluster::updateInDone(const ClusterMap& m) {
    Lock l(lock);
    updatedMap = m;
    checkUpdateIn(l);
}

void Cluster::checkUpdateIn(Lock&) {
    if (state == UPDATEE && updatedMap) {
        map = *updatedMap;
        mcast.mcastControl(ClusterReadyBody(ProtocolVersion(), myUrl.str()), self);
        state = CATCHUP;
        discarding = false;     // ok to set, we're stalled for update.
        QPID_LOG(info, *this << " received update, starting catch-up");
        deliverEventQueue.start();
    }
}

void Cluster::updateOutDone() {
    Monitor::ScopedLock l(lock);
    updateOutDone(l);
}

void Cluster::updateOutDone(Lock& l) {
    QPID_LOG(info, *this << " sent update");
    assert(state == UPDATER);
    state = READY;
    mcast.release();
    deliverEventQueue.start();       // Start processing events again.
    makeOffer(map.firstJoiner(), l); // Try another offer
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
            stream << self;
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
    mcast.mcastControl(ClusterShutdownBody(), self);
}

void Cluster::memberUpdate(Lock& l) {
    QPID_LOG(info, *this << " member update: " << map);
    std::vector<Url> urls = getUrls(l);
    std::vector<string> ids = getIds(l);
    size_t size = urls.size();
    failoverExchange->setUrls(urls);

    if (size == 1 && lastSize > 1 && state >= CATCHUP) { 
        QPID_LOG(notice, *this << " last broker standing, update queue policies");
        lastBroker = true;
        broker.getQueues().updateQueueClusterState(true);
    }
    else if (size > 1 && lastBroker) {
        QPID_LOG(notice, *this << " last broker standing joined by " << size-1 << " replicas, updating queue policies" << size);
        lastBroker = false;
        broker.getQueues().updateQueueClusterState(false);
    }
    lastSize = size;

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

    // Erase connections belonging to members that have left the cluster.
    ConnectionMap::iterator i = connections.begin();
    while (i != connections.end()) {
        ConnectionMap::iterator j = i++;
        MemberId m = j->second->getId().getMember();
        if (m != self && !map.isMember(m))
            connections.erase(j);
    }
}

std::ostream& operator<<(std::ostream& o, const Cluster& cluster) {
    static const char* STATE[] = { "INIT", "JOINER", "UPDATEE", "CATCHUP", "READY", "OFFER", "UPDATER", "LEFT" };
    return o << cluster.self << "(" << STATE[cluster.state] << ")";
}

MemberId Cluster::getId() const {
    return self;            // Immutable, no need to lock.
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

void Cluster::setClusterId(const Uuid& uuid, Lock&) {
    clusterId = uuid;
    if (mgmtObject) {
        stringstream stream;
        stream << self;
        mgmtObject->set_clusterID(clusterId.str());
        mgmtObject->set_memberID(stream.str());
    }
    QPID_LOG(debug, *this << " cluster-id = " << clusterId);
}

void Cluster::messageExpired(const MemberId&, uint64_t id, Lock&) {
    expiryPolicy->deliverExpire(id);
}

}} // namespace qpid::cluster
