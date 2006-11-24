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
// AMQP version change - kpvdr 2006-11-17
#include <qpid/framing/ProtocolVersion.h>
#include <qpid/framing/BasicDeliverBody.h>
#include <qpid/framing/BasicGetOkBody.h>

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
    decode(buffer);
}

Message::Message() : publisher(0), mandatory(false), immediate(false), redelivered(false), size(0), persistenceId(0){}

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
	// AMQP version change - kpvdr 2006-11-17
	// TODO: Make this class version-aware and link these hard-wired numbers to that version
    out->send(new AMQFrame(channel, new BasicDeliverBody(ProtocolVersion(8,0), consumerTag, deliveryTag, redelivered, exchange, routingKey)));
    sendContent(out, channel, framesize);
}

void Message::sendGetOk(OutputHandler* out, 
         int channel, 
         u_int32_t messageCount,
         u_int64_t deliveryTag, 
         u_int32_t framesize){

	// AMQP version change - kpvdr 2006-11-17
	// TODO: Make this class version-aware and link these hard-wired numbers to that version
    out->send(new AMQFrame(channel, new BasicGetOkBody(ProtocolVersion(8,0), deliveryTag, redelivered, exchange, routingKey, messageCount)));
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

void Message::decode(Buffer& buffer)
{
    decodeHeader(buffer);
    decodeContent(buffer);
}

void Message::decodeHeader(Buffer& buffer)
{
    buffer.getShortString(exchange);
    buffer.getShortString(routingKey);
    
    u_int32_t headerSize = buffer.getLong();
    AMQHeaderBody::shared_ptr headerBody(new AMQHeaderBody());
    headerBody->decode(buffer, headerSize);
    setHeader(headerBody);
}

void Message::decodeContent(Buffer& buffer)
{    
    AMQContentBody::shared_ptr contentBody;
    while (buffer.available()) {
        AMQFrame contentFrame;
        contentFrame.decode(buffer);
        contentBody = dynamic_pointer_cast<AMQContentBody, AMQBody>(contentFrame.getBody());
        addContent(contentBody);
    }        
}

void Message::encode(Buffer& buffer)
{
    encodeHeader(buffer);
    encodeContent(buffer);
}

void Message::encodeHeader(Buffer& buffer)
{
    buffer.putShortString(exchange);
    buffer.putShortString(routingKey);    
    buffer.putLong(header->size());
    header->encode(buffer);
}

void Message::encodeContent(Buffer& buffer)
{
    //Use a frame around each content block. Not really required but
    //gives some error checking at little expense. Could change in the
    //future...
    AMQBody::shared_ptr body;
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        body = static_pointer_cast<AMQBody, AMQContentBody>(*i);
        AMQFrame contentFrame(0, body);
        contentFrame.encode(buffer);
    }    
}

u_int32_t Message::encodedSize()
{
    return  encodedHeaderSize() + encodedContentSize();
}

u_int32_t Message::encodedContentSize()
{
    int encodedContentSize(0);
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        encodedContentSize += (*i)->size() + 8;//8 extra bytes for the frame
    }
    return encodedContentSize;
}

u_int32_t Message::encodedHeaderSize()
{
    return exchange.size() + 1
        + routingKey.size() + 1
        + header->size() + 4;//4 extra bytes for size
}

u_int64_t Message::expectedContentSize()
{
    return header.get() ? header->getContentSize() : 0;
}

void Message::releaseContent()
{
    content.clear();
}
