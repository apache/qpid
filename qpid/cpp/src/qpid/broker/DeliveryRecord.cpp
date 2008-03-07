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
#include "Exchange.h"
#include "qpid/log/Statement.h"

using namespace qpid::broker;
using std::string;

DeliveryRecord::DeliveryRecord(const QueuedMessage& _msg, 
                               Queue::shared_ptr _queue, 
                               const std::string _tag,
                               DeliveryToken::shared_ptr _token, 
                               const DeliveryId _id,
                               bool _acquired, bool accepted) : msg(_msg), 
                                                                queue(_queue), 
                                                                tag(_tag),
                                                                token(_token),
                                                                id(_id),
                                                                acquired(_acquired),
                                                                pull(false), 
                                                                cancelled(false),
                                                                credit(msg.payload ? msg.payload->getRequiredCredit() : 0),
                                                                size(msg.payload ? msg.payload->contentSize() : 0),
                                                                completed(false),
                                                                ended(accepted)
{
    if (accepted) setEnded();
}

DeliveryRecord::DeliveryRecord(const QueuedMessage& _msg, 
                               Queue::shared_ptr _queue, 
                               const DeliveryId _id) : msg(_msg), 
                                                       queue(_queue), 
                                                       id(_id),
                                                       acquired(true),
                                                       pull(true),
                                                       cancelled(false),
                                                       credit(msg.payload ? msg.payload->getRequiredCredit() : 0),
                                                       size(msg.payload ? msg.payload->contentSize() : 0),
                                                       completed(false),
                                                       ended(false)
{}

void DeliveryRecord::setEnded()
{
    ended = true;
    //reset msg pointer, don't need to hold on to it anymore
    msg.payload = boost::intrusive_ptr<Message>();
}

void DeliveryRecord::dequeue(TransactionContext* ctxt) const{
    if (acquired && !ended) {
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

void DeliveryRecord::redeliver(SemanticState* const session) {
    if (!ended) {
        if(pull || cancelled){
            //if message was originally sent as response to get, we must requeue it

            //or if subscription was cancelled, requeue it (waiting for
            //final confirmation for AMQP WG on this case)

            requeue();
        }else{
            msg.payload->redeliver();//mark as redelivered
            id = session->redeliver(msg, token);
        }
    }
}

void DeliveryRecord::requeue() const
{
    if (acquired && !ended) {
        msg.payload->redeliver();
        queue->requeue(msg);
    }
}

void DeliveryRecord::release() 
{
    if (acquired && !ended) {
        queue->requeue(msg);
        acquired = false;
        setEnded();
    }
}

void DeliveryRecord::complete() 
{
    completed = true; 
}

void DeliveryRecord::accept(TransactionContext* ctxt) {
    if (acquired && !ended) {
        queue->dequeue(ctxt, msg.payload);
        setEnded();
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

uint32_t DeliveryRecord::getCredit() const
{
    return credit;
}


void DeliveryRecord::addTo(Prefetch& prefetch) const{
    if(!pull){
        //ignore 'pulled' messages (i.e. those that were sent in
        //response to get) when calculating prefetch
        prefetch.size += size;
        prefetch.count++;
    }    
}

void DeliveryRecord::subtractFrom(Prefetch& prefetch) const{
    if(!pull){
        //ignore 'pulled' messages (i.e. those that were sent in
        //response to get) when calculating prefetch
        prefetch.size -= size;
        prefetch.count--;
    }
}

void DeliveryRecord::acquire(DeliveryIds& results) {
    if (queue->acquire(msg)) {
        acquired = true;
        results.push_back(id);
    } else {
        QPID_LOG(info, "Message already acquired " << id.getValue());
    }
}

void DeliveryRecord::cancel(const std::string& cancelledTag) 
{
    if (tag == cancelledTag)
        cancelled = true;
}

namespace qpid {
namespace broker {

std::ostream& operator<<(std::ostream& out, const DeliveryRecord& r) 
{
    out << "{" << "id=" << r.id.getValue();
    out << ", tag=" << r.tag << "}";
    out << ", queue=" << r.queue->getName() << "}";
    return out;
}

bool operator<(const DeliveryRecord& a, const DeliveryRecord& b)
{
    return a.id < b.id;
}

}}
