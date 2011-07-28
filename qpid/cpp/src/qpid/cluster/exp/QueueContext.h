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


#include <qpid/RefCounted.h>
#include "qpid/sys/Time.h"
#include <qpid/sys/Mutex.h>
#include "qpid/cluster/types.h"
#include <boost/intrusive_ptr.hpp>

// FIXME aconway 2011-06-08: refactor broker::Cluster to put queue ups on
// class broker::Cluster::Queue. This becomes the cluster context.

namespace qpid {
namespace broker {
class Queue;
}
namespace sys {
class Timer;
class TimerTask;
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
    QueueContext(broker::Queue& q, Multicaster& m);
    ~QueueContext();

    /** Replica state has changed, called in deliver thread. */
    void replicaState(QueueOwnership);

    /** Called when queue is stopped, no threads are dispatching.
     * Connection or deliver thread.
     */
    void stopped();

    /** Called when a consumer is added to the queue.
     *@param n: nubmer of consumers after new one is added.
     */
    void consume(size_t n);

    /** Called when a consumer is cancelled on the queue.
     *@param n: nubmer of consumers after the cancel.
     */
    void cancel(size_t n);

    /** Get the context for a broker queue. */
    static boost::intrusive_ptr<QueueContext> get(broker::Queue&);

    /** Called when the timer runs out: stop the queue. */
    void timeout();

  private:
    sys::Timer& timer;

    sys::Mutex lock;
    broker::Queue& queue;       // FIXME aconway 2011-06-08: should be shared/weak ptr?
    Multicaster& mcast;
    boost::intrusive_ptr<sys::TimerTask> timerTask;
    size_t consumers;

    // FIXME aconway 2011-06-28: need to store acquired messages for possible re-queueing.
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_QUEUESTATE_H*/
