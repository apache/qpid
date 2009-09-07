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

MessageImpl::MessageImpl(const std::string& c) : bytes(c), type(VAR_VOID), internalId(0) {}
MessageImpl::MessageImpl(const char* chars, size_t count) : bytes(chars, count), type(VAR_VOID), internalId(0) {}

void MessageImpl::setReplyTo(const Address& d) { replyTo = d; }
const Address& MessageImpl::getReplyTo() const { return replyTo; }

void MessageImpl::setSubject(const std::string& s) { subject = s; }
const std::string& MessageImpl::getSubject() const { return subject; }

void MessageImpl::setContentType(const std::string& s) { contentType = s; }
const std::string& MessageImpl::getContentType() const { return contentType; }

const VariantMap& MessageImpl::getHeaders() const { return headers; }
VariantMap& MessageImpl::getHeaders() { return headers; }

//should these methods be on MessageContent?
void MessageImpl::setBytes(const std::string& c) { clear(); bytes = c; }
void MessageImpl::setBytes(const char* chars, size_t count) { clear(); bytes.assign(chars, count); }
const std::string& MessageImpl::getBytes() const { return bytes; }
std::string& MessageImpl::getBytes() { return bytes; }


Variant& MessageImpl::operator[](const std::string& key) { return asMap()[key]; }

std::ostream& MessageImpl::print(std::ostream& out) const
{
    if (type == VAR_MAP) {
        return out << content.asMap();
    } else if (type == VAR_LIST) {
        return out << content.asList();
    } else {
        return out << bytes;
    }
}

template <class T> MessageContent& MessageImpl::append(T& t)
{
    if (type == VAR_VOID) {
        //TODO: this is inefficient, probably want to hold on to the stream object
        std::stringstream s;
        s << bytes;
        s << t;
        bytes = s.str();
    } else if (type == VAR_LIST) {
        content.asList().push_back(Variant(t));
    } else {
        throw InvalidConversion("<< operator only valid on strings and lists");
    }
    return *this;
}

MessageContent& MessageImpl::operator<<(const std::string& v) { return append(v); }
MessageContent& MessageImpl::operator<<(const char* v) { return append(v); }
MessageContent& MessageImpl::operator<<(bool v) { return append(v); }
MessageContent& MessageImpl::operator<<(int8_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(int16_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(int32_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(int64_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(uint8_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(uint16_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(uint32_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(uint64_t v) { return append(v); }
MessageContent& MessageImpl::operator<<(double v) { return append(v); }
MessageContent& MessageImpl::operator<<(float v) { return append(v); }
MessageContent& MessageImpl::operator=(const std::string& s) 
{ 
    type = VAR_VOID;
    bytes = s; 
    return *this;
}
MessageContent& MessageImpl::operator=(const char* c) 
{ 
    type = VAR_VOID;
    bytes = c;
    return *this;
}
MessageContent& MessageImpl::operator=(const Variant::Map& m)
{ 
    type = VAR_MAP;
    content = m; 
    return *this;
}

MessageContent& MessageImpl::operator=(const Variant::List& l)
{ 
    type = VAR_LIST;
    content = l; 
    return *this;
}

void MessageImpl::encode(Codec& codec)
{
    if (content.getType() != VAR_VOID) {
        bytes = EMPTY_STRING;
        codec.encode(content, bytes);
    }
}

void MessageImpl::getEncodedContent(Codec& codec, std::string& out) const
{
    if (content.getType() != VAR_VOID) {
        codec.encode(content, out);
    } else {
        out = bytes;
    }
}

void MessageImpl::decode(Codec& codec) 
{
    codec.decode(bytes, content);    
    if (content.getType() == VAR_MAP) type = VAR_MAP;
    else if (content.getType() == VAR_LIST) type = VAR_LIST;
    else type = VAR_VOID;//TODO: what if codec set some type other than map or list??
}

void MessageImpl::setInternalId(qpid::framing::SequenceNumber i) { internalId = i; }
qpid::framing::SequenceNumber MessageImpl::getInternalId() { return internalId; }

bool MessageImpl::isVoid() const { return type == VAR_VOID; }

const std::string& MessageImpl::asString() const 
{ 
    if (isVoid()) return getBytes();
    else return content.getString();//will throw an error
}
std::string& MessageImpl::asString()
{ 
    if (isVoid()) return getBytes();
    else return content.getString();//will throw an error
}

const char* MessageImpl::asChars() const
{
    if (!isVoid()) throw InvalidConversion("Content is of structured type.");
    return bytes.data();
}
size_t MessageImpl::size() const
{
    return bytes.size();
}

const Variant::Map& MessageImpl::asMap() const { return content.asMap(); }
Variant::Map& MessageImpl::asMap()
{ 
    if (isVoid()) {
        content = Variant::Map(); 
        type = VAR_MAP; 
    }
    return content.asMap(); 
}
bool MessageImpl::isMap() const { return type == VAR_MAP; }

const Variant::List& MessageImpl::asList() const { return content.asList(); }
Variant::List& MessageImpl::asList()
{ 
    if (isVoid()) {
        content = Variant::List();
        type = VAR_LIST; 
    }
    return content.asList(); 
}
bool MessageImpl::isList() const { return type == VAR_LIST; }

void MessageImpl::clear() { bytes = EMPTY_STRING; content.reset(); type = VAR_VOID; } 

MessageImpl& MessageImplAccess::get(Message& msg)
{
    return *msg.impl;
}
const MessageImpl& MessageImplAccess::get(const Message& msg)
{
    return *msg.impl;
}

}} // namespace qpid::messaging
