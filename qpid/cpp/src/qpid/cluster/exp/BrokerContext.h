#ifndef QPID_CLUSTER_BROKERCONTEXT_H
#define QPID_CLUSTER_BROKERCONTEXT_H

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
class QueueContext;
class Multicaster;

// TODO aconway 2010-10-19: experimental cluster code.

/**
 * Implements broker::Cluster interface, handles events in broker code.
 */
class BrokerContext : public broker::Cluster
{
  public:
    /** Suppress replication while in scope.
     * Used to prevent re-replication of messages received from the cluster.
     */
    struct ScopedSuppressReplication {
        ScopedSuppressReplication();
        ~ScopedSuppressReplication();
    };

    BrokerContext(Core&);
    ~BrokerContext();

    // Messages

    void routing(const boost::intrusive_ptr<broker::Message>&);
    bool enqueue(broker::Queue&, const boost::intrusive_ptr<broker::Message>&);
    void routed(const boost::intrusive_ptr<broker::Message>&);
    void acquire(const broker::QueuedMessage&);
    void dequeue(const broker::QueuedMessage&);
    void requeue(const broker::QueuedMessage&);

    // Consumers

    void consume(broker::Queue&, size_t);
    void cancel(broker::Queue&, size_t);

    // Queues
    void stopped(broker::Queue&);

    // Wiring

    void create(broker::Queue&);
    void destroy(broker::Queue&);
    void create(broker::Exchange&);
    void destroy(broker::Exchange&);
    void bind(broker::Queue&, broker::Exchange&,
              const std::string&, const framing::FieldTable&);
    void unbind(broker::Queue&, broker::Exchange&,
                const std::string&, const framing::FieldTable&);


  private:
    // Get multicaster associated with a queue
    Multicaster& mcaster(const broker::QueuedMessage& qm);
    Multicaster& mcaster(const broker::Queue& q);
    Multicaster& mcaster(const std::string&);
    Multicaster& mcaster(const uint32_t);

    Core& core;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_BROKERCONTEXT_H*/
