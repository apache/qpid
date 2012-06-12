#ifndef QPID_BROKER_REPLICATINGSUBSCRIPTION_H
#define QPID_BROKER_REPLICATINGSUBSCRIPTION_H

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

#include "QueueReplicator.h"    // For DEQUEUE_EVENT_KEY
#include "BrokerInfo.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/broker/QueueObserver.h"
#include "qpid/broker/ConsumerFactory.h"
#include "qpid/types/Uuid.h"
#include <iosfwd>

namespace qpid {

namespace broker {
class Message;
class Queue;
struct QueuedMessage;
class OwnershipToken;
}

namespace framing {
class Buffer;
}

namespace ha {
class QueueGuard;

/**
 * A susbcription that replicates to a remote backup.
 *
 * Runs on the primary. In conjunction with a QueueGuard, delays
 * completion of messages till the backup has acknowledged, informs
 * backup of locally dequeued messages.
 *
 * THREAD SAFE: Called in subscription's connection thread but also
 * in arbitrary connection threads via dequeued.
 *
 * Lifecycle: broker::Queue holds shared_ptrs to this as a consumer.
 *
 */
class ReplicatingSubscription : public broker::SemanticState::ConsumerImpl
{
  public:
    struct Factory : public broker::ConsumerFactory {
        boost::shared_ptr<broker::SemanticState::ConsumerImpl> create(
            broker::SemanticState* parent,
            const std::string& name, boost::shared_ptr<broker::Queue> ,
            bool ack, bool acquire, bool exclusive, const std::string& tag,
            const std::string& resumeId, uint64_t resumeTtl,
            const framing::FieldTable& arguments);
    };

    // Argument names for consume command.
    static const std::string QPID_REPLICATING_SUBSCRIPTION;
    static const std::string QPID_BACK;
    static const std::string QPID_FRONT;
    static const std::string QPID_BROKER_INFO;

    // TODO aconway 2012-05-23: these don't belong on ReplicatingSubscription
    /** Get position of front message on queue.
     *@return false if queue is empty.
     */
    static bool getFront(broker::Queue&, framing::SequenceNumber& result);
    /** Get next message after from in queue.
     *@return false if none found.
     */
    static bool getNext(broker::Queue&, framing::SequenceNumber from,
                        framing::SequenceNumber& result);
    static bool isEmpty(broker::Queue&);

    ReplicatingSubscription(broker::SemanticState* parent,
                            const std::string& name, boost::shared_ptr<broker::Queue> ,
                            bool ack, bool acquire, bool exclusive, const std::string& tag,
                            const std::string& resumeId, uint64_t resumeTtl,
                            const framing::FieldTable& arguments);

    ~ReplicatingSubscription();

    // Called via QueueGuard::dequeued
    void dequeued(const broker::QueuedMessage& qm);

    // Consumer overrides.
    bool deliver(broker::QueuedMessage& msg);
    void cancel();
    void acknowledged(const broker::QueuedMessage&);
    bool browseAcquired() const { return true; }

    bool hideDeletedError();

    /** Initialization that must be done after construction because it
     * requires a shared_ptr to this to exist. Will attach to guard
     */
    void initialize();

    BrokerInfo getBrokerInfo() const { return info; }

  protected:
    bool doDispatch();

  private:
    std::string logPrefix;
    boost::shared_ptr<broker::Queue> dummy; // Used to send event messages
    framing::SequenceSet dequeues;
    framing::SequenceNumber readyPosition;
    framing::SequenceNumber backupPosition;
    bool ready;
    BrokerInfo info;
    boost::shared_ptr<QueueGuard> guard;

    void sendDequeueEvent(sys::Mutex::ScopedLock&);
    void sendPositionEvent(framing::SequenceNumber, sys::Mutex::ScopedLock&);
    void setReady(sys::Mutex::ScopedLock&);
    void sendEvent(const std::string& key, framing::Buffer&);
  friend struct Factory;
};


}} // namespace qpid::broker

#endif  /*!QPID_BROKER_REPLICATINGSUBSCRIPTION_H*/
