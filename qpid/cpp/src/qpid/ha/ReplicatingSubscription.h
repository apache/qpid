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
#include "LogPrefix.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/broker/ConsumerFactory.h"
#include "qpid/broker/QueueObserver.h"
#include <boost/enable_shared_from_this.hpp>
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
class HaBroker;
class Event;
class Primary;

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
 *  ReplicatingSubscription makes calls on QueueGuard, but not vice-versa.
 */
class ReplicatingSubscription :
        public broker::SemanticState::ConsumerImpl,
        public broker::QueueObserver
{
  public:
    typedef broker::SemanticState::ConsumerImpl ConsumerImpl;

    class Factory : public broker::ConsumerFactory {
      public:
        Factory(HaBroker& hb) : haBroker(hb) {}

        HaBroker& getHaBroker() const { return haBroker; }

        boost::shared_ptr<broker::SemanticState::ConsumerImpl> create(
            broker::SemanticState* parent,
            const std::string& name, boost::shared_ptr<broker::Queue> ,
            bool ack, bool acquire, bool exclusive, const std::string& tag,
            const std::string& resumeId, uint64_t resumeTtl,
            const framing::FieldTable& arguments);
      private:
        HaBroker& haBroker;
    };

    // Argument names for consume command.
    static const std::string QPID_REPLICATING_SUBSCRIPTION;
    static const std::string QPID_BROKER_INFO;
    static const std::string QPID_ID_SET;
    // Replicator types: argument values for QPID_REPLICATING_SUBSCRIPTION argument.
    static const std::string QPID_QUEUE_REPLICATOR;

    ReplicatingSubscription(HaBroker& haBroker,
                            broker::SemanticState* parent,
                            const std::string& name, boost::shared_ptr<broker::Queue> ,
                            bool ack, bool acquire, bool exclusive, const std::string& tag,
                            const std::string& resumeId, uint64_t resumeTtl,
                            const framing::FieldTable& arguments);

    ~ReplicatingSubscription();


    // Consumer overrides.
    bool deliver(const broker::QueueCursor& cursor, const broker::Message& msg);
    void cancel();
    void acknowledged(const broker::DeliveryRecord&);
    bool browseAcquired() const { return true; }
    void stopped();

    // Hide the "queue deleted" error for a ReplicatingSubscription when a
    // queue is deleted, this is normal and not an error.
    bool hideDeletedError() { return true; }

    // QueueObserver overrides
    void enqueued(const broker::Message&) {}
    void dequeued(const broker::Message&);
    void acquired(const broker::Message&) {}
    void requeued(const broker::Message&) {}

    /** A ReplicatingSubscription is a passive observer, not counted for auto
     * deletion and immediate message purposes.
     */
    bool isCounted() { return false; }

    /** Initialization that must be done separately from construction
     * because it requires a shared_ptr to this to exist.
     */
    void initialize();

    BrokerInfo getBrokerInfo() const { return info; }

  protected:
    bool doDispatch();

  private:
    LogPrefix2 logPrefix;
    QueuePosition position;
    ReplicationIdSet dequeues;  // Dequeues to be sent in next dequeue event.
    ReplicationIdSet skipEnqueue; // Enqueues to skip: messages already on backup.
    ReplicationIdSet unready;   // Unguarded, replicated and un-acknowledged.
    bool wasStopped;
    bool ready;
    bool cancelled;
    BrokerInfo info;
    boost::shared_ptr<QueueGuard> guard;
    HaBroker& haBroker;
    boost::shared_ptr<Primary> primary;

    bool isGuarded(sys::Mutex::ScopedLock&);
    void dequeued(ReplicationId);
    void sendDequeueEvent(sys::Mutex::ScopedLock&);
    void sendIdEvent(ReplicationId, sys::Mutex::ScopedLock&);
    void sendEvent(const Event&, sys::Mutex::ScopedLock&);
    void checkReady(sys::Mutex::ScopedLock&);
  friend class Factory;
};


}} // namespace qpid::broker

#endif  /*!QPID_BROKER_REPLICATINGSUBSCRIPTION_H*/
