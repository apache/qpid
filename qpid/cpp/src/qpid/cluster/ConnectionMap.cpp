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
#include "ConnectionMap.h"
#include "Cluster.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/assert.h"

namespace qpid {
namespace cluster {

using framing::InternalErrorException;
typedef sys::Mutex::ScopedLock Lock;

void ConnectionMap::insert(ConnectionPtr p) {
    Lock l(lock);
    std::pair<Map::iterator, bool> ib = map.insert(Map::value_type(p->getId(), p));
    QPID_ASSERT(ib.second);
}

void ConnectionMap::erase(const ConnectionId& id) {
    Lock l(lock);
    Map::iterator i = map.find(id);
    QPID_ASSERT(i != map.end());
    map.erase(i);
}

ConnectionMap::ConnectionPtr ConnectionMap::get(const ConnectionId& id) {
    Lock l(lock);
    Map::const_iterator i = map.find(id);
    if (i == map.end()) {
        // Deleted local connection.
        if(id.getMember() == cluster.getId())
            return 0;
        // New remote connection, create a shadow.
        std::ostringstream mgmtId;
        mgmtId << id;
        ConnectionPtr cp = new Connection(cluster, shadowOut, mgmtId.str(), id);
        std::pair<Map::iterator, bool> ib = map.insert(Map::value_type(id, cp)); 
        QPID_ASSERT(ib.second);
        i = ib.first;
    }
    return i->second;
}

ConnectionMap::ConnectionPtr ConnectionMap::getLocal(const ConnectionId& id) {
    Lock l(lock);
    if (id.getMember() != cluster.getId()) return 0;
    Map::const_iterator i = map.find(id);
    return i == map.end() ? 0 : i->second;
}

ConnectionMap::Vector ConnectionMap::values() const {
    Lock l(lock);
    Vector result(map.size());
    std::transform(map.begin(), map.end(), result.begin(),
                   boost::bind(&Map::value_type::second, _1));
    return result;
}

void ConnectionMap::update(MemberId myId, const ClusterMap& cluster) {
    Lock l(lock);
    for (Map::iterator i = map.begin(); i != map.end(); ) {
        MemberId member = i->first.getMember();
        if (member != myId && !cluster.isMember(member)) { 
            i->second->left();
            map.erase(i++);
        } else {
            i++;
        }
    }
}

void ConnectionMap::clear() {
    Lock l(lock);
    map.clear();
}

}} // namespace qpid::cluster
