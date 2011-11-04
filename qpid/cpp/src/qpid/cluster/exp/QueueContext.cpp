
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
#include "Group.h"
#include "Multicaster.h"
#include "QueueContext.h"
#include "hash.h"
#include "qpid/cluster/types.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/ClusterQueueResubscribeBody.h"
#include "qpid/framing/ClusterQueueSubscribeBody.h"
#include "qpid/framing/ClusterQueueUnsubscribeBody.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

QueueContext::QueueContext(broker::Queue& q, Group& g, size_t maxTicks_)
    : consumers(0), consuming(true), ticks(0),
      queue(q), mcast(g.getMulticaster()), hash(hashof(q.getName())),
      maxTicks(maxTicks_)
{
    q.setClusterContext(boost::intrusive_ptr<QueueContext>(this));
    q.stopConsumers();          // Stop queue initially.
    g.getTicker().add(this);
}

QueueContext::~QueueContext() {}

namespace {
bool isOwner(QueueOwnership o) { return o == SOLE_OWNER || o == SHARED_OWNER; }
}

// Called by QueueReplica in CPG deliver thread when state changes.
void QueueContext::replicaState(QueueOwnership before, QueueOwnership after)
{
    // Interested in state changes which lead to ownership.
    // We voluntarily give up ownership before multicasting
    // the state change so we don't need to handle transitions
    // that lead to non-ownership.
    if (before != after && isOwner(after)) {
        bool start = false;
        {
            sys::Mutex::ScopedLock l(lock);
            start = !consuming;
            consuming = true;
            ticks = 0;
        }
        if (start) queue.startConsumers();
    }
}

// FIXME aconway 2011-07-27: Dont spin the token on an empty queue.

// Called in broker threads when a consumer is added
void QueueContext::consume(size_t n) {
    {
        sys::Mutex::ScopedLock l(lock);
        consumers = n;
    }
    if (n == 1) mcast.mcast(
        framing::ClusterQueueSubscribeBody(framing::ProtocolVersion(), queue.getName()));
}

// Called in broker threads when a consumer is cancelled
void QueueContext::cancel(size_t n) {
    bool stop = false;
    {
        sys::Mutex::ScopedLock l(lock);
        consumers = n;
        stop = (n == 0 && consuming);
    }
    if (stop) queue.stopConsumers();
}

// Called in Ticker thread.
void QueueContext::tick() {
    bool stop = false;
    {
        sys::Mutex::ScopedLock l(lock);
        stop = (consuming && ++ticks >= maxTicks);
    }
    // When all threads have stopped, queue will call stopped()
    if (stop) queue.stopConsumers();
}

// Callback set up by queue.stopConsumers() called in connection or timer thread.
// Called when no threads are dispatching from the queue.
void QueueContext::stopped() {
    bool resubscribe = false;
    {
        sys::Mutex::ScopedLock l(lock);
        assert(consuming);
        consuming = false;
        resubscribe = consumers;
    }
    if (resubscribe)
        mcast.mcast(framing::ClusterQueueResubscribeBody(
                        framing::ProtocolVersion(), queue.getName()));
    else
        mcast.mcast(framing::ClusterQueueUnsubscribeBody(
                        framing::ProtocolVersion(), queue.getName()));
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

void QueueContext::acquire(const broker::QueuedMessage& qm) {
    unacked.put(qm.position, qm);
}

broker::QueuedMessage QueueContext::dequeue(uint32_t position) {
    return unacked.pop(position);
}

boost::intrusive_ptr<QueueContext> QueueContext::get(broker::Queue& q) {
    return boost::intrusive_ptr<QueueContext>(
        static_cast<QueueContext*>(q.getClusterContext().get()));
}

}} // namespace qpid::cluster
