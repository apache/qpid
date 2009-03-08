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
#include "Event.h"
#include "EventFrame.h"
#include "ExpiryPolicy.h"
#include "FailoverExchange.h"
#include "LockedConnectionMap.h"
#include "Multicaster.h"
#include "NoOpConnectionOutputHandler.h"
#include "PollableQueue.h"
#include "PollerDispatch.h"
#include "Quorum.h"

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

namespace framing {
class AMQBody;
class Uuid;
}

namespace cluster {

class Connection;
class EventFrame;

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
    void updateInDone(const ClusterMap&, uint64_t frameId);

    MemberId getId() const;
    broker::Broker& getBroker() const;
    Multicaster& getMulticast() { return mcast; }

    void checkQuorum();

    size_t getReadMax() { return readMax; }
    size_t getWriteEstimate() { return writeEstimate; }

    void deliverFrame(const EventFrame&);

    // Called only during update by Connection::shadowReady
    Decoder& getDecoder() { return decoder; }

  private:
    typedef sys::Monitor::ScopedLock Lock;

    typedef PollableQueue<Event> PollableEventQueue;
    typedef PollableQueue<EventFrame> PollableFrameQueue;
    typedef std::map<ConnectionId, ConnectionPtr> ConnectionMap;

    // FIXME aconway 2009-03-07: sort functions by thread

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

    // Cluster controls implement XML methods from cluster.xml.
    void updateRequest(const MemberId&, const std::string&, Lock&);
    void updateOffer(const MemberId& updater, uint64_t updatee, const framing::Uuid&, Lock&);
    void ready(const MemberId&, const std::string&, Lock&);
    void configChange(const MemberId&, const std::string& addresses, Lock& l);
    void messageExpired(const MemberId&, uint64_t, Lock& l);
    void shutdown(const MemberId&, Lock&);

    // Helper functions
    ConnectionPtr getConnection(const ConnectionId&, Lock&);
    ConnectionVector getConnections(Lock&);
    void updateStart(const MemberId& updatee, const Url& url, Lock&);
    void makeOffer(const MemberId&, Lock&);
    void setReady(Lock&);
    void memberUpdate(Lock&);
    void setClusterId(const framing::Uuid&, Lock&);

    // == Called in CPG dispatch thread
    void deliver( // CPG deliver callback. 
        cpg_handle_t /*handle*/,
        struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    void deliverEvent(const Event&);
    
    void configChange( // CPG config change callback.
        cpg_handle_t /*handle*/,
        struct cpg_name */*group*/,
        struct cpg_address */*members*/, int /*nMembers*/,
        struct cpg_address */*left*/, int /*nLeft*/,
        struct cpg_address */*joined*/, int /*nJoined*/
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
    LockedConnectionMap localConnections;

    // Used only during initialization
    bool initialized;

    // Used only in deliverEventQueue thread or when stalled for update.
    Decoder decoder;
    bool discarding;
    
    // Remaining members are protected by lock.
    // FIXME aconway 2009-03-06: Most of these members are also only used in
    // deliverFrameQueue thread or during stall. Review and separate members
    // that require a lock, drop lock when not needed.
    // 
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

    ConnectionMap connections;
    uint64_t frameId;
    ClusterMap map;
    ClusterMap::Set elders;
    size_t lastSize;
    bool lastBroker;
    sys::Thread updateThread;
    boost::optional<ClusterMap> updatedMap;


  friend std::ostream& operator<<(std::ostream&, const Cluster&);
  friend class ClusterDispatcher;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
