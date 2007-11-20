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

// This transfer protects against the unloading of the store lib prior to the handling of the exception
#define TRANSFER_EXCEPTION(fn) try { fn; } catch (std::exception& e) { throw Exception(e.what()); }

using namespace qpid::broker;

MessageStoreModule::MessageStoreModule(const std::string& name) : store(name)
{
}

bool MessageStoreModule::init(const std::string& dir, const bool async, const bool force)
{
	TRANSFER_EXCEPTION(return store->init(dir, async, force));
}

void MessageStoreModule::create(PersistableQueue& queue)
{
    TRANSFER_EXCEPTION(store->create(queue));
}

void MessageStoreModule::destroy(PersistableQueue& queue)
{
    TRANSFER_EXCEPTION(store->destroy(queue));
}

void MessageStoreModule::create(const PersistableExchange& exchange)
{
    TRANSFER_EXCEPTION(store->create(exchange));
}

void MessageStoreModule::destroy(const PersistableExchange& exchange)
{
    TRANSFER_EXCEPTION(store->destroy(exchange));
}

void MessageStoreModule::bind(const PersistableExchange& e, const PersistableQueue& q, 
                              const std::string& k, const framing::FieldTable& a)
{
    TRANSFER_EXCEPTION(store->bind(e, q, k, a));
}

void MessageStoreModule::unbind(const PersistableExchange& e, const PersistableQueue& q, 
                                const std::string& k, const framing::FieldTable& a)
{
    TRANSFER_EXCEPTION(store->unbind(e, q, k, a));
}

void MessageStoreModule::recover(RecoveryManager& registry)
{
    TRANSFER_EXCEPTION(store->recover(registry));
}

void MessageStoreModule::stage( PersistableMessage& msg)
{
    TRANSFER_EXCEPTION(store->stage(msg));
}

void MessageStoreModule::destroy(PersistableMessage& msg)
{
    TRANSFER_EXCEPTION(store->destroy(msg));
}

void MessageStoreModule::appendContent(const PersistableMessage& msg, const std::string& data)
{
    TRANSFER_EXCEPTION(store->appendContent(msg, data));
}

void MessageStoreModule::loadContent(const qpid::broker::PersistableQueue& queue, 
     const PersistableMessage& msg, string& data, uint64_t offset, uint32_t length)
{
    TRANSFER_EXCEPTION(store->loadContent(queue, msg, data, offset, length));
}

void MessageStoreModule::enqueue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue)
{
    TRANSFER_EXCEPTION(store->enqueue(ctxt, msg, queue));
}

void MessageStoreModule::dequeue(TransactionContext* ctxt, PersistableMessage& msg, const PersistableQueue& queue)
{
    TRANSFER_EXCEPTION(store->dequeue(ctxt, msg, queue));
}

void MessageStoreModule::flush(const qpid::broker::PersistableQueue& queue)
{
    TRANSFER_EXCEPTION(store->flush(queue));
}

u_int32_t MessageStoreModule::outstandingQueueAIO(const PersistableQueue& queue)
{
    TRANSFER_EXCEPTION(return store->outstandingQueueAIO(queue));
}

std::auto_ptr<TransactionContext> MessageStoreModule::begin()
{
    TRANSFER_EXCEPTION(return store->begin());
}

std::auto_ptr<TPCTransactionContext> MessageStoreModule::begin(const std::string& xid)
{
    TRANSFER_EXCEPTION(return store->begin(xid));
}

void MessageStoreModule::prepare(TPCTransactionContext& txn)
{
    TRANSFER_EXCEPTION(store->prepare(txn));
}

void MessageStoreModule::commit(TransactionContext& ctxt)
{
    TRANSFER_EXCEPTION(store->commit(ctxt));
}

void MessageStoreModule::abort(TransactionContext& ctxt)
{
    TRANSFER_EXCEPTION(store->abort(ctxt));
}

void MessageStoreModule::collectPreparedXids(std::set<std::string>& xids)
{
    TRANSFER_EXCEPTION(store->collectPreparedXids(xids));
}
