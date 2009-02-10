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

namespace qpid {
namespace cluster {

using framing::InternalErrorException;

void ConnectionMap::insert(ConnectionPtr p) {
    std::pair<Map::iterator, bool> ib = map.insert(Map::value_type(p->getId(), p));
    if (!ib.second) {
        assert(0);
        throw InternalErrorException(QPID_MSG("Duplicate connection replica: " << p->getId()));
    }
}

void ConnectionMap::erase(const ConnectionId& id) {
    Map::iterator i = map.find(id);
    if (i == map.end()) {
        assert(0);
        QPID_LOG(warning, "Erase non-existent connection replica: " << id);
    }
    map.erase(i);
}

ConnectionMap::ConnectionPtr ConnectionMap::get(const ConnectionId& id) {
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
        assert(ib.second);      // FIXME aconway 2009-02-03: exception.
        i = ib.first;
    }
    return i->second;
}

ConnectionMap::ConnectionPtr ConnectionMap::getLocal(const ConnectionId& id) {
    if (id.getMember() != cluster.getId()) return 0;
    Map::const_iterator i = map.find(id);
    return i == map.end() ? 0 : i->second;
}

ConnectionMap::Vector ConnectionMap::values() const {
    Vector result(map.size());
    std::transform(map.begin(), map.end(), result.begin(),
                   boost::bind(&Map::value_type::second, _1));
    return result;
}

void ConnectionMap::update(MemberId myId, const ClusterMap& cluster) {
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
    map.clear();
}

}} // namespace qpid::cluster
