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

Message::Message(const Message& m) : impl(new MessageImpl(m.getBytes())) {}
Message::~Message() { delete impl; }

Message& Message::operator=(const Message& m) { *impl = *m.impl; return *this; }

void Message::setReplyTo(const Address& d) { impl->setReplyTo(d); }
const Address& Message::getReplyTo() const { return impl->getReplyTo(); }

void Message::setSubject(const std::string& s) { impl->setSubject(s); }
const std::string& Message::getSubject() const { return impl->getSubject(); }

void Message::setContentType(const std::string& s) { impl->setContentType(s); }
const std::string& Message::getContentType() const { return impl->getContentType(); }

const VariantMap& Message::getHeaders() const { return impl->getHeaders(); }
VariantMap& Message::getHeaders() { return impl->getHeaders(); }

void Message::setBytes(const std::string& c) { impl->setBytes(c); }
void Message::setBytes(const char* chars, size_t count) { impl->setBytes(chars, count); }
const std::string& Message::getBytes() const { return impl->getBytes(); }
std::string& Message::getBytes() { return impl->getBytes(); }

const char* Message::getRawContent() const { return impl->getBytes().data(); }
size_t Message::getContentSize() const { return impl->getBytes().size(); }

MessageContent& Message::getContent() { return *impl; }
const MessageContent& Message::getContent() const { return *impl; }
void Message::setContent(const std::string& s) { *impl = s; }
void Message::setContent(const Variant::Map& m) { *impl = m; }
void Message::setContent(const Variant::List& l) { *impl = l; }

void Message::encode(Codec& codec) { impl->encode(codec); }

void Message::decode(Codec& codec) { impl->decode(codec); }

std::ostream& operator<<(std::ostream& out, const MessageContent& content)
{
    return content.print(out);
}

}} // namespace qpid::messaging
