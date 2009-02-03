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
#include "ClusterMap.h"
#include "qpid/Url.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <algorithm>
#include <functional>
#include <iterator>
#include <ostream>

namespace qpid {
using namespace framing;

namespace cluster {

namespace {

void addFieldTableValue(FieldTable::ValueMap::value_type vt, ClusterMap::Map& map, ClusterMap::Set& set) {
    MemberId id(vt.first);
    set.insert(id);
    std::string url = vt.second->get<std::string>();
    if (!url.empty())
        map.insert(ClusterMap::Map::value_type(id, Url(url)));
}

void insertFieldTableFromMapValue(FieldTable& ft, const ClusterMap::Map::value_type& vt) {
    ft.setString(vt.first.str(), vt.second.str());
}

void assignFieldTable(FieldTable& ft, const ClusterMap::Map& map) {
    ft.clear();
    std::for_each(map.begin(), map.end(), boost::bind(&insertFieldTableFromMapValue, boost::ref(ft), _1));
}

}

ClusterMap::ClusterMap() {}

ClusterMap::ClusterMap(const MemberId& id, const Url& url , bool isMember) {
    alive.insert(id);
    if (isMember)
        members[id] = url;
    else
        joiners[id] = url;
}

ClusterMap::ClusterMap(const FieldTable& joinersFt, const FieldTable& membersFt) {
    std::for_each(joinersFt.begin(), joinersFt.end(), boost::bind(&addFieldTableValue, _1, boost::ref(joiners), boost::ref(alive)));
    std::for_each(membersFt.begin(), membersFt.end(), boost::bind(&addFieldTableValue, _1, boost::ref(members), boost::ref(alive)));
}

void ClusterMap::toMethodBody(framing::ClusterConnectionMembershipBody& b) const {
    b.getJoiners().clear();
    std::for_each(joiners.begin(), joiners.end(), boost::bind(&insertFieldTableFromMapValue, boost::ref(b.getJoiners()), _1));
    for(Set::const_iterator i = alive.begin(); i != alive.end(); ++i) {
        if (!isMember(*i) && !isJoiner(*i))
            b.getJoiners().setString(i->str(), std::string());
    }
    b.getMembers().clear();
    std::for_each(members.begin(), members.end(), boost::bind(&insertFieldTableFromMapValue, boost::ref(b.getMembers()), _1));
}

bool ClusterMap::configChange(
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address */*joined*/, int /*nJoined*/)
{
    cpg_address* a;
    bool memberChange=false;
    for (a = left; a != left+nLeft; ++a) {
        memberChange = memberChange || members.erase(*a);
        joiners.erase(*a);
    }
    alive.clear();
    std::copy(current, current+nCurrent, std::inserter(alive, alive.end()));
    return memberChange;
}

Url ClusterMap::getUrl(const Map& map, const  MemberId& id) {
    Map::const_iterator i = map.find(id);
    return i == map.end() ? Url() : i->second;
}
     
MemberId ClusterMap::firstJoiner() const {
    return joiners.empty() ? MemberId() : joiners.begin()->first;
}

std::vector<string> ClusterMap::memberIds() const {
    std::vector<string> ids;
    for (Map::const_iterator iter = members.begin();
         iter != members.end(); iter++) {
        std::stringstream stream;
        stream << iter->first;
        ids.push_back(stream.str());
    }
    return ids;
}

std::vector<Url> ClusterMap::memberUrls() const {
    std::vector<Url> urls(members.size());
    std::transform(members.begin(), members.end(), urls.begin(),
                   boost::bind(&Map::value_type::second, _1));
    return urls;
}

ClusterMap::Set ClusterMap::getAlive() const {
    return alive;
}

std::ostream& operator<<(std::ostream& o, const ClusterMap::Map& m) {
    std::ostream_iterator<MemberId> oi(o);
    std::transform(m.begin(), m.end(), oi, boost::bind(&ClusterMap::Map::value_type::first, _1));
    return o;
}

std::ostream& operator<<(std::ostream& o, const ClusterMap& m) {
    for (ClusterMap::Set::const_iterator i = m.alive.begin(); i != m.alive.end(); ++i) {
        o << *i;
        if (m.isMember(*i)) o << "(member)";
        else if (m.isJoiner(*i)) o << "(joiner)";
        else o << "(unknown)";
        o << " ";
    }
    return o;
}

bool ClusterMap::updateRequest(const MemberId& id, const std::string& url) {
    if (isAlive(id)) {
        joiners[id] = Url(url);
        return true;
    }
    return false;
}

bool ClusterMap::ready(const MemberId& id, const Url& url) {
    return isAlive(id) &&  members.insert(Map::value_type(id,url)).second;
}

bool ClusterMap::configChange(const std::string& addresses) {
    bool memberChange = false;
    Set update;
    for (std::string::const_iterator i = addresses.begin(); i < addresses.end(); i += 8)  
        update.insert(MemberId(std::string(i, i+8)));
    Set removed;
    std::set_difference(alive.begin(), alive.end(),
                        update.begin(), update.end(),
                        std::inserter(removed, removed.begin()));
    alive = update;
    for (Set::const_iterator i = removed.begin(); i != removed.end(); ++i) {
        memberChange = memberChange || members.erase(*i);
        joiners.erase(*i);
    }
    return memberChange;
}

boost::optional<Url> ClusterMap::updateOffer(const MemberId& from, const MemberId& to) {
    Map::iterator i = joiners.find(to);
    if (isAlive(from) && i != joiners.end()) {
        Url url= i->second;
        joiners.erase(i);       // No longer a potential updatee.
        return url;
    }
    return boost::optional<Url>();
}

ClusterMap::Set ClusterMap::intersection(const ClusterMap::Set& a, const ClusterMap::Set& b)
{
    Set intersection;
    std::set_intersection(a.begin(), a.end(),
                          b.begin(), b.end(),
                          std::inserter(intersection, intersection.begin()));
    return intersection;

}
}} // namespace qpid::cluster
