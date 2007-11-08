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

#include <set>
#include "MessageStore.h"
#include "Queue.h"

namespace qpid {
namespace broker {

/**
 * A null implementation of the MessageStore interface
 */
class NullMessageStore : public MessageStore
{
    std::set<std::string> prepared;
    const bool warn;
public:
    NullMessageStore(bool warn = false);

    virtual bool init(const std::string& dir, const bool async, const bool force);
    virtual std::auto_ptr<TransactionContext> begin();
    virtual std::auto_ptr<TPCTransactionContext> begin(const std::string& xid);
    virtual void prepare(TPCTransactionContext& txn);
    virtual void commit(TransactionContext& txn);
    virtual void abort(TransactionContext& txn);
    virtual void collectPreparedXids(std::set<std::string>& xids);

    virtual void create(PersistableQueue& queue);
    virtual void destroy(PersistableQueue& queue);
    virtual void create(const PersistableExchange& exchange);
    virtual void destroy(const PersistableExchange& exchange);

    virtual void bind(const PersistableExchange& exchange, const PersistableQueue& queue, 
                      const std::string& key, const framing::FieldTable& args);
    virtual void unbind(const PersistableExchange& exchange, const PersistableQueue& queue, 
                        const std::string& key, const framing::FieldTable& args);
    virtual void recover(RecoveryManager& queues);
    virtual void stage(PersistableMessage& msg);
    virtual void destroy(PersistableMessage& msg);
    virtual void appendContent(const PersistableMessage& msg, const std::string& data);
    virtual void loadContent(const qpid::broker::PersistableQueue& queue, 
	                   const PersistableMessage& msg, std::string& data, uint64_t offset, uint32_t length);
    virtual void enqueue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue);
    virtual void dequeue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue);
    virtual u_int32_t outstandingQueueAIO(const PersistableQueue& queue);
    virtual void flush(const qpid::broker::PersistableQueue& queue);
    ~NullMessageStore(){}
};

}
}


#endif
