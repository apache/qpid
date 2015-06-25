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
#include "MessageImpl.h"
#include "qpid/messaging/Message.h"

namespace qpid {
namespace messaging {

namespace {
const std::string EMPTY_STRING = "";
}

using namespace qpid::types;

MessageImpl::MessageImpl(const std::string& c) :
    priority(0),
    ttl(0),
    durable(false),
    redelivered(false),
    bytes(c),
    contentDecoded(false),
    internalId(0) {}
MessageImpl::MessageImpl(const char* chars, size_t count) :
    priority(0),
    ttl(0),
    durable (false),
    redelivered(false),
    bytes(chars, count),
    contentDecoded(false),
    internalId(0) {}

void MessageImpl::clear()
{
    replyTo = Address();
    subject = std::string();
    contentType = std::string();
    messageId = std::string();
    userId= std::string();
    correlationId = std::string();
    priority = 0;
    ttl = 0;
    durable = false;
    redelivered = false;
    headers = qpid::types::Variant::Map();

    bytes = std::string();
    content = qpid::types::Variant();
    contentDecoded = false;
    encoded = boost::shared_ptr<const qpid::messaging::amqp::EncodedMessage>();
    internalId = 0;
}

void MessageImpl::setReplyTo(const Address& d)
{
    replyTo = d;
    updated();
}
const Address& MessageImpl::getReplyTo() const
{
    if (!replyTo && encoded) encoded->getReplyTo(replyTo);
    return replyTo;
}

void MessageImpl::setSubject(const std::string& s)
{
    subject = s;
    updated();
}
const std::string& MessageImpl::getSubject() const
{
    if (!subject.size() && encoded) encoded->getSubject(subject);
    return subject;
}

void MessageImpl::setContentType(const std::string& s)
{
    contentType = s;
    updated();
}
const std::string& MessageImpl::getContentType() const
{
    if (!contentType.size() && encoded) encoded->getContentType(contentType);
    return contentType;
}

void MessageImpl::setMessageId(const std::string& s)
{
    messageId = s;
    updated();
}
const std::string& MessageImpl::getMessageId() const
{
    if (!messageId.size() && encoded) encoded->getMessageId(messageId);
    return messageId;
}
void MessageImpl::setUserId(const std::string& s)
{
    userId = s;
    updated();
}
const std::string& MessageImpl::getUserId() const
{
    if (!userId.size() && encoded) encoded->getUserId(userId);
    return userId;
}
void MessageImpl::setCorrelationId(const std::string& s)
{
    correlationId = s;
    updated();
}
const std::string& MessageImpl::getCorrelationId() const
{
    if (!correlationId.size() && encoded) encoded->getCorrelationId(correlationId);
    return correlationId;
}
void MessageImpl::setPriority(uint8_t p)
{
    priority = p;
}
uint8_t MessageImpl::getPriority() const
{
    return priority;
}
void MessageImpl::setTtl(uint64_t t)
{
    ttl = t;
}
uint64_t MessageImpl::getTtl() const
{
    return ttl;
}
void MessageImpl::setDurable(bool d)
{
    durable = d;
}
bool MessageImpl::isDurable() const
{
    return durable;
}
void MessageImpl::setRedelivered(bool b)
{
    redelivered = b;
}
bool MessageImpl::isRedelivered() const
{
    return redelivered;
}

const Variant::Map& MessageImpl::getHeaders() const
{
    if (!headers.size() && encoded) encoded->populate(headers);
    return headers;
}
Variant::Map& MessageImpl::getHeaders() {
    if (!headers.size() && encoded) encoded->populate(headers);
    updated();
    return headers;
}
void MessageImpl::setHeader(const std::string& key, const qpid::types::Variant& val)
{
    headers[key] = val; updated();
}

//should these methods be on MessageContent?
void MessageImpl::setBytes(const std::string& c)
{
    bytes = c;
    updated();
}
void MessageImpl::setBytes(const char* chars, size_t count)
{
    bytes.assign(chars, count);
    updated();
}
const std::string& MessageImpl::getBytes() const
{
    if (encoded && !contentDecoded) {
        encoded->getBody(bytes, content);
        contentDecoded = true;
    }
    if (bytes.empty() && content.getType() == VAR_STRING) return content.getString();
    else return bytes;
}
std::string& MessageImpl::getBytes()
{
    updated();//have to assume body may be edited, invalidating our message
    if (bytes.empty() && content.getType() == VAR_STRING) return content.getString();
    else return bytes;
}

qpid::types::Variant& MessageImpl::getContent()
{
    updated();//have to assume content may be edited, invalidating our message
    return content;
}

const qpid::types::Variant& MessageImpl::getContent() const
{
    if (encoded && !contentDecoded) {
        encoded->getBody(bytes, content);
        contentDecoded = true;
    }
    return content;
}

void MessageImpl::setInternalId(qpid::framing::SequenceNumber i) { internalId = i; }
qpid::framing::SequenceNumber MessageImpl::getInternalId() { return internalId; }

void MessageImpl::updated()
{

    if (!replyTo && encoded) encoded->getReplyTo(replyTo);
    if (!subject.size() && encoded) encoded->getSubject(subject);
    if (!contentType.size() && encoded) encoded->getContentType(contentType);
    if (!messageId.size() && encoded) encoded->getMessageId(messageId);
    if (!userId.size() && encoded) encoded->getUserId(userId);
    if (!correlationId.size() && encoded) encoded->getCorrelationId(correlationId);
    if (!headers.size() && encoded) encoded->populate(headers);
    if (encoded && !contentDecoded) {
        encoded->getBody(bytes, content);
        contentDecoded = true;
    }

    encoded.reset();
}

MessageImpl& MessageImplAccess::get(Message& msg)
{
    return *msg.impl;
}
const MessageImpl& MessageImplAccess::get(const Message& msg)
{
    return *msg.impl;
}
}} // namespace qpid::messaging
