#ifndef _broker_Queue_h
#define _broker_Queue_h

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
#include <vector>
#include <memory>
#include <deque>
#include <boost/shared_ptr.hpp>
#include "qpid/framing/amqp_types.h"
#include "ConnectionToken.h"
#include "Consumer.h"
#include "Message.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Serializer.h"
#include "qpid/sys/Monitor.h"
#include "PersistableQueue.h"
#include "QueuePolicy.h"
#include "QueueBindings.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/Queue.h"

namespace qpid {
    namespace broker {
        class Broker;
        class MessageStore;
        class QueueRegistry;
        class TransactionContext;
        class Exchange;

        using std::string;

        struct DispatchCompletion 
        {
            virtual ~DispatchCompletion() {}
            virtual void completed() = 0;
        };

        /**
         * The brokers representation of an amqp queue. Messages are
         * delivered to a queue from where they can be dispatched to
         * registered consumers or be stored until dequeued or until one
         * or more consumers registers.
         */
        class Queue : public PersistableQueue, public management::Manageable {
            typedef std::vector<Consumer::ptr> Consumers;
            typedef std::deque<QueuedMessage> Messages;
            
            struct DispatchFunctor 
            {
                Queue& queue;
                Consumer::ptr consumer;
                DispatchCompletion* sync;

                DispatchFunctor(Queue& q, DispatchCompletion* s = 0) : queue(q), sync(s) {}
                DispatchFunctor(Queue& q, Consumer::ptr c, DispatchCompletion* s = 0) : queue(q), consumer(c), sync(s) {}
                void operator()();
            };

			bool dispatching;                
            const string name;
            const bool autodelete;
            MessageStore* const store;
            const ConnectionToken* owner;
            Consumers acquirers;
            Consumers browsers;
            Messages messages;
            int next;
            mutable qpid::sys::RWlock consumerLock;
            mutable qpid::sys::Mutex messageLock;
            mutable qpid::sys::Mutex ownershipLock;
            Consumer::ptr exclusive;
            mutable uint64_t persistenceId;
            framing::FieldTable settings;
            std::auto_ptr<QueuePolicy> policy;            
            QueueBindings bindings;
            boost::shared_ptr<Exchange> alternateExchange;
            qpid::sys::Serializer<DispatchFunctor> serializer;
            DispatchFunctor dispatchCallback;
            framing::SequenceNumber sequence;
            management::Queue::shared_ptr mgmtObject;

            void pop();
            void push(Message::shared_ptr& msg);
            bool dispatch(QueuedMessage& msg);
            void setPolicy(std::auto_ptr<QueuePolicy> policy);
            /**
             * only called by serilizer
             */
            void dispatch();
            void cancel(Consumer::ptr c, Consumers& set);
            void serviceAllBrowsers();
            void serviceBrowser(Consumer::ptr c);
            Consumer::ptr allocate();
            bool seek(QueuedMessage& msg, const framing::SequenceNumber& position);
            uint32_t getAcquirerCount() const;
            bool getNextMessage(QueuedMessage& msg);
            bool exclude(Message::shared_ptr msg);
 

        public:
            virtual void notifyDurableIOComplete();
            typedef boost::shared_ptr<Queue> shared_ptr;

            typedef std::vector<shared_ptr> vector;

            Queue(const string& name, bool autodelete = false, 
                  MessageStore* const store = 0, 
                  const ConnectionToken* const owner = 0,
                  Manageable* parent = 0);
            ~Queue();

            void create(const qpid::framing::FieldTable& settings);
            void configure(const qpid::framing::FieldTable& settings);
            void destroy();
            void bound(const string& exchange, const string& key, const qpid::framing::FieldTable& args);
            void unbind(ExchangeRegistry& exchanges, Queue::shared_ptr shared_ref);

            bool acquire(const QueuedMessage& msg);

            /**
             * Delivers a message to the queue. Will record it as
             * enqueued if persistent then process it.
             */
            void deliver(Message::shared_ptr& msg);
            /**
             * Dispatches the messages immediately to a consumer if
             * one is available or stores it for later if not.
             */
            void process(Message::shared_ptr& msg);
            /**
             * Returns a message to the in-memory queue (due to lack
             * of acknowledegement from a receiver). If a consumer is
             * available it will be dispatched immediately, else it
             * will be returned to the front of the queue.
             */
            void requeue(const QueuedMessage& msg);
            /**
             * Used during recovery to add stored messages back to the queue
             */
            void recover(Message::shared_ptr& msg);
            /**
             * Request dispatch any queued messages providing there are
             * consumers for them. Only one thread can be dispatching
             * at any time, so this call schedules the despatch based on
             * the serilizer policy.
             */
            void requestDispatch(Consumer::ptr c = Consumer::ptr());
            void flush(DispatchCompletion& callback);
            void consume(Consumer::ptr c, bool exclusive = false);
            void cancel(Consumer::ptr c);
            uint32_t purge();
            uint32_t getMessageCount() const;
            uint32_t getConsumerCount() const;
            inline const string& getName() const { return name; }
            bool isExclusiveOwner(const ConnectionToken* const o) const;
            void releaseExclusiveOwnership();
            bool setExclusiveOwner(const ConnectionToken* const o);
            bool hasExclusiveConsumer() const;
            bool hasExclusiveOwner() const;
            inline bool isDurable() const { return store != 0; }
            inline const framing::FieldTable& getSettings() const { return settings; }
            inline bool isAutoDelete() const { return autodelete; }
            bool canAutoDelete() const;

            bool enqueue(TransactionContext* ctxt, Message::shared_ptr msg);
            /**
             * dequeue from store (only done once messages is acknowledged)
             */
            bool dequeue(TransactionContext* ctxt, Message::shared_ptr msg);
            /**
             * dequeues from memory only
             */
            QueuedMessage dequeue();

            const QueuePolicy* const getPolicy();

            void setAlternateExchange(boost::shared_ptr<Exchange> exchange);
            boost::shared_ptr<Exchange> getAlternateExchange();

            //PersistableQueue support:
            uint64_t getPersistenceId() const;
            void setPersistenceId(uint64_t persistenceId) const;
            void encode(framing::Buffer& buffer) const;
            uint32_t encodedSize() const;

            static Queue::shared_ptr decode(QueueRegistry& queues, framing::Buffer& buffer);
            static void tryAutoDelete(Broker& broker, Queue::shared_ptr);

            // Manageable entry points
            management::ManagementObject::shared_ptr GetManagementObject (void) const;
            management::Manageable::status_t
                ManagementMethod (uint32_t methodId, management::Args& args);
        };
    }
}


#endif  /*!_broker_Queue_h*/
