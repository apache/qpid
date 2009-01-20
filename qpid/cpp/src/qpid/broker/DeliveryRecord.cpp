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
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/MessageTransferBody.h"

using namespace qpid;
using namespace qpid::broker;
using std::string;

DeliveryRecord::DeliveryRecord(const QueuedMessage& _msg, 
                               const Queue::shared_ptr& _queue, 
                               const std::string& _tag,
                               bool _acquired,
                               bool accepted, 
                               bool _windowing,
                               uint32_t _credit) : msg(_msg), 
                                                  queue(_queue), 
                                                  tag(_tag),
                                                  acquired(_acquired),
                                                  acceptExpected(!accepted),
                                                  cancelled(false),
                                                  completed(false),
                                                  ended(accepted),
                                                  windowing(_windowing),
                                                  credit(msg.payload ? msg.payload->getRequiredCredit() : _credit)
{}

void DeliveryRecord::setEnded()
{
    ended = true;
    //reset msg pointer, don't need to hold on to it anymore
    msg.payload = boost::intrusive_ptr<Message>();

    QPID_LOG(debug, "DeliveryRecord::setEnded() id=" << id);
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

bool DeliveryRecord::coveredBy(const framing::SequenceSet* const range) const{
    return range->contains(id);
}

void DeliveryRecord::redeliver(SemanticState* const session) {
    if (!ended) {
        if(cancelled){
            //if subscription was cancelled, requeue it (waiting for
            //final confirmation for AMQP WG on this case)
            requeue();
        }else{
            msg.payload->redeliver();//mark as redelivered
            session->deliver(*this, false);
        }
    }
}

void DeliveryRecord::deliver(framing::FrameHandler& h, DeliveryId deliveryId, uint16_t framesize)
{
    id = deliveryId;
    if (msg.payload->getRedelivered()){
        msg.payload->getProperties<framing::DeliveryProperties>()->setRedelivered(true);
    }

    framing::AMQFrame method(framing::in_place<framing::MessageTransferBody>(framing::ProtocolVersion(), tag, acceptExpected ? 0 : 1, acquired ? 0 : 1));
    method.setEof(false);
    h.handle(method);
    msg.payload->sendHeader(h, framesize);
    msg.payload->sendContent(*queue, h, framesize);
}

void DeliveryRecord::requeue() const
{
    if (acquired && !ended) {
        msg.payload->redeliver();
        queue->requeue(msg);
    }
}

void DeliveryRecord::release(bool setRedelivered) 
{
    if (acquired && !ended) {
        if (setRedelivered) msg.payload->redeliver();
        queue->requeue(msg);
        acquired = false;
        setEnded();
    } else {
        QPID_LOG(debug, "Ignoring release for " << id << " acquired=" << acquired << ", ended =" << ended);
    }
}

void DeliveryRecord::complete() 
{
    completed = true; 
}

void DeliveryRecord::accept(TransactionContext* ctxt) {
    if (acquired && !ended) {
        queue->dequeue(ctxt, msg);
        setEnded();
        QPID_LOG(debug, "Accepted " << id);
    }
}

void DeliveryRecord::dequeue(TransactionContext* ctxt) const{
    if (acquired && !ended) {
        queue->dequeue(ctxt, msg);
    }
}

void DeliveryRecord::committed() const{
    queue->dequeueCommitted(msg);
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

AckRange DeliveryRecord::findRange(DeliveryRecords& records, DeliveryId first, DeliveryId last)
{
    ack_iterator start = find_if(records.begin(), records.end(), boost::bind(&DeliveryRecord::matchOrAfter, _1, first));
    ack_iterator end = start;
     
    if (start != records.end()) {
        if (first == last) {
            //just acked single element (move end past it)
            ++end;
        } else {
            //need to find end (position it just after the last record in range)
            end = find_if(start, records.end(), boost::bind(&DeliveryRecord::after, _1, last));
        }
    }
    return AckRange(start, end);
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
