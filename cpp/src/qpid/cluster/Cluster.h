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
#include "qpid/framing/FrameHandler.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Runnable.h"
#include "qpid/shared_ptr.h"
#include <boost/function.hpp>
#include <boost/scoped_ptr.hpp>
#include <map>
#include <vector>

namespace qpid {

namespace broker {
class HandlerUpdater;
}

namespace cluster {

class ChannelManager;

/**
 * Represents a cluster, provides access to data about members.
 *
 * Implements a FrameHandler that multicasts frames to the cluster.
 *
 * Requires a handler for frames arriving from the cluster,
 * normally a ChannelManager but other handlers could be interposed
 * for testing, logging etc.
 */
class Cluster : public framing::FrameHandler, private sys::Runnable {
  public:
    /** Details of a cluster member */
    struct Member {
        typedef shared_ptr<const Member> Ptr;
        /** Status of a cluster member. */
        enum Status {
            JOIN,               ///< Process joined the group.
            LEAVE,              ///< Process left the group cleanly.
            NODEDOWN,           ///< Process's node went down.
            NODEUP,             ///< Process's node joined the cluster.
            PROCDOWN,           ///< Process died without leaving.
            BROKER              ///< Broker details are available.
        };

        Member(const cpg_address&);
        std::string url;
        Status status;
    };
    
    typedef std::vector<Member::Ptr> MemberList;
    
    /**
     * Create a cluster object but do not joing.
     * @param name of the cluster.
     * @param url of this broker, sent to the cluster.
     */
    Cluster(const std::string& name, const std::string& url);

    ~Cluster();

    /** Join the cluster.
     *@handler is the handler for frames arriving from the cluster.
     */
    void join(framing::FrameHandler::Chain handler);
    
    /** Multicast a frame to the cluster. */
    void handle(framing::AMQFrame&);

    /** Get the current cluster membership. */
    MemberList getMembers() const;

    /** Called when membership changes. */
    void setCallback(boost::function<void()>);
    
    /** Number of members in the cluster. */
    size_t size() const;

  private:
    typedef Cpg::Id Id;
    typedef std::map<Id, shared_ptr<Member> >  MemberMap;

    void run();
    void notify();
    void cpgDeliver(
        cpg_handle_t /*handle*/,
        struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    void cpgConfigChange(
        cpg_handle_t /*handle*/,
        struct cpg_name */*group*/,
        struct cpg_address */*members*/, int /*nMembers*/,
        struct cpg_address */*left*/, int /*nLeft*/,
        struct cpg_address */*joined*/, int /*nJoined*/
    );

    mutable sys::Monitor lock;
    Cpg::Name name;
    std::string url;
    boost::scoped_ptr<Cpg> cpg;
    Id self;
    MemberMap members;
    sys::Thread dispatcher;
    boost::function<void()> callback;

  friend std::ostream& operator <<(std::ostream&, const Cluster&);
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
