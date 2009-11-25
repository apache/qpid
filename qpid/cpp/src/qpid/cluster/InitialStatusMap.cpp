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
#include "StoreStatus.h"
#include <algorithm>
#include <boost/bind.hpp>

namespace qpid {
namespace cluster {

using namespace std;
using namespace boost;
using namespace framing::cluster;
using namespace framing;

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

bool InitialStatusMap::isActive(const Map::value_type& v) {
    return v.second && v.second->getActive();
}

bool InitialStatusMap::hasStore(const Map::value_type& v) {
    return v.second &&
        (v.second->getStoreState() == STORE_STATE_CLEAN_STORE ||
         v.second->getStoreState() == STORE_STATE_DIRTY_STORE);
}

bool InitialStatusMap::isUpdateNeeded() {
    assert(isComplete());
    // We need an update if there are any active members.
    if (find_if(map.begin(), map.end(), &isActive) != map.end()) return true;

    // Otherwise it depends on store status, get my own status:
    Map::iterator me = map.find(self);
    assert(me != map.end());
    assert(me->second);
    switch (me->second->getStoreState()) {
      case STORE_STATE_NO_STORE:
      case STORE_STATE_EMPTY_STORE:
        // If anybody has a store then we need an update.
        return find_if(map.begin(), map.end(), &hasStore) != map.end();
      case STORE_STATE_DIRTY_STORE: return true; 
      case STORE_STATE_CLEAN_STORE: return false; // Use our own store
    }
    return false;
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
        return map.begin()->second->getClusterId(); // Youngest newcomer in node-id order
}

void InitialStatusMap::checkConsistent() {
    assert(isComplete());
    bool persistent = (map.begin()->second->getStoreState() != STORE_STATE_NO_STORE);
    Uuid clusterId;
    for (Map::iterator i = map.begin(); i != map.end(); ++i) {
        // Must not mix transient and persistent members.
        if (persistent != (i->second->getStoreState() != STORE_STATE_NO_STORE))
            throw Exception("Mixing transient and persistent brokers in a cluster");
        // Members with non-empty stores must have same cluster-id
        switch (i->second->getStoreState()) {
          case STORE_STATE_NO_STORE:
          case STORE_STATE_EMPTY_STORE:
            break;               
          case STORE_STATE_DIRTY_STORE:
          case STORE_STATE_CLEAN_STORE:
            if (!clusterId) clusterId = i->second->getClusterId();
            assert(clusterId);
            if (clusterId != i->second->getClusterId())
                throw Exception("Cluster-id mismatch, brokers belonged to different clusters.");
        }
    }
    // If this is a newly forming cluster, clean stores must have same shutdown-id
    if (find_if(map.begin(), map.end(), &isActive) == map.end()) {
        Uuid shutdownId;
        for (Map::iterator i = map.begin(); i != map.end(); ++i) {
            if (i->second->getStoreState() == STORE_STATE_CLEAN_STORE) {
                if (!shutdownId) shutdownId = i->second->getShutdownId();
                assert(shutdownId);
                if (shutdownId != i->second->getShutdownId())
                    throw Exception("Shutdown-id mismatch, brokers were not shut down together.");
            }
        }
    }
}


}} // namespace qpid::cluster
