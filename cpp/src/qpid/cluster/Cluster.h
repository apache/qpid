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
#include "JoiningHandler.h"
#include "MemberHandler.h"

#include "qpid/broker/Broker.h"
#include "qpid/sys/PollableQueue.h"
#include "qpid/sys/Monitor.h"
#include "qpid/Url.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/cluster/Cluster.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/bind.hpp>

#include <algorithm>
#include <vector>
#include <map>

namespace qpid {
namespace cluster {

class Connection;

/**
 * Connection to the cluster.
 * Keeps cluster membership data.
 */
class Cluster : private Cpg::Handler, public management::Manageable
{
  public:

    /**
     * Join a cluster.
     * @param name of the cluster.
     * @param url of this broker, sent to the cluster.
     */
    Cluster(const std::string& name, const Url& url, broker::Broker&);

    virtual ~Cluster();

    void insert(const boost::intrusive_ptr<Connection>&); // Insert a local connection
    void erase(ConnectionId);          // Erase a connection.
    
    /** Get the URLs of current cluster members. */
    std::vector<Url> getUrls() const;

    /** Number of members in the cluster. */
    size_t size() const;

    bool empty() const { return size() == 0; }
    
    /** Send to the cluster */
    void mcastControl(const framing::AMQBody& controlBody, Connection* cptr);
    void mcastBuffer(const char*, size_t, const ConnectionId&);
    void mcastEvent(const Event& e);
    
    /** Leave the cluster */
    void leave();

    // Cluster controls.
    void update(const MemberId&, const framing::FieldTable& members, uint64_t dumping);
    void dumpRequest(const MemberId&, const std::string& url);
    void ready(const MemberId&, const std::string& url);

    MemberId getSelf() const { return self; }

    void stall();
    void ready();

    void shutdown();

    broker::Broker& getBroker();

    void setDumpComplete();

    template <class F> void eachConnection(const F& f) {
        std::for_each(connections.begin(), connections.end(),
                      boost::bind(f, boost::bind(&ConnectionMap::value_type::second, _1)));
    }
    
  private:
    typedef std::map<ConnectionId, boost::intrusive_ptr<cluster::Connection> > ConnectionMap;
    typedef sys::PollableQueue<Event> EventQueue;
    enum State {
        START,      // Start state, no cluster update received yet.
        DISCARD,    // Discard updates up to dump start point.
        CATCHUP,    // Stalled at catchup point, waiting for dump.
        DUMPING,    // Stalled while sending a state dump.
        READY       // Normal processing.
    };

    void connectionEvent(const Event&);
    
    /** CPG deliver callback. */
    void deliver(
        cpg_handle_t /*handle*/,
        struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    /** CPG config change callback */
    void configChange(
        cpg_handle_t /*handle*/,
        struct cpg_name */*group*/,
        struct cpg_address */*members*/, int /*nMembers*/,
        struct cpg_address */*left*/, int /*nLeft*/,
        struct cpg_address */*joined*/, int /*nJoined*/
    );

    /** Callback to dispatch CPG events. */
    void dispatch(sys::DispatchHandle&);
    /** Callback if CPG fd is disconnected. */
    void disconnect(sys::DispatchHandle&);

    boost::intrusive_ptr<cluster::Connection> getConnection(const ConnectionId&);

    virtual qpid::management::ManagementObject* GetManagementObject(void) const;
    virtual management::Manageable::status_t ManagementMethod (uint32_t methodId, management::Args& args, std::string& text);
    void stopClusterNode(void);
    void stopFullCluster(void);
	void updateMemberStats(void);

    mutable sys::Monitor lock;  // Protect access to members.
    broker::Broker& broker;
    boost::shared_ptr<sys::Poller> poller;
    Cpg cpg;
    Cpg::Name name;
    Url url;
    ClusterMap map;
    MemberId self;
    ConnectionMap connections;
    NoOpConnectionOutputHandler shadowOut;
    sys::DispatchHandle cpgDispatchHandle;
    EventQueue connectionEventQueue;
    State state;
    qmf::org::apache::qpid::cluster::Cluster* mgmtObject; // mgnt owns lifecycle

    // Handlers for different states.
    ClusterHandler* handler;
    JoiningHandler joiningHandler;
    MemberHandler memberHandler;

  friend class JoiningHandler;
  friend class MemberHandler;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
