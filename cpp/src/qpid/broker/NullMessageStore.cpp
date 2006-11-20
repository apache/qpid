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

#include <qpid/broker/NullMessageStore.h>

#include <qpid/broker/Queue.h>
#include <qpid/broker/RecoveryManager.h>

#include <iostream>

using namespace qpid::broker;

void NullMessageStore::create(const Queue& queue){
    std::cout << "WARNING: Can't create durable queue '" << queue.getName() << "'. Persistence not enabled." << std::endl;
}
void NullMessageStore::destroy(const Queue& queue){
    std::cout << "WARNING: Can't destroy durable queue '" << queue.getName() << "'. Persistence not enabled." << std::endl;
}
void NullMessageStore::recover(RecoveryManager&){
    std::cout << "WARNING: Persistence not enabled, no recovery of queues or messages." << std::endl;
}
void NullMessageStore::enqueue(TransactionContext*, Message::shared_ptr&, const Queue& queue, const string * const){
    std::cout << "WARNING: Can't enqueue message onto '" << queue.getName() << "'. Persistence not enabled." << std::endl;
}
void NullMessageStore::dequeue(TransactionContext*, Message::shared_ptr&, const Queue& queue, const string * const){
    std::cout << "WARNING: Can't dequeue message from '" << queue.getName() << "'. Persistence not enabled." << std::endl;
}
void NullMessageStore::committed(const string * const){
    std::cout << "WARNING: Persistence not enabled." << std::endl;
}
void NullMessageStore::aborted(const string * const){
    std::cout << "WARNING: Persistence not enabled." << std::endl;
}
std::auto_ptr<TransactionContext> NullMessageStore::begin(){
    return std::auto_ptr<TransactionContext>();
}
void NullMessageStore::commit(TransactionContext*){
}
void NullMessageStore::abort(TransactionContext*){
}
