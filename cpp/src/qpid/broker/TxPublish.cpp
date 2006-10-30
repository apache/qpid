/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/broker/TxPublish.h"

using namespace qpid::broker;

TxPublish::TxPublish(Message::shared_ptr _msg) : msg(_msg) {}

bool TxPublish::prepare() throw(){
    try{
        for_each(queues.begin(), queues.end(), Prepare(msg, 0));
        return true;
    }catch(...){
        std::cout << "TxPublish::prepare() - Failed to prepare" << std::endl;
        return false;
    }
}

void TxPublish::commit() throw(){
    for_each(queues.begin(), queues.end(), Commit(msg));
}

void TxPublish::rollback() throw(){
}

void TxPublish::deliverTo(Queue::shared_ptr& queue){
    queues.push_back(queue);
}

TxPublish::Prepare::Prepare(Message::shared_ptr& _msg, const string* const _xid) : msg(_msg), xid(_xid){}

void TxPublish::Prepare::operator()(Queue::shared_ptr& queue){
    queue->enqueue(msg, xid);
}

TxPublish::Commit::Commit(Message::shared_ptr& _msg) : msg(_msg){}

void TxPublish::Commit::operator()(Queue::shared_ptr& queue){
    queue->process(msg);
}

