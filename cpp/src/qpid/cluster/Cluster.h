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
#include "ConnectionMap.h"
#include "Cpg.h"
#include "Event.h"
#include "FailoverExchange.h"
#include "Multicaster.h"
#include "EventFrame.h"
#include "NoOpConnectionOutputHandler.h"
#include "PollerDispatch.h"
#include "Quorum.h"

#include "qpid/broker/Broker.h"
#include "qpid/sys/PollableQueue.h"
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
 *
 * Threading notes: 3 thread categories: connection, deliver, dump.
 * 
 */
class Cluster : private Cpg::Handler, public management::Manageable {
  public:
    typedef boost::intrusive_ptr<Connection> ConnectionPtr;
    typedef std::vector<ConnectionPtr> Connections;
    
    /**
     * Join a cluster. 
     */
    Cluster(const std::string& name, const Url& url, broker::Broker&, bool useQuorum,
            size_t readMax, size_t writeEstimate, size_t mcastMax);

    virtual ~Cluster();

    // Connection map - called in connection threads.
    void insert(const ConnectionPtr&); 
    void erase(ConnectionId);       
    
    // URLs of current cluster members - called in connection threads.
    std::vector<std::string> getIds() const;
    std::vector<Url> getUrls() const;
    boost::shared_ptr<FailoverExchange> getFailoverExchange() const { return failoverExchange; }

    // Leave the cluster - called in any thread.
    void leave();

    // Dump completed - called in dump thread
    void dumpInDone(const ClusterMap&);

    MemberId getId() const;
    broker::Broker& getBroker() const;
    Multicaster& getMulticast() { return mcast; }

    boost::function<bool ()> isQuorate;
    void checkQuorum();         // called in connection threads.

    size_t getReadMax() { return readMax; }
    size_t getWriteEstimate() { return writeEstimate; }
    
  private:
    typedef sys::Monitor::ScopedLock Lock;

    typedef sys::PollableQueue<Event> PollableEventQueue;
    typedef sys::PollableQueue<EventFrame> PollableFrameQueue;

    // NB: The final Lock& parameter on functions below is used to mark functions
    // that should only be called by a function that already holds the lock.
    // The parameter makes it hard to forget since you have to have an instance of
    // a Lock to call the unlocked functions.

    void leave(Lock&);
    std::vector<std::string> getIds(Lock&) const;
    std::vector<Url> getUrls(Lock&) const;

    // Make an offer if we can - called in deliver thread.
    void tryMakeOffer(const MemberId&, Lock&);

    // Called in main thread in ~Broker.
    void brokerShutdown();

    // Cluster controls implement XML methods from cluster.xml.
    // Called in deliver thread.
    // 
    void dumpRequest(const MemberId&, const std::string&, Lock&);
    void dumpOffer(const MemberId& dumper, uint64_t dumpee, const framing::Uuid&, Lock&);
    void ready(const MemberId&, const std::string&, Lock&);
    void configChange(const MemberId&, const std::string& addresses, Lock& l);
    void shutdown(const MemberId&, Lock&);
    void deliveredEvent(const Event&); 
    void deliveredFrame(const EventFrame&); 

    // Helper, called in deliver thread.
    void dumpStart(const MemberId& dumpee, const Url& url, Lock&);

    void deliver( // CPG deliver callback. 
        cpg_handle_t /*handle*/,
        struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    void deliver(const Event& e, Lock&); 
    
    void configChange( // CPG config change callback.
        cpg_handle_t /*handle*/,
        struct cpg_name */*group*/,
        struct cpg_address */*members*/, int /*nMembers*/,
        struct cpg_address */*left*/, int /*nLeft*/,
        struct cpg_address */*joined*/, int /*nJoined*/
    );

    boost::intrusive_ptr<cluster::Connection> getConnection(const ConnectionId&);

    virtual qpid::management::ManagementObject* GetManagementObject() const;
    virtual management::Manageable::status_t ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);

    void stopClusterNode(Lock&);
    void stopFullCluster(Lock&);
    void memberUpdate(Lock&);

    // Called in connection IO threads .
    void checkDumpIn(Lock&);

    // Called in DumpClient thread.
    void dumpOutDone();
    void dumpOutError(const std::exception&);
    void dumpOutDone(Lock&);

    void setClusterId(const framing::Uuid&);

    // Immutable members set on construction, never changed.
    broker::Broker& broker;
    qmf::org::apache::qpid::cluster::Cluster* mgmtObject; // mgnt owns lifecycle
    boost::shared_ptr<sys::Poller> poller;
    Cpg cpg;
    const std::string name;
    const Url myUrl;
    const MemberId myId;
    const size_t readMax;
    const size_t writeEstimate;
    framing::Uuid clusterId;
    NoOpConnectionOutputHandler shadowOut;
    ClusterMap::Set myElders;
    qpid::management::ManagementAgent* mAgent;

    // Thread safe members
    Multicaster mcast;
    PollerDispatch dispatcher;
    PollableEventQueue deliverEventQueue;
    PollableFrameQueue deliverFrameQueue;
    ConnectionMap connections;
    boost::shared_ptr<FailoverExchange> failoverExchange;
    Quorum quorum;

    // Remaining members are protected by lock.
    mutable sys::Monitor lock;

    //    Local cluster state, cluster map
    enum {
        INIT,                   ///< Initial state, no CPG messages received.
        NEWBIE,                 ///< Sent dump request, waiting for dump offer.
        DUMPEE,                 ///< Stalled receive queue at dump offer, waiting for dump to complete.
        CATCHUP,                ///< Dump complete, unstalled but has not yet seen own "ready" event.
        READY,                  ///< Fully operational 
        OFFER,                  ///< Sent an offer, waiting for accept/reject.
        DUMPER,                 ///< Offer accepted, sending a state dump.
        LEFT                    ///< Final state, left the cluster.
    } state;
    ClusterMap map;
    size_t lastSize;
    bool lastBroker;
    uint64_t sequence;

    //     Dump related
    sys::Thread dumpThread;
    boost::optional<ClusterMap> dumpedMap;

  friend std::ostream& operator<<(std::ostream&, const Cluster&);
  friend class ClusterDispatcher;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
