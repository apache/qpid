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

void ClusterMap::left(const cpg_address* addrs, size_t size) {
    size_t (Members::*erase)(const MemberId&) = &Members::erase;
    std::for_each(addrs, addrs+size, boost::bind(erase, &members, _1));
    if (dumper && !isMember(dumper))
        dumper = MemberId();
}

framing::ClusterUpdateBody ClusterMap::toControl() const {
    framing::ClusterUpdateBody b;
    for (Members::const_iterator i = members.begin(); i != members.end(); ++i) 
        b.getMembers().setString(i->first.str(), i->second.str());
    b.setDumper(dumper);
    return b;
}

void ClusterMap::update(const framing::FieldTable& ftMembers, uint64_t dumper_) {
    framing:: FieldTable::ValueMap::const_iterator i;
    for (i = ftMembers.begin(); i != ftMembers.end(); ++i) 
        members[i->first] = Url(i->second->get<std::string>());
    dumper = MemberId(dumper_);
}

std::vector<Url> ClusterMap::memberUrls() const {
    std::vector<Url> result(size());
    std::transform(members.begin(), members.end(), result.begin(),
                   boost::bind(&Members::value_type::second, _1));
    return result;        
}

std::ostream& operator<<(std::ostream& o, const ClusterMap::Members::value_type& mv) {
    return o << mv.first << "=" << mv.second;
}

std::ostream& operator<<(std::ostream& o, const ClusterMap& m) {
    std::ostream_iterator<ClusterMap::Members::value_type> im(o, "\n    ");
    o << "dumper=" << m.dumper << ", members:\n    ";
    std::copy(m.members.begin(), m.members.end(), im);
    return o;
}

bool ClusterMap::sendUpdate(const MemberId& id) const {
    return dumper==id || (!dumper && first() == id);
}

void ClusterMap::ready(const MemberId& id, const Url& url) {
    members[id] = url;
    if (id == dumper)
        dumper = MemberId();
    QPID_LOG(info, id << " joined cluster: " << *this);
}

}} // namespace qpid::cluster
