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
#include "qpid/cluster/ShadowConnectionOutputHandler.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/Connection.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/log/Logger.h"
#include "qpid/Url.h"
#include "qpid/RefCounted.h"

#include <boost/optional.hpp>
#include <boost/function.hpp>
#include <boost/intrusive_ptr.hpp>

#include <map>
#include <vector>

namespace qpid {
namespace cluster {

class ConnectionInterceptor;

/**
 * Connection to the cluster.
 * Keeps cluster membership data.
 */
class Cluster : private sys::Runnable, private Cpg::Handler, public RefCounted
{
  public:
    typedef boost::tuple<Cpg::Id, void*> ShadowConnectionId;

    /** Details of a cluster member */
    struct Member {
        Cpg::Id  id;
        Url url;
    };
    
    typedef std::vector<Member> MemberList;

    /**
     * Join a cluster.
     * @param name of the cluster.
     * @param url of this broker, sent to the cluster.
     */
    Cluster(const std::string& name, const Url& url, broker::Broker&);

    virtual ~Cluster();

    /** Initialize interceptors for a new connection */
    void initialize(broker::Connection&);
    
    /** Get the current cluster membership. */
    MemberList getMembers() const;

    /** Number of members in the cluster. */
    size_t size() const;

    bool empty() const { return size() == 0; }
    
    /** Send frame to the cluster */
    void send(const framing::AMQFrame&, ConnectionInterceptor*);

    /** Leave the cluster */
    void leave();
    
    // Cluster frame handing functions
    void notify(const std::string& url);
    void connectionClose();
    
  private:
    typedef Cpg::Id Id;
    typedef std::map<Id, Member>  MemberMap;
    typedef std::map<ShadowConnectionId, ConnectionInterceptor*> ShadowConnectionMap;

    boost::function<void()> shutdownNext;
    
    void notify();              ///< Notify cluster of my details.

    void deliver(
        cpg_handle_t /*handle*/,
        struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    void configChange(
        cpg_handle_t /*handle*/,
        struct cpg_name */*group*/,
        struct cpg_address */*members*/, int /*nMembers*/,
        struct cpg_address */*left*/, int /*nLeft*/,
        struct cpg_address */*joined*/, int /*nJoined*/
    );

    void run();

    void handleMethod(Id from, ConnectionInterceptor* connection, framing::AMQMethodBody& method);

    ConnectionInterceptor* getShadowConnection(const Cpg::Id&, void*);

    mutable sys::Monitor lock;  // Protect access to members.
    Cpg cpg;
    boost::intrusive_ptr<broker::Broker> broker;
    Cpg::Name name;
    Url url;
    MemberMap members;
    sys::Thread dispatcher;
    Id self;
    ShadowConnectionMap shadowConnectionMap;
    ShadowConnectionOutputHandler shadowOut;
    char buffer[64*1024];       // FIXME aconway 2008-07-04: buffer management.

  friend std::ostream& operator <<(std::ostream&, const Cluster&);
  friend std::ostream& operator <<(std::ostream&, const MemberMap::value_type&);
  friend std::ostream& operator <<(std::ostream&, const MemberMap&);
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
