#ifndef QPID_CLUSTER_CONNECTIONMAP_H
#define QPID_CLUSTER_CONNECTIONMAP_H

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
#include "types.h"
#include "Connection.h"
#include "ClusterMap.h"
#include "qpid/sys/Mutex.h"
#include <boost/intrusive_ptr.hpp>
#include <map>

namespace qpid {
namespace cluster {

/**
 * Thread safe map of connections.
 */
class ConnectionMap
{
  public:
    typedef boost::intrusive_ptr<cluster::Connection> ConnectionPtr;
    typedef std::vector<ConnectionPtr> Vector;
    
    void insert(ConnectionId id, ConnectionPtr p) {
        ScopedLock l(lock);
        map.insert(Map::value_type(id,p));
    }

    void erase(ConnectionId id) {
        ScopedLock l(lock);
        map.erase(id);
    }

    ConnectionPtr find(ConnectionId id) const {
        ScopedLock l(lock);
        Map::const_iterator i = map.find(id);
        return i == map.end() ? ConnectionPtr() : i->second;
    }

    Vector values() const {
        Vector result(map.size());
        std::transform(map.begin(), map.end(), result.begin(),
                       boost::bind(&Map::value_type::second, _1));
        return result;
    }

    void update(MemberId myId, const ClusterMap& cluster) {
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

    void clear() {
        ScopedLock l(lock);
        map.clear();
    }

    size_t size() const { return map.size(); }
  private:
    typedef std::map<ConnectionId, ConnectionPtr> Map;
    typedef sys::Mutex::ScopedLock ScopedLock;
    
    mutable sys::Mutex lock;
    Map map;
};


}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CONNECTIONMAP_H*/
