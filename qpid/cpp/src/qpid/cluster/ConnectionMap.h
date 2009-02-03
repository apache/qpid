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
#include "NoOpConnectionOutputHandler.h"
#include "qpid/sys/Mutex.h"
#include <boost/intrusive_ptr.hpp>
#include <map>

namespace qpid {
namespace cluster {

class Cluster;

/**
 * Thread safe map of connections. The map is used in:
 * - deliver thread to look connections and create new shadow connections.
 * - local catch-up connection threads to add a caught-up shadow connections.
 * - local client connection threads when local connections are created.
 */
class ConnectionMap {
  public:
    typedef boost::intrusive_ptr<cluster::Connection> ConnectionPtr;
    typedef std::vector<ConnectionPtr> Vector;
    
    ConnectionMap(Cluster& c) : cluster(c) {}
    
    /** Insert a local connection or a caught up shadow connection.
     * Called in local connection thread.
     */
    void insert(ConnectionPtr p); 

    /** Erase a closed connection. Called in deliver thread. */
    void erase(const ConnectionId& id);

    /** Get an existing connection. */ 
    ConnectionPtr get(const ConnectionId& id);

    /** Get connections for sending an update. */
    Vector values() const;

    /** Remove connections who's members are no longer in the cluster. Deliver thread. */
    void update(MemberId myId, const ClusterMap& cluster); 

    
    void clear();

    size_t size() const;

  private:
    typedef std::map<ConnectionId, ConnectionPtr> Map;

    Cluster& cluster;
    NoOpConnectionOutputHandler shadowOut;
    Map map;
};


}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CONNECTIONMAP_H*/
