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

#include "qpid/broker/SemanticState.h"
#include "qpid/broker/QueueObserver.h"

namespace qpid {

namespace broker {
class Message;
class Queue;
class QueuedMessage;
class OwnershipToken;
}

namespace ha {

/**
 * Subscriber to a remote queue that replicates to a local queue.
 */
class ReplicatingSubscription : public broker::SemanticState::ConsumerImpl,
                                public broker::QueueObserver
{
  public:
    ReplicatingSubscription(broker::SemanticState* parent,
                            const std::string& name, boost::shared_ptr<broker::Queue> ,
                            bool ack, bool acquire, bool exclusive, const std::string& tag,
                            const std::string& resumeId, uint64_t resumeTtl,
                            const framing::FieldTable& arguments);
    ~ReplicatingSubscription();

    void init();
    void cancel();
    bool deliver(broker::QueuedMessage& msg);
    void enqueued(const broker::QueuedMessage&);
    void dequeued(const broker::QueuedMessage&);
    void acquired(const broker::QueuedMessage&) {}
    void requeued(const broker::QueuedMessage&) {}

    bool isDelayedCompletion() const { return true; }

  protected:
    bool doDispatch();
  private:
    boost::shared_ptr<broker::Queue> events;
    boost::shared_ptr<broker::Consumer> consumer;
    qpid::framing::SequenceSet range;

    void generateDequeueEvent();
    class DelegatingConsumer : public Consumer
    {
      public:
        DelegatingConsumer(ReplicatingSubscription&);
        ~DelegatingConsumer();
        bool deliver(broker::QueuedMessage& msg);
        void notify();
        bool filter(boost::intrusive_ptr<Message>);
        bool accept(boost::intrusive_ptr<Message>);
        void cancel();
        OwnershipToken* getSession();
      private:
        ReplicatingSubscription& delegate;
    };
};


}} // namespace qpid::broker

#endif  /*!QPID_BROKER_REPLICATINGSUBSCRIPTION_H*/
