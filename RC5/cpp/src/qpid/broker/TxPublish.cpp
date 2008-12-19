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

using boost::intrusive_ptr;
using namespace qpid::broker;

TxPublish::TxPublish(intrusive_ptr<Message> _msg) : msg(_msg) {}

bool TxPublish::prepare(TransactionContext* ctxt) throw(){
    try{
        for_each(queues.begin(), queues.end(), Prepare(ctxt, msg));
        return true;
    }catch(const std::exception& e){
        QPID_LOG(error, "Failed to prepare: " << e.what());
    }catch(...){
        QPID_LOG(error, "Failed to prepare (unknown error)");
    }
    return false;
}

void TxPublish::commit() throw(){
    for_each(queues.begin(), queues.end(), Commit(msg));
}

void TxPublish::rollback() throw(){
}

void TxPublish::deliverTo(const boost::shared_ptr<Queue>& queue){
    if (!queue->isLocal(msg)) {
        queues.push_back(queue);
        delivered = true;
    } else {
        QPID_LOG(debug, "Won't enqueue local message for " << queue->getName());
    }
}

TxPublish::Prepare::Prepare(TransactionContext* _ctxt, intrusive_ptr<Message>& _msg) 
    : ctxt(_ctxt), msg(_msg){}

void TxPublish::Prepare::operator()(const boost::shared_ptr<Queue>& queue){
    if (!queue->enqueue(ctxt, msg)){
        /**
	* if not store then mark message for ack and deleivery once
	* commit happens, as async IO will never set it when no store
	* exists
	*/
	msg->enqueueComplete();
    }
}

TxPublish::Commit::Commit(intrusive_ptr<Message>& _msg) : msg(_msg){}

void TxPublish::Commit::operator()(const boost::shared_ptr<Queue>& queue){
    queue->process(msg);
}

uint64_t TxPublish::contentSize ()
{
    return msg->contentSize ();
}
