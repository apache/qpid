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
#include "InitialStatusMap.h"
#include <algorithm>
#include <boost/bind.hpp>

using namespace std;
using namespace boost;

namespace qpid {
namespace cluster {

InitialStatusMap::InitialStatusMap(const MemberId& self_, size_t size_)
    : self(self_), completed(), resendNeeded(), size(size_)
{}

void InitialStatusMap::configChange(const MemberSet& members) {
    resendNeeded = false;
    bool wasComplete = isComplete();
    if (firstConfig.empty()) firstConfig = members;
    MemberSet::const_iterator i = members.begin();
    Map::iterator j = map.begin();
    while (i != members.end() || j != map.end()) {
        if (i == members.end()) { // j not in members, member left
            Map::iterator k = j++;
            map.erase(k);
        }
        else if (j == map.end()) { // i not in map, member joined
            resendNeeded = true;
            map[*i] = optional<Status>();
            ++i;
        }
        else if (*i < j->first) { // i not in map, member joined
            resendNeeded = true;
            map[*i] = optional<Status>();
            ++i;
        }
        else if (*i > j->first) { // j not in members, member left
            Map::iterator k = j++;
            map.erase(k);
        }
        else {
            i++; j++;
        }
    }
    if (resendNeeded) {         // Clear all status
        for (Map::iterator i = map.begin(); i != map.end(); ++i)
            i->second = optional<Status>();
    }
    completed = isComplete() && !wasComplete; // Set completed on the transition.
}

void InitialStatusMap::received(const MemberId& m, const Status& s){
    bool wasComplete = isComplete();
    map[m] = s;
    completed = isComplete() && !wasComplete; // Set completed on the transition.
}

bool InitialStatusMap::notInitialized(const Map::value_type& v) {
    return !v.second;
}

bool InitialStatusMap::isActive(const Map::value_type& v) {
    return v.second && v.second->getActive();
}

bool InitialStatusMap::isComplete() {
    return !map.empty() && find_if(map.begin(), map.end(), &notInitialized) == map.end()
        && (map.size() >= size);
}

bool InitialStatusMap::transitionToComplete() {
    return completed;
}

bool InitialStatusMap::isResendNeeded() {
    bool ret = resendNeeded;
    resendNeeded = false;
    return ret;
}

bool InitialStatusMap::isUpdateNeeded() {
    assert(isComplete());
    // If there are any active members we need an update.
    return find_if(map.begin(), map.end(), &isActive) != map.end();
}

MemberSet InitialStatusMap::getElders() {
    assert(isComplete());
    MemberSet elders;
    // Elders are from first config change, active or higher node-id.
    for (MemberSet::iterator i = firstConfig.begin(); i != firstConfig.end(); ++i) {
        if (map.find(*i) != map.end() && (map[*i]->getActive() || *i > self))
            elders.insert(*i);
    }
    return elders;
}

// Get cluster ID from an active member or the youngest newcomer.
framing::Uuid InitialStatusMap::getClusterId() {
    assert(isComplete());
    assert(!map.empty());
    Map::iterator i = find_if(map.begin(), map.end(), &isActive);
    if (i != map.end())
        return i->second->getClusterId(); // An active member
    else
        return map.begin()->second->getClusterId();
}

std::map<MemberId, Url> InitialStatusMap::getMemberUrls() {
    assert(isComplete());
    assert(!isUpdateNeeded());
    std::map<MemberId, Url> urlMap;
    for (Map::iterator i = map.begin(); i != map.end(); ++i) {
        assert(i->second);
        urlMap.insert(std::make_pair(i->first, i->second->getUrl()));
    }
    return urlMap;
}

}} // namespace qpid::cluster
