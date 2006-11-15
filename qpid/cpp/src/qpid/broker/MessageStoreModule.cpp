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

#include <qpid/broker/MessageStoreModule.h>
#include <iostream>

using namespace qpid::broker;

MessageStoreModule::MessageStoreModule(const std::string& name) : store(name)
{
}

void MessageStoreModule::create(const Queue& queue)
{
    store->create(queue);
}

void MessageStoreModule::destroy(const Queue& queue)
{
    store->destroy(queue);
}

void MessageStoreModule::recover(QueueRegistry& registry)
{
    store->recover(registry);
}

void MessageStoreModule::enqueue(TransactionContext* ctxt, Message::shared_ptr& msg, const Queue& queue, const string * const xid)
{
    store->enqueue(ctxt, msg, queue, xid);
}

void MessageStoreModule::dequeue(TransactionContext* ctxt, Message::shared_ptr& msg, const Queue& queue, const string * const xid)
{
    store->dequeue(ctxt, msg, queue, xid);
}

void MessageStoreModule::committed(const string * const xid)
{
    store->committed(xid);
}

void MessageStoreModule::aborted(const string * const xid)
{
    store->aborted(xid);
}

std::auto_ptr<TransactionContext> MessageStoreModule::begin()
{
    return store->begin();
}

void MessageStoreModule::commit(TransactionContext* ctxt)
{
    store->commit(ctxt);
}

void MessageStoreModule::abort(TransactionContext* ctxt)
{
    store->abort(ctxt);
}
