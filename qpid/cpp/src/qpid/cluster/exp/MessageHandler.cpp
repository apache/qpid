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
#include "Group.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/sys/Thread.h"
#include "qpid/log/Statement.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace cluster {

using namespace broker;
using namespace framing;

MessageHandler::MessageHandler(Group& g, Core& c) :
    HandlerBase(g.getEventHandler()),
    broker(c.getBroker()),
    core(c),
    messageBuilders(g.getMessageBuilders()),
    messageHolder(g.getMessageHolder())
{}

bool MessageHandler::handle(const framing::AMQFrame& frame) {
    assert(frame.getBody());
    const AMQBody& body = *frame.getBody();
    if (framing::invoke(*this, body).wasHandled()) return true;
    // Test for message frame
    if (body.type() == HEADER_BODY || body.type() == CONTENT_BODY ||
        (body.getMethod() && body.getMethod()->isA<MessageTransferBody>()))
    {
        boost::shared_ptr<broker::Queue> queue;
        boost::intrusive_ptr<broker::Message> message;
        if (sender() == self())
            messageHolder.check(frame, queue, message);
        else
            messageBuilders.handle(sender(), frame, queue, message);
        if (message) {
            BrokerContext::ScopedSuppressReplication ssr;
            queue->deliver(message);
            if (sender() == self()) // Async completion
                message->getIngressCompletion().finishCompleter(); 
        }
        return true;
    }
    return false;
}

boost::shared_ptr<broker::Queue> MessageHandler::findQueue(
    const std::string& q, const char* msg)
{
    boost::shared_ptr<Queue> queue = broker.getQueues().find(q);
    if (!queue) throw Exception(QPID_MSG(msg << ": unknown queue " << q));
    return queue;
}

void MessageHandler::enqueue(const std::string& q, uint16_t channel) {
    // We only need to build message from other brokers, our own messages
    // are held by the MessageHolder.
    if (sender() != self()) {
        boost::shared_ptr<Queue> queue = findQueue(q, "cluster: enqueue");
        messageBuilders.announce(sender(), channel, queue);
    }
}

// FIXME aconway 2011-09-14: performance: pack acquires into a SequenceSet
// and scan queue once.
void MessageHandler::acquire(const std::string& q, uint32_t position) {
    // FIXME aconway 2011-09-15: systematic logging across cluster module.
    QPID_LOG(trace, "cluster: message " << q << "[" << position
             << "] acquired by " << PrettyId(sender(), self()));
    // Note acquires from other members. My own acquires were executed in
    // the broker thread
    if (sender() != self()) {
        boost::shared_ptr<Queue> queue = findQueue(q, "cluster: acquire");
        QueuedMessage qm;
        BrokerContext::ScopedSuppressReplication ssr;
        if (!queue->acquireMessageAt(position, qm))
            throw Exception(QPID_MSG("cluster: acquire: message not found: "
                                     << q << "[" << position << "]"));
        assert(qm.position.getValue() == position);
        assert(qm.payload);
        // Save on context for possible requeue if released/rejected.
        QueueContext::get(*queue)->acquire(qm);
        // FIXME aconway 2011-09-19: need to record by member-ID to
        // requeue if member leaves.
    }
}

void MessageHandler::dequeue(const std::string& q, uint32_t position) {
    // FIXME aconway 2011-09-15: systematic logging across cluster module.
    QPID_LOG(trace, "cluster: message " << q << "[" << position
             << "] dequeued by " << PrettyId(sender(), self()));

    // FIXME aconway 2010-10-28: for local dequeues, we should
    // complete the ack that initiated the dequeue at this point, see
    // BrokerContext::dequeue

    // My own dequeues were processed in the broker thread before multicasting.
    if (sender() != self()) {
        boost::shared_ptr<Queue> queue = findQueue(q, "cluster: dequeue");
        QueuedMessage qm = QueueContext::get(*queue)->dequeue(position);
        BrokerContext::ScopedSuppressReplication ssr;
        if (qm.queue) queue->dequeue(0, qm);
    }
}

void MessageHandler::requeue(const std::string& q, uint32_t position, bool redelivered) {
    if (sender() != self()) {
        boost::shared_ptr<Queue> queue = findQueue(q, "cluster: requeue");
        QueueContext::get(*queue)->requeue(position, redelivered);
    }
}

}} // namespace qpid::cluster
