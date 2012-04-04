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

/**
 * <h1>CLUSTER IMPLEMENTATION OVERVIEW</h1>
 *
 * The cluster works on the principle that if all members of the
 * cluster receive identical input, they will all produce identical
 * results. cluster::Connections intercept data received from clients
 * and multicast it via CPG. The data is processed (passed to the
 * broker::Connection) only when it is received from CPG in cluster
 * order. Each cluster member has Connection objects for directly
 * connected clients and "shadow" Connection objects for connections
 * to other members.
 *
 * This assumes that all broker actions occur deterministically in
 * response to data arriving on client connections. There are two
 * situations where this assumption fails:
 *  - sending data in response to polling local connections for writabiliy.
 *  - taking actions based on a timer or timestamp comparison.
 *
 * IMPORTANT NOTE: any time code is added to the broker that uses timers,
 * the cluster may need to be updated to take account of this.
 *
 *
 * USE OF TIMESTAMPS IN THE BROKER
 *
 * The following are the current areas where broker uses timers or timestamps:
 *
 * - Producer flow control: broker::SemanticState uses
 *   connection::getClusterOrderOutput.  a FrameHandler that sends
 *   frames to the client via the cluster. Used by broker::SessionState
 *
 * - QueueCleaner, Message TTL: uses ExpiryPolicy, which is
 *   implemented by cluster::ExpiryPolicy.
 *
 * - Connection heartbeat: sends connection controls, not part of
 *   session command counting so OK to ignore.
 *
 * - LinkRegistry: only cluster elder is ever active for links.
 *
 * - management::ManagementBroker: uses MessageHandler supplied by  cluster
 *   to send messages to the broker via the cluster.
 *
 * cluster::ExpiryPolicy uses cluster time.
 *
 * ClusterTimer implements periodic timed events in the cluster context.
 * Used for:
 * - periodic management events.
 * - DTX transaction timeouts.
 *
 * <h1>CLUSTER PROTOCOL OVERVIEW</h1>
 *
 * Messages sent to/from CPG are called Events.
 *
 * An Event carries a ConnectionId, which includes a MemberId and a
 * connection number.
 *
 * Events are either
 *  - Connection events: non-0 connection number and are associated with a connection.
 *  - Cluster Events: 0 connection number, are not associated with a connection.
 *
 * Events are further categorized as:
 *  - Control: carries method frame(s) that affect cluster behavior.
 *  - Data: carries raw data received from a client connection.
 *
 * The cluster defines extensions to the AMQP command set in ../../../xml/cluster.xml
 * which defines two classes:
 *  - cluster: cluster control information.
 *  - cluster.connection: control information for a specific connection.
 *
 * The following combinations are legal:
 *  - Data frames carrying connection data.
 *  - Cluster control events carrying cluster commands.
 *  - Connection control events carrying cluster.connection commands.
 *  - Connection control events carrying non-cluster frames: frames sent to the client.
 *    e.g. flow-control frames generated on a timer.
 *
 * <h1>CLUSTER INITIALIZATION OVERVIEW</h1>
 *
 * @see InitialStatusMap
 *
 * When a new member joins the CPG group, all members (including the
 * new one) multicast their "initial status." The new member is in
 * PRE_INIT mode until it gets a complete set of initial status
 * messages from all cluster members. In a newly-forming cluster is
 * then in INIT mode until the configured cluster-size members have
 * joined.
 *
 * The newcomer uses initial status to determine
 *  - The cluster UUID
 *  - Am I speaking the correct version of the cluster protocol?
 *  - Do I need to get an update from an existing active member?
 *  - Can I recover from my own store?
 *
 * Pre-initialization happens in the Cluster constructor (plugin
 * early-init phase) because it needs to set the recovery flag before
 * the store initializes. This phase lasts until inital-status is
 * received for all active members. The PollableQueues and Multicaster
 * are in "bypass" mode during this phase since the poller has not
 * started so there are no threads to serve pollable queues.
 *
 * The remaining initialization happens in Cluster::initialize() or,
 * if cluster-size=N is specified, in the deliver thread when an
 * initial-status control is delivered that brings the total to N.
 */
#include "qpid/Exception.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/sys/ClusterSafe.h"
#include "qpid/cluster/ClusterSettings.h"
#include "qpid/cluster/Connection.h"
#include "qpid/cluster/UpdateClient.h"
#include "qpid/cluster/RetractClient.h"
#include "qpid/cluster/FailoverExchange.h"
#include "qpid/cluster/UpdateDataExchange.h"
#include "qpid/cluster/UpdateExchange.h"
#include "qpid/cluster/ClusterTimer.h"
#include "qpid/cluster/CredentialsExchange.h"
#include "qpid/cluster/UpdateClient.h"

#include "qpid/assert.h"
#include "qmf/org/apache/qpid/cluster/ArgsClusterStopClusterNode.h"
#include "qmf/org/apache/qpid/cluster/Package.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/SignalHandler.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/ClusterConfigChangeBody.h"
#include "qpid/framing/ClusterClockBody.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionAbortBody.h"
#include "qpid/framing/ClusterRetractOfferBody.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/framing/ClusterReadyBody.h"
#include "qpid/framing/ClusterShutdownBody.h"
#include "qpid/framing/ClusterUpdateOfferBody.h"
#include "qpid/framing/ClusterUpdateRequestBody.h"
#include "qpid/framing/ClusterConnectionAnnounceBody.h"
#include "qpid/framing/ClusterErrorCheckBody.h"
#include "qpid/framing/ClusterTimerWakeupBody.h"
#include "qpid/framing/ClusterDeliverToQueueBody.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Helpers.h"
#include "qpid/log/Statement.h"
#include "qpid/UrlArray.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/memory.h"
#include "qpid/sys/Thread.h"

