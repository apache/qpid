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

namespace qpid {
namespace messaging {

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

void Message::setTtl(uint64_t ttl) { impl->ttl = ttl; }
uint64_t Message::getTtl() const { return impl->ttl; }

void Message::setDurable(bool durable) { impl->durable = durable; }
bool Message::getDurable() const { return impl->durable; }

bool Message::isRedelivered() const { return impl->redelivered; }

const VariantMap& Message::getHeaders() const { return impl->getHeaders(); }
VariantMap& Message::getHeaders() { return impl->getHeaders(); }

void Message::setContent(const std::string& c) { impl->setBytes(c); }
void Message::setContent(const char* chars, size_t count) { impl->setBytes(chars, count); }
const std::string& Message::getContent() const { return impl->getBytes(); }
std::string& Message::getContent() { return impl->getBytes(); }

void Message::getContent(std::pair<const char*, size_t>& content) const
{
    content.first = impl->getBytes().data();
    content.second = impl->getBytes().size();
}

}} // namespace qpid::messaging
