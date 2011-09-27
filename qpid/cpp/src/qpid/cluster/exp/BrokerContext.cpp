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
#include "Multicaster.h"
#include "hash.h"
#include "qpid/framing/ClusterMessageEnqueueBody.h"
#include "qpid/framing/ClusterMessageAcquireBody.h"
#include "qpid/framing/ClusterMessageDequeueBody.h"
#include "qpid/framing/ClusterMessageRequeueBody.h"
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
const ProtocolVersion pv;     // shorthand

// noReplicate means the current thread is handling a message
// received from the cluster so it should not be replicated.
QPID_TSS bool tssNoReplicate = false;
}

// FIXME aconway 2011-09-26: de-const the broker::Cluster interface,
// then de-const here.
Multicaster& BrokerContext::mcaster(const broker::QueuedMessage& qm) {
    return core.getGroup(hashof(qm)).getMulticaster();
}

Multicaster& BrokerContext::mcaster(const broker::Queue& q) {
    return core.getGroup(hashof(q)).getMulticaster();
}

Multicaster& BrokerContext::mcaster(const std::string& name) {
    return core.getGroup(hashof(name)).getMulticaster();
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

BrokerContext::~BrokerContext() {}

bool BrokerContext::enqueue(Queue& queue, const boost::intrusive_ptr<Message>& msg)
{
    if (tssNoReplicate) return true;
    // FIXME aconway 2010-10-20: replicate message in fragments
    // (frames), using fixed size bufffers.
    std::string data(msg->encodedSize(),char());
    framing::Buffer buf(&data[0], data.size());
    msg->encode(buf);
    mcaster(queue).mcast(ClusterMessageEnqueueBody(pv, queue.getName(), data));
    return false; // Strict order, wait for CPG self-delivery to enqueue.
}

// routing and routed are no-ops. They are needed to implement fanout
// optimization, which is currently not implemnted
void BrokerContext::routing(const boost::intrusive_ptr<broker::Message>&) {}
void BrokerContext::routed(const boost::intrusive_ptr<Message>&) {}

void BrokerContext::acquire(const broker::QueuedMessage& qm) {
    if (tssNoReplicate) return;
    mcaster(qm).mcast(ClusterMessageAcquireBody(pv, qm.queue->getName(), qm.position));
}

void BrokerContext::dequeue(const broker::QueuedMessage& qm) {
    if (!tssNoReplicate)
        mcaster(qm).mcast(
            ClusterMessageDequeueBody(pv, qm.queue->getName(), qm.position));
}

void BrokerContext::requeue(const broker::QueuedMessage& qm) {
    if (!tssNoReplicate)
        mcaster(qm).mcast(ClusterMessageRequeueBody(
                       pv,
                       qm.queue->getName(),
                       qm.position,
                       qm.payload->getRedelivered()));
}

// FIXME aconway 2011-06-08: should be be using shared_ptr to q here?
void BrokerContext::create(broker::Queue& q) {
    if (tssNoReplicate) return;
    assert(!QueueContext::get(q));
    boost::intrusive_ptr<QueueContext> context(
        new QueueContext(q, core.getSettings().getConsumeLock(), mcaster(q.getName())));
    std::string data(q.encodedSize(), '\0');
    framing::Buffer buf(&data[0], data.size());
    q.encode(buf);
    mcaster(q).mcast(ClusterWiringCreateQueueBody(pv, data));
    // FIXME aconway 2011-07-29: Need asynchronous completion.
}

void BrokerContext::destroy(broker::Queue& q) {
    if (tssNoReplicate) return;
     mcaster(q).mcast(ClusterWiringDestroyQueueBody(pv, q.getName()));
}

void BrokerContext::create(broker::Exchange& ex) {
    if (tssNoReplicate) return;
    std::string data(ex.encodedSize(), '\0');
    framing::Buffer buf(&data[0], data.size());
    ex.encode(buf);
    mcaster(ex.getName()).mcast(ClusterWiringCreateExchangeBody(pv, data));
}

void BrokerContext::destroy(broker::Exchange& ex) {
    if (tssNoReplicate) return;
    mcaster(ex.getName()).mcast(
        ClusterWiringDestroyExchangeBody(pv, ex.getName()));
}

void BrokerContext::bind(broker::Queue& q, broker::Exchange& ex,
                         const std::string& key, const framing::FieldTable& args)
{
    if (tssNoReplicate) return;
    mcaster(q).mcast(ClusterWiringBindBody(pv, q.getName(), ex.getName(), key, args));
}

void BrokerContext::unbind(broker::Queue& q, broker::Exchange& ex,
                           const std::string& key, const framing::FieldTable& args)
{
    if (tssNoReplicate) return;
    mcaster(q).mcast(ClusterWiringUnbindBody(pv, q.getName(), ex.getName(), key, args));
}

// n is the number of consumers including the one just added.
void BrokerContext::consume(broker::Queue& q, size_t n) {
    QueueContext::get(q)->consume(n);
}

// n is the number of consumers after the cancel.
void BrokerContext::cancel(broker::Queue& q, size_t n) {
    QueueContext::get(q)->cancel(n);
}

void BrokerContext::stopped(broker::Queue& q) {
    boost::intrusive_ptr<QueueContext> qc = QueueContext::get(q);
    // Don't forward the stopped call if the queue does not yet have a
    // cluster context - this when the queue is first created locally.
    if (qc) qc->stopped();
}

}} // namespace qpid::cluster

