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
#include <BrokerMessage.h>
#include <iostream>

#include <InMemoryContent.h>
#include <LazyLoadedContent.h>
#include <MessageStore.h>
#include <BasicDeliverBody.h>
#include <BasicGetOkBody.h>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

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

Message::Message(Buffer& buffer, bool headersOnly, u_int32_t contentChunkSize) : 
    publisher(0), mandatory(false), immediate(false), redelivered(false), size(0), persistenceId(0){

    decode(buffer, headersOnly, contentChunkSize);
}

Message::Message() : publisher(0), mandatory(false), immediate(false), redelivered(false), size(0), persistenceId(0){}

Message::~Message(){
    if (content.get()) content->destroy();
}

void Message::setHeader(AMQHeaderBody::shared_ptr _header){
    this->header = _header;
}

void Message::addContent(AMQContentBody::shared_ptr data){
    if (!content.get()) {
        content = std::auto_ptr<Content>(new InMemoryContent());
    }
    content->add(data);
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
                      u_int32_t framesize,
		      ProtocolVersion* version){
    // CCT -- TODO - Update code generator to take pointer/ not instance to avoid extra contruction
    out->send(new AMQFrame(*version, channel, new BasicDeliverBody(*version, consumerTag, deliveryTag, redelivered, exchange, routingKey)));
    sendContent(out, channel, framesize, version);
}

void Message::sendGetOk(OutputHandler* out, 
         int channel, 
         u_int32_t messageCount,
         u_int64_t deliveryTag, 
         u_int32_t framesize,
	 ProtocolVersion* version){
     // CCT -- TODO - Update code generator to take pointer/ not instance to avoid extra contruction
     out->send(new AMQFrame(*version, channel, new BasicGetOkBody(*version, deliveryTag, redelivered, exchange, routingKey, messageCount)));
    sendContent(out, channel, framesize, version);
}

void Message::sendContent(OutputHandler* out, int channel, u_int32_t framesize, ProtocolVersion* version){
    AMQBody::shared_ptr headerBody = static_pointer_cast<AMQBody, AMQHeaderBody>(header);
    out->send(new AMQFrame(*version, channel, headerBody));

    Mutex::ScopedLock locker(contentLock);
    if (content.get()) content->send(*version, out, channel, framesize);
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

void Message::decode(Buffer& buffer, bool headersOnly, u_int32_t contentChunkSize)
{
    decodeHeader(buffer);
    if (!headersOnly) decodeContent(buffer, contentChunkSize);
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

void Message::decodeContent(Buffer& buffer, u_int32_t chunkSize)
{    
    u_int64_t expected = expectedContentSize();
    if (expected != buffer.available()) {
        std::cout << "WARN: Expected " << expectedContentSize() << " bytes, got " << buffer.available() << std::endl;
        throw Exception("Cannot decode content, buffer not large enough.");
    }

    if (!chunkSize || chunkSize > expected) {
        chunkSize = expected;
    }

    u_int64_t total = 0;
    while (total < expectedContentSize()) {
        u_int64_t remaining =  expected - total;
        AMQContentBody::shared_ptr contentBody(new AMQContentBody());        
        contentBody->decode(buffer, remaining < chunkSize ? remaining : chunkSize);
        addContent(contentBody);
        total += chunkSize;
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
    Mutex::ScopedLock locker(contentLock);
    if (content.get()) content->encode(buffer);
}

u_int32_t Message::encodedSize()
{
    return  encodedHeaderSize() + encodedContentSize();
}

u_int32_t Message::encodedContentSize()
{
    Mutex::ScopedLock locker(contentLock);
    return content.get() ? content->size() : 0;
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

void Message::releaseContent(MessageStore* store)
{
    Mutex::ScopedLock locker(contentLock);
    if (!isPersistent() && persistenceId == 0) {
        store->stage(this);
    }
    if (!content.get() || content->size() > 0) {
        //set content to lazy loading mode (but only if there is stored content):

        //Note: the LazyLoadedContent instance contains a raw pointer to the message, however it is
        //      then set as a member of that message so its lifetime is guaranteed to be no longer than
        //      that of the message itself
        content = std::auto_ptr<Content>(new LazyLoadedContent(store, this, expectedContentSize()));
    }
}

void Message::setContent(std::auto_ptr<Content>& _content)
{
    Mutex::ScopedLock locker(contentLock);
    content = _content;
}
