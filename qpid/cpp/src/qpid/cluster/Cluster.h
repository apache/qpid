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

#include "qpid/broker/Broker.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/log/Logger.h"
#include "qpid/Url.h"


#include <boost/optional.hpp>
#include <boost/function.hpp>
#include <boost/intrusive_ptr.hpp>

#include <map>
#include <vector>

namespace qpid { namespace cluster {

/**
 * Connection to the cluster.
 * Keeps cluster membership data.
 */
class Cluster : private sys::Runnable, private Cpg::Handler
{
  public:
    /** Details of a cluster member */
    struct Member {
        Member(const Url& url_=Url()) : url(url_) {}
        Url url;        ///< Broker address.
    };
    
    typedef std::vector<Member> MemberList;

    /**
     * Join a cluster.
     * @param name of the cluster.
     * @param url of this broker, sent to the cluster.
     */
    Cluster(const std::string& name, const Url& url, broker::Broker&);

    virtual ~Cluster();

    // FIXME aconway 2008-01-29: 
    boost::intrusive_ptr<broker::PreviewSessionManager::Observer> getObserver() { return observer; }
    
    /** Get the current cluster membership. */
    MemberList getMembers() const;

    /** Number of members in the cluster. */
    size_t size() const;

    bool empty() const { return size() == 0; }
    
    /** Wait for predicate(*this) to be true, up to timeout.
     *@return True if predicate became true, false if timed out.
     *Note the predicate may not be true after wait returns,
     *all the caller can say is it was true at some earlier point.
     */
    bool wait(boost::function<bool(const Cluster&)> predicate,
              sys::Duration timeout=sys::TIME_INFINITE) const;

    /** Send frame to the cluster */
    void send(framing::AMQFrame&, framing::FrameHandler*);
    
  private:
    typedef Cpg::Id Id;
    typedef std::map<Id, Member>  MemberMap;
    
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
    void handleClusterFrame(Id from, framing::AMQFrame&);

    mutable sys::Monitor lock;
    Cpg cpg;
    Cpg::Name name;
    Url url;
    Id self;
    MemberMap members;
    sys::Thread dispatcher;
    boost::function<void()> callback;
    boost::intrusive_ptr<broker::PreviewSessionManager::Observer> observer;

  friend std::ostream& operator <<(std::ostream&, const Cluster&);
  friend std::ostream& operator <<(std::ostream&, const MemberMap::value_type&);
  friend std::ostream& operator <<(std::ostream&, const MemberMap&);
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
