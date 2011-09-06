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
#include <qpid/sys/Mutex.h>
#include <boost/intrusive_ptr.hpp>


// FIXME aconway 2011-06-08: refactor broker::Cluster to put queue ups on
// class broker::Cluster::Queue. This becomes the cluster context.

namespace qpid {
namespace broker {
class Queue;
}
namespace cluster {

class Multicaster;

 /**
 * Queue state that is not replicated to the cluster.
 * Manages the local queue start/stop status
 *
 * Thread safe: Called by connection and dispatch threads.
 */
class QueueContext : public RefCounted {
    // FIXME aconway 2011-06-07: consistent use of shared vs. intrusive ptr?
  public:
    QueueContext(broker::Queue& q, Multicaster& m);

    /** Sharing ownership of queue, can acquire up to limit before releasing.
     * Called in deliver thread.
     */
    void sharedOwner(size_t limit);

    /** Sole owner of queue, no limits to acquiring */
    void soleOwner();

    /**
     * Count an acquired message against the limit.
     * Called from connection threads while consuming messages
     */
    void acquire();

    /** Called if the queue becomes empty, from connection thread. */
    void empty();

    /** Called when queue is stopped, connection or deliver thread. */
    void stopped();

    /** Called when the last subscription to a queue is cancelled */
    void unsubscribed();

    /** Get the context for a broker queue. */
    static boost::intrusive_ptr<QueueContext> get(broker::Queue&);

  private:
    void release();

    sys::Mutex lock;
    enum { NOT_OWNER, SOLE_OWNER, SHARED_OWNER } owner;
    size_t count;               // Count of dequeues remaining, 0 means no limit.
    broker::Queue& queue;       // FIXME aconway 2011-06-08: should be shared/weak ptr?
    Multicaster& mcast;

    // FIXME aconway 2011-06-28: need to store acquired messages for possible re-queueing.
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_QUEUESTATE_H*/
