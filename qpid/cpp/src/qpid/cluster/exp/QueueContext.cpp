
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

#include "BrokerContext.h"
#include "EventHandler.h"
#include "Group.h"
#include "Multicaster.h"
#include "QueueContext.h"
#include "QueueHandler.h"
#include "hash.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/framing/ClusterMessageAcquireBody.h"
#include "qpid/framing/ClusterMessageDequeueBody.h"
#include "qpid/framing/ClusterQueueConsumedBody.h"
#include "qpid/framing/ClusterQueueSubscribeBody.h"
#include "qpid/framing/ClusterQueueUnsubscribeBody.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

using framing::SequenceSet;
const framing::ProtocolVersion pv;     // shorthand

QueueContext::QueueContext(broker::Queue& q, Group& g, size_t maxTicks_)
    : ownership(UNSUBSCRIBED), consumers(0), consuming(false), ticks(0),
      queue(q), mcast(g.getMulticaster()), hash(hashof(q.getName())),
      maxTicks(maxTicks_), group(g)
{
    q.setClusterContext(std::auto_ptr<broker::Context>(this));
    q.stopConsumers();          // Stop queue initially.
    group.getTicker().add(this);
}

QueueContext::~QueueContext() {
    // Lifecycle: must remove all references to this context  before it is deleted.
    // Must be sure that there can be no use of this context later.
    group.getTicker().remove(this);
    group.getEventHandler().getHandler<QueueHandler>()->remove(queue);
}

namespace {
bool isOwner(QueueOwnership o) { return o == SOLE_OWNER || o == SHARED_OWNER; }
}

// Called by QueueReplica in CPG deliver thread when state changes.
void QueueContext::replicaState(QueueOwnership before, QueueOwnership after)
{
    sys::Mutex::ScopedLock l(lock);
    // Interested in state changes which lead to ownership.
    // We voluntarily give up ownership before multicasting
    // the state change so we don't need to handle transitions
    // that lead to non-ownership.

    if (before != after && isOwner(after)) {
        assert(before == ownership);
        if (!consuming) queue.startConsumers();
        consuming = true;
        ticks = 0;
    }
    ownership = after;
}

// FIXME aconway 2011-07-27: Dont spin the token on an empty queue.

// Called in broker threads when a consumer is added
void QueueContext::consume(size_t n) {
    sys::Mutex::ScopedLock l(lock);
    if (consumers == 0 && n > 0 && ownership == UNSUBSCRIBED)
        mcast.mcast(
            framing::ClusterQueueSubscribeBody(pv, queue.getName()));
    consumers = n;
}

// Called in broker threads when a consumer is cancelled
void QueueContext::cancel(size_t n) {
    sys::Mutex::ScopedLock l(lock);
    consumers = n;
    if (n == 0 && consuming) queue.stopConsumers();
}

// FIXME aconway 2011-11-03: review scope of locking around sendConsumed

// Called in Ticker thread.
void QueueContext::tick() {
    sys::Mutex::ScopedLock l(lock);
    if (!consuming) return;     // Nothing to do if we don't have the lock.
    if (ownership == SHARED_OWNER && ++ticks >= maxTicks) queue.stopConsumers();
    else if (ownership == SOLE_OWNER) sendConsumed(l); // Status report on consumption
}

// Callback set up by queue.stopConsumers() called in connection or timer thread.
// Called when no threads are dispatching from the queue.
void QueueContext::stopped() {
    sys::Mutex::ScopedLock l(lock);
    if (!consuming) return; // !consuming => initial stopConsumers in ctor.
    sendConsumed(l);
    mcast.mcast(
        framing::ClusterQueueUnsubscribeBody(pv, queue.getName(), consumers));
    consuming = false;
}

void QueueContext::sendConsumed(const sys::Mutex::ScopedLock&) {
    if (acquired.empty() && dequeued.empty()) return; // Nothing to send
    mcast.mcast(
        framing::ClusterQueueConsumedBody(pv, queue.getName(), acquired,dequeued));
    acquired.clear();
    dequeued.clear();
}

void QueueContext::requeue(uint32_t position, bool redelivered) {
    // No lock, unacked has its own lock.
    broker::QueuedMessage qm = unacked.pop(position);
    if (qm.queue) {
        if (redelivered) qm.payload->redeliver();
        BrokerContext::ScopedSuppressReplication ssr;
        queue.requeue(qm);
    }
}

void QueueContext::localAcquire(uint32_t position) {
    QPID_LOG(trace, "cluster queue " << queue.getName() << " acquired " << position);
    sys::Mutex::ScopedLock l(lock);
    assert(consuming);
    acquired.add(position);
}

void QueueContext::localDequeue(uint32_t position) {
    QPID_LOG(trace, "cluster queue " << queue.getName() << " dequeued " << position);
    // FIXME aconway 2010-10-28: for local dequeues, we should
    // complete the ack that initiated the dequeue at this point.
    sys::Mutex::ScopedLock l(lock);

    // FIXME aconway 2011-11-03: this assertion fails for explicit accept
    // because it doesn't respect the consume lock.
    // assert(consuming);

    dequeued.add(position);
}

void QueueContext::consumed(
    const MemberId& sender,
    const SequenceSet& acquired,
    const SequenceSet& dequeued)
{
    // No lock, doesn't touch any members.

    // FIXME aconway 2011-09-15: systematic logging across cluster module.
    // FIXME aconway 2011-09-23: pretty printing for identifier.
    QPID_LOG(trace, "cluster: " << sender << " acquired: " << acquired
             << " dequeued: " << dequeued << " on queue: " << queue.getName());

    // Note acquires from other members. My own acquires were executed in
    // the connection thread
    if (sender != group.getSelf()) {
        // FIXME aconway 2011-09-23: avoid individual finds, scan queue once.
        for (SequenceSet::iterator i =  acquired.begin(); i != acquired.end(); ++i)
            acquire(*i);
    }
    // Process deques from the queue owner.
    // FIXME aconway 2011-09-23: avoid individual finds, scan queue once.
    for (SequenceSet::iterator i =  dequeued.begin(); i != dequeued.end(); ++i)
        dequeue(*i);
}

// Remote acquire
void QueueContext::acquire(uint32_t position) {
    // No lock, doesn't touch any members.
    broker::QueuedMessage qm;
    BrokerContext::ScopedSuppressReplication ssr;
    if (!queue.acquireMessageAt(position, qm))
        // FIXME aconway 2011-10-31: error handling
        throw Exception(QPID_MSG("cluster: acquire: message not found: "
                                 << queue.getName() << "[" << position << "]"));
    assert(qm.position.getValue() == position);
    assert(qm.payload);
    unacked.put(qm.position, qm); // unacked has its own lock.
}

void QueueContext::dequeue(uint32_t position) {
    // No lock, doesn't touch any members. unacked has its own lock.
    broker::QueuedMessage qm = unacked.pop(position);
    BrokerContext::ScopedSuppressReplication ssr;
    if (qm.queue) queue.dequeue(0, qm);
}

QueueContext* QueueContext::get(broker::Queue& q) {
    return static_cast<QueueContext*>(q.getClusterContext());
}

// FIXME aconway 2011-09-23: make unacked a plain map, use lock.

}} // namespace qpid::cluster
