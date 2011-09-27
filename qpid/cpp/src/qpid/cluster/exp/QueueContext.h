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
#include "CountdownTimer.h"
#include "qpid/RefCounted.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Mutex.h"
#include "qpid/cluster/types.h"
#include <boost/intrusive_ptr.hpp>

// FIXME aconway 2011-06-08: refactor broker::Cluster to put queue ups on
// class broker::Cluster::Queue. This becomes the cluster context.

namespace qpid {
namespace broker {
class Queue;
class QueuedMessage;
}

namespace cluster {

class Multicaster;

 /**
 * Queue state that is not replicated to the cluster.
 * Manages the local queue start/stop status.
 *
 * Thread safe: Called by connection, dispatch and timer threads.
 */
class QueueContext : public RefCounted {
  public:
    QueueContext(broker::Queue& q, sys::Duration consumeLock, Multicaster& m);
    ~QueueContext();

    /** Replica state has changed, called in deliver thread.
     * @param before replica state before the event.
     * @param before replica state after the event.
     * @param self is true if this was a self-delivered event.
     */
    void replicaState(QueueOwnership before, QueueOwnership after, bool self);

    /** Called when queue is stopped, no threads are dispatching.
     * May be called in connection or deliver thread.
     */
    void stopped();

    /** Called in connection thread when a consumer is added.
     *@param n: nubmer of consumers after new one is added.
     */
    void consume(size_t n);

    /** Called in connection thread when a consumer is cancelled on the queue.
     *@param n: nubmer of consumers after the cancel.
     */
    void cancel(size_t n);

    /** Called in timer thread when the timer runs out. */
    void timeout();

    /** Called by MessageHandler to requeue a message. */
    void requeue(uint32_t position, bool redelivered);

    /** Called by MessageHandler when a mesages is acquired. */
    void acquire(const broker::QueuedMessage& qm);

    /** Called by MesageHandler when a message is dequeued. */
    broker::QueuedMessage dequeue(uint32_t position);

    size_t getHash() const { return hash; }


    /** Get the cluster context for a broker queue. */
    static boost::intrusive_ptr<QueueContext> get(broker::Queue&) ;

private:
    sys::Mutex lock;
    CountdownTimer timer;
    broker::Queue& queue;       // FIXME aconway 2011-06-08: should be shared/weak ptr?
    Multicaster& mcast;
    size_t consumers;
    size_t hash;

    typedef LockedMap<uint32_t, broker::QueuedMessage> UnackedMap; // FIXME aconway 2011-09-15: don't need read/write map? Rename
    UnackedMap unacked;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_QUEUESTATE_H*/
