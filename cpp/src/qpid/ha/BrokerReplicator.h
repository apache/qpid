#ifndef QPID_HA_REPLICATOR_H
#define QPID_HA_REPLICATOR_H

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

#include "ReplicateLevel.h"
#include "qpid/broker/Exchange.h"
#include "qpid/types/Variant.h"
#include <boost/shared_ptr.hpp>

namespace qpid {

namespace broker {
class Broker;
class Link;
class Bridge;
class SessionHandler;
}

namespace framing {
class FieldTable;
}

namespace ha {
class HaBroker;

/**
 * Replicate configuration on a backup broker.
 *
 * Implemented as an exchange that subscribes to receive QMF
 * configuration events from the primary. It configures local queues
 * exchanges and bindings to replicate the primary.
 * It also creates QueueReplicators for newly replicated queues.
 *
 * THREAD SAFE: Has no mutable state.
 *
 */
class BrokerReplicator : public broker::Exchange
{
  public:
    BrokerReplicator(HaBroker&, const boost::shared_ptr<broker::Link>&);
    ~BrokerReplicator();
    std::string getType() const;

    // Exchange methods
    bool bind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    bool unbind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    void route(broker::Deliverable&);
    bool isBound(boost::shared_ptr<broker::Queue>, const std::string* const, const framing::FieldTable* const);

  private:
    void initializeBridge(broker::Bridge&, broker::SessionHandler&);

    ReplicateLevel replicateLevel(const std::string&);
    ReplicateLevel replicateLevel(const framing::FieldTable& args);
    ReplicateLevel replicateLevel(const types::Variant::Map& args);

    void doEventQueueDeclare(types::Variant::Map& values);
    void doEventQueueDelete(types::Variant::Map& values);
    void doEventExchangeDeclare(types::Variant::Map& values);
    void doEventExchangeDelete(types::Variant::Map& values);
    void doEventBind(types::Variant::Map&);
    void doEventUnbind(types::Variant::Map&);

    void doResponseQueue(types::Variant::Map& values);
    void doResponseExchange(types::Variant::Map& values);
    void doResponseBind(types::Variant::Map& values);
    void doResponseHaBroker(types::Variant::Map& values);

    void startQueueReplicator(const boost::shared_ptr<broker::Queue>&);

    HaBroker& haBroker;
    broker::Broker& broker;
    boost::shared_ptr<broker::Link> link;
};
}} // namespace qpid::broker

#endif  /*!QPID_HA_REPLICATOR_H*/
