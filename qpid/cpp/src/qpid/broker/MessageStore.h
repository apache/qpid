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
#ifndef _MessageStore_
#define _MessageStore_

#include <qpid/broker/Message.h>
#include <qpid/broker/Queue.h>
#include <qpid/broker/TransactionalStore.h>

namespace qpid {
    namespace broker {
        class Queue;
        class QueueRegistry;

        /**
         * An abstraction of the persistent storage for messages.
         */
        class MessageStore : public TransactionalStore{
        public:
            /**
             * Record the existance of a durable queue
             */
            virtual void create(const Queue& queue) = 0;
            /**
             * Destroy a durable queue
             */
            virtual void destroy(const Queue& queue) = 0;

            /**
             * Request recovery of queue and message state from store
             */
            virtual void recover(QueueRegistry& queues) = 0;

            /**
             * Enqueues a message, storing the message if it has not
             * been previously stored and recording that the given
             * message is on the given queue.
             * 
             * @param msg the message to enqueue
             * @param queue the name of the queue onto which it is to be enqueued
             * @param xid (a pointer to) an identifier of the
             * distributed transaction in which the operation takes
             * place or null for 'local' transactions
             */
            virtual void enqueue(TransactionContext* ctxt, Message::shared_ptr& msg, const Queue& queue, const string * const xid) = 0;
            /**
             * Dequeues a message, recording that the given message is
             * no longer on the given queue and deleting the message
             * if it is no longer on any other queue.
             * 
             * @param msg the message to dequeue
             * @param queue the name of th queue from which it is to be dequeued
             * @param xid (a pointer to) an identifier of the
             * distributed transaction in which the operation takes
             * place or null for 'local' transactions
             */
            virtual void dequeue(TransactionContext* ctxt, Message::shared_ptr& msg, const Queue& queue, const string * const xid) = 0;
            /**
             * Treat all enqueue/dequeues where this xid was specified as being committed.
             */
            virtual void committed(const string * const xid) = 0;
            /**
             * Treat all enqueue/dequeues where this xid was specified as being aborted.
             */
            virtual void aborted(const string * const xid) = 0;

            virtual ~MessageStore(){}
        };
    }
}


#endif
