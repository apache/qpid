
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

#include "QueueContext.h"
#include "Multicaster.h"
#include "qpid/cluster/types.h"
#include "BrokerContext.h"      // for ScopedSuppressReplication
#include "hash.h"
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

QueueContext::QueueContext(broker::Queue& q, sys::Duration consumeLock, Multicaster& m)
    : timer(boost::bind(&QueueContext::timeout, this),
            q.getBroker()->getTimer(),
            consumeLock),
      queue(q), mcast(m), consumers(0), hash(hashof(q.getName()))
{
    q.setClusterContext(boost::intrusive_ptr<QueueContext>(this));
    q.stopConsumers();          // Stop queue initially.
}

QueueContext::~QueueContext() {}

namespace {
bool isOwner(QueueOwnership o) { return o == SOLE_OWNER || o == SHARED_OWNER; }
}

// Called by QueueReplica in CPG deliver thread when state changes.
void QueueContext::replicaState(
    QueueOwnership before, QueueOwnership after, bool selfDelivered)
{
    // No lock, this function does not touch any member variables.

    // Invariants for ownership:
    // UNSUBSCRIBED, SUBSCRIBED <=> timer stopped, queue stopped
    // SOLE_OWNER <=> timer stopped, queue started
    // SHARED_OWNER <=> timer started, queue started

    // Interested in state changes and my own events which lead to
    // ownership.
    if ((before != after || selfDelivered) && isOwner(after)) {
        QPID_LOG(trace, "cluster start consumers on " << queue.getName() << ", timer "
                 << (after==SHARED_OWNER? "start" : "stop"));
        sys::Mutex::ScopedLock l(lock); // FIXME aconway 2011-09-29: REMOVE
        queue.startConsumers();
        if (after == SHARED_OWNER) timer.start();
        else timer.stop();
    }

    // If we lost ownership then the queue and timer will already have
    // been stopped by timeout()
}

// FIXME aconway 2011-07-27: Dont spin the token on an empty queue.

// Called in connection threads when a consumer is added
void QueueContext::consume(size_t n) {
    sys::Mutex::ScopedLock l(lock);
    consumers = n;
    if (n == 1) mcast.mcast(
        framing::ClusterQueueSubscribeBody(framing::ProtocolVersion(), queue.getName()));
}

// Called in connection threads when a consumer is cancelled
void QueueContext::cancel(size_t n) {
    sys::Mutex::ScopedLock l(lock);
    consumers = n;
    // When consuming threads are stopped, this->stopped will be called.
    if (n == 0) {
        QPID_LOG(trace, "cluster stop consumers and timer on " << queue.getName());
        timer.stop();
        queue.stopConsumers();
    }
}

// Called in timer thread.
void QueueContext::timeout() {
    // When all threads have stopped, queue will call stopped()
    QPID_LOG(trace, "cluster timeout, stopping consumers on " << queue.getName());
    queue.stopConsumers();
}

// Callback set up by queue.stopConsumers() called in connection thread.
// Called when no threads are dispatching from the queue.
void QueueContext::stopped() {
    sys::Mutex::ScopedLock l(lock);
    QPID_LOG(trace, "cluster timeout, stopped consumers on " << queue.getName()
             << (consumers == 0 ? " unsubscribed" : " resubscribe"));
    if (consumers == 0)
        mcast.mcast(framing::ClusterQueueUnsubscribeBody(
                        framing::ProtocolVersion(), queue.getName()));
    else            // FIXME aconway 2011-09-13: check if we're owner?
        mcast.mcast(framing::ClusterQueueResubscribeBody(
                        framing::ProtocolVersion(), queue.getName()));
}

void QueueContext::requeue(uint32_t position, bool redelivered) {
    // No lock, unacked has its own lock.
    broker::QueuedMessage qm;
    if (unacked.get(position, qm)) {
        unacked.erase(position);
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
