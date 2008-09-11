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

#include "qpid/cluster/Cpg.h"
#include "qpid/cluster/Event.h"
#include "qpid/sys/PollableQueue.h"
#include "qpid/cluster/NoOpConnectionOutputHandler.h"

#include "qpid/broker/Broker.h"
#include "qpid/sys/Monitor.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/Url.h"

#include <boost/intrusive_ptr.hpp>

#include <map>
#include <vector>

namespace qpid {
namespace cluster {

class Connection;

/**
 * Connection to the cluster.
 * Keeps cluster membership data.
 */
class Cluster : private Cpg::Handler
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
    void mcastFrame(const framing::AMQFrame&, const ConnectionId&);
    void mcastBuffer(const char*, size_t, const ConnectionId&);
    void mcastEvent(const Event& e);
    
    /** Leave the cluster */
    void leave();
    
    void joining(const MemberId&, const std::string& url);
    void ready(const MemberId&);

    MemberId getSelf() const { return self; }

    void shutdown();

    broker::Broker& getBroker();
    
  private:
    typedef std::map<MemberId, Url>  UrlMap;
    typedef std::map<ConnectionId, boost::intrusive_ptr<cluster::Connection> > ConnectionMap;
    typedef sys::PollableQueue<Event> EventQueue;

    boost::function<void()> shutdownNext;

    /** Handle a delivered frame */
    void deliverFrame(framing::AMQFrame&, const ConnectionId&);

    void deliverBuffer(const char*, size_t, const ConnectionId&);

    void deliverEvent(const Event&);
    
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

    void handleMethod(MemberId from, cluster::Connection* connection, framing::AMQMethodBody& method);

    boost::intrusive_ptr<cluster::Connection> getConnection(const ConnectionId&);

    mutable sys::Monitor lock;  // Protect access to members.
    broker::Broker& broker;
    boost::shared_ptr<sys::Poller> poller;
    Cpg cpg;
    Cpg::Name name;
    Url url;
    UrlMap urls;
    MemberId self;
    ConnectionMap connections;
    NoOpConnectionOutputHandler shadowOut;
    sys::DispatchHandle cpgDispatchHandle;
    EventQueue deliverQueue;
    
  friend std::ostream& operator <<(std::ostream&, const Cluster&);
  friend std::ostream& operator <<(std::ostream&, const UrlMap::value_type&);
  friend std::ostream& operator <<(std::ostream&, const UrlMap&);
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
