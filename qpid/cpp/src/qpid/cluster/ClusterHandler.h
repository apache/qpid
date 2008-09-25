#ifndef QPID_CLUSTER_CLUSTERHANDLER_H
#define QPID_CLUSTER_CLUSTERHANDLER_H

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

#include "Cpg.h"
#include "types.h"
#include <boost/intrusive_ptr.hpp>

namespace qpid {

namespace framing { class AMQFrame; }

namespace cluster {

class Connection;
class Cluster;
class Event;

/**
 * Interface for handing cluster events.
 * Implementations provide different behavior for different states of a member..
 */
class ClusterHandler
{
  public:
    ClusterHandler(Cluster& c);
    virtual ~ClusterHandler();

    bool invoke(const MemberId&, framing::AMQFrame& f);

    virtual void update(const MemberId&, const framing::FieldTable& members, uint64_t dumping) = 0;
    virtual void dumpRequest(const MemberId&, const std::string& url) = 0;
    virtual void ready(const MemberId&, const std::string& url) = 0;
    virtual void shutdown(const MemberId&);

    virtual void deliver(Event& e) = 0; // Deliver a connection event.

    virtual void configChange(cpg_address *current, int nCurrent,
                              cpg_address *left, int nLeft,
                              cpg_address *joined, int nJoined) = 0;

    virtual void insert(const boost::intrusive_ptr<Connection>& c) = 0;
    virtual void catchUpClosed(const boost::intrusive_ptr<Connection>& c) = 0;

  protected:
    Cluster& cluster;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CLUSTERHANDLER_H*/
