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

#include "BrokerMessage.h"
#include <iostream>

#include "InMemoryContent.h"
#include "LazyLoadedContent.h"
#include "MessageStore.h"
#include "BrokerQueue.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/BasicDeliverBody.h"
#include "qpid/framing/BasicGetOkBody.h"
#include "qpid/framing/BasicPublishBody.h"
#include "qpid/framing/AMQContentBody.h"
#include "qpid/framing/AMQHeaderBody.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ChannelAdapter.h"
#include "RecoveryManagerImpl.h"

namespace qpid{
namespace broker{

struct BasicGetToken : DeliveryToken
{
    typedef boost::shared_ptr<BasicGetToken> shared_ptr;

    Queue::shared_ptr queue;

    BasicGetToken(Queue::shared_ptr q) : queue(q) {}
};

struct BasicConsumeToken : DeliveryToken 
{
    typedef boost::shared_ptr<BasicConsumeToken> shared_ptr;

    const string consumer;

    BasicConsumeToken(const string c) : consumer(c) {}
};

}
}

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

BasicMessage::BasicMessage(
    const ConnectionToken* const _publisher, 
    const string& _exchange, const string& _routingKey, 
    bool _mandatory, bool _immediate
) :
    Message(_publisher, _exchange, _routingKey, _mandatory,
            _immediate, framing::AMQMethodBody::shared_ptr(new BasicPublishBody(ProtocolVersion(0,9)))),
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

DeliveryToken::shared_ptr BasicMessage::createGetToken(Queue::shared_ptr queue)
{
    return DeliveryToken::shared_ptr(new BasicGetToken(queue));
}

DeliveryToken::shared_ptr BasicMessage::createConsumeToken(const string& consumer)
{
    return DeliveryToken::shared_ptr(new BasicConsumeToken(consumer));
}

void BasicMessage::deliver(ChannelAdapter& channel, 
                           const string& consumerTag, DeliveryId id, 
                           uint32_t framesize)
{
    channel.send(make_shared_ptr(
    	new BasicDeliverBody(
            channel.getVersion(), consumerTag, id.getValue(),
            getRedelivered(), getExchange(), getRoutingKey())));
    sendContent(channel, framesize);
}

void BasicMessage::sendGetOk(ChannelAdapter& channel,
                             uint32_t messageCount,
                             DeliveryId id, 
                             uint32_t framesize)
{
    channel.send(make_shared_ptr(
        new BasicGetOkBody(
            channel.getVersion(),
            id.getValue(), getRedelivered(), getExchange(),
            getRoutingKey(), messageCount))); 
    sendContent(channel, framesize);
}

void BasicMessage::deliver(framing::ChannelAdapter& channel, DeliveryId id, DeliveryToken::shared_ptr token, uint32_t framesize)
{
    BasicConsumeToken::shared_ptr consume = dynamic_pointer_cast<BasicConsumeToken>(token);
    if (consume) {
        deliver(channel, consume->consumer, id, framesize);
    } else {
        BasicGetToken::shared_ptr get = dynamic_pointer_cast<BasicGetToken>(token);
        if (get) {
            sendGetOk(channel, get->queue->getMessageCount(), id.getValue(), framesize);
        } else {
            //TODO:
            //either need to be able to convert to a message transfer or
            //throw error of some kind to allow this to be handled higher up
            throw Exception("Conversion to BasicMessage not defined!");
        }
    }
}

void BasicMessage::sendContent(ChannelAdapter& channel, uint32_t framesize)
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
        QPID_LOG(error, "Expected " << expectedContentSize() << " bytes, got " << buffer.available());
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


uint32_t BasicMessage::getRequiredCredit() const
{
    return header->size() + contentSize();
}
