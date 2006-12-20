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
#include <assert.h>

#include <iostream>
#include <sstream>

#include <boost/bind.hpp>

#include "BrokerChannel.h"
#include "QpidError.h"

using std::mem_fun_ref;
using std::bind2nd;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;


Channel::Channel(OutputHandler* _out, int _id, u_int32_t _framesize, MessageStore* const _store, u_int64_t _stagingThreshold) :
    id(_id), 
    out(_out), 
    currentDeliveryTag(1),
    transactional(false),
    prefetchSize(0),
    prefetchCount(0),
    framesize(_framesize),
    tagGenerator("sgen"),
    store(_store),
    messageBuilder(this, _store, _stagingThreshold){

    outstanding.reset();
}

Channel::~Channel(){
}

bool Channel::exists(const string& consumerTag){
    Mutex::ScopedLock l(lock);
    return consumers.find(consumerTag) != consumers.end();
}

void Channel::consume(
    string& tag, Queue::shared_ptr queue, bool acks,
    bool exclusive, ConnectionToken* const connection, const FieldTable*)
{
    Mutex::ScopedLock l(lock);
    if(tag.empty()) tag = tagGenerator.generate();
    // TODO aconway 2006-12-13: enforce ownership of consumer
    // with auto_ptr.
    ConsumerImpl* c(new ConsumerImpl(this, tag, queue, connection, acks));
    try{
        queue->consume(c, exclusive);//may throw exception
        consumers[tag] = c;
    }catch(ExclusiveAccessException& e){
        delete c;
        throw e;
    }
}

void Channel::cancel(consumer_iterator i) {
    // Private, must be called with lock held.
    ConsumerImpl* c = i->second;
    consumers.erase(i);
    if(c){
        c->cancel();
        delete c;
    }
}

void Channel::cancel(const string& tag){
    Mutex::ScopedLock l(lock);
    consumer_iterator i = consumers.find(tag);
    if(i != consumers.end()){
        cancel(i);
    }
}

void Channel::close(){
    {
        Mutex::ScopedLock l(lock);
        while(!consumers.empty()) {
            cancel(consumers.begin());
        }
    }
    // TODO aconway 2006-12-13: does recovery need to be atomic with
    // cancelling all consumers?
    recover(true);
}

void Channel::begin(){
    transactional = true;
}

void Channel::commit(){
    TxAck txAck(accumulatedAck, unacked);
    txBuffer.enlist(&txAck);
    if(txBuffer.prepare(store)){
        txBuffer.commit();
    }
    accumulatedAck.clear();
}

void Channel::rollback(){
    txBuffer.rollback();
    accumulatedAck.clear();
}

void Channel::deliver(Message::shared_ptr& msg, const string& consumerTag, Queue::shared_ptr& queue, bool ackExpected){
    u_int64_t deliveryTag;
    {
        Mutex::ScopedLock l(lock);
        deliveryTag = currentDeliveryTag++;
        if(ackExpected){
            unacked.push_back(
                DeliveryRecord(msg, queue, consumerTag, deliveryTag));
            outstanding.size += msg->contentSize();
            outstanding.count++;
        }
    }
    msg->deliver(out, id, consumerTag, deliveryTag, framesize);
}

bool Channel::checkPrefetch(Message::shared_ptr& msg){
    bool countOk = !prefetchCount || prefetchCount > unacked.size();
    bool sizeOk = !prefetchSize || prefetchSize > msg->contentSize() + outstanding.size || unacked.empty();
    return countOk && sizeOk;
}

Channel::ConsumerImpl::ConsumerImpl(Channel* _parent, const string& _tag, 
                                    Queue::shared_ptr _queue, 
                                    ConnectionToken* const _connection, bool ack) : parent(_parent), 
                                                                                    tag(_tag), 
                                                                                    queue(_queue),
                                                                                    connection(_connection),
                                                                                    ackExpected(ack), 
                                                                                    blocked(false){
}

bool Channel::ConsumerImpl::deliver(Message::shared_ptr& msg){
    if(!connection || connection != msg->getPublisher()){//check for no_local
        if(ackExpected && !parent->checkPrefetch(msg)){
            blocked = true;
        }else{
            blocked = false;
            parent->deliver(msg, tag, queue, ackExpected);
            return true;
        }
    }
    return false;
}

void Channel::ConsumerImpl::cancel(){
    if(queue) queue->cancel(this);
}

void Channel::ConsumerImpl::requestDispatch(){
    if(blocked) queue->dispatch();
}

