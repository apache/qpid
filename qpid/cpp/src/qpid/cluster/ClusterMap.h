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
#include "qpid/Url.h"
#include "qpid/framing/ClusterConnectionMembershipBody.h"

#include <boost/function.hpp>
#include <boost/optional.hpp>

#include <vector>
#include <deque>
#include <map>
#include <set>
#include <iosfwd>

namespace qpid {
namespace cluster {

/**
 * Map of established cluster members and newbies waiting for a brain dump.
 */
class ClusterMap {
  public:
    typedef std::map<MemberId, Url> Map;
    typedef std::set<MemberId> Set;

    ClusterMap();
    ClusterMap(const MemberId& id, const Url& url, bool isReady);
    ClusterMap(const framing::FieldTable& urls, const framing::FieldTable& states);

    /** Update from config change.
     *@return true if member set changed.
     */
    bool configChange(
        cpg_address *current, int nCurrent,
        cpg_address *left, int nLeft,
        cpg_address *joined, int nJoined);

    bool configChange(const std::string& addresses);

    bool isNewbie(const MemberId& id) const { return newbies.find(id) != newbies.end(); }
    bool isMember(const MemberId& id) const { return members.find(id) != members.end(); }
    bool isAlive(const MemberId& id) const { return alive.find(id) != alive.end(); }
    
    Url getNewbieUrl(const MemberId& id) { return getUrl(newbies, id); }
    Url getMemberUrl(const MemberId& id) { return getUrl(members, id); }

    /** First newbie in the cluster in ID order, target for offers */
    MemberId firstNewbie() const;

    /** Convert map contents to a cluster control body. */
    framing::ClusterConnectionMembershipBody asMethodBody() const;

    size_t aliveCount() const { return alive.size(); }
    size_t memberCount() const { return members.size(); }
    std::vector<Url> memberUrls() const;
    Set getAlive() const;

    bool dumpRequest(const MemberId& id, const std::string& url);       
    /** Return non-empty Url if accepted */
    boost::optional<Url> dumpOffer(const MemberId& from, const MemberId& to);

    /**@return true If this is a new member */ 
    bool ready(const MemberId& id, const Url&);

    /**
     * Utility method to return intersection of two member sets
     */
    static Set intersection(const Set& a, const Set& b);
  private:
    Url getUrl(const Map& map, const  MemberId& id);
    
    Map newbies, members;
    Set alive;

  friend std::ostream& operator<<(std::ostream&, const Map&);
  friend std::ostream& operator<<(std::ostream&, const ClusterMap&);
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CLUSTERMAP_H*/
