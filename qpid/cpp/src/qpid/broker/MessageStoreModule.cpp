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

#include "MessageStoreModule.h"
#include <iostream>

using namespace qpid::broker;

MessageStoreModule::MessageStoreModule(const std::string& name) : store(name)
{
}

bool MessageStoreModule::init(const std::string& dir, const bool async, const bool force)
{
	return store->init(dir, async, force);
}

void MessageStoreModule::create(PersistableQueue& queue)
{
    store->create(queue);
}

void MessageStoreModule::destroy(PersistableQueue& queue)
{
    store->destroy(queue);
}

void MessageStoreModule::create(const PersistableExchange& exchange)
{
    store->create(exchange);
}

void MessageStoreModule::destroy(const PersistableExchange& exchange)
{
    store->destroy(exchange);
}

void MessageStoreModule::bind(const PersistableExchange& e, const PersistableQueue& q, 
                              const std::string& k, const framing::FieldTable& a)
{
    store->bind(e, q, k, a);
}

void MessageStoreModule::unbind(const PersistableExchange& e, const PersistableQueue& q, 
                                const std::string& k, const framing::FieldTable& a)
{
    store->unbind(e, q, k, a);
}

void MessageStoreModule::recover(RecoveryManager& registry)
{
    store->recover(registry);
}

void MessageStoreModule::stage( intrusive_ptr<PersistableMessage>& msg)
{
    store->stage(msg);
}

void MessageStoreModule::destroy(intrusive_ptr<PersistableMessage>& msg)
{
    store->destroy(msg);
}

void MessageStoreModule::appendContent(intrusive_ptr<const PersistableMessage>& msg, const std::string& data)
{
    store->appendContent(msg, data);
}

void MessageStoreModule::loadContent(const qpid::broker::PersistableQueue& queue, 
     intrusive_ptr<const PersistableMessage>& msg, string& data, uint64_t offset, uint32_t length)
{
    store->loadContent(queue, msg, data, offset, length);
}

void MessageStoreModule::enqueue(TransactionContext* ctxt, intrusive_ptr<PersistableMessage>& msg, const PersistableQueue& queue)
{
    store->enqueue(ctxt, msg, queue);
}

void MessageStoreModule::dequeue(TransactionContext* ctxt, intrusive_ptr<PersistableMessage>& msg, const PersistableQueue& queue)
{
    store->dequeue(ctxt, msg, queue);
}

void MessageStoreModule::flush(const qpid::broker::PersistableQueue& queue)
{
    store->flush(queue);
}

u_int32_t MessageStoreModule::outstandingQueueAIO(const PersistableQueue& queue)
{
    return store->outstandingQueueAIO(queue);
}

std::auto_ptr<TransactionContext> MessageStoreModule::begin()
{
    return store->begin();
}

std::auto_ptr<TPCTransactionContext> MessageStoreModule::begin(const std::string& xid)
{
    return store->begin(xid);
}

void MessageStoreModule::prepare(TPCTransactionContext& txn)
{
    store->prepare(txn);
}

void MessageStoreModule::commit(TransactionContext& ctxt)
{
    store->commit(ctxt);
}

void MessageStoreModule::abort(TransactionContext& ctxt)
{
    store->abort(ctxt);
}

void MessageStoreModule::collectPreparedXids(std::set<std::string>& xids)
{
    store->collectPreparedXids(xids);
}
