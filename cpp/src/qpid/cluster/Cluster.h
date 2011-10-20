#ifndef QPID_CLUSTER_CLUSTER_H
#define QPID_CLUSTER_CLUSTER_H

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

#include "ClusterMap.h"
#include "ClusterSettings.h"
#include "Cpg.h"
#include "Decoder.h"
#include "ErrorCheck.h"
#include "Event.h"
#include "EventFrame.h"
#include "ExpiryPolicy.h"
#include "FailoverExchange.h"
#include "InitialStatusMap.h"
#include "LockedConnectionMap.h"
#include "Multicaster.h"
#include "NoOpConnectionOutputHandler.h"
#include "PollableQueue.h"
#include "PollerDispatch.h"
#include "Quorum.h"
#include "StoreStatus.h"
#include "UpdateReceiver.h"

#include "qmf/org/apache/qpid/cluster/Cluster.h"
#include "qpid/Url.h"
#include "qpid/broker/Broker.h"
#include "qpid/management/Manageable.h"
#include "qpid/sys/Monitor.h"

#include <boost/bind.hpp>
#include <boost/intrusive_ptr.hpp>
#include <boost/optional.hpp>

#include <algorithm>
#include <map>
#include <vector>

namespace qpid {

namespace broker {
class Message;
class AclModule;
}

namespace framing {
class AMQFrame;
class AMQBody;
struct Uuid;
}

namespace sys {
class Timer;
class AbsTime;
class Duration;
}

namespace cluster {

class Connection;
struct EventFrame;
class ClusterTimer;
class UpdateDataExchange;

/**
 * Connection to the cluster
 */
class Cluster : private Cpg::Handler, public management::Manageable {
  public:
    typedef boost::intrusive_ptr<Connection> ConnectionPtr;
    typedef std::vector<ConnectionPtr> ConnectionVector;

    // Public functions are thread safe unless otherwise mentioned in a comment.

    // Construct the cluster in plugin earlyInitialize.
    Cluster(const ClusterSettings&, broker::Broker&);
    virtual ~Cluster();

    // Called by plugin initialize: cluster start-up requires transport plugins .
    // Thread safety: only called by plugin initialize.
    void initialize();

    // Connection map.
    void addLocalConnection(const ConnectionPtr&);
    void addShadowConnection(const ConnectionPtr&);
    void erase(const ConnectionId&);

    // URLs of current cluster members.
    std::vector<std::string> getIds() const;
    std::vector<Url> getUrls() const;
    boost::shared_ptr<FailoverExchange> getFailoverExchange() const { return failoverExchange; }

    // Leave the cluster - called when fatal errors occur.
    void leave();

    // Update completed - called in update thread
    void updateInClosed();
    void updateInDone(const ClusterMap&);
    void updateInRetracted();
    // True if we are expecting to receive catch-up connections.
    bool isExpectingUpdate();

    MemberId getId() const;
    broker::Broker& getBroker() const;
    Multicaster& getMulticast() { return mcast; }

    const ClusterSettings& getSettings() const { return settings; }

    void deliverFrame(const EventFrame&);

    // Called in deliverFrame thread to indicate an error from the broker.
    void flagError(Connection&, ErrorCheck::ErrorType, const std::string& msg);

    // Called only during update by Connection::shadowReady
    Decoder& getDecoder() { return decoder; }

    ExpiryPolicy& getExpiryPolicy() { return *expiryPolicy; }

    UpdateReceiver& getUpdateReceiver() { return updateReceiver; }

    bool isElder() const;

    // Generates a log message for debugging purposes.
    std::string debugSnapshot();

    // Defer messages delivered in an unsafe context by multicasting.
    bool deferDeliveryImpl(const std::string& queue,
                           const boost::intrusive_ptr<broker::Message>& msg);

    sys::AbsTime getClusterTime();
    void sendClockUpdate();
    void clock(const uint64_t time);

    static bool loggable(const framing::AMQFrame&); // True if the frame should be logged.

  private:
    typedef sys::Monitor::ScopedLock Lock;

    typedef PollableQueue<Event> PollableEventQueue;
    typedef PollableQueue<EventFrame> PollableFrameQueue;
    typedef std::map<ConnectionId, ConnectionPtr> ConnectionMap;

    /** Version number of the cluster protocol, to avoid mixed versions. */
    static const uint32_t CLUSTER_VERSION;

    // NB: A dummy Lock& parameter marks functions that must only be
    // called with Cluster::lock  locked.

    void leave(Lock&);
    std::vector<std::string> getIds(Lock&) const;
    std::vector<Url> getUrls(Lock&) const;

    // == Called in main thread from Broker destructor.
    void brokerShutdown();

    // == Called in deliverEventQueue thread
    void deliveredEvent(const Event&);

    // == Called in deliverFrameQueue thread
    void deliveredFrame(const EventFrame&);
    void processFrame(const EventFrame&, Lock&);

