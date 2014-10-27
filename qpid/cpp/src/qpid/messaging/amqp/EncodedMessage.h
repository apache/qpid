#ifndef QPID_MESSAGING_AMQP_ENCODEDMESSAGE_H
#define QPID_MESSAGING_AMQP_ENCODEDMESSAGE_H

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

#include "qpid/messaging/ImportExport.h"

#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/MessageId.h"
#include "qpid/amqp/MessageReader.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/types/Variant.h"
#include <boost/optional.hpp>

namespace qpid {
namespace amqp {
struct Descriptor;
}
namespace messaging {
class Address;
class MessageImpl;
namespace amqp {

/**
 * Used to 'lazy-decode' an AMQP 1.0 message.
 *
 * There are four categories of data item:
 *
 * (i) simple, fixed width primitives - priority, ttl, durability,
 * delivery count - for which lazy-decoding doesn't buy much. These
 * are decoded unconditionally on an initial scan of the message.
 *
 * (ii) standard variable length string properties - subject,
 * message-id, user-id etc - which require conversion to a std::string
 * for returning to the application. By delaying the conversion of
 * these to a std::string we can avoid allocation & copying until it
 * is actually required. The initial scan of the message merely
 * records the position of these strings within the raw message data.
 *
 * (iii) custom, application defined headers. These form a map, and
 * again, delaying the creation of that map until it is actually
 * required can be advantageous. The initial scan of the message merely
 * records the position of this section within the raw message data.
 *
 * (iv) the body content. This may be retreived as a std::string, or
 * as a char*. Avoiding conversion to the string until it is required
 * is advantageous. The initial scan of the message merely records the
 * position of this section within the raw message data.
 *
 * At present the Message class only explicitly exposes some of the
 * standard property and headers defined by AMQP 1.0. The remainder
 * will have to be accessed through the message 'headers' map, using
 * the 'x-amqp-' prefix.
 */
class EncodedMessage
{
  public:
    QPID_MESSAGING_EXTERN EncodedMessage();
    QPID_MESSAGING_EXTERN EncodedMessage(size_t);
    QPID_MESSAGING_EXTERN EncodedMessage(const EncodedMessage&);
    QPID_MESSAGING_EXTERN ~EncodedMessage();


    QPID_MESSAGING_EXTERN size_t getSize() const;
    QPID_MESSAGING_EXTERN char* getData();
    QPID_MESSAGING_EXTERN const char* getData() const;
    QPID_MESSAGING_EXTERN void trim(size_t);
    QPID_MESSAGING_EXTERN void resize(size_t);

    QPID_MESSAGING_EXTERN void setNestAnnotationsOption(bool);
    void getReplyTo(qpid::messaging::Address&) const;
    void getSubject(std::string&) const;
    void getContentType(std::string&) const;
    void getMessageId(std::string&) const;
    void getUserId(std::string&) const;
    void getCorrelationId(std::string&) const;
    void populate(qpid::types::Variant::Map&) const;
    void getBody(std::string&, qpid::types::Variant&) const;

    QPID_MESSAGING_EXTERN void init(qpid::messaging::MessageImpl&);
    QPID_MESSAGING_EXTERN qpid::amqp::CharSequence getBareMessage() const;
    qpid::amqp::CharSequence getBody() const;
    QPID_MESSAGING_EXTERN bool hasHeaderChanged(const qpid::messaging::MessageImpl&) const;
  private:
    size_t size;
    char* data;
    bool nestAnnotations;

    class InitialScan : public qpid::amqp::MessageReader
    {
      public:
        InitialScan(EncodedMessage& e, qpid::messaging::MessageImpl& m);
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
      private:
        EncodedMessage& em;
        qpid::messaging::MessageImpl& mi;
    };
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
    boost::optional<int64_t> absoluteExpiryTime;
    boost::optional<int64_t> creationTime;
    qpid::amqp::CharSequence groupId;
    boost::optional<uint32_t> groupSequence;
    qpid::amqp::CharSequence replyToGroupId;
    //application-properties:
    qpid::amqp::CharSequence applicationProperties;
    //application data:
    qpid::amqp::CharSequence body;
    std::string bodyType;
    qpid::types::Variant content;

    //footer:
    qpid::amqp::CharSequence footer;

    void init();
    //not implemented:
    EncodedMessage& operator=(const EncodedMessage&);
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_ENCODEDMESSAGE_H*/
