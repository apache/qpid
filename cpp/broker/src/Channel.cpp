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
#include "Channel.h"
#include "QpidError.h"
#include <iostream>
#include <sstream>
#include <assert.h>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::concurrent;


Channel::Channel(OutputHandler* _out, int _id, u_int32_t _framesize) : out(_out), 
                                                                       id(_id), 
                                                                       prefetchCount(0),
                                                                       prefetchSize(0),
                                                                       outstandingSize(0),
                                                                       outstandingCount(0),
                                                                       framesize(_framesize),
                                                                       transactional(false),
                                                                       deliveryTag(1),
                                                                       tagGenerator("sgen"){}

Channel::~Channel(){
}

bool Channel::exists(const string& consumerTag){
    return consumers.find(consumerTag) != consumers.end();
}

void Channel::consume(string& tag, Queue::shared_ptr queue, bool acks, bool exclusive, ConnectionToken* const connection){
    if(tag.empty()) tag = tagGenerator.generate();

    ConsumerImpl* c(new ConsumerImpl(this, tag, queue, connection, acks));
    try{
        queue->consume(c, exclusive);//may throw exception
        consumers[tag] = c;
    }catch(ExclusiveAccessException& e){
        delete c;
        throw e;
    }
}

void Channel::cancel(consumer_iterator i){
    ConsumerImpl* c = i->second;
    consumers.erase(i);
    if(c){
        c->cancel();
        delete c;
    }
}

void Channel::cancel(const string& tag){
    consumer_iterator i = consumers.find(tag);
    if(i != consumers.end()){
        cancel(i);
    }
}

void Channel::close(){
    //cancel all consumers
    for(consumer_iterator i = consumers.begin(); i != consumers.end(); i = consumers.begin() ){
        cancel(i);
    }
}

void Channel::begin(){
    transactional = true;
}

void Channel::commit(){

}

void Channel::rollback(){

}

void Channel::deliver(Message::shared_ptr& msg, string& consumerTag, Queue::shared_ptr& queue, bool ackExpected){
    Locker locker(deliveryLock);

    u_int64_t myDeliveryTag = deliveryTag++;
    if(ackExpected){
        unacknowledged.push_back(AckRecord(msg, queue, consumerTag, myDeliveryTag));
        outstandingSize += msg->contentSize();
        outstandingCount++;
    }
    //send deliver method, header and content(s)
    msg->deliver(out, id, consumerTag, myDeliveryTag, framesize);
}

bool Channel::checkPrefetch(Message::shared_ptr& msg){
    Locker locker(deliveryLock);
    bool countOk = !prefetchCount || prefetchCount > unacknowledged.size();
    bool sizeOk = !prefetchSize || prefetchSize > msg->contentSize() + outstandingSize || unacknowledged.empty();
    return countOk && sizeOk;
}

Channel::ConsumerImpl::ConsumerImpl(Channel* _parent, string& _tag, 
                                    Queue::shared_ptr _queue, 
                                    ConnectionToken* const _connection, bool ack) : parent(_parent), 
                                                                                    tag(_tag), 
                                                                                    queue(_queue),
                                                                                    connection(_connection),
                                                                                    ackExpected(ack), 
                                                                                    blocked(false){
}

bool Channel::ConsumerImpl::deliver(Message::shared_ptr& msg){
    if(connection != msg->getPublisher()){//check for no_local
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

void Channel::checkMessage(const std::string& text){
    if(!message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, text);
    }
}

void Channel::handlePublish(Message* msg){
    if(message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got publish before previous content was completed.");
    }
    message = Message::shared_ptr(msg);
}

void Channel::ack(u_int64_t deliveryTag, bool multiple){
    Locker locker(deliveryLock);//need to synchronize with possible concurrent delivery
    
    ack_iterator i = find_if(unacknowledged.begin(), unacknowledged.end(), MatchAck(deliveryTag));
    if(i == unacknowledged.end()){
        throw InvalidAckException();
    }else if(multiple){        
        unacknowledged.erase(unacknowledged.begin(), ++i);
        //recompute prefetch outstanding (note: messages delivered through get are ignored)
        CalculatePrefetch calc(for_each(unacknowledged.begin(), unacknowledged.end(), CalculatePrefetch()));
        outstandingSize = calc.getSize();
        outstandingCount = calc.getCount();
    }else{
        if(!i->pull){
            outstandingSize -= i->msg->contentSize();
            outstandingCount--;
        }
        unacknowledged.erase(i);        
    }

    //if the prefetch limit had previously been reached, there may
    //be messages that can be now be delivered
    for(consumer_iterator i = consumers.begin(); i != consumers.end(); i++){
        i->second->requestDispatch();
    }
}

void Channel::recover(bool requeue){
    Locker locker(deliveryLock);//need to synchronize with possible concurrent delivery

    if(requeue){
        outstandingSize = 0;
        outstandingCount = 0;
        ack_iterator start(unacknowledged.begin());
        ack_iterator end(unacknowledged.end());
        for_each(start, end, Requeue());
        unacknowledged.erase(start, end);
    }else{
        for_each(unacknowledged.begin(), unacknowledged.end(), Redeliver(this));        
    }
}

bool Channel::get(Queue::shared_ptr queue, bool ackExpected){
    Message::shared_ptr msg = queue->dequeue();
    if(msg){
        Locker locker(deliveryLock);
        u_int64_t myDeliveryTag = deliveryTag++;
        msg->sendGetOk(out, id, queue->getMessageCount() + 1, myDeliveryTag, framesize);
        if(ackExpected){
            unacknowledged.push_back(AckRecord(msg, queue, myDeliveryTag));
        }
        return true;
    }else{
        return false;
    }
}

Channel::MatchAck::MatchAck(u_int64_t _tag) : tag(_tag) {}

bool Channel::MatchAck::operator()(AckRecord& record) const{
    return tag == record.deliveryTag;
}

void Channel::Requeue::operator()(AckRecord& record) const{
    record.msg->redeliver();
    record.queue->deliver(record.msg);
}

Channel::Redeliver::Redeliver(Channel* const _channel) : channel(_channel) {}

void Channel::Redeliver::operator()(AckRecord& record) const{
    if(record.pull){
        //if message was originally sent as response to get, we must requeue it
        record.msg->redeliver();
        record.queue->deliver(record.msg);
    }else{
        record.msg->deliver(channel->out, channel->id, record.consumerTag, record.deliveryTag, channel->framesize);
    }
}

Channel::CalculatePrefetch::CalculatePrefetch() : size(0){}

void Channel::CalculatePrefetch::operator()(AckRecord& record){
    if(!record.pull){
        //ignore messages that were sent in response to get when calculating prefetch
        size += record.msg->contentSize();
        count++;
    }
}

u_int32_t Channel::CalculatePrefetch::getSize(){
    return size;
}

u_int16_t Channel::CalculatePrefetch::getCount(){
    return count;
}
