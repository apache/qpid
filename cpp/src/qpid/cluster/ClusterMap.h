#ifndef QPID_CLUSTER_CLUSTERMAP_H
#define QPID_CLUSTER_CLUSTERMAP_H

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "types.h"
#include "qpid/framing/ClusterUpdateBody.h"
#include "qpid/Url.h"
#include <boost/function.hpp>
#include <vector>
#include <deque>
#include <map>
#include <iosfwd>

namespace qpid {
namespace cluster {

/**
 * Map of established cluster members and brain-dumps in progress.
 * A dumper is an established member that is sending catch-up data.
 * A dumpee is an aspiring member that is receiving catch-up data.
 */
class ClusterMap {
  public:
    typedef std::map<MemberId, Url> Members;
    Members members;
    MemberId dumper;

    ClusterMap();
    
    /** First member of the cluster in ID order, gets to perform one-off tasks. */
    MemberId first() const;

    /** Update for members leaving.
     *@return true if the cluster membership changed.
     */
    bool left(const cpg_address* addrs, size_t size);

    /** Convert map contents to a cluster update body. */
    framing::ClusterUpdateBody toControl() const;

    /** Add a new member or dump complete if id == dumper. */
    bool ready(const MemberId& id, const Url& url);

    /** Apply update delivered from cluster.
     *@return true if cluster membership changed.
     **/
    bool update(const framing::FieldTable& members, uint64_t dumper);

    bool isMember(const MemberId& id) const { return members.find(id) != members.end(); }

    bool sendUpdate(const MemberId& id) const; // True if id should send an update.
    std::vector<Url> memberUrls() const;
    size_t size() const { return members.size(); }
    
    bool empty() const { return members.empty(); }
  private:

  friend std::ostream& operator<<(std::ostream&, const ClusterMap&);
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CLUSTERMAP_H*/
