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

#include "qpid/broker/Exchange.h"
#include "qpid/types/Variant.h"

// FIXME aconway 2011-11-17: relocate to ../ha

namespace qpid {

namespace broker {
class Broker;
}

namespace ha {

/**
 * Pseudo-exchange for recreating local queues and/or exchanges on
 * receipt of QMF events indicating their creation on another node
 */
class WiringReplicator : public broker::Exchange
{
  public:
    WiringReplicator(const std::string&, broker::Broker&);
    ~WiringReplicator();
    std::string getType() const;
    bool bind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    bool unbind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    void route(broker::Deliverable&, const std::string&, const framing::FieldTable*);
    bool isBound(boost::shared_ptr<broker::Queue>, const std::string* const, const framing::FieldTable* const);

    static bool isWiringReplicatorDestination(const std::string&);
    static boost::shared_ptr<broker::Exchange> create(const std::string&, broker::Broker&);
    static const std::string typeName;
  private:

    void doEventQueueDeclare(types::Variant::Map& values);
    void doEventQueueDelete(types::Variant::Map& values);
    void doEventExchangeDeclare(types::Variant::Map& values);
    void doEventExchangeDelete(types::Variant::Map& values);
    void doEventBind(types::Variant::Map&);
    void doResponseQueue(types::Variant::Map& values);
    void doResponseExchange(types::Variant::Map& values);
    void doResponseBind(types::Variant::Map& values);

  private:
    broker::Broker& broker;
};
}} // namespace qpid::broker

#endif  /*!QPID_HA_REPLICATOR_H*/
