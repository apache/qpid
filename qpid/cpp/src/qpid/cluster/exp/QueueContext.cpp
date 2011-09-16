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
#include "BrokerContext.h"      // for ScopedSuppressReplication
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

// FIXME aconway 2011-09-16: configurable timeout.
QueueContext::QueueContext(broker::Queue& q, Multicaster& m)
    : ownership(UNSUBSCRIBED),
      timer(boost::bind(&QueueContext::timeout, this),
            q.getBroker()->getTimer(),
            100*sys::TIME_MSEC),
      queue(q), mcast(m), consumers(0)
{
    q.setClusterContext(boost::intrusive_ptr<QueueContext>(this));
}

QueueContext::~QueueContext() {}

// Invariant for ownership:
// UNSUBSCRIBED, SUBSCRIBED => timer stopped, queue stopped
// SOLE_OWNER => timer stopped, queue started
// SHARED_OWNER => timer started, queue started

namespace {
bool isOwner(QueueOwnership o) { return o == SOLE_OWNER || o == SHARED_OWNER; }
}

// Called by QueueReplica in CPG deliver thread when state changes.
void QueueContext::replicaState(QueueOwnership newOwnership) {
    sys::Mutex::ScopedLock l(lock);
    QueueOwnership before = ownership;
    QueueOwnership after = newOwnership;
    ownership = after;
    if (!isOwner(before) && !isOwner(after))
        ;   // Nothing to do, now ownership change on this transition.
    else if (isOwner(before) && !isOwner(after)) // Lost ownership
        ; // Nothing to do, queue and timer were stopped before
          // sending unsubscribe/resubscribe.
    else if (!isOwner(before) && isOwner(after)) { // Took ownership
        queue.startConsumers();
        if (after == SHARED_OWNER) timer.start();
    }
    else if (isOwner(before) && isOwner(after) && before != after) {
        if (after == SOLE_OWNER) timer.stop();
        else timer.start();
    }
}

// FIXME aconway 2011-07-27: Dont spin the token on an empty or idle queue.

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
        timer.stop();
        queue.stopConsumers(); // FIXME aconway 2011-07-28: Ok inside lock?
    }
}

// Called in timer thread.
void QueueContext::timeout() {
    // When all threads have stopped, queue will call stopped()
    queue.stopConsumers();
}

// Callback set up by queue.stopConsumers() called in connection thread.
// Called when no threads are dispatching from the queue.
void QueueContext::stopped() {
    sys::Mutex::ScopedLock l(lock);
    // FIXME aconway 2011-07-28: review thread safety of state.
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

void QueueContext::dequeue(uint32_t position) {
    unacked.erase(position);
}

boost::intrusive_ptr<QueueContext> QueueContext::get(broker::Queue& q) {
    return boost::intrusive_ptr<QueueContext>(
        static_cast<QueueContext*>(q.getClusterContext().get()));
}

}} // namespace qpid::cluster
