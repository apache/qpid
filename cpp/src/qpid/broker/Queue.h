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
#include <set>
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

        /**
         * The brokers representation of an amqp queue. Messages are
         * delivered to a queue from where they can be dispatched to
         * registered consumers or be stored until dequeued or until one
         * or more consumers registers.
         */
        class Queue : public PersistableQueue, public management::Manageable {
            typedef std::set<Consumer*> Listeners;
            typedef std::deque<QueuedMessage> Messages;

            const string name;
            const bool autodelete;
            MessageStore* const store;
            const ConnectionToken* owner;
            uint32_t consumerCount;
            bool exclusive;
            Listeners listeners;
            Messages messages;
            mutable qpid::sys::Mutex consumerLock;
            mutable qpid::sys::Mutex messageLock;
            mutable qpid::sys::Mutex ownershipLock;
            mutable uint64_t persistenceId;
            framing::FieldTable settings;
            std::auto_ptr<QueuePolicy> policy;            
            QueueBindings bindings;
            boost::shared_ptr<Exchange> alternateExchange;
            framing::SequenceNumber sequence;
            management::Queue::shared_ptr mgmtObject;

            void pop();
            void push(intrusive_ptr<Message>& msg);
            void setPolicy(std::auto_ptr<QueuePolicy> policy);
            bool seek(QueuedMessage& msg, Consumer& position);
            bool getNextMessage(QueuedMessage& msg, Consumer& c);
            bool consumeNextMessage(QueuedMessage& msg, Consumer& c);
            bool browseNextMessage(QueuedMessage& msg, Consumer& c);
            bool canExcludeUnwanted();

            void notify();
            void removeListener(Consumer&);
            void addListener(Consumer&);

        public:
            virtual void notifyDurableIOComplete();
            typedef boost::shared_ptr<Queue> shared_ptr;

            typedef std::vector<shared_ptr> vector;

            Queue(const string& name, bool autodelete = false, 
                  MessageStore* const store = 0, 
                  const ConnectionToken* const owner = 0,
                  Manageable* parent = 0);
            ~Queue();

            bool dispatch(Consumer&);

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
            void deliver(intrusive_ptr<Message>& msg);
            /**
             * Dispatches the messages immediately to a consumer if
             * one is available or stores it for later if not.
             */
            void process(intrusive_ptr<Message>& msg);
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
            void recover(intrusive_ptr<Message>& msg);

            void consume(Consumer& c, bool exclusive = false);
            void cancel(Consumer& c);

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

            bool enqueue(TransactionContext* ctxt, intrusive_ptr<Message> msg);
            /**
             * dequeue from store (only done once messages is acknowledged)
             */
            bool dequeue(TransactionContext* ctxt, intrusive_ptr<Message> msg);
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
