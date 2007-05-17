#ifndef _broker_BrokerQueue_h
#define _broker_BrokerQueue_h

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
#include <queue>
#include <boost/shared_ptr.hpp>
#include "qpid/framing/amqp_types.h"
#include "ConnectionToken.h"
#include "Consumer.h"
#include "BrokerMessage.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Monitor.h"
#include "PersistableQueue.h"
#include "QueuePolicy.h"

// TODO aconway 2007-02-06: Use auto_ptr and boost::ptr_vector to
// enforce ownership of Consumers.

namespace qpid {
    namespace broker {
        class MessageStore;
        class QueueRegistry;

        /**
         * Thrown when exclusive access would be violated.
         */
        using std::string;

        /**
         * The brokers representation of an amqp queue. Messages are
         * delivered to a queue from where they can be dispatched to
         * registered consumers or be stored until dequeued or until one
         * or more consumers registers.
         */
        class Queue : public PersistableQueue{
            typedef std::vector<Consumer*> Consumers;
            typedef std::queue<Message::shared_ptr> Messages;
            
            const string name;
            const uint32_t autodelete;
            MessageStore* const store;
            const ConnectionToken* const owner;
            Consumers consumers;
            Messages messages;
            bool queueing;
            bool dispatching;
            int next;
            mutable qpid::sys::Mutex lock;
            int64_t lastUsed;
            Consumer* exclusive;
            mutable uint64_t persistenceId;
            framing::FieldTable settings;
            std::auto_ptr<QueuePolicy> policy;            

            void pop();
            void push(Message::shared_ptr& msg);
            bool startDispatching();
            bool dispatch(Message::shared_ptr& msg);
            void setPolicy(std::auto_ptr<QueuePolicy> policy);

        public:
            
            typedef boost::shared_ptr<Queue> shared_ptr;

            typedef std::vector<shared_ptr> vector;
	    
            Queue(const string& name, uint32_t autodelete = 0, 
                  MessageStore* const store = 0, 
                  const ConnectionToken* const owner = 0);
            ~Queue();

            void create(const qpid::framing::FieldTable& settings);
            void configure(const qpid::framing::FieldTable& settings);
            void destroy();
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
             * Used during recovery to add stored messages back to the queue
             */
            void recover(Message::shared_ptr& msg);
            /**
             * Dispatch any queued messages providing there are
             * consumers for them. Only one thread can be dispatching
             * at any time, but this method (rather than the caller)
             * is responsible for ensuring that.
             */
            void dispatch();
            void consume(Consumer* c, bool exclusive = false);
            void cancel(Consumer* c);
            uint32_t purge();
            uint32_t getMessageCount() const;
            uint32_t getConsumerCount() const;
            inline const string& getName() const { return name; }
            inline const bool isExclusiveOwner(const ConnectionToken* const o) const { return o == owner; }
            inline bool hasExclusiveConsumer() const { return exclusive; }
            inline bool isDurable() const { return store != 0; }

            bool canAutoDelete() const;

            void enqueue(TransactionContext* ctxt, Message::shared_ptr& msg);
            /**
             * dequeue from store (only done once messages is acknowledged)
             */
            void dequeue(TransactionContext* ctxt, Message::shared_ptr& msg);
            /**
             * dequeues from memory only
             */
            Message::shared_ptr dequeue();

            const QueuePolicy* const getPolicy();

            //PersistableQueue support:
            uint64_t getPersistenceId() const;
            void setPersistenceId(uint64_t persistenceId) const;
            void encode(framing::Buffer& buffer) const;
            uint32_t encodedSize() const;

            static Queue::shared_ptr decode(QueueRegistry& queues, framing::Buffer& buffer);
        };
    }
}


#endif  /*!_broker_BrokerQueue_h*/
