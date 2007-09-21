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
#include "DeliveryRecord.h"
#include "DeliverableMessage.h"
#include "SemanticState.h"
#include "BrokerExchange.h"
#include "qpid/log/Statement.h"

using namespace qpid::broker;
using std::string;

DeliveryRecord::DeliveryRecord(QueuedMessage& _msg, 
                               Queue::shared_ptr _queue, 
                               const string _consumerTag, 
                               const DeliveryId _id,
                               bool _acquired, bool _confirmed) : msg(_msg), 
                                                                  queue(_queue), 
                                                                  consumerTag(_consumerTag),
                                                                  id(_id),
                                                                  acquired(_acquired),
                                                                  confirmed(_confirmed),
                                                                  pull(false)
{
}

DeliveryRecord::DeliveryRecord(QueuedMessage& _msg, 
                               Queue::shared_ptr _queue, 
                               const DeliveryId _id) : msg(_msg), 
                                                                queue(_queue), 
                                                                consumerTag(""),
                                                                id(_id),
                                                                acquired(true),
                                                                confirmed(false),
                                                                pull(true){}


void DeliveryRecord::dequeue(TransactionContext* ctxt) const{
    if (acquired && !confirmed) {
        queue->dequeue(ctxt, msg.payload);
    }
}

bool DeliveryRecord::matches(DeliveryId tag) const{
    return id == tag;
}

bool DeliveryRecord::matchOrAfter(DeliveryId tag) const{
    return matches(tag) || after(tag);
}

bool DeliveryRecord::after(DeliveryId tag) const{
    return id > tag;
}

bool DeliveryRecord::coveredBy(const framing::AccumulatedAck* const range) const{
    return range->covers(id);
}

void DeliveryRecord::redeliver(SemanticState* const session) const{
    if (!confirmed) {
        if(pull){
            //if message was originally sent as response to get, we must requeue it
            requeue();
        }else{
            session->deliver(msg.payload, consumerTag, id);
        }
    }
}

void DeliveryRecord::requeue() const
{
    if (!confirmed) {
        msg.payload->redeliver();
        queue->requeue(msg);
    }
}

void DeliveryRecord::release() 
{
    if (!confirmed) {
        queue->requeue(msg);
        acquired = false;
    }
}

void DeliveryRecord::reject() 
{    
    Exchange::shared_ptr alternate = queue->getAlternateExchange();
    if (alternate) {
        DeliverableMessage delivery(msg.payload);
        alternate->route(delivery, msg.payload->getRoutingKey(), msg.payload->getApplicationHeaders());
        QPID_LOG(info, "Routed rejected message from " << queue->getName() << " to " 
                 << alternate->getName());
    } else {
        //just drop it
        QPID_LOG(info, "Dropping rejected message from " << queue->getName());
    }
}

void DeliveryRecord::updateByteCredit(uint32_t& credit) const
{
    credit += msg.payload->getRequiredCredit();
}


void DeliveryRecord::addTo(Prefetch& prefetch) const{
    if(!pull){
        //ignore 'pulled' messages (i.e. those that were sent in
        //response to get) when calculating prefetch
        prefetch.size += msg.payload->contentSize();
        prefetch.count++;
    }    
}

void DeliveryRecord::subtractFrom(Prefetch& prefetch) const{
    if(!pull){
        //ignore 'pulled' messages (i.e. those that were sent in
        //response to get) when calculating prefetch
        prefetch.size -= msg.payload->contentSize();
        prefetch.count--;
    }
}

void DeliveryRecord::acquire(std::vector<DeliveryId>& results) {
    if (queue->acquire(msg)) {
        acquired = true;
        results.push_back(id);
    }
}

namespace qpid {
namespace broker {

std::ostream& operator<<(std::ostream& out, const DeliveryRecord& r) {
    out << "{" << "id=" << r.id.getValue();
    out << ", consumer=" << r.consumerTag;
    out << ", queue=" << r.queue->getName() << "}";
    return out;
}

}}
