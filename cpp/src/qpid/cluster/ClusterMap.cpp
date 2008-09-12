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
#include <boost/bind.hpp>
#include <algorithm>
#include <functional>

namespace qpid {
using namespace framing;

namespace cluster {

ClusterMap::ClusterMap() {}

MemberId ClusterMap::urlNotice(const MemberId& id, const Url& url) {
    if (isMember(id)) return MemberId(); // Ignore notices from established members.
    if (isDumpee(id)) {
        // Dumpee caught up, graduate to member with new URL and remove dumper from list.
        dumpees.erase(id);
        members[id] = url;
    }
    else if (members.empty()) {
        // First in cluster, congratulations!
        members[id] = url;
    }
    else {
        // New member needs brain dump.
        MemberId dumper = nextDumper();
        Dumpee& d = dumpees[id];
        d.url = url;
        d.dumper = dumper;
        return dumper;
    }
    return MemberId();
}

MemberId ClusterMap::nextDumper() const {
    // Choose the first member in member-id order of the group that
    // has the least number of dumps-in-progress.
    assert(!members.empty());
    MemberId dumper = members.begin()->first;
    int minDumps  = dumps(dumper);
    MemberMap::const_iterator i = ++members.begin();
    while (i != members.end()) {
        int d = dumps(i->first);
        if (d < minDumps) {
            minDumps = d;
            dumper = i->first;
        }
        ++i;
    }
    return dumper;
}

void ClusterMap::leave(const MemberId& id) {
    if (isDumpee(id))
        dumpees.erase(id);
    if (isMember(id)) {
        members.erase(id);
        DumpeeMap::iterator i = dumpees.begin();
        while (i != dumpees.end()) {
            if (i->second.dumper == id) dumpees.erase(i++);
            else ++i;
        }
    }
}

struct ClusterMap::MatchDumper {
    MemberId d;
    MatchDumper(const MemberId& i) : d(i) {}
    bool operator()(const DumpeeMap::value_type& v) const { return v.second.dumper == d; }
};

int ClusterMap::dumps(const MemberId& id) const {
    return std::count_if(dumpees.begin(), dumpees.end(), MatchDumper(id));
}

void ClusterMap::dumpFailed(const MemberId& dumpee) { dumpees.erase(dumpee); }

framing::ClusterMapBody ClusterMap::toControl() const {
    framing::ClusterMapBody b;
    for (MemberMap::const_iterator i = members.begin(); i != members.end(); ++i) 
        b.getMembers().setString(i->first.str(), i->second.str());
    for (DumpeeMap::const_iterator i = dumpees.begin(); i != dumpees.end(); ++i)  {
        b.getDumpees().setString(i->first.str(), i->second.url.str());
        b.getDumps().setString(i->first.str(), i->second.dumper.str());
    }
    return b;
}

void ClusterMap::fromControl(const framing::ClusterMapBody& b) {
    *this = ClusterMap();       // Reset any current contents.
    FieldTable::ValueMap::const_iterator i;
    for (i = b.getMembers().begin(); i != b.getMembers().end(); ++i) 
        members[i->first] = Url(i->second->get<std::string>());
    for (i = b.getDumpees().begin(); i != b.getDumpees().end(); ++i)
        dumpees[i->first].url = Url(i->second->get<std::string>());
    for (i = b.getDumps().begin(); i != b.getDumps().end(); ++i)
        dumpees[i->first].dumper = MemberId(i->second->get<std::string>());
}

}} // namespace qpid::cluster
