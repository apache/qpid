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

ClusterMap::ClusterMap() : dumping(false) {}

MemberId ClusterMap::first() {
    return (empty()) ?  MemberId() : begin()->first;
}

void ClusterMap::configChange(const cpg_address* addrs, size_t size) {
    iterator i = begin();
    while (i != end()) { // Remove members that are no longer in addrs.
        if (std::find(addrs, addrs+size, i->first) == addrs+size)
            erase(i++);
        else
            ++i;
    }
}

framing::ClusterUpdateBody ClusterMap::toControl() const {
    framing::ClusterUpdateBody b;
    for (const_iterator i = begin(); i != end(); ++i) 
        b.getMembers().setString(i->first.str(), i->second.str());
    b.setDumping(dumping);
    return b;
}

void ClusterMap::update(const FieldTable& ftMembers, bool dump) {
    dumping = dump;
    FieldTable::ValueMap::const_iterator i;
    for (i = ftMembers.begin(); i != ftMembers.end(); ++i) 
        (*this)[i->first] = Url(i->second->get<std::string>());
}

void ClusterMap::fromControl(const framing::ClusterUpdateBody& b) {
    update(b.getMembers(), b.getDumping());
}

std::vector<Url> ClusterMap::memberUrls() const {
    std::vector<Url> result(size());
    std::transform(begin(), end(), result.begin(),
                   boost::bind(&value_type::second, _1));
    return result;        
}

std::ostream& operator<<(std::ostream& o, const ClusterMap::value_type& mv) {
    return o << mv.first << "=" << mv.second;
}

std::ostream& operator<<(std::ostream& o, const ClusterMap& m) {
    std::ostream_iterator<ClusterMap::value_type> im(o, "\n    ");
    std::copy(m.begin(), m.end(), im);
    return o;
}

}} // namespace qpid::cluster
