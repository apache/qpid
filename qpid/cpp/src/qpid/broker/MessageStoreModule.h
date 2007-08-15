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
#ifndef _MessageStoreModule_
#define _MessageStoreModule_

#include "BrokerMessage.h"
#include "MessageStore.h"
#include "BrokerQueue.h"
#include "RecoveryManager.h"
#include "qpid/sys/Module.h"

namespace qpid {
namespace broker {

/**
 * A null implementation of the MessageStore interface
 */
class MessageStoreModule : public MessageStore
{
    qpid::sys::Module<MessageStore> store;
public:
    MessageStoreModule(const std::string& name);

    std::auto_ptr<TransactionContext> begin();
    std::auto_ptr<TPCTransactionContext> begin(const std::string& xid);
    void prepare(TPCTransactionContext& txn);
    void commit(TransactionContext& txn);
    void abort(TransactionContext& txn);
    void collectPreparedXids(std::set<std::string>& xids);

    void create(const PersistableQueue& queue);
    void destroy(const PersistableQueue& queue);
    void create(const PersistableExchange& exchange);
    void destroy(const PersistableExchange& exchange);
    void bind(const PersistableExchange& exchange, const PersistableQueue& queue, 
              const std::string& key, const framing::FieldTable& args);
    void unbind(const PersistableExchange& exchange, const PersistableQueue& queue, 
                const std::string& key, const framing::FieldTable& args);
    void recover(RecoveryManager& queues);
    void stage(PersistableMessage& msg);
    void destroy(PersistableMessage& msg);
    void appendContent(PersistableMessage& msg, const std::string& data);
    void loadContent(PersistableMessage& msg, std::string& data, uint64_t offset, uint32_t length);

    void enqueue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue);
    void dequeue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue);
    u_int32_t outstandingQueueAIO(const PersistableQueue& queue);

    ~MessageStoreModule(){}
};

}
}


#endif
