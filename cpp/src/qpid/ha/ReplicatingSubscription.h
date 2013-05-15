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

#include "BrokerInfo.h"
#include "qpid/broker/SemanticState.h"
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
 * Runs on the primary. In conjunction with a QueueGuard, delays completion of
 * messages till the backup has acknowledged, informs backup of locally dequeued
 * messages.
 *
 * A ReplicatingSubscription is "ready" when all the messages on the queue have
 * either been acknowledged by the backup, or are protected by the queue guard.
 * On a primary broker the ReplicatingSubscription calls Primary::readyReplica
 * when it is ready.
 *
 * THREAD SAFE: Called in subscription's connection thread but also in arbitrary
 * connection threads via dequeued.
 *
 * Lifecycle: broker::Queue holds shared_ptrs to this as a consumer.
 *
 * Lock Hierarchy: ReplicatingSubscription MUST NOT call QueueGuard with its
 * lock held QueueGuard MAY call ReplicatingSubscription with its lock held.
 */
class ReplicatingSubscription : public broker::SemanticState::ConsumerImpl
{
  public:
    typedef broker::SemanticState::ConsumerImpl ConsumerImpl;

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

    ReplicatingSubscription(broker::SemanticState* parent,
                            const std::string& name, boost::shared_ptr<broker::Queue> ,
                            bool ack, bool acquire, bool exclusive, const std::string& tag,
                            const std::string& resumeId, uint64_t resumeTtl,
                            const framing::FieldTable& arguments);

    ~ReplicatingSubscription();

    // Called via QueueGuard::dequeued.
    //@return true if the message requires completion.
    void dequeued(const broker::Message&);

    // Called during initial scan for dequeues.
    void dequeued(framing::SequenceNumber first, framing::SequenceNumber last);

    // Consumer overrides.
    bool deliver(const broker::QueueCursor& cursor, const broker::Message& msg);
    void cancel();
    void acknowledged(const broker::DeliveryRecord&);
    bool browseAcquired() const { return true; }
    // Hide the "queue deleted" error for a ReplicatingSubscription when a
    // queue is deleted, this is normal and not an error.
    bool hideDeletedError() { return true; }
    // Not counted for auto deletion and immediate message purposes.
    bool isCounted() { return false; }

    /** Initialization that must be done separately from construction
     * because it requires a shared_ptr to this to exist.
     */
    void initialize();

    BrokerInfo getBrokerInfo() const { return info; }

  protected:
    bool doDispatch();

  private:
    std::string logPrefix;
    framing::SequenceSet dequeues;
    framing::SequenceNumber position;
    framing::SequenceNumber backupPosition;
    bool ready;
    BrokerInfo info;
    boost::shared_ptr<QueueGuard> guard;

    void sendDequeueEvent(sys::Mutex::ScopedLock&);
    void sendPositionEvent(framing::SequenceNumber, sys::Mutex::ScopedLock&);
    void setReady();
    void sendEvent(const std::string& key, framing::Buffer&);
  friend struct Factory;
};


}} // namespace qpid::broker

#endif  /*!QPID_BROKER_REPLICATINGSUBSCRIPTION_H*/