#include <boost/shared_ptr.hpp>
#include <boost/bind.hpp>
#include <boost/cast.hpp>
#include <boost/current_function.hpp>
#include <algorithm>
#include <iterator>
#include <map>
#include <ostream>


namespace qpid {
namespace cluster {
using namespace qpid;
using namespace qpid::framing;
using namespace qpid::sys;
using namespace qpid::cluster;
using namespace framing::cluster;
using namespace std;
using management::ManagementAgent;
using management::ManagementObject;
using management::Manageable;
using management::Args;
namespace _qmf = ::qmf::org::apache::qpid::cluster;
namespace arg=client::arg;

/**
 * NOTE: must increment this number whenever any incompatible changes in
 * cluster protocol/behavior are made. It allows early detection and
 * sensible reporting of an attempt to mix different versions in a
 * cluster.
 *
 * Currently use SVN revision to avoid clashes with versions from
 * different branches.
 */
const uint32_t Cluster::CLUSTER_VERSION = 1207877;

struct ClusterDispatcher : public framing::AMQP_AllOperations::ClusterHandler {
    qpid::cluster::Cluster& cluster;
    MemberId member;
    Cluster::Lock& l;
    ClusterDispatcher(Cluster& c, const MemberId& id, Cluster::Lock& l_) : cluster(c), member(id), l(l_) {}

    void updateRequest(const std::string& url) { cluster.updateRequest(member, url, l); }

