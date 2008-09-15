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
#include <iterator>
#include <ostream>

namespace qpid {
using namespace framing;

namespace cluster {

ClusterMap::ClusterMap() : stalled(false) {}

MemberId ClusterMap::dumpRequest(const MemberId& id, const Url& url) {
    if (stalled) {
        stallq.push_back(boost::bind(&ClusterMap::dumpRequest, this, id, url));
        return MemberId();
    }
    MemberId dumper = nextDumper();
    Dumpee& d = dumpees[id];
    d.url = url;
    d.dumper = dumper;
    return dumper;
}

void ClusterMap::ready(const MemberId& id, const Url& url) {
    if (stalled) {
        stallq.push_back(boost::bind(&ClusterMap::ready, this, id, url));
        return;
    }
    dumpees.erase(id);
    members[id] = url;
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
    if (stalled) {
        stallq.push_back(boost::bind(&ClusterMap::leave, this, id));
        return;
    }

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

void ClusterMap::dumpError(const MemberId& dumpee) {
    if (stalled) {
        stallq.push_back(boost::bind(&ClusterMap::dumpError, this, dumpee));
        return;
    }
    dumpees.erase(dumpee);
}

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

void ClusterMap::init(const FieldTable& ftMembers,const FieldTable& ftDumpees, const FieldTable& ftDumps) {
    *this = ClusterMap();       // Reset any current contents.
    FieldTable::ValueMap::const_iterator i;
    for (i = ftMembers.begin(); i != ftMembers.end(); ++i) 
        members[i->first] = Url(i->second->get<std::string>());
    for (i = ftDumpees.begin(); i != ftDumpees.end(); ++i)
        dumpees[i->first].url = Url(i->second->get<std::string>());
    for (i = ftDumps.begin(); i != ftDumps.end(); ++i)
        dumpees[i->first].dumper = MemberId(i->second->get<std::string>());
}

void ClusterMap::fromControl(const framing::ClusterMapBody& b) {
    init(b.getMembers(), b.getDumpees(), b.getDumps());
}

std::vector<Url> ClusterMap::memberUrls() const {
    std::vector<Url> result(members.size());
    std::transform(members.begin(), members.end(), result.begin(),
                   boost::bind(&MemberMap::value_type::second, _1));
    return result;        
}

void ClusterMap::stall() { stalled = true; }

namespace {
template <class F> void call(const F& f) { f(); }
}

void ClusterMap::unstall() {
    stalled = false;
    std::for_each(stallq.begin(), stallq.end(),
                  boost::bind(&boost::function<void()>::operator(), _1));
    stallq.clear();
}

std::ostream& operator<<(std::ostream& o, const ClusterMap::MemberMap::value_type& mv) {
    return o << mv.first << "=" << mv.second;
}

std::ostream& operator<<(std::ostream& o, const ClusterMap::DumpeeMap::value_type& dv) {
    return o << "dump: " << dv.second.dumper << " to "  << dv.first << "=" << dv.second.url;
}

std::ostream& operator<<(std::ostream& o, const ClusterMap& m) {
    std::ostream_iterator<ClusterMap::MemberMap::value_type> im(o, "\n    ");
    std::ostream_iterator<ClusterMap::DumpeeMap::value_type> id(o, "\n    ");
    std::copy(m.members.begin(), m.members.end(), im);
    std::copy(m.dumpees.begin(), m.dumpees.end(), id);
    return o;
}

}} // namespace qpid::cluster
