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

#include <iostream>

using namespace qpid::broker;

NullMessageStore::NullMessageStore(bool _warn) : warn(_warn){}

void NullMessageStore::create(const PersistableQueue& queue)
{
    if (warn) std::cout << "WARNING: Can't create durable queue '" << queue.getName() << "'. Persistence not enabled." << std::endl;
}

void NullMessageStore::destroy(const PersistableQueue& queue)
{
    if (warn) std::cout << "WARNING: Can't destroy durable queue '" << queue.getName() << "'. Persistence not enabled." << std::endl;
}

void NullMessageStore::create(const PersistableExchange&)
{
}

void NullMessageStore::destroy(const PersistableExchange&)
{
}

void NullMessageStore::recover(RecoveryManager&)
{
    if (warn) std::cout << "WARNING: Persistence not enabled, no recovery of queues or messages." << std::endl;
}

void NullMessageStore::stage(PersistableMessage&)
{
    if (warn) std::cout << "WARNING: Can't stage message. Persistence not enabled." << std::endl;
}

void NullMessageStore::destroy(PersistableMessage&)
{
    if (warn) std::cout << "WARNING: No need to destroy staged message. Persistence not enabled." << std::endl;
}

void NullMessageStore::appendContent(PersistableMessage&, const string&)
{
    if (warn) std::cout << "WARNING: Can't append content. Persistence not enabled." << std::endl;
}

void NullMessageStore::loadContent(PersistableMessage&, string&, uint64_t, uint32_t)
{
    if (warn) std::cout << "WARNING: Can't load content. Persistence not enabled." << std::endl;
}

void NullMessageStore::enqueue(TransactionContext*, PersistableMessage&, const PersistableQueue& queue)
{
    if (warn) std::cout << "WARNING: Can't enqueue message onto '" << queue.getName() << "'. Persistence not enabled." << std::endl;
}

void NullMessageStore::dequeue(TransactionContext*, PersistableMessage&, const PersistableQueue& queue)
{
    if (warn) std::cout << "WARNING: Can't dequeue message from '" << queue.getName() << "'. Persistence not enabled." << std::endl;
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
