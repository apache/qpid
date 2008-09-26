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

ClusterMap::ClusterMap() {}

MemberId ClusterMap::first() const {
    return (members.empty()) ?  MemberId() : members.begin()->first;
}

bool ClusterMap::left(const cpg_address* addrs, size_t nLeft) {
    bool changed=false;
    for (const cpg_address* a = addrs; a < addrs+nLeft; ++a) 
        changed = members.erase(*a) || changed;
    if (dumper && !isMember(dumper))
        dumper = MemberId();
    QPID_LOG_IF(debug, changed, "Members left. " << *this);
    return changed;
}

framing::ClusterUpdateBody ClusterMap::toControl() const {
    framing::ClusterUpdateBody b;
    for (Members::const_iterator i = members.begin(); i != members.end(); ++i) 
        b.getMembers().setString(i->first.str(), i->second.str());
    b.setDumper(dumper);
    return b;
}

bool ClusterMap::update(const framing::FieldTable& ftMembers, uint64_t dumper_) {
    dumper = MemberId(dumper_);
    bool changed = false;
    framing:: FieldTable::ValueMap::const_iterator i;
    for (i = ftMembers.begin(); i != ftMembers.end(); ++i) {
        MemberId id(i->first);
        Url url(i->second->get<std::string>());
        changed = members.insert(Members::value_type(id, url)).second || changed;
    }
    QPID_LOG_IF(debug, changed, "Update: " << *this);
    return changed;
}

std::vector<Url> ClusterMap::memberUrls() const {
    std::vector<Url> result(size());
    std::transform(members.begin(), members.end(), result.begin(),
                   boost::bind(&Members::value_type::second, _1));
    return result;        
}

std::ostream& operator<<(std::ostream& o, const ClusterMap& m) {
    o << "Broker members:";
    for (ClusterMap::Members::const_iterator i=m.members.begin(); i != m.members.end(); ++i) {
        o << " " << i->first;
        if (i->first == m.dumper) o << "(dumping)";
    }
    return o;
}

bool ClusterMap::sendUpdate(const MemberId& id) const {
    return dumper==id || (!dumper && first() == id);
}

bool ClusterMap::ready(const MemberId& id, const Url& url) {
    bool changed = members.insert(Members::value_type(id,url)).second;
    if (id == dumper) {
        dumper = MemberId();
        QPID_LOG(info, id << " finished dump. " << *this);
    }
    else {
        QPID_LOG(info, id << " joined, url=" << url << ". " << *this);
    }
    return changed;
}

}} // namespace qpid::cluster
