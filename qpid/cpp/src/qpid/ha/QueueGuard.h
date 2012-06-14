#ifndef QPID_HA_QUEUEGUARD_H
#define QPID_HA_QUEUEGUARD_H

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

#include "types.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/types/Uuid.h"
#include "qpid/sys/Mutex.h"
#include <boost/shared_ptr.hpp>
#include <deque>
#include <set>

namespace qpid {
namespace broker {
class Queue;
struct QueuedMessage;
class Message;
}

namespace ha {
class BrokerInfo;
class ReplicatingSubscription;

/**
 * A queue guard is a QueueObserver that delays completion of new
 * messages arriving on a queue.  It works as part of a
 * ReplicatingSubscription to ensure messages are not acknowledged
 * till they have been replicated.
 *
 * The guard is created before the ReplicatingSubscription to protect
 * messages arriving before the creation of the subscription.
 *
 * THREAD SAFE: Concurrent calls:
 *  - enqueued() via QueueObserver in arbitrary connection threads.
 *  - attach(), cancel(), complete() from ReplicatingSubscription in subscription thread.
 */
class QueueGuard {
  public:
    QueueGuard(broker::Queue& q, const BrokerInfo&);
    ~QueueGuard();

    /** QueueObserver override. Delay completion of the message.
     * NOTE: Called under the queues message lock.
     */
    void enqueued(const broker::QueuedMessage&);

    /** QueueObserver override: Complete a delayed message.
     * NOTE: Called under the queues message lock.
     */
    void dequeued(const broker::QueuedMessage&);

    /** Complete a delayed message. */
    void complete(const broker::QueuedMessage&);

    /** Complete all delayed messages. */
    void cancel();

    void attach(ReplicatingSubscription&);

    /** The first sequence number protected by this guard.
     * All messages at or after this position are protected.
     */
    framing::SequenceNumber getFirstSafe();

  private:
    class QueueObserver;

    sys::Mutex lock;
    std::string logPrefix;
    broker::Queue& queue;
    framing::SequenceSet delayed;
    ReplicatingSubscription* subscription;
    boost::shared_ptr<QueueObserver> observer;
    framing::SequenceNumber firstSafe; // Immutable
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_QUEUEGUARD_H*/
