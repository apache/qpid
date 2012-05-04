#ifndef QPID_CLUSTER_CREDENTIALSEXCHANGE_H
#define QPID_CLUSTER_CREDENTIALSEXCHANGE_H

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
#include <qpid/broker/Exchange.h>
#include <qpid/sys/Mutex.h>
#include <qpid/sys/Time.h>
#include <string>
#include <map>

namespace qpid {
namespace cluster {

class Cluster;

/**
 * New members joining the cluster send their identity information to this
 * exchange to prove they are authenticated as the cluster user.
 * The exchange rejects messages that are not properly authenticated
 */
class CredentialsExchange : public broker::Exchange
{
  public:
    static const std::string NAME;

    CredentialsExchange(Cluster&);

    /** Check if this member has credentials. The credentials are deleted. */
    bool check(MemberId member);

    /** Throw an exception if the calling connection is not the cluster user. Store credentials in msg. */
    void route(broker::Deliverable& msg);

    // Exchange overrides
    std::string getType() const;
    bool bind(boost::shared_ptr<broker::Queue> queue, const std::string& routingKey, const framing::FieldTable* args);
    bool unbind(boost::shared_ptr<broker::Queue> queue, const std::string& routingKey, const framing::FieldTable* args);
    bool isBound(boost::shared_ptr<broker::Queue> queue, const std::string* const routingKey, const framing::FieldTable* const args);

  private:
    typedef std::map<MemberId, sys::AbsTime> Map;
    sys::Mutex lock;
    Map map;
    std::string username;
    sys::Duration timeout;
    bool authenticate;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_CREDENTIALSEXCHANGE_H*/
