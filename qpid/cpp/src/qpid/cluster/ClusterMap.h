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
#include "qpid/framing/ClusterMapBody.h"
#include "qpid/Url.h"
#include <boost/optional.hpp>
#include <vector>
#include <map>

namespace qpid {
namespace cluster {

/**
 * Map of established cluster members and brain-dumps in progress.
 * A dumper is an established member that is sending catch-up data.
 * A dumpee is an aspiring member that is receiving catch-up data.
 */
class ClusterMap
{
  public:
    ClusterMap();
    
    /** Update map for url-notice event.
     *@param from Member that sent the notice.
     *@param url  URL for from.
     *@return MemberId of member that should dump to URL, or a null
     * MemberId() if no dump is needed.
     */
    MemberId urlNotice(const MemberId& from, const Url& url);

    /** Dump failed notice */
    void dumpFailed(const MemberId&);
    
    /** Update map for leave event */
    void leave(const MemberId&);

    /** Number of unfinished dumps for member. */
    int dumps(const MemberId&) const;

    /** Convert map contents to a cluster control body. */
    framing::ClusterMapBody toControl() const;

    /** Initialize map contents from a cluster control body. */
    void fromControl(const framing::ClusterMapBody&);

    size_t memberCount() const { return members.size(); }    
    size_t dumpeeCount() const { return dumpees.size(); }    
    bool isMember(const MemberId& id) const { return members.find(id) != members.end(); }  
    bool isDumpee(const MemberId& id) const { return dumpees.find(id) != dumpees.end(); }

  private:
    struct Dumpee { Url url; MemberId dumper; };
    typedef std::map<MemberId, Url> MemberMap;
    typedef std::map<MemberId, Dumpee> DumpeeMap;
    struct MatchDumper;
    
    MemberId nextDumper() const;

    MemberMap members;
    DumpeeMap dumpees;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CLUSTERMAP_H*/
