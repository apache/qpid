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
#include "qpid/broker/QueueObserver.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/types/Uuid.h"
#include "qpid/sys/Mutex.h"
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <deque>
#include <set>

namespace qpid {
namespace broker {
class Queue;
class QueuedMessage;
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
 * messages arriving before the creation of the subscription has not
 * yet seen.
 *
 * THREAD SAFE: Called concurrently via QueueObserver::enqueued in
 * arbitrary connection threads, and from ReplicatingSubscription
 * in the subscriptions thread.
 */
class QueueGuard : public broker::QueueObserver,
                   public boost::enable_shared_from_this<QueueGuard>
{
  public:
    QueueGuard(broker::Queue& q, const BrokerInfo&);

    /** Must be called after ctor, requires a shared_ptr to this to exist.
     * Must be called before ReplicatingSubscription::initialize(this)
     */
    void initialize();

    /** QueueObserver override. Delay completion of the message. */
    void enqueued(const broker::QueuedMessage&);

    /** QueueObserver override: Complete a delayed message */
    void dequeued(const broker::QueuedMessage&);

    /** Complete a delayed message. */
    void complete(const broker::QueuedMessage&);

    /** Complete all delayed messages. */
    void cancel();

    void attach(ReplicatingSubscription&);

    // Unused QueueObserver functions.
    void acquired(const broker::QueuedMessage&) {}
    void requeued(const broker::QueuedMessage&) {}

  private:
    sys::Mutex lock;
    std::string logPrefix;
    broker::Queue& queue;
    framing::SequenceSet delayed;
    ReplicatingSubscription* subscription;

    void complete(const broker::QueuedMessage&, sys::Mutex::ScopedLock&);
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_QUEUEGUARD_H*/
