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
#include "Message.h"
#include "qpid/amqp/Decoder.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp/MessageEncoder.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/Buffer.h"
#include <string.h>

namespace qpid {
namespace broker {
namespace amqp {

namespace {
std::string empty;
}

std::string Message::getRoutingKey() const
{
    std::string v;
    v.assign(subject.data, subject.size);
    return v;
}
std::string Message::getUserId() const
{
    std::string v;
    v.assign(userId.data, userId.size);
    return v;
}

bool Message::isPersistent() const
{
    return durable && durable.get();
}
bool Message::getTtl(uint64_t& t) const
{
    if (!ttl) {
        return false;
    } else {
        t = ttl.get();
        return true;
    }
}

uint8_t Message::getPriority() const
{
    if (!priority) return 4;
    else return priority.get();
}

std::string Message::getPropertyAsString(const std::string& /*key*/) const { return empty; }
std::string Message::getAnnotationAsString(const std::string& /*key*/) const { return empty; }
void Message::processProperties(MapHandler&) const {}

//getContentSize() is primarily used in stats about the number of
//bytes enqueued/dequeued etc, not sure whether this is the right name
//and whether it should indeed only be the content that is thus
//measured
uint64_t Message::getContentSize() const { return data.size(); }
//getContent() is used primarily for decoding qmf messages in management and ha
std::string Message::getContent() const { return empty; }

Message::Message(size_t size) : data(size)
{
    deliveryAnnotations.init();
    messageAnnotations.init();
    bareMessage.init();

    userId.init();
    to.init();
    subject.init();
    replyTo.init();
    contentType.init();
    contentEncoding.init();

    applicationProperties.init();
    body.init();
    footer.init();
}
char* Message::getData() { return &data[0]; }
const char* Message::getData() const { return &data[0]; }
size_t Message::getSize() const { return data.size(); }

qpid::amqp::MessageId Message::getMessageId() const
{
    return messageId;
}
qpid::amqp::CharSequence Message::getReplyTo() const
{
    return replyTo;
}
qpid::amqp::MessageId Message::getCorrelationId() const
{
    return correlationId;
}
qpid::amqp::CharSequence Message::getContentType() const
{
    return contentType;
}
qpid::amqp::CharSequence Message::getContentEncoding() const
{
    return contentEncoding;
}

qpid::amqp::CharSequence Message::getDeliveryAnnotations() const
{
    return deliveryAnnotations;
}
qpid::amqp::CharSequence Message::getMessageAnnotations() const
{
    return messageAnnotations;
}
qpid::amqp::CharSequence Message::getApplicationProperties() const
{
    return applicationProperties;
}
qpid::amqp::CharSequence Message::getBareMessage() const
{
    return bareMessage;
}
qpid::amqp::CharSequence Message::getBody() const
{
    return body;
}
qpid::amqp::CharSequence Message::getFooter() const
{
    return footer;
}

void Message::scan()
{
    qpid::amqp::Decoder decoder(getData(), getSize());
    decoder.read(*this);
    bareMessage = qpid::amqp::MessageReader::getBareMessage();
    if (bareMessage.data && !bareMessage.size) {
        bareMessage.size = getSize() - (bareMessage.data - getData());
    }
}

const Message& Message::get(const qpid::broker::Message& message)
{
    const Message* m = dynamic_cast<const Message*>(&message.getEncoding());
    if (!m) throw qpid::Exception("Translation not yet implemented!!");
    return *m;
}

void Message::onDurable(bool b) { durable = b; }
void Message::onPriority(uint8_t i) { priority = i; }
void Message::onTtl(uint32_t i) { ttl = i; }
void Message::onFirstAcquirer(bool b) { firstAcquirer = b; }
void Message::onDeliveryCount(uint32_t i) { deliveryCount = i; }

void Message::onMessageId(uint64_t v) { messageId.set(v); }
void Message::onMessageId(const qpid::amqp::CharSequence& v, qpid::types::VariantType t) { messageId.set(v, t); }
void Message::onUserId(const qpid::amqp::CharSequence& v) { userId = v; }
void Message::onTo(const qpid::amqp::CharSequence& v) { to = v; }
void Message::onSubject(const qpid::amqp::CharSequence& v) { subject = v; }
void Message::onReplyTo(const qpid::amqp::CharSequence& v) { replyTo = v; }
void Message::onCorrelationId(uint64_t v) { correlationId.set(v); }
void Message::onCorrelationId(const qpid::amqp::CharSequence& v, qpid::types::VariantType t) { correlationId.set(v, t);}
void Message::onContentType(const qpid::amqp::CharSequence& v) { contentType = v; }
void Message::onContentEncoding(const qpid::amqp::CharSequence& v) { contentEncoding = v; }
void Message::onAbsoluteExpiryTime(int64_t) {}
void Message::onCreationTime(int64_t) {}
void Message::onGroupId(const qpid::amqp::CharSequence&) {}
void Message::onGroupSequence(uint32_t) {}
void Message::onReplyToGroupId(const qpid::amqp::CharSequence&) {}

void Message::onApplicationProperties(const qpid::amqp::CharSequence& v) { applicationProperties = v; }
void Message::onDeliveryAnnotations(const qpid::amqp::CharSequence& v) { deliveryAnnotations = v; }
void Message::onMessageAnnotations(const qpid::amqp::CharSequence& v) { messageAnnotations = v; }
void Message::onBody(const qpid::amqp::CharSequence& v, const qpid::amqp::Descriptor&) { body = v; }
void Message::onBody(const qpid::types::Variant&, const qpid::amqp::Descriptor&) {}
void Message::onFooter(const qpid::amqp::CharSequence& v) { footer = v; }


//PersistableMessage interface:
void Message::encode(framing::Buffer& buffer) const
{
    buffer.putLong(0);//4-byte format indicator
    buffer.putRawData((const uint8_t*) getData(), getSize());
    QPID_LOG(debug, "Encoded 1.0 message of " << getSize() << " bytes, including " << bareMessage.size << " bytes of 'bare message'");
}
uint32_t Message::encodedSize() const
{
    return 4/*format indicator*/ + data.size();
}
//in 1.0 the binary header/content makes less sense and in any case
//the functionality that split originally supported (i.e. lazy-loaded
//messages) is no longer in use; for 1.0 we therefore treat the whole
//content as 'header' and load it in the first stage.
uint32_t Message::encodedHeaderSize() const
{
    return encodedSize();
}
void Message::decodeHeader(framing::Buffer& buffer)
{
    if (buffer.available() != getSize()) {
        QPID_LOG(warning, "1.0 Message buffer was " << data.size() << " bytes, but " << buffer.available() << " bytes are available. Resizing.");
        data.resize(buffer.available());
    }
    buffer.getRawData((uint8_t*) getData(), getSize());
    scan();
    QPID_LOG(debug, "Decoded 1.0 message of " << getSize() << " bytes, including " << bareMessage.size << " bytes of 'bare message'");
}
void Message::decodeContent(framing::Buffer& /*buffer*/) {}

boost::intrusive_ptr<PersistableMessage> Message::merge(const std::map<std::string, qpid::types::Variant>& annotations) const
{
    //message- or delivery- annotations? would have to determine that from the name, for now assume always message-annotations
    size_t extra = 0;
    if (messageAnnotations) {
        //TODO: actual merge required
    } else {
        //add whole new section
        extra = qpid::amqp::MessageEncoder::getEncodedSize(annotations, true);
    }
    boost::intrusive_ptr<Message> copy(new Message(data.size()+extra));
    size_t position(0);
    if (deliveryAnnotations) {
        ::memcpy(&copy->data[position], deliveryAnnotations.data, deliveryAnnotations.size);
        position += deliveryAnnotations.size;
    }
    if (messageAnnotations) {
        //TODO: actual merge required
        ::memcpy(&copy->data[position], messageAnnotations.data, messageAnnotations.size);
        position += messageAnnotations.size;
    } else {
        qpid::amqp::MessageEncoder encoder(&copy->data[position], extra);
        encoder.writeMap(annotations, &qpid::amqp::message::MESSAGE_ANNOTATIONS, true);
        position += extra;
    }
    if (bareMessage) {
        ::memcpy(&copy->data[position], bareMessage.data, bareMessage.size);
        position += bareMessage.size;
    }
    if (footer) {
        ::memcpy(&copy->data[position], footer.data, footer.size);
        position += footer.size;
    }
    copy->scan();
    return copy;
}

}}} // namespace qpid::broker::amqp
