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
#include <boost/cast.hpp>

#include <BrokerMessage.h>
#include <iostream>

#include <InMemoryContent.h>
#include <LazyLoadedContent.h>
#include <MessageStore.h>
#include <BasicDeliverBody.h>
#include <BasicGetOkBody.h>
#include <AMQContentBody.h>
#include <AMQHeaderBody.h>
#include "AMQMethodBody.h"
#include "AMQFrame.h"
#include "framing/ChannelAdapter.h"
#include "RecoveryManagerImpl.h"

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

BasicMessage::BasicMessage(
    const ConnectionToken* const _publisher, 
    const string& _exchange, const string& _routingKey, 
    bool _mandatory, bool _immediate, framing::AMQMethodBody::shared_ptr respondTo
) :
    Message(_publisher, _exchange, _routingKey, _mandatory,
            _immediate, respondTo),
    size(0)
{}

// For tests only.
BasicMessage::BasicMessage() : size(0)
{}

BasicMessage::~BasicMessage(){}

void BasicMessage::setHeader(AMQHeaderBody::shared_ptr _header){
    this->header = _header;
}

void BasicMessage::addContent(AMQContentBody::shared_ptr data){
    if (!content.get()) {
        content = std::auto_ptr<Content>(new InMemoryContent());
    }
    content->add(data);
    size += data->size();    
}

bool BasicMessage::isComplete(){
    return header.get() && (header->getContentSize() == contentSize());
}

void BasicMessage::deliver(ChannelAdapter& channel, 
                           const string& consumerTag, uint64_t deliveryTag, 
                           uint32_t framesize)
{
    // CCT -- TODO - Update code generator to take pointer/ not
    // instance to avoid extra contruction
    channel.send(
    	new BasicDeliverBody(
            channel.getVersion(), consumerTag, deliveryTag,
            getRedelivered(), getExchange(), getRoutingKey()));
    sendContent(channel, framesize);
}

void BasicMessage::sendGetOk(const MethodContext& context,
    					     const std::string& /*destination*/,
                             uint32_t messageCount,
                             uint64_t deliveryTag, 
                             uint32_t framesize)
{
    // CCT -- TODO - Update code generator to take pointer/ not
    // instance to avoid extra contruction
    context.channel->send(
        new BasicGetOkBody(
            context.channel->getVersion(),
            context.methodBody->getRequestId(),
            deliveryTag, getRedelivered(), getExchange(),
            getRoutingKey(), messageCount)); 
    sendContent(*context.channel, framesize);
}

void BasicMessage::sendContent(
    ChannelAdapter& channel, uint32_t framesize)
{
    channel.send(header);
    Mutex::ScopedLock locker(contentLock);
    if (content.get())
        content->send(channel,  framesize);
}

BasicHeaderProperties* BasicMessage::getHeaderProperties(){
    return boost::polymorphic_downcast<BasicHeaderProperties*>(
        header->getProperties());
}

const FieldTable& BasicMessage::getApplicationHeaders(){
    return getHeaderProperties()->getHeaders();
}

bool BasicMessage::isPersistent()
{
    if(!header) return false;
    BasicHeaderProperties* props = getHeaderProperties();
    return props && props->getDeliveryMode() == PERSISTENT;
}

void BasicMessage::decode(Buffer& buffer, bool headersOnly, uint32_t contentChunkSize)
{
    decodeHeader(buffer);
    if (!headersOnly) decodeContent(buffer, contentChunkSize);
}

void BasicMessage::decodeHeader(Buffer& buffer)
{
    //don't care about the type here, but want encode/decode to be symmetric
    RecoveryManagerImpl::decodeMessageType(buffer);    

    string exchange;
    string routingKey;

    buffer.getShortString(exchange);
    buffer.getShortString(routingKey);
    setRouting(exchange, routingKey);
    
    uint32_t headerSize = buffer.getLong();
    AMQHeaderBody::shared_ptr headerBody(new AMQHeaderBody());
    headerBody->decode(buffer, headerSize);
    setHeader(headerBody);
}

void BasicMessage::decodeContent(Buffer& buffer, uint32_t chunkSize)
{    
    uint64_t expected = expectedContentSize();
    if (expected != buffer.available()) {
        std::cout << "WARN: Expected " << expectedContentSize() << " bytes, got " << buffer.available() << std::endl;
        throw Exception("Cannot decode content, buffer not large enough.");
    }

    if (!chunkSize || chunkSize > expected) {
        chunkSize = expected;
    }

    uint64_t total = 0;
    while (total < expectedContentSize()) {
        uint64_t remaining =  expected - total;
        AMQContentBody::shared_ptr contentBody(new AMQContentBody());        
        contentBody->decode(buffer, remaining < chunkSize ? remaining : chunkSize);
        addContent(contentBody);
        total += chunkSize;
    }
}

void BasicMessage::encode(Buffer& buffer) const
{
    encodeHeader(buffer);
    encodeContent(buffer);
}

void BasicMessage::encodeHeader(Buffer& buffer) const
{
    RecoveryManagerImpl::encodeMessageType(*this, buffer);
    buffer.putShortString(getExchange());
    buffer.putShortString(getRoutingKey());    
    buffer.putLong(header->size());
    header->encode(buffer);
}

void BasicMessage::encodeContent(Buffer& buffer) const
{
    Mutex::ScopedLock locker(contentLock);
    if (content.get()) content->encode(buffer);
}

uint32_t BasicMessage::encodedSize() const
{
    return  encodedHeaderSize() + encodedContentSize();
}

uint32_t BasicMessage::encodedContentSize() const
{
    Mutex::ScopedLock locker(contentLock);
    return content.get() ? content->size() : 0;
}

uint32_t BasicMessage::encodedHeaderSize() const
{
    return RecoveryManagerImpl::encodedMessageTypeSize()
        +getExchange().size() + 1
        + getRoutingKey().size() + 1
        + header->size() + 4;//4 extra bytes for size
}

uint64_t BasicMessage::expectedContentSize()
{
    return header.get() ? header->getContentSize() : 0;
}

void BasicMessage::releaseContent(MessageStore* store)
{
    Mutex::ScopedLock locker(contentLock);
    if (!isPersistent() && getPersistenceId() == 0) {
        store->stage(*this);
    }
    if (!content.get() || content->size() > 0) {
        //set content to lazy loading mode (but only if there is
        //stored content):

        //Note: the LazyLoadedContent instance contains a raw pointer
        //to the message, however it is then set as a member of that
        //message so its lifetime is guaranteed to be no longer than
        //that of the message itself
        content = std::auto_ptr<Content>(
            new LazyLoadedContent(store, this, expectedContentSize()));
    }
}

void BasicMessage::setContent(std::auto_ptr<Content>& _content)
{
    Mutex::ScopedLock locker(contentLock);
    content = _content;
}
