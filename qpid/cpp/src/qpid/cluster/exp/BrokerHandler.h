#ifndef QPID_CLUSTER_BROKERHANDLER_H
#define QPID_CLUSTER_BROKERHANDLER_H

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

#include "qpid/broker/Cluster.h"
#include "qpid/sys/AtomicValue.h"

namespace qpid {
namespace cluster {
class Core;

// TODO aconway 2010-10-19: experimental cluster code.

/**
 * Implements broker::Cluster interface, handles events in broker code.
 */
class BrokerHandler : public broker::Cluster
{
  public:
    /** Suppress replication while in scope.
     * Used to prevent re-replication of messages received from the cluster.
     */
    struct ScopedSuppressReplication {
        ScopedSuppressReplication();
        ~ScopedSuppressReplication();
    };

    BrokerHandler(Core&);

    // FIXME aconway 2010-10-20: implement all points.

    // Messages

    void routing(const boost::intrusive_ptr<broker::Message>&);
    bool enqueue(broker::Queue&, const boost::intrusive_ptr<broker::Message>&);
    void routed(const boost::intrusive_ptr<broker::Message>&);
    void acquire(const broker::QueuedMessage&) {}
    void release(const broker::QueuedMessage&) {}
    void dequeue(const broker::QueuedMessage&);

    // Consumers

    void consume(const broker::Queue&, size_t) {}
    void cancel(const broker::Queue&, size_t) {}

    // Wiring

    void create(const broker::Queue&);
    void destroy(const broker::Queue&);
    void create(const broker::Exchange&);
    void destroy(const broker::Exchange&);
    void bind(const broker::Queue&, const broker::Exchange&,
              const std::string&, const framing::FieldTable&);
    void unbind(const broker::Queue&, const broker::Exchange&,
                const std::string&, const framing::FieldTable&);


  private:
    uint32_t nextRoutingId();

    Core& core;
    sys::AtomicValue<uint32_t> routingId;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_BROKERHANDLER_H*/
