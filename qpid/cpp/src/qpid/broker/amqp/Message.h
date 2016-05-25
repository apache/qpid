#ifndef QPID_BROKER_AMQP_MESSAGE_H
#define QPID_BROKER_AMQP_MESSAGE_H

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
#include "qpid/broker/Message.h"
#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/MessageId.h"
#include "qpid/amqp/MessageReader.h"
#include <boost/optional.hpp>

namespace qpid {
namespace framing {
class Buffer;
}
namespace broker {
namespace amqp {

/**
 * Represents an AMQP 1.0 format message
 */
class Message : public qpid::broker::Message::SharedStateImpl, private qpid::amqp::MessageReader, public qpid::broker::PersistableMessage
{
  public:
    //Encoding interface:
    std::string getRoutingKey() const;
    bool isPersistent() const;
    uint8_t getPriority() const;
    uint64_t getMessageSize() const;
    std::string getPropertyAsString(const std::string& key) const;
    std::string getAnnotationAsString(const std::string& key) const;
    bool getTtl(uint64_t&) const;
    std::string getContent() const;
    void processProperties(qpid::amqp::MapHandler&) const;
    std::string printProperties() const;
    std::string getUserId() const;
    uint64_t getTimestamp() const;
    std::string getTo() const;
    std::string getSubject() const;
    std::string getReplyTo() const;
    qpid::amqp::MessageId getMessageId() const;
    qpid::amqp::MessageId getCorrelationId() const;

    qpid::amqp::CharSequence getReplyToAsCharSequence() const;
    qpid::amqp::CharSequence getContentType() const;
    qpid::amqp::CharSequence getContentEncoding() const;

    qpid::amqp::CharSequence getDeliveryAnnotations() const;
    qpid::amqp::CharSequence getMessageAnnotations() const;
    qpid::amqp::CharSequence getApplicationProperties() const;
    qpid::amqp::CharSequence getBareMessage() const;
    qpid::amqp::CharSequence getBody() const;
    qpid::amqp::CharSequence getFooter() const;
    bool isTypedBody() const;
    qpid::types::Variant getTypedBody() const;
    const qpid::amqp::Descriptor& getBodyDescriptor() const;

    Message(size_t size);
    char* getData();
    const char* getData() const;
    size_t getSize() const;
    void scan();

    //PersistableMessage interface:
    void encode(framing::Buffer& buffer) const;
    uint32_t encodedSize() const;
    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer);
    uint32_t encodedHeaderSize() const;
    boost::intrusive_ptr<PersistableMessage> merge(const std::map<std::string, qpid::types::Variant>& annotations) const;

    static const Message& get(const qpid::broker::Message&);
  private:
    std::vector<char> data;

    //header:
    boost::optional<bool> durable;
    boost::optional<uint8_t> priority;
    boost::optional<uint32_t> ttl;
    boost::optional<bool> firstAcquirer;
    boost::optional<uint32_t> deliveryCount;
    //annotations:
    qpid::amqp::CharSequence deliveryAnnotations;
    qpid::amqp::CharSequence messageAnnotations;

    qpid::amqp::CharSequence bareMessage;//properties, application-properties and content
    //properties:
    qpid::amqp::MessageId messageId;
    qpid::amqp::CharSequence userId;
    qpid::amqp::CharSequence to;
    qpid::amqp::CharSequence subject;
    qpid::amqp::CharSequence replyTo;
    qpid::amqp::MessageId correlationId;
    qpid::amqp::CharSequence contentType;
    qpid::amqp::CharSequence contentEncoding;
    boost::optional<int64_t> creationTime;

    //application-properties:
    qpid::amqp::CharSequence applicationProperties;

    //body:
    qpid::amqp::CharSequence body;
    qpid::types::Variant typedBody;
    std::string bodyType;
    qpid::amqp::Descriptor bodyDescriptor;

    //footer:
    qpid::amqp::CharSequence footer;

    //header:
    void onDurable(bool b);
    void onPriority(uint8_t i);
    void onTtl(uint32_t i);
    void onFirstAcquirer(bool b);
    void onDeliveryCount(uint32_t i);
    //properties:
    void onMessageId(uint64_t);
    void onMessageId(const qpid::amqp::CharSequence&, qpid::types::VariantType);
    void onUserId(const qpid::amqp::CharSequence& v);
    void onTo(const qpid::amqp::CharSequence& v);
    void onSubject(const qpid::amqp::CharSequence& v);
    void onReplyTo(const qpid::amqp::CharSequence& v);
    void onCorrelationId(uint64_t);
    void onCorrelationId(const qpid::amqp::CharSequence&, qpid::types::VariantType);
    void onContentType(const qpid::amqp::CharSequence& v);
    void onContentEncoding(const qpid::amqp::CharSequence& v);
    void onAbsoluteExpiryTime(int64_t i);
    void onCreationTime(int64_t);
    void onGroupId(const qpid::amqp::CharSequence&);
    void onGroupSequence(uint32_t);
    void onReplyToGroupId(const qpid::amqp::CharSequence&);

    void onApplicationProperties(const qpid::amqp::CharSequence&, const qpid::amqp::CharSequence&);
    void onDeliveryAnnotations(const qpid::amqp::CharSequence&, const qpid::amqp::CharSequence&);
    void onMessageAnnotations(const qpid::amqp::CharSequence&, const qpid::amqp::CharSequence&);

    void onData(const qpid::amqp::CharSequence&);
    void onAmqpSequence(const qpid::amqp::CharSequence&);
    void onAmqpValue(const qpid::amqp::CharSequence&, const std::string& type, const qpid::amqp::Descriptor*);
    void onAmqpValue(const qpid::types::Variant&, const qpid::amqp::Descriptor*);

    void onFooter(const qpid::amqp::CharSequence&, const qpid::amqp::CharSequence&);
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_MESSAGE_H*/
