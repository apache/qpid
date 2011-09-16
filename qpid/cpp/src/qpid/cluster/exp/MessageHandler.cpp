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

#include "Core.h"
#include "MessageHandler.h"
#include "BrokerContext.h"
#include "QueueContext.h"
#include "EventHandler.h"
#include "PrettyId.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/Buffer.h"
#include "qpid/sys/Thread.h"
#include "qpid/log/Statement.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace cluster {
using namespace broker;

MessageHandler::MessageHandler(EventHandler& e) :
    HandlerBase(e),
    broker(e.getCore().getBroker())
{}

bool MessageHandler::invoke(const framing::AMQBody& body) {
    return framing::invoke(*this, body).wasHandled();
}

void MessageHandler::routing(RoutingId routingId, const std::string& message) {
    if (sender() == self()) return; // Already in getCore().getRoutingMap()
    boost::intrusive_ptr<Message> msg = new Message;
    // FIXME aconway 2010-10-28: decode message in bounded-size buffers.
    framing::Buffer buf(const_cast<char*>(&message[0]), message.size());
    msg->decodeHeader(buf);
    msg->decodeContent(buf);
    memberMap[sender()].routingMap[routingId] = msg;
}

boost::shared_ptr<broker::Queue> MessageHandler::findQueue(
    const std::string& q, const char* msg)
{
    boost::shared_ptr<Queue> queue = broker.getQueues().find(q);
    if (!queue) throw Exception(QPID_MSG(msg << ": unknown queue " << q));
    return queue;
}

void MessageHandler::enqueue(RoutingId routingId, const std::string& q) {
    boost::shared_ptr<Queue> queue = findQueue(q, "Cluster enqueue failed");
    boost::intrusive_ptr<Message> msg;
    if (sender() == self())
        msg = eventHandler.getCore().getRoutingMap().get(routingId);
    else
        msg = memberMap[sender()].routingMap[routingId];
    if (!msg) throw Exception(QPID_MSG("Cluster enqueue on " << q
                                       << " failed: unknown message"));
    BrokerContext::ScopedSuppressReplication ssr;
    queue->deliver(msg);
}

void MessageHandler::routed(RoutingId routingId) {
    if (sender() == self())
        eventHandler.getCore().getRoutingMap().erase(routingId);
    else
        memberMap[sender()].routingMap.erase(routingId);
}

// FIXME aconway 2011-09-14: performance: pack acquires into a SequenceSet
// and scan queue once.
void MessageHandler::acquire(const std::string& q, uint32_t position) {
    // Note acquires from other members. My own acquires were executed in
    // the connection thread
    if (sender() != self()) {
        boost::shared_ptr<Queue> queue = findQueue(q, "Cluster acquire failed");
        QueuedMessage qm;
        BrokerContext::ScopedSuppressReplication ssr;
        if (!queue->acquireMessageAt(position, qm))
            throw Exception(QPID_MSG("Cluster acquire: message not found: "
                                     << q << "[" << position << "]"));
        assert(qm.position.getValue() == position);
        assert(qm.payload);
        // Save on context for possible requeue if released/rejected.
        QueueContext::get(*queue)->acquire(qm);
    }
    // FIXME aconway 2011-09-15: systematic logging across cluster module.
    QPID_LOG(trace, "cluster message " << q << "[" << position
             << "] acquired by " << PrettyId(sender(), self()));
 }

void MessageHandler::dequeue(const std::string& q, uint32_t position) {
    if (sender() == self()) {
        // FIXME aconway 2010-10-28: we should complete the ack that initiated
        // the dequeue at this point, see BrokerContext::dequeue
    }
    else {
        // FIXME aconway 2011-09-15: new cluster, inefficient looks up
        // message by position multiple times?
        boost::shared_ptr<Queue> queue = findQueue(q, "Cluster dequeue failed");
        // Remove fom the unacked list
        QueueContext::get(*queue)->dequeue(position);
        BrokerContext::ScopedSuppressReplication ssr;
        QueuedMessage qm = queue->find(position);
        if (qm.queue) queue->dequeue(0, qm);
    }
}

void MessageHandler::requeue(const std::string& q, uint32_t position, bool redelivered) {
    if (sender() != self()) {
        boost::shared_ptr<Queue> queue = findQueue(q, "Cluster requeue failed");
        QueueContext::get(*queue)->requeue(position, redelivered);
    }
}

}} // namespace qpid::cluster
