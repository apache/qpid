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
#ifndef _MessageStore_
#define _MessageStore_

#include <BrokerMessage.h>
#include <FieldTable.h>
#include <RecoveryManager.h>
#include <TransactionalStore.h>

namespace qpid {
    namespace broker {
        struct MessageStoreSettings
        {
            /**
             * Messages whose content length is larger than this value
             * will be staged (i.e. will have thier data written to
             * disk as it arrives) and will load their data lazily. On
             * recovery therefore, only the headers should be loaded.
             */
            u_int64_t stagingThreshold;
        };
        /**
         * An abstraction of the persistent storage for messages. (In
         * all methods, any pointers/references to queues or messages
         * are valid only for the duration of the call).
         */
        class MessageStore : public TransactionalStore{
        public:
            /**
             * Record the existance of a durable queue
             */
            virtual void create(const Queue& queue, const qpid::framing::FieldTable& settings) = 0;
            /**
             * Destroy a durable queue
             */
            virtual void destroy(const Queue& queue) = 0;

            /**
             * Request recovery of queue and message state from store
             */
            virtual void recover(RecoveryManager& queues, const MessageStoreSettings* const settings = 0) = 0;

            /**
             * Stores a messages before it has been enqueued
             * (enqueueing automatically stores the message so this is
             * only required if storage is required prior to that
             * point). If the message has not yet been stored it will
             * store the headers as well as any content passed in. A
             * persistence id will be set on the message which can be
             * used to load the content or to append to it.
             */
            virtual void stage(Message* const msg) = 0;
            
            /**
             * Destroys a previously staged message. This only needs
             * to be called if the message is never enqueued. (Once
             * enqueued, deletion will be automatic when the message
             * is dequeued from all queues it was enqueued onto).
             */
            virtual void destroy(Message* const msg) = 0;

            /**
             * Appends content to a previously staged message
             */
            virtual void appendContent(Message* const msg, const std::string& data) = 0;

            /**
             * Loads (a section) of content data for the specified
             * message (previously stored through a call to stage or
             * enqueue) into data. The offset refers to the content
             * only (i.e. an offset of 0 implies that the start of the
             * content should be loaded, not the headers or related
             * meta-data).
             */
            virtual void loadContent(Message* const msg, std::string& data, u_int64_t offset, u_int32_t length) = 0;

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
            virtual void enqueue(TransactionContext* ctxt, Message* const msg, const Queue& queue, const std::string * const xid) = 0;
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
            virtual void dequeue(TransactionContext* ctxt, Message* const msg, const Queue& queue, const std::string * const xid) = 0;

            /**
             * Treat all enqueue/dequeues where this xid was specified as being prepared.
             */
            virtual void prepared(const std::string * const xid) = 0;
            /**
             * Treat all enqueue/dequeues where this xid was specified as being committed.
             */
            virtual void committed(const std::string * const xid) = 0;
            /**
             * Treat all enqueue/dequeues where this xid was specified as being aborted.
             */
            virtual void aborted(const std::string * const xid) = 0;

            virtual ~MessageStore(){}
        };
    }
}


#endif
