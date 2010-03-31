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
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/amqp_0_10/Codecs.h"
#include <boost/format.hpp>

namespace qpid {
namespace messaging {

using namespace qpid::types;

Message::Message(const std::string& bytes) : impl(new MessageImpl(bytes)) {}
Message::Message(const char* bytes, size_t count) : impl(new MessageImpl(bytes, count)) {}

Message::Message(const Message& m) : impl(new MessageImpl(*m.impl)) {}
Message::~Message() { delete impl; }

Message& Message::operator=(const Message& m) { *impl = *m.impl; return *this; }

void Message::setReplyTo(const Address& d) { impl->setReplyTo(d); }
const Address& Message::getReplyTo() const { return impl->getReplyTo(); }

void Message::setSubject(const std::string& s) { impl->setSubject(s); }
const std::string& Message::getSubject() const { return impl->getSubject(); }

void Message::setContentType(const std::string& s) { impl->setContentType(s); }
const std::string& Message::getContentType() const { return impl->getContentType(); }

void Message::setMessageId(const std::string& id) { impl->messageId = id; }
const std::string& Message::getMessageId() const { return impl->messageId; }

void Message::setUserId(const std::string& id) { impl->userId = id; }
const std::string& Message::getUserId() const { return impl->userId; }

void Message::setCorrelationId(const std::string& id) { impl->correlationId = id; }
const std::string& Message::getCorrelationId() const { return impl->correlationId; }

uint8_t Message::getPriority() const { return impl->priority; }
void Message::setPriority(uint8_t priority) { impl->priority = priority; }

void Message::setTtl(Duration ttl) { impl->ttl = ttl.getMilliseconds(); }
Duration Message::getTtl() const { return Duration(impl->ttl); }

void Message::setDurable(bool durable) { impl->durable = durable; }
bool Message::getDurable() const { return impl->durable; }

bool Message::getRedelivered() const { return impl->redelivered; }
void Message::setRedelivered(bool redelivered) { impl->redelivered = redelivered; }

const Variant::Map& Message::getProperties() const { return impl->getHeaders(); }
Variant::Map& Message::getProperties() { return impl->getHeaders(); }

void Message::setContent(const std::string& c) { impl->setBytes(c); }
void Message::setContent(const char* chars, size_t count) { impl->setBytes(chars, count); }
std::string Message::getContent() const { return impl->getBytes(); }

const char* Message::getContentPtr() const
{
    return impl->getBytes().data();
}

size_t Message::getContentSize() const
{
    return impl->getBytes().size();
}


EncodingException::EncodingException(const std::string& msg) : qpid::Exception(msg) {}

const std::string BAD_ENCODING("Unsupported encoding: %1% (only %2% is supported at present).");

bool checkEncoding(const std::string& requested, const std::string& supported)
{
    if (requested.size()) {
        if (requested == supported) return true;
        else throw EncodingException((boost::format(BAD_ENCODING) % requested % supported).str());
    } else {
        return false;
    }
}

/*
 * Currently only support a single encoding type for both list and
 * map, based on AMQP 0-10, though wider support is anticipated in the
 * future. This method simply checks that the desired encoding (if one
 * is specified, either through the message-content or through an
 * override) is indeed supported.
 */
void checkEncoding(const Message& message, const std::string& requested, const std::string& supported)
{
    checkEncoding(requested, supported) || checkEncoding(message.getContentType(), supported);
}
 
void decode(const Message& message, Variant::Map& map, const std::string& encoding)
{
    checkEncoding(message, encoding, qpid::amqp_0_10::MapCodec::contentType);
    qpid::amqp_0_10::MapCodec::decode(message.getContent(), map);
}
void decode(const Message& message, Variant::List& list, const std::string& encoding)
{
    checkEncoding(message, encoding, qpid::amqp_0_10::ListCodec::contentType);
    qpid::amqp_0_10::ListCodec::decode(message.getContent(), list);
}
void encode(const Variant::Map& map, Message& message, const std::string& encoding)
{
    checkEncoding(message, encoding, qpid::amqp_0_10::MapCodec::contentType);
    std::string content;
    qpid::amqp_0_10::MapCodec::encode(map, content);
    message.setContent(content);
}
void encode(const Variant::List& list, Message& message, const std::string& encoding)
{
    checkEncoding(message, encoding, qpid::amqp_0_10::ListCodec::contentType);
    std::string content;
    qpid::amqp_0_10::ListCodec::encode(list, content);
    message.setContent(content);
}

}} // namespace qpid::messaging
