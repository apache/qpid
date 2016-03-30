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
#include <qpid/Exception.h>
#include <boost/format.hpp>

namespace qpid {
namespace messaging {

using namespace qpid::types;

Message::Message(const std::string& bytes) : impl(new MessageImpl(bytes)) {}
Message::Message(const char* bytes, size_t count) : impl(new MessageImpl(bytes, count)) {}
Message::Message(qpid::types::Variant& c) : impl(new MessageImpl(std::string()))
{
    setContentObject(c);
}

Message::Message(const Message& m) : impl(new MessageImpl(*m.impl)) {}
Message::~Message() { delete impl; }

Message& Message::operator=(const Message& m) { *impl = *m.impl; return *this; }

void Message::setReplyTo(const Address& d) { impl->setReplyTo(d); }
const Address& Message::getReplyTo() const { return impl->getReplyTo(); }

void Message::setSubject(const std::string& s) { impl->setSubject(s); }
const std::string& Message::getSubject() const { return impl->getSubject(); }

void Message::setContentType(const std::string& s) { impl->setContentType(s); }
const std::string& Message::getContentType() const { return impl->getContentType(); }

void Message::setMessageId(const std::string& id) { impl->setMessageId(id); }
const std::string& Message::getMessageId() const { return impl->getMessageId(); }

void Message::setUserId(const std::string& id) { impl->setUserId(id); }
const std::string& Message::getUserId() const { return impl->getUserId(); }

void Message::setCorrelationId(const std::string& id) { impl->setCorrelationId(id); }
const std::string& Message::getCorrelationId() const { return impl->getCorrelationId(); }

uint8_t Message::getPriority() const { return impl->getPriority(); }
void Message::setPriority(uint8_t priority) { impl->setPriority(priority); }

void Message::setTtl(Duration ttl) { impl->setTtl(ttl.getMilliseconds()); }
Duration Message::getTtl() const { return Duration(impl->getTtl()); }

void Message::setDurable(bool durable) { impl->setDurable(durable); }
bool Message::getDurable() const { return impl->isDurable(); }

bool Message::getRedelivered() const { return impl->isRedelivered(); }
void Message::setRedelivered(bool redelivered) { impl->setRedelivered(redelivered); }

const Variant::Map& Message::getProperties() const { return impl->getHeaders(); }
Variant::Map& Message::getProperties() { return impl->getHeaders(); }
void Message::setProperties(const Variant::Map& p) { getProperties() = p; }
void Message::setProperty(const std::string& k, const qpid::types::Variant& v) { impl->setHeader(k,v); }

void Message::setContent(const std::string& c) { this->setContentBytes(c); }
void Message::setContent(const char* chars, size_t count) {
    impl->getContent().reset();
    impl->setBytes(chars, count);
}
std::string Message::getContent() const { return impl->getBytes(); }

void Message::setContentBytes(const std::string& c) {
    impl->getContent().reset();
    impl->setBytes(c);
}
std::string Message::getContentBytes() const {return impl->getBytes(); }

qpid::types::Variant& Message::getContentObject() { return impl->getContent(); }
void Message::setContentObject(const qpid::types::Variant& c) { impl->getContent() = c; }
const qpid::types::Variant& Message::getContentObject() const { return impl->getContent(); }

const char* Message::getContentPtr() const
{
    return impl->getBytes().data();
}

size_t Message::getContentSize() const
{
    return impl->getBytes().size();
}

EncodingException::EncodingException(const std::string& msg) : qpid::types::Exception(msg) {}

const std::string BAD_ENCODING("Unsupported encoding: %1% (only %2% is supported at present).");

template <class C> struct MessageCodec
{
    static bool checkEncoding(const std::string& requested)
    {
        if (requested.size()) {
            if (requested == C::contentType) return true;
            else throw EncodingException((boost::format(BAD_ENCODING) % requested % C::contentType).str());
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
    static void checkEncoding(const Message& message, const std::string& requested)
    {
        checkEncoding(requested) || checkEncoding(message.getContentType());
    }

    static void decode(const Message& message, typename C::ObjectType& object, const std::string& encoding)
    {
        checkEncoding(message, encoding);
        try {
            C::decode(message.getContent(), object);
        } catch (const qpid::Exception &ex) {
            throw EncodingException(ex.what());
        }
    }

    static void encode(const typename C::ObjectType& map, Message& message, const std::string& encoding)
    {
        checkEncoding(message, encoding);
        std::string content;
        C::encode(map, content);
        message.setContentType(C::contentType);
        message.setContent(content);
    }
};

void decode(const Message& message, Variant::Map& map, const std::string& encoding)
{
    MessageCodec<qpid::amqp_0_10::MapCodec>::decode(message, map, encoding);
}
void decode(const Message& message, Variant::List& list, const std::string& encoding)
{
    MessageCodec<qpid::amqp_0_10::ListCodec>::decode(message, list, encoding);
}
void encode(const Variant::Map& map, Message& message, const std::string& encoding)
{
    MessageCodec<qpid::amqp_0_10::MapCodec>::encode(map, message, encoding);
}
void encode(const Variant::List& list, Message& message, const std::string& encoding)
{
    MessageCodec<qpid::amqp_0_10::ListCodec>::encode(list, message, encoding);
}

}} // namespace qpid::messaging