    // Cluster controls implement XML methods from cluster.xml.
    void updateRequest(const MemberId&, const std::string&, Lock&);
    void updateOffer(const MemberId& updater, uint64_t updatee, Lock&);
    void retractOffer(const MemberId& updater, uint64_t updatee, Lock&);
    void initialStatus(const MemberId&,
                       uint32_t version,
                       bool active,
                       const framing::Uuid& clusterId,
                       framing::cluster::StoreState,
                       const framing::Uuid& shutdownId,
                       const std::string& firstConfig,
                       Lock&);
    void ready(const MemberId&, const std::string&, Lock&);
    void configChange(const MemberId&,
                      const std::string& members,
                      const std::string& left,
                      const std::string& joined,
                      Lock& l);
    void errorCheck(const MemberId&, uint8_t type, SequenceNumber frameSeq, Lock&);
    void timerWakeup(const MemberId&, const std::string& name, Lock&);
    void timerDrop(const MemberId&, const std::string& name, Lock&);
    void shutdown(const MemberId&, const framing::Uuid& shutdownId, Lock&);
    void deliverToQueue(const std::string& queue, const std::string& message, Lock&);
    void clock(const uint64_t time, Lock&);

    // Helper functions
    ConnectionPtr getConnection(const EventFrame&, Lock&);
    ConnectionVector getConnections(Lock&);
    void updateStart(const MemberId& updatee, const Url& url, Lock&);
    void makeOffer(const MemberId&, Lock&);
    void setReady(Lock&);
    void memberUpdate(Lock&);
    void setClusterId(const framing::Uuid&, Lock&);
    void erase(const ConnectionId&, Lock&);
    void requestUpdate(Lock& );
    void initMapCompleted(Lock&);
    void becomeElder(Lock&);
    void setMgmtStatus(Lock&);
    void updateMgmtMembership(Lock&);

    // == Called in CPG dispatch thread
    void deliver( // CPG deliver callback.
        cpg_handle_t /*handle*/,
        const struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    void deliverEvent(const Event&);

    void configChange( // CPG config change callback.
        cpg_handle_t /*handle*/,
        const struct cpg_name */*group*/,
        const struct cpg_address */*members*/, int /*nMembers*/,
        const struct cpg_address */*left*/, int /*nLeft*/,
        const struct cpg_address */*joined*/, int /*nJoined*/
    );

    // == Called in management threads.
    virtual qpid::management::ManagementObject* GetManagementObject() const;
    virtual management::Manageable::status_t ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);

    void stopClusterNode(Lock&);
    void stopFullCluster(Lock&);

    // == Called in connection IO threads .
    void checkUpdateIn(Lock&);

    // == Called in UpdateClient thread.
    void updateOutDone();
    void updateOutError(const std::exception&);
    void updateOutDone(Lock&);

    // Immutable members set on construction, never changed.
    const ClusterSettings settings;
    broker::Broker& broker;
    qmf::org::apache::qpid::cluster::Cluster* mgmtObject; // mgnt owns lifecycle
    boost::shared_ptr<sys::Poller> poller;
    Cpg cpg;
    const std::string name;
    Url myUrl;
    const MemberId self;
    framing::Uuid clusterId;
    NoOpConnectionOutputHandler shadowOut;
    qpid::management::ManagementAgent* mAgent;
    boost::intrusive_ptr<ExpiryPolicy> expiryPolicy;

    // Thread safe members
    Multicaster mcast;
    PollerDispatch dispatcher;
    PollableEventQueue deliverEventQueue;
    PollableFrameQueue deliverFrameQueue;
    boost::shared_ptr<FailoverExchange> failoverExchange;
    boost::shared_ptr<UpdateDataExchange> updateDataExchange;
    Quorum quorum;
    LockedConnectionMap localConnections;

    // Used only in deliverEventQueue thread or when stalled for update.
    Decoder decoder;
    bool discarding;


    // Remaining members are protected by lock.
    mutable sys::Monitor lock;


    //    Local cluster state, cluster map
    enum {
        PRE_INIT,///< Have not yet received complete initial status map.
        INIT,    ///< Waiting to reach cluster-size.
        JOINER,  ///< Sent update request, waiting for update offer.
        UPDATEE, ///< Stalled receive queue at update offer, waiting for update to complete.
        CATCHUP, ///< Update complete, unstalled but has not yet seen own "ready" event.
        READY,   ///< Fully operational
        OFFER,   ///< Sent an offer, waiting for accept/reject.
        UPDATER, ///< Offer accepted, sending a state update.
        LEFT     ///< Final state, left the cluster.
    } state;

    ConnectionMap connections;
    InitialStatusMap initMap;
    StoreStatus store;
    ClusterMap map;
    MemberSet elders;
    bool elder;
    size_t lastAliveCount;
    bool lastBroker;
    sys::Thread updateThread;
    boost::optional<ClusterMap> updatedMap;
    bool updateRetracted, updateClosed;
    ErrorCheck error;
    UpdateReceiver updateReceiver;
    ClusterTimer* timer;
    sys::Timer clockTimer;
    sys::AbsTime clusterTime;
    sys::Duration clusterTimeOffset;
    broker::AclModule* acl;

  friend std::ostream& operator<<(std::ostream&, const Cluster&);
  friend struct ClusterDispatcher;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
