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
#include "ReplicationExchange.h"
#include "constants.h"
#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>

namespace qpid {
namespace replication {

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::replication::constants;

ReplicationExchange::ReplicationExchange(const std::string& name, bool durable, 
                                         const FieldTable& args,
                                         QueueRegistry& qr,
                                         Manageable* parent) 
    : Exchange(name, durable, args, parent), queues(qr), expectingEnqueue(false) {}

std::string ReplicationExchange::getType() const { return typeName; }            

void ReplicationExchange::route(Deliverable& msg, const std::string& /*routingKey*/, const FieldTable* args)
{
    if (args) {
        std::string eventType = args->getAsString(REPLICATION_EVENT_TYPE);
        if (eventType == ENQUEUE) {
            expectingEnqueue = true;
            targetQueue = args->getAsString(REPLICATION_TARGET_QUEUE);
            QPID_LOG(debug, "Recorded replicated 'enqueue' event for " << targetQueue);
            return;
        } else if (eventType == DEQUEUE) {
            std::string queueName = args->getAsString(REPLICATION_TARGET_QUEUE);
            Queue::shared_ptr queue = queues.find(queueName);
            SequenceNumber position(args->getAsInt(DEQUEUED_MESSAGE_POSITION));

            QueuedMessage dequeued;
            if (queue->acquireMessageAt(position, dequeued)) {
                queue->dequeue(0, dequeued);
                QPID_LOG(debug, "Processed replicated 'dequeue' event from " << queueName << " at position " << position);
            } else {
                QPID_LOG(warning, "Could not acquire message " << position << " from " << queueName);
            }
            
            return;
        } else if (!eventType.empty()) {
            throw IllegalArgumentException(QPID_MSG("Illegal value for " << REPLICATION_EVENT_TYPE << ": " << eventType));
        }
    }
    //if we get here assume its not an event message, assume its an enqueue
    if (expectingEnqueue) {
        Queue::shared_ptr queue = queues.find(targetQueue);
        msg.deliverTo(queue);
        expectingEnqueue = false;
        targetQueue.clear();
        QPID_LOG(debug, "Eenqueued replicated message onto " << targetQueue);
    } else {
        QPID_LOG(warning, "Dropping unexpected message");
    }
}

bool ReplicationExchange::bind(Queue::shared_ptr /*queue*/, const std::string& /*routingKey*/, const FieldTable* /*args*/)
{
    throw NotImplementedException("Replication exchange does not support bind operation");
}

bool ReplicationExchange::unbind(Queue::shared_ptr /*queue*/, const std::string& /*routingKey*/, const FieldTable* /*args*/)
{
    throw NotImplementedException("Replication exchange does not support unbind operation");
}

bool ReplicationExchange::isBound(Queue::shared_ptr /*queue*/, const string* const /*routingKey*/, const FieldTable* const /*args*/)
{
    return false;
}

const std::string ReplicationExchange::typeName("replication");


struct ReplicationExchangePlugin : Plugin
{
    Broker* broker;

    ReplicationExchangePlugin();
    void earlyInitialize(Plugin::Target& target);
    void initialize(Plugin::Target& target);
    Exchange::shared_ptr create(const std::string& name, bool durable,
                                const framing::FieldTable& args, 
                                management::Manageable* parent);
};

ReplicationExchangePlugin::ReplicationExchangePlugin() : broker(0) {}

Exchange::shared_ptr ReplicationExchangePlugin::create(const std::string& name, bool durable,
                                                       const framing::FieldTable& args, 
                                                       management::Manageable* parent)
{
    Exchange::shared_ptr e(new ReplicationExchange(name, durable, args, broker->getQueues(), parent));
    return e;
}


void ReplicationExchangePlugin::initialize(Plugin::Target& target)
{
    broker = dynamic_cast<broker::Broker*>(&target);
    if (broker) {
        ExchangeRegistry::FactoryFunction f = boost::bind(&ReplicationExchangePlugin::create, this, _1, _2, _3, _4);
        broker->getExchanges().registerType(ReplicationExchange::typeName, f);
        QPID_LOG(info, "Registered replication exchange");
    }
}

void ReplicationExchangePlugin::earlyInitialize(Target&) {}

static ReplicationExchangePlugin exchangePlugin;

}} // namespace qpid::replication
