#ifndef QPID_CLUSTER_EXP_QUEUESTATE_H
#define QPID_CLUSTER_EXP_QUEUESTATE_H

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

#include "LockedMap.h"
#include "Ticker.h"
#include "qpid/RefCounted.h"
#include "qpid/broker/Context.h"
#include "qpid/cluster/types.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Time.h"

namespace qpid {
namespace broker {
class Queue;
class QueuedMessage;
}

namespace cluster {

class Multicaster;
class Group;

 /**
 * Local Queue state, manage start/stop consuming on the queue.
 * Destroyed when the queue is destroyed, it must erase itself
 * from any cluster data structures in its destructor.
 *
 * THREAD SAFE: Called by connection threads and Ticker dispatch threads.
 */
class QueueContext : public broker::Context, Ticker::Tickable {
  public:
    QueueContext(broker::Queue&, Group&, size_t consumeTicks);
    ~QueueContext();

    /** Replica state has changed, called in deliver thread.
     * @param before replica state before the event.
     * @param before replica state after the event.
     */
    void replicaState(QueueOwnership before, QueueOwnership after);

    /** Called when queue is stopped, no threads are dispatching.
     * May be called in connection or deliver thread.
     */
    void stopped();

    /** Called in broker thread when a consumer is added.
     *@param n: nubmer of consumers after new one is added.
     */
    void consume(size_t n);

    /** Called in broker thread when a consumer is cancelled on the queue.
     *@param n: nubmer of consumers after the cancel.
     */
    void cancel(size_t n);

    /** Called regularly at the tick interval in an IO thread.*/
    void tick();

    /** Called by MessageHandler to requeue a message. */
    void requeue(uint32_t position, bool redelivered);

    /** Called by BrokerContext when a mesages is acquired locally. */
    void localAcquire(uint32_t position);

    /** Called by MessageHandler when a mesages is acquired. */
    void acquire(uint32_t position);

    /** Called by MesageHandler when a message is dequeued. */
    void dequeue(uint32_t position);

    /** Called by BrokerContext when a message is dequeued locally. */
    void localDequeue(uint32_t position);

    /** Called in deliver thread, take note of another brokers acquires/dequeues. */
    void consumed(const MemberId&,
                  const framing::SequenceSet& acquired,
                  const framing::SequenceSet& dequeued);


    size_t getHash() const { return hash; }
    broker::Queue& getQueue() { return queue; }

    /** Get the cluster context for a broker queue. */
    static QueueContext* get(broker::Queue&);

private:
    void sendConsumed(const sys::Mutex::ScopedLock&);

    sys::Mutex lock;
    QueueOwnership ownership;   // Ownership status.
    size_t consumers;           // Number of local consumers.
    bool consuming;             // True if we have the lock.
    size_t ticks;               // Ticks since we got the lock.

    // Following members are immutable
    broker::Queue& queue;
    Multicaster& mcast;
    size_t hash;
    size_t maxTicks;            // Max ticks we are allowed.
    framing::SequenceSet acquired, dequeued; // Track local acquires/dequeues.

    // Following members are safe to use without holding a lock
    typedef LockedMap<uint32_t, broker::QueuedMessage> UnackedMap;
    UnackedMap unacked;
    Group& group;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_QUEUESTATE_H*/
