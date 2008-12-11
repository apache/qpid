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

#include "Cpg.h"
#include "Event.h"
#include "NoOpConnectionOutputHandler.h"
#include "ClusterMap.h"
#include "ConnectionMap.h"
#include "FailoverExchange.h"
#include "Quorum.h"

#include "qpid/broker/Broker.h"
#include "qpid/sys/PollableQueue.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/LockPtr.h"
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
 * Connection to the cluster.
 *
 */
class Cluster : private Cpg::Handler, public management::Manageable {
  public:
    typedef boost::intrusive_ptr<Connection> ConnectionPtr;
    typedef std::vector<ConnectionPtr> Connections;
    
    /**
     * Join a cluster. 
     */
    Cluster(const std::string& name, const Url& url, broker::Broker&, bool useQuorum, size_t readMax);

    virtual ~Cluster();

    // Connection map
    void insert(const ConnectionPtr&); 
    void erase(ConnectionId);       
    
    // Send to the cluster 
    void mcastControl(const framing::AMQBody& controlBody, const ConnectionId&, uint32_t id);
    void mcastBuffer(const char*, size_t, const ConnectionId&, uint32_t id);

    // URLs of current cluster members.
    std::vector<Url> getUrls() const;
    boost::shared_ptr<FailoverExchange> getFailoverExchange() const { return failoverExchange; }

    // Leave the cluster
    void leave();

    // Dump completedJo
    void dumpInDone(const ClusterMap&);

    MemberId getId() const;
    broker::Broker& getBroker() const;

    boost::function<bool ()> isQuorate;
    void checkQuorum();

    size_t getReadMax() { return readMax; }
    
  private:
    typedef sys::LockPtr<Cluster,sys::Monitor> LockPtr;
    typedef sys::LockPtr<const Cluster,sys::Monitor> ConstLockPtr;
    typedef sys::Monitor::ScopedLock Lock;

    typedef sys::PollableQueue<Event> PollableEventQueue;
    typedef std::deque<Event> PlainEventQueue;

    // NB: The final Lock& parameter on functions below is used to mark functions
    // that should only be called by a function that already holds the lock.
    // The parameter makes it hard to forget since you have to have an instance of
    // a Lock to call the unlocked functions.

    void mcastControl(const framing::AMQBody& controlBody);
    void mcast(const Event& e);

    void leave(Lock&);
    std::vector<Url> getUrls(Lock&) const;

    void sendMcast(PollableEventQueue::Queue& );
    
    // Called via CPG, deliverQueue or DumpClient threads. 
    void tryMakeOffer(const MemberId&, Lock&);

    // Called in main thread in ~Broker.
    void brokerShutdown();

    // Cluster controls implement XML methods from cluster.xml.
    // May be called in CPG thread via deliver() OR in deliverQueue thread.
    // 
    void dumpRequest(const MemberId&, const std::string&, Lock&);
    void dumpOffer(const MemberId& dumper, uint64_t dumpee, const framing::Uuid&, Lock&);
    void ready(const MemberId&, const std::string&, Lock&);
    void configChange(const MemberId&, const std::string& addresses, Lock& l);
    void shutdown(const MemberId&, Lock&);
    void delivered(PollableEventQueue::Queue&); // deliverQueue callback
    void deliveredEvent(const Event&); 

    void dumpStart(const MemberId& dumpee, const Url& url, Lock&);

    // CPG callbacks, called in CPG IO thread.
    void dispatch(sys::DispatchHandle&); // Dispatch CPG events.
    void disconnect(sys::DispatchHandle&); // PG was disconnected

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

    mutable sys::Monitor lock;

    broker::Broker& broker;
    boost::shared_ptr<sys::Poller> poller;
    Cpg cpg;
    const std::string name;
    const Url myUrl;
    const MemberId myId;

    ConnectionMap connections;
    NoOpConnectionOutputHandler shadowOut;
    sys::DispatchHandle cpgDispatchHandle;
    PollableEventQueue deliverQueue, mcastQueue;
    PlainEventQueue mcastStallQueue;
    uint32_t mcastId;
    framing::Uuid clusterId;

    qmf::org::apache::qpid::cluster::Cluster* mgmtObject; // mgnt owns lifecycle

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
    sys::Thread dumpThread;
    boost::optional<ClusterMap> dumpedMap;
    
    size_t lastSize;
    bool lastBroker;
    boost::shared_ptr<FailoverExchange> failoverExchange;

    Quorum quorum;
    size_t readMax;

  friend std::ostream& operator<<(std::ostream&, const Cluster&);
  friend class ClusterDispatcher;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
