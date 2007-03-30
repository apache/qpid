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
class NullMessageStore : public MessageStore
{
    const bool warn;
public:
    NullMessageStore(bool warn = false);

    virtual std::auto_ptr<TransactionContext> begin();
    virtual std::auto_ptr<TPCTransactionContext> begin(const std::string& xid);
    virtual void prepare(TPCTransactionContext& txn);
    virtual void commit(TransactionContext& txn);
    virtual void abort(TransactionContext& txn);

    virtual void create(const PersistableQueue& queue);
    virtual void destroy(const PersistableQueue& queue);
    virtual void create(const PersistableExchange& exchange);
    virtual void destroy(const PersistableExchange& exchange);
    virtual void recover(RecoveryManager& queues);
    virtual void stage(PersistableMessage& msg);
    virtual void destroy(PersistableMessage& msg);
    virtual void appendContent(PersistableMessage& msg, const std::string& data);
    virtual void loadContent(PersistableMessage& msg, std::string& data, uint64_t offset, uint32_t length);
    virtual void enqueue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue);
    virtual void dequeue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue);
    ~NullMessageStore(){}
};

}
}


#endif
