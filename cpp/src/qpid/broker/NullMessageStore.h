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
#ifndef _NullMessageStore_
#define _NullMessageStore_

#include <qpid/broker/Message.h>
#include <qpid/broker/MessageStore.h>
#include <qpid/broker/Queue.h>

namespace qpid {
    namespace broker {

        /**
         * A null implementation of the MessageStore interface
         */
        class NullMessageStore : public MessageStore{
            const bool warn;
        public:
            NullMessageStore(bool warn = true);
            void virtual create(const Queue& queue);
            void virtual destroy(const Queue& queue);
            void virtual recover(RecoveryManager& queues);
            void virtual stage(Message::shared_ptr& msg);
            void virtual destroy(Message::shared_ptr& msg);
            void virtual enqueue(TransactionContext* ctxt, Message::shared_ptr& msg, const Queue& queue, const string * const xid);
            void virtual dequeue(TransactionContext* ctxt, Message::shared_ptr& msg, const Queue& queue, const string * const xid);
            void virtual committed(const string * const xid);
            void virtual aborted(const string * const xid);
            virtual std::auto_ptr<TransactionContext> begin();
            void virtual commit(TransactionContext* ctxt);
            void virtual abort(TransactionContext* ctxt);
            ~NullMessageStore(){}
        };
    }
}


#endif
