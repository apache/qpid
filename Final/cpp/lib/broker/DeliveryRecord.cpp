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
#include <DeliveryRecord.h>
#include <BrokerChannel.h>

using namespace qpid::broker;
using std::string;

DeliveryRecord::DeliveryRecord(Message::shared_ptr _msg, 
                               Queue::shared_ptr _queue, 
                               const string _consumerTag, 
                               const u_int64_t _deliveryTag) : msg(_msg), 
                                                               queue(_queue), 
                                                               consumerTag(_consumerTag),
                                                               deliveryTag(_deliveryTag),
                                                               pull(false){}

DeliveryRecord::DeliveryRecord(Message::shared_ptr _msg, 
                               Queue::shared_ptr _queue, 
                               const u_int64_t _deliveryTag) : msg(_msg), 
                                                               queue(_queue), 
                                                               consumerTag(""),
                                                               deliveryTag(_deliveryTag),
                                                               pull(true){}


void DeliveryRecord::discard(TransactionContext* ctxt, const std::string* const xid) const{
    queue->dequeue(ctxt, msg, xid);
}

void DeliveryRecord::discard() const{
    discard(0, 0);
}

bool DeliveryRecord::matches(u_int64_t tag) const{
    return deliveryTag == tag;
}

bool DeliveryRecord::coveredBy(const AccumulatedAck* const range) const{
    return range->covers(deliveryTag);
}

void DeliveryRecord::redeliver(Channel* const channel) const{
    if(pull){
        //if message was originally sent as response to get, we must requeue it
        requeue();
    }else{
        channel->deliver(msg, consumerTag, deliveryTag);
    }
}

void DeliveryRecord::requeue() const{
    msg->redeliver();
    queue->process(msg);
}

void DeliveryRecord::addTo(Prefetch* const prefetch) const{
    if(!pull){
        //ignore 'pulled' messages (i.e. those that were sent in
        //response to get) when calculating prefetch
        prefetch->size += msg->contentSize();
        prefetch->count++;
    }    
}

void DeliveryRecord::subtractFrom(Prefetch* const prefetch) const{
    if(!pull){
        //ignore 'pulled' messages (i.e. those that were sent in
        //response to get) when calculating prefetch
        prefetch->size -= msg->contentSize();
        prefetch->count--;
    }
}
