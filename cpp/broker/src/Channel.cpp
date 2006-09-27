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
                                                                       framesize(_framesize),
                                                                       transactional(false),
                                                                       deliveryTag(1),
                                                                       tagGenerator("sgen"){}

Channel::~Channel(){
    for(consumer_iterator i = consumers.begin(); i != consumers.end(); i = consumers.begin() ){
        std::cout << "ERROR: Channel consumer appears not to have been cancelled before channel was destroyed." << std::endl;
        delete (i->second);
    }
}

bool Channel::exists(string& consumerTag){
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

void Channel::cancel(string& tag){
    consumer_iterator i = consumers.find(tag);
    if(i != consumers.end()){
        ConsumerImpl* c = i->second;
        consumers.erase(i);
        if(c){
            c->cancel();
            delete c;
        }
    }
}

void Channel::close(){
    //cancel all consumers
    for(consumer_iterator i = consumers.begin(); i != consumers.end(); i = consumers.begin() ){
        ConsumerImpl* c = i->second;
        consumers.erase(i);
        if(c){
            c->cancel();
            delete c;
        }
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
    }
    //send deliver method, header and content(s)
    msg->deliver(out, id, consumerTag, myDeliveryTag, framesize);
}

Channel::ConsumerImpl::ConsumerImpl(Channel* _parent, string& _tag, 
                                    Queue::shared_ptr _queue, 
                                    ConnectionToken* const _connection, bool ack) : parent(_parent), 
                                                                                    tag(_tag), 
                                                                                    queue(_queue),
                                                                                    connection(_connection),
                                                                                    ackExpected(ack){
}

bool Channel::ConsumerImpl::deliver(Message::shared_ptr& msg){
    if(connection != msg->getPublisher()){
        parent->deliver(msg, tag, queue, ackExpected);
        return true;
    }else{
        return false;
    }
}

void Channel::ConsumerImpl::cancel(){
    if(queue) queue->cancel(this);
}

void Channel::handlePublish(Message* msg){
    if(message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got publish before previous content was completed.");
    }
    message = Message::shared_ptr(msg);
}

void Channel::handleHeader(AMQHeaderBody::shared_ptr header, ExchangeRegistry* exchanges){
    if(!message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got header before publish.");
    }
    message->setHeader(header);
    if(message->isComplete()){
        publish(exchanges);
    }
}

void Channel::handleContent(AMQContentBody::shared_ptr content, ExchangeRegistry* exchanges){
    if(!message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got content before publish.");
    }
    message->addContent(content);
    if(message->isComplete()){
        publish(exchanges);
    }
}

void Channel::publish(ExchangeRegistry* exchanges){
    if(!route(message, exchanges)){
        std::cout << "WARNING: Could not route message." << std::endl;
    }
    message.reset();
}

void Channel::ack(u_int64_t deliveryTag, bool multiple){
    ack_iterator i = find_if(unacknowledged.begin(), unacknowledged.end(), MatchAck(deliveryTag));
    if(i == unacknowledged.end()){
        //error: how should this be signalled?
    }else if(multiple){
        unacknowledged.erase(unacknowledged.begin(), ++i);
    }else{
        unacknowledged.erase(i);
    }
}

void Channel::recover(bool requeue){
    if(requeue){
        for_each(unacknowledged.begin(), unacknowledged.end(), Requeue());
        unacknowledged.clear();
    }else{
        for_each(unacknowledged.begin(), unacknowledged.end(), Redeliver(this));        
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
    record.msg->deliver(channel->out, channel->id, record.consumerTag, record.deliveryTag, channel->framesize);
}
