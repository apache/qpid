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

#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace broker {

/**
 * A null implementation of the MessageStore interface
 */
class NullMessageStore : public MessageStore
{
    std::set<std::string> prepared;
    uint64_t nextPersistenceId;
  public:
    NullMessageStore();

    virtual bool init(const Options* options);
    virtual std::auto_ptr<TransactionContext> begin();
    virtual std::auto_ptr<TPCTransactionContext> begin(const std::string& xid);
    virtual void prepare(TPCTransactionContext& txn);
    virtual void commit(TransactionContext& txn);
    virtual void abort(TransactionContext& txn);
    virtual void collectPreparedXids(std::set<std::string>& xids);

    virtual void create(PersistableQueue& queue, const framing::FieldTable& args);
    virtual void destroy(PersistableQueue& queue);
    virtual void create(const PersistableExchange& exchange, const framing::FieldTable& args);
    virtual void destroy(const PersistableExchange& exchange);

    virtual void bind(const PersistableExchange& exchange, const PersistableQueue& queue, 
                      const std::string& key, const framing::FieldTable& args);
    virtual void unbind(const PersistableExchange& exchange, const PersistableQueue& queue, 
                        const std::string& key, const framing::FieldTable& args);
    virtual void create(const PersistableConfig& config);
    virtual void destroy(const PersistableConfig& config);
    virtual void recover(RecoveryManager& queues);
    virtual void stage(const boost::intrusive_ptr<PersistableMessage>& msg);
    virtual void destroy(PersistableMessage& msg);
    virtual void appendContent(const boost::intrusive_ptr<const PersistableMessage>& msg,
                               const std::string& data);
    virtual void loadContent(const qpid::broker::PersistableQueue& queue,
                             const boost::intrusive_ptr<const PersistableMessage>& msg, std::string& data,
                             uint64_t offset, uint32_t length);
    virtual void enqueue(TransactionContext* ctxt,
                         const boost::intrusive_ptr<PersistableMessage>& msg,
                         const PersistableQueue& queue);
    virtual void dequeue(TransactionContext* ctxt,
                         const boost::intrusive_ptr<PersistableMessage>& msg,
                         const PersistableQueue& queue);
    virtual uint32_t outstandingQueueAIO(const PersistableQueue& queue);
    virtual void flush(const qpid::broker::PersistableQueue& queue);
    ~NullMessageStore(){}

    virtual bool isNull() const;
    static bool isNullStore(const MessageStore*);
};

}
}


#endif
