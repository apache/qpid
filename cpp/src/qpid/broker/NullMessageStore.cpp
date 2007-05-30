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

#include "NullMessageStore.h"
#include "RecoveryManager.h"
#include "qpid/log/Statement.h"

#include <iostream>

using namespace qpid::broker;

NullMessageStore::NullMessageStore(bool _warn) : warn(_warn){}

void NullMessageStore::create(const PersistableQueue& queue)
{
    QPID_LOG(warning, "Can't create durable queue '" << queue.getName() << "'. Persistence not enabled.");
}

void NullMessageStore::destroy(const PersistableQueue& queue)
{
    QPID_LOG(warning, "Can't destroy durable queue '" << queue.getName() << "'. Persistence not enabled.");
}

void NullMessageStore::create(const PersistableExchange& exchange)
{
    QPID_LOG(warning, "Can't create durable exchange '"
             << exchange.getName() << "'. Persistence not enabled.");
}

void NullMessageStore::destroy(const PersistableExchange& )
{}

void NullMessageStore::bind(const PersistableExchange&, const PersistableQueue&, const std::string&, const framing::FieldTable&){}

void NullMessageStore::unbind(const PersistableExchange&, const PersistableQueue&, const std::string&, const framing::FieldTable&){}

void NullMessageStore::recover(RecoveryManager&)
{
    QPID_LOG(warning, "Persistence not enabled, no recovery of queues or messages.");
}

void NullMessageStore::stage(PersistableMessage&)
{
    QPID_LOG(warning, "Can't stage message. Persistence not enabled.");
}

void NullMessageStore::destroy(PersistableMessage&)
{
    QPID_LOG(warning, "No need to destroy staged message. Persistence not enabled.");
}

void NullMessageStore::appendContent(PersistableMessage&, const string&)
{
    QPID_LOG(warning, "Can't load content. Persistence not enabled.");
}

void NullMessageStore::loadContent(PersistableMessage&, string&, uint64_t, uint32_t)
{
    QPID_LOG(warning, "WARNING: Can't load content. Persistence not enabled.");
}

void NullMessageStore::enqueue(TransactionContext*, PersistableMessage&, const PersistableQueue& queue)
{
    QPID_LOG(warning, "Can't enqueue message onto '" << queue.getName() << "'. Persistence not enabled.");
}

void NullMessageStore::dequeue(TransactionContext*, PersistableMessage&, const PersistableQueue& queue)
{
    QPID_LOG(warning, "Can't dequeue message from '" << queue.getName() << "'. Persistence not enabled.");
}

std::auto_ptr<TransactionContext> NullMessageStore::begin()
{
    return std::auto_ptr<TransactionContext>();
}

std::auto_ptr<TPCTransactionContext> NullMessageStore::begin(const std::string&)
{
    return std::auto_ptr<TPCTransactionContext>();
}

void NullMessageStore::prepare(TPCTransactionContext&)
{
}

void NullMessageStore::commit(TransactionContext&)
{
}

void NullMessageStore::abort(TransactionContext&)
{
}

void NullMessageStore::collectPreparedXids(std::set<string>&)
{

}
