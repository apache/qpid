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

#include "ClusterSettings.h"
#include "ClusterMap.h"
#include "ConnectionMap.h"
#include "Cpg.h"
#include "Event.h"
#include "FailoverExchange.h"
#include "Multicaster.h"
#include "EventFrame.h"
#include "NoOpConnectionOutputHandler.h"
#include "PollerDispatch.h"
#include "Quorum.h"
#include "PollableQueue.h"
#include "ExpiryPolicy.h"

#include "qpid/broker/Broker.h"
#include "qpid/sys/Monitor.h"
#include "qpid/management/Manageable.h"
#include "qpid/Url.h"
#include "qmf/org/apache/qpid/cluster/Cluster.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/bind.hpp>
#include <boost/optional.hpp>

#include <algorithm>
#include <vector>
#include <map>

namespace qpid {

namespace framing {
class AMQBody;
class Uuid;
}

namespace cluster {

class Connection;

/**
 * Connection to the cluster
 */
class Cluster : private Cpg::Handler, public management::Manageable {
  public:
    typedef boost::intrusive_ptr<Connection> ConnectionPtr;
    typedef std::vector<ConnectionPtr> Connections;

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
    void updateInDone(const ClusterMap&, uint64_t eventId, uint64_t frameId);

    MemberId getId() const;
    broker::Broker& getBroker() const;
    Multicaster& getMulticast() { return mcast; }

    void checkQuorum();

    size_t getReadMax() { return readMax; }
    size_t getWriteEstimate() { return writeEstimate; }

    // Process a connection frame. Called by Connection with decoded frames.
    // Thread safety: only called in deliverEventQueue thread.
    void connectionFrame(const EventFrame&); 

  private:
    typedef sys::Monitor::ScopedLock Lock;

    typedef PollableQueue<Event> PollableEventQueue;
    typedef PollableQueue<EventFrame> PollableFrameQueue;

    // NB: A dummy Lock& parameter marks functions that must only be
    // called with Cluster::lock  locked.
 
    void leave(Lock&);
    std::vector<std::string> getIds(Lock&) const;
    std::vector<Url> getUrls(Lock&) const;

    // Make an offer if we can - called in deliver thread.
    void makeOffer(const MemberId&, Lock&);

    // Called in main thread from Broker destructor.
    void brokerShutdown();

    // Cluster controls implement XML methods from cluster.xml.
    // Called in deliverEventQueue thread.
    void updateRequest(const MemberId&, const std::string&, Lock&);
    void updateOffer(const MemberId& updater, uint64_t updatee, const framing::Uuid&, Lock&);
    void ready(const MemberId&, const std::string&, Lock&);
    void configChange(const MemberId&, const std::string& addresses, Lock& l);
    void messageExpired(const MemberId&, uint64_t, Lock& l);
    void shutdown(const MemberId&, Lock&);
    // Helper, called by updateOffer.
    void updateStart(const MemberId& updatee, const Url& url, Lock&);

    // Used by cluster controls.
    void stall(Lock&);
    void unstall(Lock&);

    // Handlers for pollable queues.
    void deliveredEvent(const Event&); 
    void deliveredFrame(const EventFrame&); 

    void setReady(Lock&);

    void deliver( // CPG deliver callback. 
        cpg_handle_t /*handle*/,
        struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    void deliver(const Event&);
    
    void configChange( // CPG config change callback.
        cpg_handle_t /*handle*/,
        struct cpg_name */*group*/,
        struct cpg_address */*members*/, int /*nMembers*/,
        struct cpg_address */*left*/, int /*nLeft*/,
        struct cpg_address */*joined*/, int /*nJoined*/
    );

    virtual qpid::management::ManagementObject* GetManagementObject() const;
    virtual management::Manageable::status_t ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);

    void stopClusterNode(Lock&);
    void stopFullCluster(Lock&);
    void memberUpdate(Lock&);

    // Called in connection IO threads .
    void checkUpdateIn(Lock&);

    // Called in UpdateClient thread.
    void updateOutDone();
    void updateOutError(const std::exception&);
    void updateOutDone(Lock&);

    void setClusterId(const framing::Uuid&, Lock&);

    // Immutable members set on construction, never changed.
    ClusterSettings settings;
    broker::Broker& broker;
    qmf::org::apache::qpid::cluster::Cluster* mgmtObject; // mgnt owns lifecycle
    boost::shared_ptr<sys::Poller> poller;
    Cpg cpg;
    const std::string name;
    Url myUrl;
    const MemberId self;
    const size_t readMax;
    const size_t writeEstimate;
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
    Quorum quorum;
    ConnectionMap connections;
 
    // Used only in deliverFrameQueue thread or in deliverEventQueue thread when stalled.
    uint64_t frameId;

    // Used only during initialization
    bool initialized;

    // Remaining members are protected by lock
    mutable sys::Monitor lock;

    //    Local cluster state, cluster map
    enum {
        INIT,    ///< Initial state, no CPG messages received.
        JOINER,  ///< Sent update request, waiting for update offer.
        UPDATEE, ///< Stalled receive queue at update offer, waiting for update to complete.
        CATCHUP, ///< Update complete, unstalled but has not yet seen own "ready" event.
        READY,   ///< Fully operational 
        OFFER,   ///< Sent an offer, waiting for accept/reject.
        UPDATER, ///< Offer accepted, sending a state update.
        LEFT     ///< Final state, left the cluster.
    } state;

    uint64_t eventId;
    ClusterMap map;
    ClusterMap::Set elders;
    size_t lastSize;
    bool lastBroker;
    sys::Thread updateThread;
    sys::Runnable* updateTask;
    boost::optional<ClusterMap> updatedMap;


  friend std::ostream& operator<<(std::ostream&, const Cluster&);
  friend class ClusterDispatcher;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
