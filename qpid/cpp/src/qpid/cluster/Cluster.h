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
#include "qpid/framing/ProtocolVersion.h"
#include <boost/scoped_ptr.hpp>
#include <map>
#include <vector>

namespace qpid {
namespace cluster {

/**
 * Represents a cluster. Creating an instance joins current process
 * to the cluster. 
 */
class Cluster : public framing::FrameHandler, private sys::Runnable {
  public:
    /** Details of a cluster member */
    struct Member {
        Member(const std::string& url_) : url(url_) {}
        std::string url;
    };

    typedef std::vector<shared_ptr<const Member> > MemberList;
    
    /**
     * Join a cluster.
     * @param name of the cluster.
     * @param url of this broker, sent to the cluster.
     * @param next handler receives the frame when it has been
     * acknowledged by the cluster.
     */
    Cluster(const std::string& name,
            const std::string& url,
            framing::FrameHandler& next,
            framing::ProtocolVersion);

    ~Cluster();
    
    /** Multicast a frame to the cluster. */
    void handle(framing::AMQFrame&);

    /** Get the current cluster membership. */
    MemberList getMembers() const;
    
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

    Id self;
    Cpg::Name name;
    std::string url;
    framing::ProtocolVersion version;
    boost::scoped_ptr<Cpg> cpg;
    framing::FrameHandler& next;
    MemberMap members;
    sys::Thread dispatcher;
    
  protected:
    // Allow access from ClusterTest subclass.
    mutable sys::Monitor lock;

  friend std::ostream& operator <<(std::ostream&, const Cluster&);
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLUSTER_H*/
