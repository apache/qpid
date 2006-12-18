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

#include <BrokerMessage.h>
#include <MessageStore.h>
#include <BrokerQueue.h>

namespace qpid {
    namespace broker {

        /**
         * A null implementation of the MessageStore interface
         */
        class NullMessageStore : public MessageStore{
            const bool warn;
        public:
            NullMessageStore(bool warn = true);
            virtual void create(const Queue& queue, const qpid::framing::FieldTable& settings);
            virtual void destroy(const Queue& queue);
            virtual void recover(RecoveryManager& queues, const MessageStoreSettings* const settings = 0);
            virtual void stage(Message* const msg);
            virtual void destroy(Message* const msg);
            virtual void appendContent(Message* const msg, const std::string& data);
            virtual void loadContent(Message* const msg, std::string& data, u_int64_t offset, u_int32_t length);
            virtual void enqueue(TransactionContext* ctxt, Message* const msg, const Queue& queue, const string * const xid);
            virtual void dequeue(TransactionContext* ctxt, Message* const msg, const Queue& queue, const string * const xid);
            virtual void prepared(const std::string * const xid);
            virtual void committed(const std::string * const xid);
            virtual void aborted(const std::string * const xid);
            virtual std::auto_ptr<TransactionContext> begin();
            virtual void commit(TransactionContext* ctxt);
            virtual void abort(TransactionContext* ctxt);
            ~NullMessageStore(){}
        };
    }
}


#endif