    void initialStatus(uint32_t version, bool active, const Uuid& clusterId,
                       uint8_t storeState, const Uuid& shutdownId,
                       const std::string& firstConfig, const framing::Array& urls)
    {
        cluster.initialStatus(
            member, version, active, clusterId,
            framing::cluster::StoreState(storeState), shutdownId,
            firstConfig, urls, l);
    }
    void ready(const std::string& url) {
        cluster.ready(member, url, l);
    }
    void configChange(const std::string& members,
                      const std::string& left,
                      const std::string& joined)
    {
        cluster.configChange(member, members, left, joined, l);
    }
    void updateOffer(uint64_t updatee) {
        cluster.updateOffer(member, updatee, l);
    }
    void retractOffer(uint64_t updatee) { cluster.retractOffer(member, updatee, l); }
    void errorCheck(uint8_t type, const framing::SequenceNumber& frameSeq) {
        cluster.errorCheck(member, type, frameSeq, l);
    }
    void timerWakeup(const std::string& name) { cluster.timerWakeup(member, name, l); }
    void timerDrop(const std::string& name) { cluster.timerDrop(member, name, l); }
    void shutdown(const Uuid& id) { cluster.shutdown(member, id, l); }
    void deliverToQueue(const std::string& queue, const std::string& message) {
        cluster.deliverToQueue(queue, message, l);
    }
    void clock(uint64_t time) { cluster.clock(time, l); }
    bool invoke(AMQBody& body) { return framing::invoke(*this, body).wasHandled(); }
};

Cluster::Cluster(const ClusterSettings& set, broker::Broker& b) :
    settings(set),
    broker(b),
    mgmtObject(0),
    poller(b.getPoller()),
    cpg(*this),
    name(settings.name),
    self(cpg.self()),
    clusterId(true),
    mAgent(0),
    expiryPolicy(new ExpiryPolicy(*this)),
    mcast(cpg, poller, boost::bind(&Cluster::leave, this)),
    dispatcher(cpg, poller, boost::bind(&Cluster::leave, this)),
    deliverEventQueue(boost::bind(&Cluster::deliveredEvent, this, _1),
                      boost::bind(&Cluster::leave, this),
                      "Error decoding events, may indicate a broker version mismatch",
                      poller),
    deliverFrameQueue(boost::bind(&Cluster::deliveredFrame, this, _1),
                      boost::bind(&Cluster::leave, this),
                      "Error delivering frames",
                      poller),
    failoverExchange(new FailoverExchange(broker.GetVhostObject(), &broker)),
    credentialsExchange(new CredentialsExchange(*this)),
    quorum(boost::bind(&Cluster::leave, this)),
    decoder(boost::bind(&Cluster::deliverFrame, this, _1)),
    discarding(true),
    state(PRE_INIT),
    initMap(self, settings.size),
    store(broker.getDataDir().getPath()),
    elder(false),
    lastAliveCount(0),
    lastBroker(false),
    updateRetracted(false),
    updateClosed(false),
    error(*this),
    acl(0)
{
    broker.setInCluster(true);

    // We give ownership of the timer to the broker and keep a plain pointer.
    // This is OK as it means the timer has the same lifetime as the broker.
    timer = new ClusterTimer(*this);
    broker.setClusterTimer(std::auto_ptr<sys::Timer>(timer));

    // Failover exchange provides membership updates to clients.
    broker.getExchanges().registerExchange(failoverExchange);

    // CredentialsExchange is used to authenticate new cluster members
    broker.getExchanges().registerExchange(credentialsExchange);

    // Load my store status before we go into initialization
    if (! broker::NullMessageStore::isNullStore(&broker.getStore())) {
        store.load();
        clusterId = store.getClusterId();
        QPID_LOG(notice, "Cluster store state: " << store)
            }
    cpg.join(name);
    // pump the CPG dispatch manually till we get past PRE_INIT.
    while (state == PRE_INIT)
        cpg.dispatchOne();
}

Cluster::~Cluster() {
    broker.setClusterTimer(std::auto_ptr<sys::Timer>(0)); // Delete cluster timer
    if (updateThread) updateThread.join(); // Join the previous updatethread.
}

void Cluster::initialize() {
    if (settings.quorum) quorum.start(poller);
    if (settings.url.empty())
        myUrl = Url::getIpAddressesUrl(broker.getPort(broker::Broker::TCP_TRANSPORT));
    else
        myUrl = settings.url;
    broker.getKnownBrokers = boost::bind(&Cluster::getUrls, this);
    broker.deferDelivery = boost::bind(&Cluster::deferDeliveryImpl, this, _1, _2);
    broker.setExpiryPolicy(expiryPolicy);
    deliverEventQueue.bypassOff();
    deliverEventQueue.start();
    deliverFrameQueue.bypassOff();
    deliverFrameQueue.start();
    mcast.start();

    /// Create management object
    mAgent = broker.getManagementAgent();
    if (mAgent != 0){
        _qmf::Package  packageInit(mAgent);
        mgmtObject = new _qmf::Cluster (mAgent, this, &broker,name,myUrl.str());
        mAgent->addObject (mgmtObject);
    }

    // Run initMapCompleted immediately to process the initial configuration
    // that allowed us to transition out of PRE_INIT
    assert(state == INIT);
    initMapCompleted(*(Mutex::ScopedLock*)0); // Fake lock, single-threaded context.

    // Add finalizer last for exception safety.
    broker.addFinalizer(boost::bind(&Cluster::brokerShutdown, this));

    // Start dispatching CPG events.
    dispatcher.start();
}

// Called in connection thread to insert a client connection.
void Cluster::addLocalConnection(const boost::intrusive_ptr<Connection>& c) {
    assert(c->getId().getMember() == self);
    localConnections.insert(c);
}

// Called in connection thread to insert an updated shadow connection.
void Cluster::addShadowConnection(const boost::intrusive_ptr<Connection>& c) {
    QPID_LOG(debug, *this << " new shadow connection " << c->getId());
    // Safe to use connections here because we're pre-catchup, stalled
    // and discarding, so deliveredFrame is not processing any
    // connection events.
    assert(discarding);
    pair<ConnectionMap::iterator, bool> ib
        = connections.insert(ConnectionMap::value_type(c->getId(), c));
    // Like this to avoid tripping up unused variable warning when NDEBUG set
    if (!ib.second) assert(ib.second);
}

void Cluster::erase(const ConnectionId& id) {
    Lock l(lock);
    erase(id,l);
}

void Cluster::eraseLocal(const ConnectionId& id) {
    Lock l(lock);
    eraseLocal(id,l);
}

// Called by Connection::deliverClose() in deliverFrameQueue thread.
void Cluster::erase(const ConnectionId& id, Lock&) {
    connections.erase(id);
    decoder.erase(id);
}

void Cluster::eraseLocal(const ConnectionId& id, Lock&) {
    localConnections.getErase(id);
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

#define LEAVE_TRY(STMT) try { STMT; }                                   \
    catch (const std::exception& e) {                                   \
        QPID_LOG(warning, *this << " error leaving cluster: " << e.what()); \
    } do {} while(0)

void Cluster::leave(Lock&) {
    if (state != LEFT) {
        state = LEFT;
        QPID_LOG(notice, *this << " leaving cluster " << name);
        // Finalize connections now now to avoid problems later in destructor.
        ClusterSafeScope css;   // Don't trigger cluster-safe assertions.
        LEAVE_TRY(localConnections.clear());
        LEAVE_TRY(connections.clear());
        LEAVE_TRY(broker::SignalHandler::shutdown());
    }
}

// Deliver CPG message.
void Cluster::deliver(
    cpg_handle_t /*handle*/,
    const cpg_name* /*group*/,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len)
{
    MemberId from(nodeid, pid);
    framing::Buffer buf(static_cast<char*>(msg), msg_len);
    Event e(Event::decodeCopy(from, buf));
    deliverEvent(e);
}

void Cluster::deliverEvent(const Event& e) { deliverEventQueue.push(e); }

void Cluster::deliverFrame(const EventFrame& e) { deliverFrameQueue.push(e); }

const ClusterUpdateOfferBody* castUpdateOffer(const framing::AMQBody* body) {
    return  (body && body->getMethod() &&
             body->getMethod()->isA<ClusterUpdateOfferBody>()) ?
        static_cast<const ClusterUpdateOfferBody*>(body) : 0;
}

const ClusterConnectionAnnounceBody* castAnnounce( const framing::AMQBody *body) {
    return  (body && body->getMethod() &&
             body->getMethod()->isA<ClusterConnectionAnnounceBody>()) ?
        static_cast<const ClusterConnectionAnnounceBody*>(body) : 0;
}

// Handler for deliverEventQueue.
// This thread decodes frames from events.
void Cluster::deliveredEvent(const Event& e) {
    if (e.isCluster()) {
        EventFrame ef(e, e.getFrame());
        // Stop the deliverEventQueue on update offers.
        // This preserves the connection decoder fragments for an update.
        // Only do this for the two brokers that are directly involved in this
        // offer: the one making the offer, or the one receiving it.
        const ClusterUpdateOfferBody* offer = castUpdateOffer(ef.frame.getBody());
        if (offer && ( e.getMemberId() == self || MemberId(offer->getUpdatee()) == self) ) {
            QPID_LOG(info, *this << " stall for update offer from " << e.getMemberId()
                     << " to " << MemberId(offer->getUpdatee()));
            deliverEventQueue.stop();
        }
        deliverFrame(ef);
    }
    else if(!discarding) {
        if (e.isControl())
            deliverFrame(EventFrame(e, e.getFrame()));
        else {
            try { decoder.decode(e, e.getData()); }
            catch (const Exception& ex) {
                // Close a connection that is sending us invalid data.
                QPID_LOG(error, *this << " aborting connection "
                         << e.getConnectionId() << ": " << ex.what());
                framing::AMQFrame abort((ClusterConnectionAbortBody()));
                deliverFrame(EventFrame(EventHeader(CONTROL, e.getConnectionId()), abort));
            }
        }
    }
}

void Cluster::flagError(
    Connection& connection, ErrorCheck::ErrorType type, const std::string& msg)
{
    Mutex::ScopedLock l(lock);
    if (connection.isCatchUp()) {
        QPID_LOG(critical, *this << " error on update connection " << connection
                 << ": " << msg);
        leave(l);
    }
    error.error(connection, type, map.getFrameSeq(), map.getMembers(), msg);
}

// Handler for deliverFrameQueue.
// This thread executes the main logic.
void Cluster::deliveredFrame(const EventFrame& efConst) {
    Mutex::ScopedLock l(lock);
    sys::ClusterSafeScope css; // Don't trigger cluster-safe asserts.
    if (state == LEFT) return;
    EventFrame e(efConst);
    const ClusterUpdateOfferBody* offer = castUpdateOffer(e.frame.getBody());
    if (offer && error.isUnresolved()) {
        // We can't honour an update offer that is delivered while an
        // error is in progress so replace it with a retractOffer and re-start
        // the event queue.
        e.frame = AMQFrame(
            ClusterRetractOfferBody(ProtocolVersion(), offer->getUpdatee()));
        deliverEventQueue.start();
    }
    // Process each frame through the error checker.
    if (error.isUnresolved()) {
        error.delivered(e);
        while (error.canProcess())  // There is a frame ready to process.
            processFrame(error.getNext(), l);
    }
    else
        processFrame(e, l);
}


void Cluster::processFrame(const EventFrame& e, Lock& l) {
    if (e.isCluster()) {
        QPID_LOG_IF(trace, loggable(e.frame), *this << " DLVR: " << e);
        ClusterDispatcher dispatch(*this, e.connectionId.getMember(), l);
        if (!framing::invoke(dispatch, *e.frame.getBody()).wasHandled())
            throw Exception(QPID_MSG("Invalid cluster control"));
    }
    else if (state >= CATCHUP) {
        map.incrementFrameSeq();
        ConnectionPtr connection = getConnection(e, l);
        if (connection) {
            QPID_LOG_IF(trace, loggable(e.frame),
                        *this << " DLVR " << map.getFrameSeq() << ":  " << e);
            connection->deliveredFrame(e);
        }
        else
            throw Exception(QPID_MSG("Unknown connection: " << e));
    }
    else // Drop connection frames while state < CATCHUP
        QPID_LOG_IF(trace, loggable(e.frame), *this << " DROP (joining): " << e);
}

// Called in deliverFrameQueue thread
ConnectionPtr Cluster::getConnection(const EventFrame& e, Lock&) {
    ConnectionId id = e.connectionId;
    ConnectionMap::iterator i = connections.find(id);
    if (i != connections.end()) return i->second;
    ConnectionPtr cp;
    // If the frame is an announcement for a new connection, add it.
    const ClusterConnectionAnnounceBody *announce = castAnnounce(e.frame.getBody());
    if (e.frame.getBody() && e.frame.getMethod() && announce)
    {
        if (id.getMember() == self)  { // Announces one of my own
            cp = localConnections.getErase(id);
            assert(cp);
        }
        else {              // New remote connection, create a shadow.
            qpid::sys::SecuritySettings secSettings;
            if (announce) {
                secSettings.ssf = announce->getSsf();
                secSettings.authid = announce->getAuthid();
                secSettings.nodict = announce->getNodict();
            }
            cp = new Connection(*this, shadowOut, announce->getManagementId(), id, secSettings);
        }
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

// CPG config-change callback.
void Cluster::configChange (
    cpg_handle_t /*handle*/,
    const cpg_name */*group*/,
    const cpg_address *members, int nMembers,
    const cpg_address *left, int nLeft,
    const cpg_address *joined, int nJoined)
{
    Mutex::ScopedLock l(lock);
    string membersStr, leftStr, joinedStr;
    // Encode members and enqueue as an event so the config change can
    // be executed in the correct thread.
    for (const cpg_address* p = members; p < members+nMembers; ++p)
        membersStr.append(MemberId(*p).str());
    for (const cpg_address* p = left; p < left+nLeft; ++p)
        leftStr.append(MemberId(*p).str());
    for (const cpg_address* p = joined; p < joined+nJoined; ++p)
        joinedStr.append(MemberId(*p).str());
    deliverEvent(Event::control(ClusterConfigChangeBody(
                                    ProtocolVersion(), membersStr, leftStr, joinedStr),
                                self));
}

void Cluster::setReady(Lock&) {
    state = READY;
    mcast.setReady();
    broker.getQueueEvents().enable();
    enableClusterSafe();    // Enable cluster-safe assertions.
}

// Set the management status from the Cluster::state.
//
// NOTE: Management updates are sent based on property changes.  In
// order to keep consistency across the cluster, we touch the local
// management status property even if it is locally unchanged for any
// event that could have cause a cluster property change on any cluster member.
void Cluster::setMgmtStatus(Lock&) {
    if (mgmtObject)
        mgmtObject->set_status(state >= CATCHUP ? "ACTIVE" : "JOINING");
}

void Cluster::initMapCompleted(Lock& l) {
    // Called on completion of the initial status map.
    QPID_LOG(debug, *this << " initial status map complete. ");
    setMgmtStatus(l);
    if (state == PRE_INIT) {
        // PRE_INIT means we're still in the earlyInitialize phase, in the constructor.
        // We decide here whether we want to recover from our store.
        // We won't recover if we are joining an active cluster or our store is dirty.
        if (store.hasStore() &&
            store.getState() != STORE_STATE_EMPTY_STORE &&
            (initMap.isActive() || store.getState() == STORE_STATE_DIRTY_STORE))
            broker.setRecovery(false); // Ditch my current store.
        state = INIT;
    }
    else if (state == INIT) {
        // INIT means we are past Cluster::initialize().

        // If we're forming an initial cluster (no active members)
        // then we wait to reach the configured cluster-size
        if (!initMap.isActive() && initMap.getActualSize() < initMap.getRequiredSize()) {
            QPID_LOG(info, *this << initMap.getActualSize()
                     << " members, waiting for at least " << initMap.getRequiredSize());
            return;
        }

        initMap.checkConsistent();
        elders = initMap.getElders();
        QPID_LOG(debug, *this << " elders: " << elders);
        if (elders.empty())
            becomeElder(l);
        else {
            broker.getLinks().setPassive(true);
            broker.getQueueEvents().disable();
            QPID_LOG(info, *this << " not active for links.");
        }
        setClusterId(initMap.getClusterId(), l);

        if (initMap.isUpdateNeeded())  { // Joining established cluster.
            authenticate();
            broker.setRecovery(false); // Ditch my current store.
            broker.setClusterUpdatee(true);

            // Update exchange is used during updates to replicate messages
            // without modifying delivery-properties.exchange.
            broker.getExchanges().registerExchange(
                boost::shared_ptr<broker::Exchange>(new UpdateExchange(this)));

            // Update-data exchange is used during update for passing data that
            // may be too large for single control frame.
            updateDataExchange.reset(new UpdateDataExchange(*this));
            broker.getExchanges().registerExchange(updateDataExchange);

            if (mAgent) mAgent->suppress(true); // Suppress mgmt output during update.
            state = JOINER;
            mcast.mcastControl(ClusterUpdateRequestBody(ProtocolVersion(), myUrl.str()), self);
            QPID_LOG(notice, *this << " joining cluster " << name);
        }
        else {                  // I can go ready.
            discarding = false;
            setReady(l);
            // Must be called *before* memberUpdate so first update will be generated.
            failoverExchange->setReady();
            memberUpdate(l);
            updateMgmtMembership(l);
            mcast.mcastControl(ClusterReadyBody(ProtocolVersion(), myUrl.str()), self);
            QPID_LOG(notice, *this << " joined cluster " << name);
        }
    }
}

void Cluster::configChange(const MemberId&,
                           const std::string& membersStr,
                           const std::string& leftStr,
                           const std::string& joinedStr,
                           Lock& l)
{
    if (state == LEFT) return;
    MemberSet members = decodeMemberSet(membersStr);
    MemberSet left = decodeMemberSet(leftStr);
    MemberSet joined = decodeMemberSet(joinedStr);
    QPID_LOG(notice, *this << " configuration change: " << members);
    QPID_LOG_IF(notice, !left.empty(), *this << " Members left: " << left);
    QPID_LOG_IF(notice, !joined.empty(), *this << " Members joined: " << joined);

    // If we are still joining, make sure there is someone to give us an update.
    elders = intersection(elders, members);
    if (elders.empty() && INIT < state && state < CATCHUP) {
        QPID_LOG(critical, "Cannot update, all potential updaters left the cluster.");
        leave(l);
        return;
    }
    bool memberChange = map.configChange(members);

    // Update initital status for members joining or leaving.
    initMap.configChange(members);
    if (initMap.isResendNeeded()) {
        mcast.mcastControl(
            ClusterInitialStatusBody(
                ProtocolVersion(), CLUSTER_VERSION, state > INIT, clusterId,
                store.getState(), store.getShutdownId(),
                initMap.getFirstConfigStr(),
                vectorToUrlArray(getUrls(l))
            ),
            self);
    }
    if (initMap.transitionToComplete()) initMapCompleted(l);

    if (state >= CATCHUP && memberChange) {
        memberUpdate(l);
        if (elders.empty()) becomeElder(l);
    }

    updateMgmtMembership(l);     // Update on every config change for consistency
}

struct ClusterClockTask : public sys::TimerTask {
    Cluster& cluster;
    sys::Timer& timer;

    ClusterClockTask(Cluster& cluster, sys::Timer& timer, uint16_t clockInterval)
      : TimerTask(Duration(clockInterval * TIME_MSEC),"ClusterClock"), cluster(cluster), timer(timer) {}

    void fire() {
      cluster.sendClockUpdate();
      setupNextFire();
      timer.add(this);
    }
};

void Cluster::becomeElder(Lock&) {
    if (elder) return;          // We were already the elder.
    // We are the oldest, reactive links if necessary
    QPID_LOG(info, *this << " became the elder, active for links.");
    elder = true;
    broker.getLinks().setPassive(false);
    timer->becomeElder();

    clockTimer.add(new ClusterClockTask(*this, clockTimer, settings.clockInterval));
}

void Cluster::makeOffer(const MemberId& id, Lock& ) {
    if (state == READY && map.isJoiner(id)) {
        state = OFFER;
        QPID_LOG(info, *this << " send update-offer to " << id);
        mcast.mcastControl(ClusterUpdateOfferBody(ProtocolVersion(), id), self);
    }
}

namespace {
struct AppendQueue {
    ostream* os;
    AppendQueue(ostream& o) : os(&o) {}
    void operator()(const boost::shared_ptr<broker::Queue>& q) {
        (*os) << " " << q->getName() << "=" << q->getMessageCount();
    }
};
} // namespace

// Log a snapshot of broker state, used for debugging inconsistency problems.
// May only be called in deliver thread.
std::string Cluster::debugSnapshot() {
    assertClusterSafe();
    std::ostringstream msg;
    msg << "Member joined, frameSeq=" << map.getFrameSeq() << ", queue snapshot:";
    AppendQueue append(msg);
    broker.getQueues().eachQueue(append);
    return msg.str();
}

// Called from Broker::~Broker when broker is shut down.  At this
// point we know the poller has stopped so no poller callbacks will be
// invoked. We must ensure that CPG has also shut down so no CPG
// callbacks will be invoked.
//
void Cluster::brokerShutdown()  {
    sys::ClusterSafeScope css; // Don't trigger cluster-safe asserts.
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

void Cluster::initialStatus(const MemberId& member, uint32_t version, bool active,
                            const framing::Uuid& id,
                            framing::cluster::StoreState store,
                            const framing::Uuid& shutdownId,
                            const std::string& firstConfig,
                            const framing::Array& urls,
                            Lock& l)
{
    if (version != CLUSTER_VERSION) {
        QPID_LOG(critical, *this << " incompatible cluster versions " <<
                 version << " != " << CLUSTER_VERSION);
        leave(l);
        return;
    }
    QPID_LOG_IF(debug, state == PRE_INIT, *this
                << " received initial status from " << member);
    initMap.received(
        member,
        ClusterInitialStatusBody(ProtocolVersion(), version, active, id,
                                 store, shutdownId, firstConfig, urls)
    );
    if (initMap.transitionToComplete()) initMapCompleted(l);
}

void Cluster::ready(const MemberId& id, const std::string& url, Lock& l) {
    try {
        if (map.ready(id, Url(url)))
            memberUpdate(l);
        if (state == CATCHUP && id == self) {
            setReady(l);
            QPID_LOG(notice, *this << " caught up.");
        }
    } catch (const Url::Invalid& e) {
        QPID_LOG(error, "Invalid URL in cluster ready command: " << url);
    }
     // Update management on every ready event to be consistent across cluster.
    setMgmtStatus(l);
    updateMgmtMembership(l);
}

void Cluster::updateOffer(const MemberId& updater, uint64_t updateeInt, Lock& l) {
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
            QPID_LOG(info, *this << " cancelled offer to " << updatee << " unstall");
            setReady(l);
            makeOffer(map.firstJoiner(), l); // Maybe make another offer.
            deliverEventQueue.start(); // Go back to normal processing
        }
    }
    else if (updatee == self && url) {
        assert(state == JOINER);
        state = UPDATEE;
        acl = broker.getAcl();
        broker.setAcl(0);       // Disable ACL during update
        QPID_LOG(notice, *this << " receiving update from " << updater);
        checkUpdateIn(l);
    }
    else {
        QPID_LOG(info, *this << " unstall, ignore update " << updater
                 << " to " << updatee);
        deliverEventQueue.start(); // Not involved in update.
    }
    if (updatee != self && url) {
        QPID_LOG(debug, debugSnapshot());
        if (mAgent) mAgent->clusterUpdate();
        // Updatee will call clusterUpdate() via checkUpdateIn() when update completes
    }
}

static client::ConnectionSettings connectionSettings(const ClusterSettings& settings) {
    client::ConnectionSettings cs;
    cs.username = settings.username;
    cs.password = settings.password;
    cs.mechanism = settings.mechanism;
    return cs;
}

void Cluster::retractOffer(const MemberId& updater, uint64_t updateeInt, Lock& l) {
    // An offer was received while handling an error, and converted to a retract.
    // Behavior is very similar to updateOffer.
    if (state == LEFT) return;
    MemberId updatee(updateeInt);
    boost::optional<Url> url = map.updateOffer(updater, updatee);
    if (updater == self) {
        assert(state == OFFER);
        if (url)  {             // My offer was first.
            if (updateThread)
                updateThread.join(); // Join the previous updateThread to avoid leaks.
            updateThread = Thread(new RetractClient(*url, connectionSettings(settings)));
        }
        setReady(l);
        makeOffer(map.firstJoiner(), l); // Maybe make another offer.
        // Don't unstall the event queue, that was already done in deliveredFrame
    }
    QPID_LOG(debug,*this << " retracted offer " << updater << " to " << updatee);
}

void Cluster::updateStart(const MemberId& updatee, const Url& url, Lock& l) {
    // Check for credentials if authentication is enabled.
    if (broker.getOptions().auth && !credentialsExchange->check(updatee)) {
        QPID_LOG(error, "Un-authenticated attempt to join the cluster");
        return;
    }
    // NOTE: deliverEventQueue is already stopped at the stall point by deliveredEvent.
    if (state == LEFT) return;
    assert(state == OFFER);
    state = UPDATER;
    QPID_LOG(notice, *this << " sending update to " << updatee << " at " << url);
    if (updateThread)
        updateThread.join(); // Join the previous updateThread to avoid leaks.
    updateThread = Thread(
        new UpdateClient(self, updatee, url, broker, map, *expiryPolicy,
                         getConnections(l), decoder,
                         boost::bind(&Cluster::updateOutDone, this),
                         boost::bind(&Cluster::updateOutError, this, _1),
                         connectionSettings(settings)));
}

// Called in network thread
void Cluster::updateInClosed() {
    Lock l(lock);
    assert(!updateClosed);
    updateClosed = true;
    checkUpdateIn(l);
}

// Called in update thread.
void Cluster::updateInDone(const ClusterMap& m) {
    Lock l(lock);
    updatedMap = m;
    checkUpdateIn(l);
}

void Cluster::updateInRetracted() {
    Lock l(lock);
    updateRetracted = true;
    map.clearStatus();
    checkUpdateIn(l);
}

bool Cluster::isExpectingUpdate() {
    Lock l(lock);
    return state <= UPDATEE;
}

// Called in update thread or deliver thread.
void Cluster::checkUpdateIn(Lock& l) {
    if (state != UPDATEE) return; // Wait till we reach the stall point.
    if (!updateClosed) return;  // Wait till update connection closes.
    if (updatedMap) { // We're up to date
        map = *updatedMap;
        mcast.mcastControl(ClusterReadyBody(ProtocolVersion(), myUrl.str()), self);
        state = CATCHUP;
        memberUpdate(l);
        // Must be called *after* memberUpdate() to avoid sending an extra update.
        failoverExchange->setReady();
        // NB: don't updateMgmtMembership() here as we are not in the deliver
        // thread. It will be updated on delivery of the "ready" we just mcast.
        broker.setClusterUpdatee(false);
        broker.setAcl(acl);     // Restore ACL
        discarding = false;     // OK to set, we're stalled for update.
        QPID_LOG(notice, *this << " update complete, starting catch-up.");
        QPID_LOG(debug, debugSnapshot()); // OK to call because we're stalled.
        if (mAgent) {
            // Update management agent now, after all update activity is complete.
            updateDataExchange->updateManagementAgent(mAgent);
            mAgent->suppress(false); // Enable management output.
            mAgent->clusterUpdate();
        }
        // Restore alternate exchange settings on exchanges.
        broker.getExchanges().eachExchange(
            boost::bind(&broker::Exchange::recoveryComplete, _1,
                        boost::ref(broker.getExchanges())));
        enableClusterSafe();    // Enable cluster-safe assertions
        deliverEventQueue.start();
        // FIXME aconway 2012-04-04: unregister/delete Update[Data]Exchange
        updateDataExchange.reset();
        broker.getExchanges().destroy(UpdateDataExchange::EXCHANGE_NAME);
        broker.getExchanges().destroy(UpdateClient::UPDATE);
    }
    else if (updateRetracted) { // Update was retracted, request another update
        updateRetracted = false;
        updateClosed = false;
        state = JOINER;
        QPID_LOG(notice, *this << " update retracted, sending new update request.");
        mcast.mcastControl(ClusterUpdateRequestBody(ProtocolVersion(), myUrl.str()), self);
        deliverEventQueue.start();
    }
}

void Cluster::updateOutDone() {
    Monitor::ScopedLock l(lock);
    updateOutDone(l);
}

void Cluster::updateOutDone(Lock& l) {
    QPID_LOG(notice, *this << " update sent");
    assert(state == UPDATER);
    state = READY;
    deliverEventQueue.start();       // Start processing events again.
    makeOffer(map.firstJoiner(), l); // Try another offer
}

void Cluster::updateOutError(const std::exception& e)  {
    Monitor::ScopedLock l(lock);
    QPID_LOG(error, *this << " error sending update: " << e.what());
    updateOutDone(l);
}

void Cluster ::shutdown(const MemberId& , const Uuid& id, Lock& l) {
    QPID_LOG(notice, *this << " cluster shut down by administrator.");
    if (store.hasStore()) store.clean(id);
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
    QPID_LOG(notice, *this << " cluster member stopped by administrator.");
    leave(l);
}

void Cluster::stopFullCluster(Lock& ) {
    QPID_LOG(notice, *this << " shutting down cluster " << name);
    mcast.mcastControl(ClusterShutdownBody(ProtocolVersion(), Uuid(true)), self);
}

void Cluster::memberUpdate(Lock& l) {
    // Ignore config changes while we are joining.
    if (state < CATCHUP) return;
    QPID_LOG(info, *this << " member update: " << map);
    size_t aliveCount = map.aliveCount();
    assert(map.isAlive(self));
    failoverExchange->updateUrls(getUrls(l));

    // Mark store clean if I am the only broker, dirty otherwise.
    if (store.hasStore()) {
        if (aliveCount == 1) {
            if (store.getState() != STORE_STATE_CLEAN_STORE) {
                QPID_LOG(notice, *this << "Sole member of cluster, marking store clean.");
                store.clean(Uuid(true));
            }
        }
        else {
            if (store.getState() != STORE_STATE_DIRTY_STORE) {
                QPID_LOG(notice, "Running in a cluster, marking store dirty.");
                store.dirty();
            }
        }
    }

    // If I am the last member standing, set queue policies.
    if (aliveCount == 1 && lastAliveCount > 1 && state >= CATCHUP) {
        QPID_LOG(notice, *this << " last broker standing, update queue policies");
        lastBroker = true;
        broker.getQueues().updateQueueClusterState(true);
    }
    else if (aliveCount > 1 && lastBroker) {
        QPID_LOG(notice, *this << " last broker standing joined by " << aliveCount-1
                 << " replicas, updating queue policies.");
        lastBroker = false;
        broker.getQueues().updateQueueClusterState(false);
    }
    lastAliveCount = aliveCount;

    // Close connections belonging to members that have left the cluster.
    ConnectionMap::iterator i = connections.begin();
    while (i != connections.end()) {
        ConnectionMap::iterator j = i++;
        MemberId m = j->second->getId().getMember();
        if (m != self && !map.isMember(m)) {
            j->second->close();
            erase(j->second->getId(), l);
        }
    }
}

// See comment on Cluster::setMgmtStatus
void Cluster::updateMgmtMembership(Lock& l) {
    if (!mgmtObject) return;
    std::vector<Url> urls = getUrls(l);
    mgmtObject->set_clusterSize(urls.size());
    string urlstr;
    for(std::vector<Url>::iterator i = urls.begin(); i != urls.end(); i++ ) {
        if (i != urls.begin()) urlstr += ";";
        urlstr += i->str();
    }
    std::vector<string> ids = getIds(l);
    string idstr;
    for(std::vector<string>::iterator i = ids.begin(); i != ids.end(); i++ ) {
        if (i != ids.begin()) idstr += ";";
        idstr += *i;
    }
    mgmtObject->set_members(urlstr);
    mgmtObject->set_memberIDs(idstr);
}

namespace {
template <class T> struct AutoClose {
    T closeme;
    AutoClose(T t) : closeme(t) {}
    ~AutoClose() { closeme.close(); }
};
}

// Updatee connects to established member and stores credentials
// in the qpid.cluster-credentials exchange to prove it
// is safe for updater to connect and give an update.
void Cluster::authenticate() {
    if (!broker.getOptions().auth) return;
    std::vector<Url> urls = initMap.getUrls();
    for (std::vector<Url>::iterator i = urls.begin(); i != urls.end(); ++i) {
        if (!i->empty()) {
            client::Connection c;
            c.open(*i, connectionSettings(settings));
            AutoClose<client::Connection> closeConnection(c);
            client::Session s = c.newSession(CredentialsExchange::NAME);
            AutoClose<client::Session> closeSession(s);
            client::Message credentials;
            credentials.getHeaders().setUInt64(CredentialsExchange::NAME, getId());
            s.messageTransfer(arg::content=credentials, arg::destination=CredentialsExchange::NAME);
            s.sync();
        }
    }
}

std::ostream& operator<<(std::ostream& o, const Cluster& cluster) {
    static const char* STATE[] = {
        "PRE_INIT", "INIT", "JOINER", "UPDATEE", "CATCHUP",
        "READY", "OFFER", "UPDATER", "LEFT"
    };
    assert(sizeof(STATE)/sizeof(*STATE) == Cluster::LEFT+1);
    o << "cluster(" << cluster.self << " " << STATE[cluster.state];
    if (cluster.error.isUnresolved()) o << "/error";
    return o << ")";
}

MemberId Cluster::getId() const {
    return self;            // Immutable, no need to lock.
}

broker::Broker& Cluster::getBroker() const {
    return broker; // Immutable,  no need to lock.
}

void Cluster::setClusterId(const Uuid& uuid, Lock&) {
    clusterId = uuid;
    if (store.hasStore()) store.setClusterId(uuid);
    if (mgmtObject) {
        stringstream stream;
        stream << self;
        mgmtObject->set_clusterID(clusterId.str());
        mgmtObject->set_memberID(stream.str());
    }
    QPID_LOG(notice, *this << " cluster-uuid = " << clusterId);
}

void Cluster::errorCheck(const MemberId& from, uint8_t type, framing::SequenceNumber frameSeq, Lock&) {
    // If we see an errorCheck here (rather than in the ErrorCheck
    // class) then we have processed succesfully past the point of the
    // error.
    if (state >= CATCHUP) // Don't respond pre catchup, we don't know what happened
        error.respondNone(from, type, frameSeq);
}

void Cluster::timerWakeup(const MemberId& , const std::string& name, Lock&) {
    if (state >= CATCHUP) // Pre catchup our timer isn't set up.
        timer->deliverWakeup(name);
}

void Cluster::timerDrop(const MemberId& , const std::string& name, Lock&) {
    QPID_LOG(debug, "Cluster timer drop " << map.getFrameSeq() << ": " << name)
        if (state >= CATCHUP) // Pre catchup our timer isn't set up.
            timer->deliverDrop(name);
}

bool Cluster::isElder() const {
    return elder;
}

void Cluster::deliverToQueue(const std::string& queue, const std::string& message, Lock& l)
{
    broker::Queue::shared_ptr q = broker.getQueues().find(queue);
    if (!q) {
        QPID_LOG(critical, *this << " cluster delivery to non-existent queue: " << queue);
        leave(l);
    }
    framing::Buffer buf(const_cast<char*>(message.data()), message.size());
    boost::intrusive_ptr<broker::Message> msg(new broker::Message);
    msg->decodeHeader(buf);
    msg->decodeContent(buf);
    q->deliver(msg);
}

sys::AbsTime Cluster::getClusterTime() {
    Mutex::ScopedLock l(lock);
    return clusterTime;
}

// This method is called during update on the updatee to set the initial cluster time.
void Cluster::clock(const uint64_t time) {
    Mutex::ScopedLock l(lock);
    clock(time, l);
}

// called when broadcast message received
void Cluster::clock(const uint64_t time, Lock&) {
    clusterTime = AbsTime(EPOCH, time);
    AbsTime now = AbsTime::now();

    if (!elder) {
      clusterTimeOffset = Duration(now, clusterTime);
    }
}

// called by elder timer to send clock broadcast
void Cluster::sendClockUpdate() {
    Mutex::ScopedLock l(lock);
    int64_t nanosecondsSinceEpoch = Duration(EPOCH, now());
    nanosecondsSinceEpoch += clusterTimeOffset;
    mcast.mcastControl(ClusterClockBody(ProtocolVersion(), nanosecondsSinceEpoch), self);
}

bool Cluster::deferDeliveryImpl(const std::string& queue,
                                const boost::intrusive_ptr<broker::Message>& msg)
{
    if (isClusterSafe()) return false;
    std::string message;
    message.resize(msg->encodedSize());
    framing::Buffer buf(const_cast<char*>(message.data()), message.size());
    msg->encode(buf);
    mcast.mcastControl(ClusterDeliverToQueueBody(ProtocolVersion(), queue, message), self);
    return true;
}

bool Cluster::loggable(const AMQFrame& f) {
    const  AMQMethodBody* method = (f.getMethod());
    if (!method) return true;     // Not a method
    bool isClock = method->amqpClassId() ==  ClusterClockBody::CLASS_ID
        && method->amqpMethodId() == ClusterClockBody::METHOD_ID;
    return !isClock;
}

}} // namespace qpid::cluster
