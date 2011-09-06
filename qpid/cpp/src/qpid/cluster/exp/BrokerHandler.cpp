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
#include "BrokerHandler.h"
#include "qpid/framing/ClusterMessageRoutingBody.h"
#include "qpid/framing/ClusterMessageRoutedBody.h"
#include "qpid/framing/ClusterMessageEnqueueBody.h"
#include "qpid/framing/ClusterMessageDequeueBody.h"
#include "qpid/framing/ClusterWiringCreateQueueBody.h"
#include "qpid/framing/ClusterWiringCreateExchangeBody.h"
#include "qpid/framing/ClusterWiringDestroyQueueBody.h"
#include "qpid/framing/ClusterWiringDestroyExchangeBody.h"
#include "qpid/framing/ClusterWiringBindBody.h"
#include "qpid/framing/ClusterWiringUnbindBody.h"
#include "qpid/sys/Thread.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Exchange.h"
#include "qpid/framing/Buffer.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

using namespace framing;
using namespace broker;

namespace {
// noReplicate means the current thread is handling a message
// received from the cluster so it should not be replciated.
QPID_TSS bool tssNoReplicate = false;

// Routing ID of the message being routed in the current thread.
// 0 if we are not currently routing a message.
QPID_TSS RoutingId tssRoutingId = 0;
}

BrokerHandler::ScopedSuppressReplication::ScopedSuppressReplication() {
    assert(!tssNoReplicate);
    tssNoReplicate = true;
}

BrokerHandler::ScopedSuppressReplication::~ScopedSuppressReplication() {
    assert(tssNoReplicate);
    tssNoReplicate = false;
}

BrokerHandler::BrokerHandler(Core& c) : core(c) {}

RoutingId BrokerHandler::nextRoutingId() {
    RoutingId id = ++routingId;
    if (id == 0) id = ++routingId; // Avoid 0 on wrap-around.
    return id;
}

void BrokerHandler::routing(const boost::intrusive_ptr<Message>&) { }

bool BrokerHandler::enqueue(Queue& queue, const boost::intrusive_ptr<Message>& msg)
{
    if (tssNoReplicate) return true;
    if (!tssRoutingId) {             // This is the first enqueue, so send the message
        tssRoutingId = nextRoutingId();
        // FIXME aconway 2010-10-20: replicate message in fixed size buffers.
        std::string data(msg->encodedSize(),char());
        framing::Buffer buf(&data[0], data.size());
        msg->encode(buf);
        core.mcast(ClusterMessageRoutingBody(ProtocolVersion(), tssRoutingId, data));
        core.getRoutingMap().put(tssRoutingId, msg);
    }
    core.mcast(ClusterMessageEnqueueBody(ProtocolVersion(), tssRoutingId, queue.getName()));
    // TODO aconway 2010-10-21: configable option for strict (wait
    // for CPG deliver to do local deliver) vs.  loose (local deliver
    // immediately).
    return false;
}

void BrokerHandler::routed(const boost::intrusive_ptr<Message>&) {
    if (tssRoutingId) {             // we enqueued at least one message.
        core.mcast(ClusterMessageRoutedBody(ProtocolVersion(), tssRoutingId));
        // Note: routingMap is cleaned up on CPG delivery in MessageHandler.
        tssRoutingId = 0;
    }
}

void BrokerHandler::dequeue(const broker::QueuedMessage& qm) {
    if (tssNoReplicate) return;
    // FIXME aconway 2010-10-28: we also need to delay completion of the
    // ack that caused this dequeue until self-delivery of the mcast below.
    core.mcast(ClusterMessageDequeueBody(
                   ProtocolVersion(), qm.queue->getName(), qm.position));
}

void BrokerHandler::create(const broker::Queue& q) {
    if (tssNoReplicate) return;
    std::string data(q.encodedSize(), '\0');
    framing::Buffer buf(&data[0], data.size());
    q.encode(buf);
    core.mcast(ClusterWiringCreateQueueBody(ProtocolVersion(), data));
}

void BrokerHandler::destroy(const broker::Queue& q) {
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringDestroyQueueBody(ProtocolVersion(), q.getName()));
}

void BrokerHandler::create(const broker::Exchange& ex) {
    if (tssNoReplicate) return;
    std::string data(ex.encodedSize(), '\0');
    framing::Buffer buf(&data[0], data.size());
    ex.encode(buf);
    core.mcast(ClusterWiringCreateExchangeBody(ProtocolVersion(), data));
}

void BrokerHandler::destroy(const broker::Exchange& ex) {
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringDestroyExchangeBody(ProtocolVersion(), ex.getName()));
}

void BrokerHandler::bind(const broker::Queue& q, const broker::Exchange& ex,
                         const std::string& key, const framing::FieldTable& args)
{
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringBindBody(
                   ProtocolVersion(), q.getName(), ex.getName(), key, args));
}

void BrokerHandler::unbind(const broker::Queue& q, const broker::Exchange& ex,
                         const std::string& key, const framing::FieldTable& args)
{
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringUnbindBody(
                   ProtocolVersion(), q.getName(), ex.getName(), key, args));
}

}} // namespace qpid::cluster
