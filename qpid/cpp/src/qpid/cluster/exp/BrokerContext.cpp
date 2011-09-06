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
#include "BrokerContext.h"
#include "QueueContext.h"
#include "QueueHandler.h"
#include "qpid/framing/ClusterMessageRoutingBody.h"
#include "qpid/framing/ClusterMessageRoutedBody.h"
#include "qpid/framing/ClusterMessageEnqueueBody.h"
#include "qpid/framing/ClusterMessageAcquireBody.h"
#include "qpid/framing/ClusterMessageDequeueBody.h"
#include "qpid/framing/ClusterMessageReleaseBody.h"
#include "qpid/framing/ClusterWiringCreateQueueBody.h"
#include "qpid/framing/ClusterWiringCreateExchangeBody.h"
#include "qpid/framing/ClusterWiringDestroyQueueBody.h"
#include "qpid/framing/ClusterWiringDestroyExchangeBody.h"
#include "qpid/framing/ClusterWiringBindBody.h"
#include "qpid/framing/ClusterWiringUnbindBody.h"
#include "qpid/framing/ClusterQueueSubscribeBody.h"
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

BrokerContext::ScopedSuppressReplication::ScopedSuppressReplication() {
    assert(!tssNoReplicate);
    tssNoReplicate = true;
}

BrokerContext::ScopedSuppressReplication::~ScopedSuppressReplication() {
    assert(tssNoReplicate);
    tssNoReplicate = false;
}

BrokerContext::BrokerContext(Core& c, boost::intrusive_ptr<QueueHandler> q)
    : core(c), queueHandler(q) {}

RoutingId BrokerContext::nextRoutingId() {
    RoutingId id = ++routingId;
    if (id == 0) id = ++routingId; // Avoid 0 on wrap-around.
    return id;
}

void BrokerContext::routing(const boost::intrusive_ptr<Message>&) { }

bool BrokerContext::enqueue(Queue& queue, const boost::intrusive_ptr<Message>& msg)
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

void BrokerContext::routed(const boost::intrusive_ptr<Message>&) {
    if (tssRoutingId) {             // we enqueued at least one message.
        core.mcast(ClusterMessageRoutedBody(ProtocolVersion(), tssRoutingId));
        // Note: routingMap is cleaned up on CPG delivery in MessageHandler.
        tssRoutingId = 0;
    }
}

void BrokerContext::acquire(const broker::QueuedMessage& qm) {
    if (tssNoReplicate) return;
    core.mcast(ClusterMessageAcquireBody(
                   ProtocolVersion(), qm.queue->getName(), qm.position));
}

// FIXME aconway 2011-05-24: need to handle acquire and release.
// Dequeue in the wrong place?
void BrokerContext::dequeue(const broker::QueuedMessage& qm) {
    if (tssNoReplicate) return;
    core.mcast(ClusterMessageDequeueBody(
                   ProtocolVersion(), qm.queue->getName(), qm.position));
}

void BrokerContext::release(const broker::QueuedMessage& ) {
    // FIXME aconway 2011-05-24: TODO
}

// FIXME aconway 2011-06-08: should be be using shared_ptr to q here?
void BrokerContext::create(broker::Queue& q) {
    if (tssNoReplicate) return; // FIXME aconway 2011-06-08: revisit
    // FIXME aconway 2011-06-08: error handling- if already set...
    // Create local context immediately, queue will be stopped until replicated.
    boost::intrusive_ptr<QueueContext> context(
        new QueueContext(q,core.getMulticaster()));
    std::string data(q.encodedSize(), '\0');
    framing::Buffer buf(&data[0], data.size());
    q.encode(buf);
    core.mcast(ClusterWiringCreateQueueBody(ProtocolVersion(), data));
    // FIXME aconway 2011-07-29: Need asynchronous completion.
}

void BrokerContext::destroy(broker::Queue& q) {
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringDestroyQueueBody(ProtocolVersion(), q.getName()));
}

void BrokerContext::create(broker::Exchange& ex) {
    if (tssNoReplicate) return;
    std::string data(ex.encodedSize(), '\0');
    framing::Buffer buf(&data[0], data.size());
    ex.encode(buf);
    core.mcast(ClusterWiringCreateExchangeBody(ProtocolVersion(), data));
}

void BrokerContext::destroy(broker::Exchange& ex) {
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringDestroyExchangeBody(ProtocolVersion(), ex.getName()));
}

void BrokerContext::bind(broker::Queue& q, broker::Exchange& ex,
                         const std::string& key, const framing::FieldTable& args)
{
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringBindBody(
                   ProtocolVersion(), q.getName(), ex.getName(), key, args));
}

void BrokerContext::unbind(broker::Queue& q, broker::Exchange& ex,
                           const std::string& key, const framing::FieldTable& args)
{
    if (tssNoReplicate) return;
    core.mcast(ClusterWiringUnbindBody(
                   ProtocolVersion(), q.getName(), ex.getName(), key, args));
}

// n is the number of consumers including the one just added.
// FIXME aconway 2011-06-27: rename, conflicting terms.
void BrokerContext::consume(broker::Queue& q, size_t n) {
    QueueContext::get(q)->consume(n);
}

// n is the number of consumers after the cancel.
void BrokerContext::cancel(broker::Queue& q, size_t n) {
    QueueContext::get(q)->cancel(n);
}

void BrokerContext::empty(broker::Queue& ) {
    // FIXME aconway 2011-06-28: is this needed?
}

void BrokerContext::stopped(broker::Queue& q) {
    boost::intrusive_ptr<QueueContext> qc = QueueContext::get(q);
    // Don't forward the stopped call if the queue does not yet have a cluster context
    // this when the queue is first created locally.
    if (qc) qc->stopped();
}

}} // namespace qpid::cluster
