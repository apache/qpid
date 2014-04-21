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
#include "qpid/messaging/amqp/EncodedMessage.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/Exception.h"
#include "qpid/amqp/Decoder.h"
#include "qpid/amqp/DataBuilder.h"
#include "qpid/amqp/ListBuilder.h"
#include "qpid/amqp/MapBuilder.h"
#include "qpid/amqp/typecodes.h"
#include "qpid/types/encodings.h"
#include "qpid/log/Statement.h"
#include <boost/lexical_cast.hpp>
#include <string.h>

namespace qpid {
namespace messaging {
namespace amqp {
using namespace qpid::amqp;

EncodedMessage::EncodedMessage(size_t s) : size(s), data(size ? new char[size] : 0), nestAnnotations(false)
{
    init();
}

EncodedMessage::EncodedMessage() : size(0), data(0), nestAnnotations(false)
{
    init();
}

EncodedMessage::EncodedMessage(const EncodedMessage& other) : size(other.size), data(size ? new char[size] : 0), nestAnnotations(false)
{
    init();
}

void EncodedMessage::init()
{
    //init all CharSequence members
    deliveryAnnotations.init();
    messageAnnotations.init();
    userId.init();
    to.init();
    subject.init();
    replyTo.init();
    contentType.init();
    contentEncoding.init();
    groupId.init();
    replyToGroupId.init();
    applicationProperties.init();
    body.init();
    footer.init();
}

EncodedMessage::~EncodedMessage()
{
    delete[] data;
}

size_t EncodedMessage::getSize() const
{
    return size;
}
void EncodedMessage::trim(size_t t)
{
    size = t;
}
void EncodedMessage::resize(size_t s)
{
    delete[] data;
    size = s;
    data = new char[size];
}

char* EncodedMessage::getData()
{
    return data;
}
const char* EncodedMessage::getData() const
{
    return data;
}

void EncodedMessage::init(qpid::messaging::MessageImpl& impl)
{
    try {
        //initial scan of raw data
        qpid::amqp::Decoder decoder(data, size);
        InitialScan reader(*this, impl);
        decoder.read(reader);
        bareMessage = reader.getBareMessage();
        if (bareMessage.data && !bareMessage.size) {
            bareMessage.size = (data + size) - bareMessage.data;
        }
    } catch (const qpid::Exception& e) {
        throw FetchError(e.what());
    }
}
void EncodedMessage::setNestAnnotationsOption(bool b) { nestAnnotations = b; }

void EncodedMessage::populate(qpid::types::Variant::Map& map) const
{
    try {
        //decode application properties
        if (applicationProperties) {
            qpid::amqp::Decoder decoder(applicationProperties.data, applicationProperties.size);
            decoder.readMap(map);
        }
        //add in 'x-amqp-' prefixed values
        if (!!firstAcquirer) {
            map["x-amqp-first-acquirer"] = firstAcquirer.get();
        }
        if (!!deliveryCount) {
            map["x-amqp-delivery-count"] = deliveryCount.get();
        }
        if (to) {
            map["x-amqp-to"] = to.str();
        }
        if (!!absoluteExpiryTime) {
            map["x-amqp-absolute-expiry-time"] = absoluteExpiryTime.get();
        }
        if (!!creationTime) {
            map["x-amqp-creation-time"] = creationTime.get();
        }
        if (groupId) {
            map["x-amqp-group-id"] = groupId.str();
        }
        if (!!groupSequence) {
            map["x-amqp-group-sequence"] = groupSequence.get();
        }
        if (replyToGroupId) {
            map["x-amqp-reply-to-group-id"] = replyToGroupId.str();
        }
        //add in any annotations
        if (deliveryAnnotations) {
            qpid::amqp::Decoder decoder(deliveryAnnotations.data, deliveryAnnotations.size);
            if (nestAnnotations) {
                map["x-amqp-delivery-annotations"] = decoder.readMap();
            } else {
                decoder.readMap(map);
            }
        }
        if (messageAnnotations) {
            qpid::amqp::Decoder decoder(messageAnnotations.data, messageAnnotations.size);
            if (nestAnnotations) {
                map["x-amqp-message-annotations"] = decoder.readMap();
            } else {
                decoder.readMap(map);
            }
        }
    } catch (const qpid::Exception& e) {
        throw FetchError(e.what());
    }
}
qpid::amqp::CharSequence EncodedMessage::getBareMessage() const
{
    return bareMessage;
}

void EncodedMessage::getReplyTo(qpid::messaging::Address& a) const
{
    std::string rt = replyTo.str();
    std::string::size_type i = rt.find('/');
    if (i != std::string::npos && i > 0 && rt.find('/', i+1) == std::string::npos) {
        //handle <name>/<subject> special case
        a.setName(rt.substr(0, i));
        a.setSubject(rt.substr(i+1));
    } else {
        a.setName(rt);
    }
}
void EncodedMessage::getSubject(std::string& s) const
{
    s.assign(subject.data, subject.size);
}
void EncodedMessage::getContentType(std::string& s) const
{
    s.assign(contentType.data, contentType.size);
}
void EncodedMessage::getUserId(std::string& s) const
{
    s.assign(userId.data, userId.size);
}
void EncodedMessage::getMessageId(std::string& s) const
{
    messageId.assign(s);
}
void EncodedMessage::getCorrelationId(std::string& s) const
{
    correlationId.assign(s);
}
void EncodedMessage::getBody(std::string& raw, qpid::types::Variant& c) const
{
    try {
        if (!content.isVoid()) {
            c = content;//integer types, floats, bool etc
            //TODO: populate raw data?
        } else {
            if (bodyType.empty()
                || bodyType == qpid::amqp::typecodes::BINARY_NAME
                || bodyType == qpid::types::encodings::UTF8
                || bodyType == qpid::types::encodings::ASCII)
            {
                c = std::string(body.data, body.size);
                c.setEncoding(bodyType);
            } else if (bodyType == qpid::amqp::typecodes::LIST_NAME) {
                qpid::amqp::ListBuilder builder;
                qpid::amqp::Decoder decoder(body.data, body.size);
                decoder.read(builder);
                c = builder.getList();
                raw.assign(body.data, body.size);
            } else if (bodyType == qpid::amqp::typecodes::MAP_NAME) {
                qpid::amqp::DataBuilder builder(new qpid::types::Variant::Map());
                qpid::amqp::Decoder decoder(body.data, body.size);
                decoder.read(builder);
                c = builder.getValue().asMap();
                raw.assign(body.data, body.size);
            } else if (bodyType == qpid::amqp::typecodes::UUID_NAME) {
                if (body.size == qpid::types::Uuid::SIZE) c = qpid::types::Uuid(body.data);
                raw.assign(body.data, body.size);
            } else if (bodyType == qpid::amqp::typecodes::ARRAY_NAME) {
                raw.assign(body.data, body.size);
            }
        }
    } catch (const qpid::Exception& e) {
        throw FetchError(e.what());
    }
}

qpid::amqp::CharSequence EncodedMessage::getBody() const
{
    return body;
}

bool EncodedMessage::hasHeaderChanged(const qpid::messaging::MessageImpl& msg) const
{
    if (!durable) {
        if (msg.isDurable()) return true;
    } else {
        if (durable.get() != msg.isDurable()) return true;
    }

    if (!priority) {
        if (msg.getPriority() != 4) return true;
    } else {
        if (priority.get() != msg.getPriority()) return true;
    }

    if (msg.getTtl() && (!ttl || msg.getTtl() != ttl.get())) {
        return true;
    }

    //first-acquirer can't be changed via Message interface as yet

    if (msg.isRedelivered() && (!deliveryCount || deliveryCount.get() == 0)) {
        return true;
    }

    return false;
}



EncodedMessage::InitialScan::InitialScan(EncodedMessage& e, qpid::messaging::MessageImpl& m) : em(e), mi(m)
{
    //set up defaults as needed:
    mi.setPriority(4);
}
//header:
void EncodedMessage::InitialScan::onDurable(bool b) { mi.setDurable(b); em.durable = b; }
void EncodedMessage::InitialScan::onPriority(uint8_t i) { mi.setPriority(i); em.priority = i; }
void EncodedMessage::InitialScan::onTtl(uint32_t i) { mi.setTtl(i); em.ttl = i; }
void EncodedMessage::InitialScan::onFirstAcquirer(bool b) { em.firstAcquirer = b; }
void EncodedMessage::InitialScan::onDeliveryCount(uint32_t i)
{
    mi.setRedelivered(i);
    em.deliveryCount = i;
}

//properties:
void EncodedMessage::InitialScan::onMessageId(uint64_t v) { em.messageId.set(v); }
void EncodedMessage::InitialScan::onMessageId(const qpid::amqp::CharSequence& v, qpid::types::VariantType t) { em.messageId.set(v, t); }
void EncodedMessage::InitialScan::onUserId(const qpid::amqp::CharSequence& v) { em.userId = v; }
void EncodedMessage::InitialScan::onTo(const qpid::amqp::CharSequence& v) { em.to = v; }
void EncodedMessage::InitialScan::onSubject(const qpid::amqp::CharSequence& v) { em.subject = v; }
void EncodedMessage::InitialScan::onReplyTo(const qpid::amqp::CharSequence& v) { em.replyTo = v;}
void EncodedMessage::InitialScan::onCorrelationId(uint64_t v) { em.correlationId.set(v); }
void EncodedMessage::InitialScan::onCorrelationId(const qpid::amqp::CharSequence& v, qpid::types::VariantType t) { em.correlationId.set(v, t); }
void EncodedMessage::InitialScan::onContentType(const qpid::amqp::CharSequence& v) { em.contentType = v; }
void EncodedMessage::InitialScan::onContentEncoding(const qpid::amqp::CharSequence& v) { em.contentEncoding = v; }
void EncodedMessage::InitialScan::onAbsoluteExpiryTime(int64_t i) { em.absoluteExpiryTime = i; }
void EncodedMessage::InitialScan::onCreationTime(int64_t i) { em.creationTime = i; }
void EncodedMessage::InitialScan::onGroupId(const qpid::amqp::CharSequence& v) { em.groupId = v; }
void EncodedMessage::InitialScan::onGroupSequence(uint32_t i) { em.groupSequence = i; }
void EncodedMessage::InitialScan::onReplyToGroupId(const qpid::amqp::CharSequence& v) { em.replyToGroupId = v; }

void EncodedMessage::InitialScan::onApplicationProperties(const qpid::amqp::CharSequence& v, const qpid::amqp::CharSequence&) { em.applicationProperties = v; }
void EncodedMessage::InitialScan::onDeliveryAnnotations(const qpid::amqp::CharSequence& v, const qpid::amqp::CharSequence&) { em.deliveryAnnotations = v; }
void EncodedMessage::InitialScan::onMessageAnnotations(const qpid::amqp::CharSequence& v, const qpid::amqp::CharSequence&) { em.messageAnnotations = v; }

void EncodedMessage::InitialScan::onData(const qpid::amqp::CharSequence& v)
{
    em.body = v;
}
void EncodedMessage::InitialScan::onAmqpSequence(const qpid::amqp::CharSequence& v)
{
    em.body = v;
    em.bodyType = qpid::amqp::typecodes::LIST_NAME;
}
void EncodedMessage::InitialScan::onAmqpValue(const qpid::amqp::CharSequence& v, const std::string& type)
{
    em.body = v;
    if (type == qpid::amqp::typecodes::STRING_NAME) {
        em.bodyType = qpid::types::encodings::UTF8;
    } else if (type == qpid::amqp::typecodes::SYMBOL_NAME) {
        em.bodyType = qpid::types::encodings::ASCII;
    } else {
        em.bodyType = type;
    }
}
void EncodedMessage::InitialScan::onAmqpValue(const qpid::types::Variant& v)
{
    em.content = v;
}

void EncodedMessage::InitialScan::onFooter(const qpid::amqp::CharSequence& v, const qpid::amqp::CharSequence&) { em.footer = v; }

}}} // namespace qpid::messaging::amqp
