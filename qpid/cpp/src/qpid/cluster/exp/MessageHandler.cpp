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

MessageHandler::MessageHandler(EventHandler& e, Core& c) :
    HandlerBase(e),
    broker(c.getBroker()),
    core(c)
{}

bool MessageHandler::invoke(const framing::AMQBody& body) {
    return framing::invoke(*this, body).wasHandled();
}

boost::shared_ptr<broker::Queue> MessageHandler::findQueue(
    const std::string& q, const char* msg)
{
    boost::shared_ptr<Queue> queue = broker.getQueues().find(q);
    if (!queue) throw Exception(QPID_MSG(msg << ": unknown queue " << q));
    return queue;
}

void MessageHandler::enqueue(const std::string& q, const std::string& message) {

    boost::shared_ptr<Queue> queue = findQueue(q, "Cluster enqueue failed");
    // FIXME aconway 2010-10-28: decode message by frame in bounded-size buffers.
    boost::intrusive_ptr<broker::Message> msg = new broker::Message();
    framing::Buffer buf(const_cast<char*>(&message[0]), message.size());
    msg->decodeHeader(buf);
    msg->decodeContent(buf);
    BrokerContext::ScopedSuppressReplication ssr;
    queue->deliver(msg);
}

// FIXME aconway 2011-09-14: performance: pack acquires into a SequenceSet
// and scan queue once.
void MessageHandler::acquire(const std::string& q, uint32_t position) {
    // FIXME aconway 2011-09-15: systematic logging across cluster module.
    QPID_LOG(trace, "cluster message " << q << "[" << position
             << "] acquired by " << PrettyId(sender(), self()));
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
        // FIXME aconway 2011-09-19: need to record by member-ID to
        // requeue if member leaves.
    }
}

void MessageHandler::dequeue(const std::string& q, uint32_t position) {
    // FIXME aconway 2011-09-15: systematic logging across cluster module.
    QPID_LOG(trace, "cluster message " << q << "[" << position
             << "] dequeued by " << PrettyId(sender(), self()));

    // FIXME aconway 2010-10-28: for local dequeues, we should
    // complete the ack that initiated the dequeue at this point, see
    // BrokerContext::dequeue

    // My own dequeues were processed in the connection thread before multicasting.
    if (sender() != self()) {
        boost::shared_ptr<Queue> queue = findQueue(q, "Cluster dequeue failed");
        QueuedMessage qm = QueueContext::get(*queue)->dequeue(position);
        BrokerContext::ScopedSuppressReplication ssr;
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