void Channel::handlePublish(Message* _message, Exchange::shared_ptr _exchange){
    Message::shared_ptr message(_message);
    exchange = _exchange;
    messageBuilder.initialise(message);
}

void Channel::handleHeader(AMQHeaderBody::shared_ptr header){
    messageBuilder.setHeader(header);
    //at this point, decide based on the size of the message whether we want
    //to stage it by saving content directly to disk as it arrives
}

void Channel::handleContent(AMQContentBody::shared_ptr content){
    messageBuilder.addContent(content);
}

void Channel::complete(Message::shared_ptr& msg){
    if(exchange){
        if(transactional){
            TxPublish* deliverable = new TxPublish(msg);
            exchange->route(*deliverable, msg->getRoutingKey(), &(msg->getHeaderProperties()->getHeaders()));
            txBuffer.enlist(new DeletingTxOp(deliverable));
        }else{
            DeliverableMessage deliverable(msg);
            exchange->route(deliverable, msg->getRoutingKey(), &(msg->getHeaderProperties()->getHeaders()));
        }
        exchange.reset();
    }else{
        std::cout << "Exchange not known in" << BOOST_CURRENT_FUNCTION
                  << std::endl;
    }
}

void Channel::ack(u_int64_t deliveryTag, bool multiple) {
    if(transactional){
        Mutex::ScopedLock locker(lock);    
        accumulatedAck.update(deliveryTag, multiple);
        //TODO: I think the outstanding prefetch size & count should
        //be updated at this point...
        //TODO: ...this may then necessitate dispatching to consumers
    }
    else {
        {
            Mutex::ScopedLock locker(lock);    
            ack_iterator i = find_if(
                unacked.begin(), unacked.end(),
                boost::bind(&DeliveryRecord::matches, _1, deliveryTag));
            if(i == unacked.end()) {
                throw InvalidAckException();
            }
            else if(multiple) {     
                ack_iterator end = ++i;
                for_each(unacked.begin(), end,
                         mem_fun_ref(&DeliveryRecord::discard));
                unacked.erase(unacked.begin(), end);

                //recalculate the prefetch:
                outstanding.reset();
                for_each(
                    unacked.begin(), unacked.end(),
                    boost::bind(&DeliveryRecord::addTo, _1, &outstanding));
            }
            else {
                i->discard();
                i->subtractFrom(&outstanding);
                unacked.erase(i);        
            }
        }
        //if the prefetch limit had previously been reached, there may
        //be messages that can be now be delivered

        // TODO aconway 2006-12-13: Does this need to be atomic?
        // If so we need a redesign, requestDispatch re-enters
        // Channel::dispatch.
        // 
       for(consumer_iterator j = consumers.begin(); j != consumers.end(); j++){
            j->second->requestDispatch();
        }
    }
}

void Channel::recover(bool requeue) {
    std::list<DeliveryRecord> copyUnacked;
    boost::function1<void, DeliveryRecord&> recoverFn;
    {
        Mutex::ScopedLock l(lock);
        if(requeue) {
            outstanding.reset();
            copyUnacked.swap(unacked);
            recoverFn = boost::bind(&DeliveryRecord::requeue, _1);
        }
        else {
            copyUnacked = unacked;
            recoverFn = boost::bind(&DeliveryRecord::redeliver, _1, this);
        }
    }
    // TODO aconway 2006-12-13: Does recovery of copyUnacked have to
    // be atomic with extracting the list?
    for_each(copyUnacked.begin(), copyUnacked.end(), recoverFn);
}

bool Channel::get(Queue::shared_ptr queue, bool ackExpected){
    Mutex::ScopedLock l(lock);
    // TODO aconway 2006-12-13: Nasty to have all these external calls
    // inside a critical.section but none appear to have blocking potential.
    // sendGetOk does non-blocking IO
    // 
    Message::shared_ptr msg = queue->dequeue();
    if(msg) {
        u_int64_t myDeliveryTag = currentDeliveryTag++;
        u_int32_t count = queue->getMessageCount();
        msg->sendGetOk(out, id, count + 1, myDeliveryTag, framesize);
        if(ackExpected){
            unacked.push_back(DeliveryRecord(msg, queue, myDeliveryTag));
        }
        return true;
    }
    return false;
}

void Channel::deliver(Message::shared_ptr& msg, const string& consumerTag,
                      u_int64_t deliveryTag){
    msg->deliver(out, id, consumerTag, deliveryTag, framesize);
}
