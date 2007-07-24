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
#include "qpid/log/Statement.h"
#include "TxPublish.h"

using namespace qpid::broker;

TxPublish::TxPublish(Message::shared_ptr _msg) : msg(_msg) {}

bool TxPublish::prepare(TransactionContext* ctxt) throw(){
    try{
        for_each(queues.begin(), queues.end(), Prepare(ctxt, msg));
        return true;
    }catch(...){
        QPID_LOG(error, "Failed to prepare");
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
    delivered = true;
}

TxPublish::Prepare::Prepare(TransactionContext* _ctxt, Message::shared_ptr& _msg) 
    : ctxt(_ctxt), msg(_msg){}

void TxPublish::Prepare::operator()(Queue::shared_ptr& queue){
    queue->enqueue(ctxt, msg);
}

TxPublish::Commit::Commit(Message::shared_ptr& _msg) : msg(_msg){}

void TxPublish::Commit::operator()(Queue::shared_ptr& queue){
    queue->process(msg);
}

