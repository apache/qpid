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
class LogPrefix;

/**
 * A susbcription that represents a backup replicating a queue.
 *
 * Runs on the primary. Delays completion of messages till the backup
 * has acknowledged, informs backup of locally dequeued messages.
 *
 * THREAD SAFE: Used as a consumer in subscription's connection
 * thread, and as a QueueObserver in arbitrary connection threads.
 *
 * Lifecycle: broker::Queue holds shared_ptrs to this as a consumer.
 *
 */
class ReplicatingSubscription : public broker::SemanticState::ConsumerImpl,
                                public broker::QueueObserver
{
  public:
    struct Factory : public broker::ConsumerFactory {
        HaBroker& haBroker;
        Factory(HaBroker& hb) : haBroker(hb) {}
        boost::shared_ptr<broker::SemanticState::ConsumerImpl> create(
            broker::SemanticState* parent,
            const std::string& name, boost::shared_ptr<broker::Queue> ,
            bool ack, bool acquire, bool exclusive, const std::string& tag,
            const std::string& resumeId, uint64_t resumeTtl,
            const framing::FieldTable& arguments);
    };

    // Argument names for consume command.
    static const std::string QPID_REPLICATING_SUBSCRIPTION;
    static const std::string QPID_HIGH_SEQUENCE_NUMBER;
    static const std::string QPID_LOW_SEQUENCE_NUMBER;
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

    ReplicatingSubscription(HaBroker&,
                            broker::SemanticState* parent,
                            const std::string& name, boost::shared_ptr<broker::Queue> ,
                            bool ack, bool acquire, bool exclusive, const std::string& tag,
                            const std::string& resumeId, uint64_t resumeTtl,
                            const framing::FieldTable& arguments);

    ~ReplicatingSubscription();

    // QueueObserver overrides. NB called with queue lock held.
    void enqueued(const broker::QueuedMessage&);
    void dequeued(const broker::QueuedMessage&);
    void acquired(const broker::QueuedMessage&) {}
    void requeued(const broker::QueuedMessage&) {}

    // Consumer overrides.
    bool deliver(broker::QueuedMessage& msg);
    void cancel();
    void acknowledged(const broker::QueuedMessage&);
    bool browseAcquired() const { return true; }

    bool hideDeletedError();
    /** Initialization that must be done after construction because it
     * requires a shared_ptr to this to exist.
     */
    void initialize();

  protected:
    bool doDispatch();
  private:
    typedef std::map<framing::SequenceNumber, broker::QueuedMessage> Delayed;

    HaBroker&  haBroker;
    LogPrefix logPrefix;
    boost::shared_ptr<broker::Queue> dummy; // Used to send event messages
    Delayed delayed;
    framing::SequenceSet dequeues;
    framing::SequenceNumber backupPosition;
    framing::SequenceNumber readyPosition;
    bool ready;

    void complete(const broker::QueuedMessage&, const sys::Mutex::ScopedLock&);
    void cancelComplete(const Delayed::value_type& v, const sys::Mutex::ScopedLock&);
    void sendDequeueEvent(const sys::Mutex::ScopedLock&);
    void sendPositionEvent(framing::SequenceNumber, const sys::Mutex::ScopedLock&);
    void setReady(const sys::Mutex::ScopedLock&);
    void sendEvent(const std::string& key, framing::Buffer&);
  friend struct Factory;
};


}} // namespace qpid::broker

#endif  /*!QPID_BROKER_REPLICATINGSUBSCRIPTION_H*/
