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
void insertSet(ClusterMap::Set& set, const ClusterMap::Map::value_type& v) { set.insert(v.first); }

void insertMap(ClusterMap::Map& map, FieldTable::ValueMap::value_type vt) {
    map.insert(ClusterMap::Map::value_type(vt.first, Url(vt.second->get<std::string>())));
}

void assignMap(ClusterMap::Map& map, const FieldTable& ft) {
    map.clear();
    std::for_each(ft.begin(), ft.end(), boost::bind(&insertMap, boost::ref(map), _1));
}

void insertFieldTable(FieldTable& ft, const ClusterMap::Map::value_type& vt) {
    return ft.setString(vt.first.str(), vt.second.str());
}

void assignFieldTable(FieldTable& ft, const ClusterMap::Map& map) {
    ft.clear();
    std::for_each(map.begin(), map.end(), boost::bind(&insertFieldTable, boost::ref(ft), _1));
}
}

ClusterMap::ClusterMap() {}

ClusterMap::ClusterMap(const MemberId& id, const Url& url , bool isMember) {
    alive.insert(id);
    if (isMember)
        members[id] = url;
    else
        newbies[id] = url;
}

ClusterMap::ClusterMap(const FieldTable& newbiesFt, const FieldTable& membersFt) {
    assignMap(newbies, newbiesFt);
    assignMap(members, membersFt);
    std::for_each(newbies.begin(), newbies.end(), boost::bind(&insertSet, boost::ref(alive), _1));
    std::for_each(members.begin(), members.end(), boost::bind(&insertSet, boost::ref(alive), _1));
}

bool ClusterMap::configChange(
    cpg_address *current, int nCurrent,
    cpg_address *left, int nLeft,
    cpg_address */*joined*/, int /*nJoined*/)
{
    cpg_address* a;
    bool memberChange=false;
    for (a = left; a != left+nLeft; ++a) {
        memberChange = members.erase(*a);
        newbies.erase(*a);
    }
    alive.clear();
    std::copy(current, current+nCurrent, std::inserter(alive, alive.end()));
    return memberChange;
}

Url ClusterMap::getUrl(const Map& map, const  MemberId& id) {
    Map::const_iterator i = map.find(id);
    return i == map.end() ? Url() : i->second;
}
     
MemberId ClusterMap::firstNewbie() const {
    return newbies.empty() ? MemberId() : newbies.begin()->first;
}

ClusterConnectionMembershipBody ClusterMap::asMethodBody() const {
    framing::ClusterConnectionMembershipBody b;
    assignFieldTable(b.getNewbies(), newbies);
    assignFieldTable(b.getMembers(), members);
    return b;
}

std::vector<Url> ClusterMap::memberUrls() const {
    std::vector<Url> urls(members.size());
    std::transform(members.begin(), members.end(), urls.begin(),
                   boost::bind(&Map::value_type::second, _1));
    return urls;
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
        if (m.isNewbie(*i)) o << "(newbie)";
        o << " ";
    }
    return o;
}

bool ClusterMap::dumpRequest(const MemberId& id, const std::string& url) {
    if (isAlive(id)) {
        newbies[id] = Url(url);
        return true;
    }
    return false;
}

bool ClusterMap::ready(const MemberId& id, const Url& url) {
    return isAlive(id) &&  members.insert(Map::value_type(id,url)).second;
}

boost::optional<Url> ClusterMap::dumpOffer(const MemberId& from, const MemberId& to) {
    Map::iterator i = newbies.find(to);
    if (isAlive(from) && i != newbies.end()) {
        Url url= i->second;
        newbies.erase(i);       // No longer a potential dumpee.
        return url;
    }
    return boost::none;
}

}} // namespace qpid::cluster
