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
#include <qpid/broker/Message.h>
#include <iostream>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;

Message::Message(const ConnectionToken* const _publisher, 
                 const string& _exchange, const string& _routingKey, 
                 bool _mandatory, bool _immediate) : publisher(_publisher),
                                                     exchange(_exchange),
                                                     routingKey(_routingKey), 
                                                     mandatory(_mandatory),
                                                     immediate(_immediate),
                                                     redelivered(false),
                                                     size(0),
                                                     persistenceId(0) {}

Message::Message(Buffer& buffer) : publisher(0), mandatory(false), immediate(false), redelivered(false), size(0), persistenceId(0){
    buffer.getShortString(exchange);
    buffer.getShortString(routingKey);
    
    AMQFrame headerFrame;
    headerFrame.decode(buffer);
    AMQHeaderBody::shared_ptr headerBody = dynamic_pointer_cast<AMQHeaderBody, AMQBody>(headerFrame.getBody());
    setHeader(headerBody);
    
    AMQContentBody::shared_ptr contentBody;
    while (buffer.available()) {
        AMQFrame contentFrame;
        contentFrame.decode(buffer);
        contentBody = dynamic_pointer_cast<AMQContentBody, AMQBody>(contentFrame.getBody());
        addContent(contentBody);
    }        
}

Message::~Message(){}

void Message::setHeader(AMQHeaderBody::shared_ptr _header){
    this->header = _header;
}

void Message::addContent(AMQContentBody::shared_ptr data){
    content.push_back(data);
    size += data->size();    
}

bool Message::isComplete(){
    return header.get() && (header->getContentSize() == contentSize());
}

void Message::redeliver(){
    redelivered = true;
}

void Message::deliver(OutputHandler* out, int channel, 
                      const string& consumerTag, u_int64_t deliveryTag, 
                      u_int32_t framesize){
    out->send(new AMQFrame(channel, new BasicDeliverBody(consumerTag, deliveryTag, redelivered, exchange, routingKey)));
    sendContent(out, channel, framesize);
}

void Message::sendGetOk(OutputHandler* out, 
         int channel, 
         u_int32_t messageCount,
         u_int64_t deliveryTag, 
         u_int32_t framesize){

    out->send(new AMQFrame(channel, new BasicGetOkBody(deliveryTag, redelivered, exchange, routingKey, messageCount)));
    sendContent(out, channel, framesize);
}

void Message::sendContent(OutputHandler* out, int channel, u_int32_t framesize){
    AMQBody::shared_ptr headerBody = static_pointer_cast<AMQBody, AMQHeaderBody>(header);
    out->send(new AMQFrame(channel, headerBody));
    for(content_iterator i = content.begin(); i != content.end(); i++){
        if((*i)->size() > framesize){
            //TODO: need to split it
            std::cout << "WARNING: Dropped message. Re-fragmentation not yet implemented." << std::endl;
        }else{
            AMQBody::shared_ptr contentBody = static_pointer_cast<AMQBody, AMQContentBody>(*i);
            out->send(new AMQFrame(channel, contentBody));
        }
    }
}

BasicHeaderProperties* Message::getHeaderProperties(){
    return dynamic_cast<BasicHeaderProperties*>(header->getProperties());
}

const ConnectionToken* const Message::getPublisher(){
    return publisher;
}

bool Message::isPersistent()
{
    if(!header) return false;
    BasicHeaderProperties* props = getHeaderProperties();
    return props && props->getDeliveryMode() == PERSISTENT;
}

void Message::encode(Buffer& buffer)
{
    buffer.putShortString(exchange);
    buffer.putShortString(routingKey);
    
    AMQBody::shared_ptr body;

    body = static_pointer_cast<AMQBody, AMQHeaderBody>(header);

    AMQFrame headerFrame(0, body);
    headerFrame.encode(buffer);
    
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        body = static_pointer_cast<AMQBody, AMQContentBody>(*i);
        AMQFrame contentFrame(0, body);
        contentFrame.encode(buffer);
    }    
}

u_int32_t Message::encodedSize()
{
    int encodedContentSize(0);
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        encodedContentSize += (*i)->size() + 8;//8 extra bytes for the frame (TODO, could replace frame by simple size)
    }

    return exchange.size() + 1
        + routingKey.size() + 1
        + header->size() + 8 //8 extra bytes for frame (TODO, could actually remove the frame)
        + encodedContentSize;
}
