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

// FIXME aconway 2008-09-15: rename cluster status?

/**
 * Map of established cluster members and brain-dumps in progress.
 * A dumper is an established member that is sending catch-up data.
 * A dumpee is an aspiring member that is receiving catch-up data.
 */
class ClusterMap : public std::map<MemberId, Url> {
  public:
    ClusterMap();
    
    /** First member of the cluster in ID order, gets to perform one-off tasks. */
    MemberId first();

    /** Update for CPG config change. */
    void configChange(const cpg_address* addrs, size_t size);


    /** Convert map contents to a cluster control body. */
    framing::ClusterUpdateBody toControl() const;

    /** Update with first member. */
    using std::map<MemberId, Url>::insert;
    void insert(const MemberId& id, const Url& url) { insert(value_type(id,url)); }
    void setDumping(bool d) { dumping = d; }

    /** Apply update delivered from clsuter. */
    void update(const framing::FieldTable& members, bool dumping);
    void fromControl(const framing::ClusterUpdateBody&);

    bool isMember(const MemberId& id) const { return find(id) != end(); }
    bool isDumping() const { return dumping; }

    std::vector<Url> memberUrls() const;

  private:
    bool dumping;

  friend std::ostream& operator<<(std::ostream&, const ClusterMap&);
  friend std::ostream& operator<<(std::ostream& o, const ClusterMap::value_type& mv);
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CLUSTERMAP_H*/
