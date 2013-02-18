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
#include "QueueRange.h"
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
 * A queue guard is a QueueObserver that delays completion of new messages
 * arriving on a queue.  It works as part of a ReplicatingSubscription to ensure
 * messages are not acknowledged till they have been replicated.
 *
 * The guard can be created before the ReplicatingSubscription to protect
 * messages arriving before the creation of the subscription.
 *
 * THREAD SAFE: Concurrent calls:
 *  - enqueued() via QueueObserver in arbitrary connection threads.
 *  - attach(), cancel(), complete() from ReplicatingSubscription in subscription thread.
 *
 * Lock Hierarchy: ReplicatingSubscription MUS NOT call QueueGuard with it's lock held
 * QueueGuard MAY call ReplicatingSubscription with it's lock held.
 */
class QueueGuard {
  public:
    QueueGuard(broker::Queue& q, const BrokerInfo&);
    ~QueueGuard();

    /** QueueObserver override. Delay completion of the message.
     * NOTE: Called under the queues message lock.
     */
    void enqueued(const broker::Message&);

    /** QueueObserver override: Complete a delayed message.
     * NOTE: Called under the queues message lock.
     */
    void dequeued(const broker::Message&);

    /** Complete a delayed message. */
    void complete(framing::SequenceNumber);

    /** Complete all delayed messages. */
    void cancel();

    void attach(ReplicatingSubscription&);

    /**
     * Return the un-guarded queue range at the time the QueueGuard was created.
     *
     * The first position guaranteed to be protected by the guard is
     * getRange().getBack()+1. It is possible that the guard has protected some
     * messages before that point. Any such messages are dealt with in subscriptionStart
     *
     * The QueueGuard is created in 3 situations
     * - when a backup is promoted, guards are created for expected backups.
     * - when a new queue is created on the primary
     * - when a new backup joins.
     *
     * In the last situation the queue is active while the guard is being
     * created.
     *
     */
    const QueueRange& getRange() const { return range; } // range is immutable, no lock needed.

    /** Inform the guard of the stating position for the attached subscription.
     * Complete messages that will not be seen by the subscription.
     *@return true if the subscription has already advanced to a guarded position.
     */
    bool subscriptionStart(framing::SequenceNumber position);

  private:
    class QueueObserver;
    typedef std::map<framing::SequenceNumber,
                     boost::intrusive_ptr<broker::AsyncCompletion> > Delayed;

    void complete(framing::SequenceNumber, sys::Mutex::ScopedLock &);
    void complete(Delayed::iterator, sys::Mutex::ScopedLock &);

    sys::Mutex lock;
    bool cancelled;
    std::string logPrefix;
    broker::Queue& queue;
    Delayed delayed;
    ReplicatingSubscription* subscription;
    boost::shared_ptr<QueueObserver> observer;
    QueueRange range;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_QUEUEGUARD_H*/
