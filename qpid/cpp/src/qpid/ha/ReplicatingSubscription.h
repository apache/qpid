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
#include <iosfwd>

namespace qpid {

namespace broker {
class Message;
class Queue;
class QueuedMessage;
class OwnershipToken;
}

namespace framing {
class Buffer;
}

namespace ha {

/**
 * A susbcription that represents a backup replicating a queue.
 *
 * Runs on the primary. Delays completion of messages till the backup
 * has acknowledged, informs backup of locally dequeued messages.
 *
 * THREAD SAFE: Used as a consumer in subscription's connection
 * thread, and as a QueueObserver in arbitrary connection threads.
 */
class ReplicatingSubscription : public broker::SemanticState::ConsumerImpl,
                                public broker::QueueObserver
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

    ReplicatingSubscription(broker::SemanticState* parent,
                            const std::string& name, boost::shared_ptr<broker::Queue> ,
                            bool ack, bool acquire, bool exclusive, const std::string& tag,
                            const std::string& resumeId, uint64_t resumeTtl,
                            const framing::FieldTable& arguments);

    ~ReplicatingSubscription();

    // QueueObserver overrides.
    bool deliver(broker::QueuedMessage& msg);
    void enqueued(const broker::QueuedMessage&);
    void dequeued(const broker::QueuedMessage&);
    void acquired(const broker::QueuedMessage&) {}
    void requeued(const broker::QueuedMessage&) {}

    // Consumer overrides.
    void cancel();
    void acknowledged(const broker::QueuedMessage&);
    bool browseAcquired() const { return true; }

    bool hideDeletedError();

  protected:
    bool doDispatch();
  private:
    typedef std::map<framing::SequenceNumber, broker::QueuedMessage> Delayed;
    std::string logPrefix;
    boost::shared_ptr<broker::Queue> events;
    boost::shared_ptr<broker::Consumer> consumer;
    Delayed delayed;
    framing::SequenceSet dequeues;
    framing::SequenceNumber backupPosition;

    void complete(const broker::QueuedMessage&, const sys::Mutex::ScopedLock&);
    void cancelComplete(const Delayed::value_type& v, const sys::Mutex::ScopedLock&);
    void sendDequeueEvent(const sys::Mutex::ScopedLock&);
    void sendPositionEvent(framing::SequenceNumber, const sys::Mutex::ScopedLock&);
    void sendEvent(const std::string& key, framing::Buffer&,
                   const sys::Mutex::ScopedLock&);

    class DelegatingConsumer : public Consumer
    {
      public:
        DelegatingConsumer(ReplicatingSubscription&);
        ~DelegatingConsumer();
        bool deliver(broker::QueuedMessage& msg);
        void notify();
        bool filter(boost::intrusive_ptr<broker::Message>);
        bool accept(boost::intrusive_ptr<broker::Message>);
        void cancel() {}
        void acknowledged(const broker::QueuedMessage&) {}
        bool browseAcquired() const;

        broker::OwnershipToken* getSession();

      private:
        ReplicatingSubscription& delegate;
    };
};


}} // namespace qpid::broker

#endif  /*!QPID_BROKER_REPLICATINGSUBSCRIPTION_H*/
