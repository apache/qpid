/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _Queue_
#define _Queue_

#include <vector>
#include <queue>
#include <boost/shared_ptr.hpp>
#include "qpid/framing/amqp_types.h"
#include "qpid/broker/Binding.h"
#include "qpid/broker/ConnectionToken.h"
#include "qpid/broker/Consumer.h"
#include "qpid/broker/Message.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"

namespace qpid {
    namespace broker {
        class MessageStore;

        /**
         * Thrown when exclusive access would be violated.
         */
        struct ExclusiveAccessException{};

        /**
         * The brokers representation of an amqp queue. Messages are
         * delivered to a queue from where they can be dispatched to
         * registered consumers or be stored until dequeued or until one
         * or more consumers registers.
         */
        class Queue{
            const string name;
            const u_int32_t autodelete;
            MessageStore* const store;
            const ConnectionToken* const owner;
            std::vector<Consumer*> consumers;
            std::queue<Binding*> bindings;
            std::queue<Message::shared_ptr> messages;
            bool queueing;
            bool dispatching;
            int next;
            mutable qpid::sys::Monitor lock;
            int64_t lastUsed;
            Consumer* exclusive;
            u_int64_t persistenceId;

            bool startDispatching();
            bool dispatch(Message::shared_ptr& msg);

        public:
            
            typedef boost::shared_ptr<Queue> shared_ptr;

            typedef std::vector<shared_ptr> vector;
	    
            Queue(const string& name, u_int32_t autodelete = 0, 
                  MessageStore* const store = 0, 
                  const ConnectionToken* const owner = 0);
            ~Queue();

            void create();
            void destroy();
            /**
             * Informs the queue of a binding that should be cancelled on
             * destruction of the queue.
             */
            void bound(Binding* b);
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
             * Dispatch any queued messages providing there are
             * consumers for them. Only one thread can be dispatching
             * at any time, but this method (rather than the caller)
             * is responsible for ensuring that.
             */
            void dispatch();
            void consume(Consumer* c, bool exclusive = false);
            void cancel(Consumer* c);
            Message::shared_ptr dequeue();
            u_int32_t purge();
            u_int32_t getMessageCount() const;
            u_int32_t getConsumerCount() const;
            inline const string& getName() const { return name; }
            inline const bool isExclusiveOwner(const ConnectionToken* const o) const { return o == owner; }
            inline bool hasExclusiveConsumer() const { return exclusive; }
            inline u_int64_t getPersistenceId() const { return persistenceId; }
            inline void setPersistenceId(u_int64_t _persistenceId) { persistenceId = _persistenceId; }

            bool canAutoDelete() const;

            void enqueue(TransactionContext* ctxt, Message::shared_ptr& msg, const string * const xid);
            void dequeue(TransactionContext* ctxt, Message::shared_ptr& msg, const string * const xid);
        };
    }
}


#endif
