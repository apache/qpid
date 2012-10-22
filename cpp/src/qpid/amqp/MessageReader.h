#ifndef QPID_AMQP_MESSAGEREADER_H
#define QPID_AMQP_MESSAGEREADER_H

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

#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/Reader.h"
#include "qpid/amqp/ListReader.h"
#include "qpid/types/Variant.h"
#include "qpid/CommonImportExport.h"

namespace qpid {
namespace amqp {

/**
 * Reader for an AMQP 1.0 message
 */
class MessageReader : public Reader
{
  public:
    QPID_COMMON_EXTERN MessageReader();

    //header, properties, amqp-sequence, amqp-value
    QPID_COMMON_EXTERN bool onStartList(uint32_t, const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onEndList(uint32_t, const Descriptor*);

    //delivery-annotations, message-annotations, application-headers, amqp-value
    QPID_COMMON_EXTERN bool onStartMap(uint32_t, const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onEndMap(uint32_t, const Descriptor*);

    //data, amqp-value
    QPID_COMMON_EXTERN void onBinary(const CharSequence&, const Descriptor*);

    //amqp-value
    QPID_COMMON_EXTERN void onNull(const Descriptor*);
    QPID_COMMON_EXTERN void onString(const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onSymbol(const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onBoolean(bool, const Descriptor*);
    QPID_COMMON_EXTERN void onUByte(uint8_t, const Descriptor*);
    QPID_COMMON_EXTERN void onUShort(uint16_t, const Descriptor*);
    QPID_COMMON_EXTERN void onUInt(uint32_t, const Descriptor*);
    QPID_COMMON_EXTERN void onULong(uint64_t, const Descriptor*);
    QPID_COMMON_EXTERN void onByte(int8_t, const Descriptor*);
    QPID_COMMON_EXTERN void onShort(int16_t, const Descriptor*);
    QPID_COMMON_EXTERN void onInt(int32_t, const Descriptor*);
    QPID_COMMON_EXTERN void onLong(int64_t, const Descriptor*);
    QPID_COMMON_EXTERN void onFloat(float, const Descriptor*);
    QPID_COMMON_EXTERN void onDouble(double, const Descriptor*);
    QPID_COMMON_EXTERN void onUuid(const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onTimestamp(int64_t, const Descriptor*);
    QPID_COMMON_EXTERN bool onStartArray(uint32_t, const CharSequence&, const Constructor&, const Descriptor*);
    QPID_COMMON_EXTERN void onEndArray(uint32_t, const Descriptor*);
    QPID_COMMON_EXTERN void onDescriptor(const Descriptor&, const char*);

    //header:
    virtual void onDurable(bool) = 0;
    virtual void onPriority(uint8_t) = 0;
    virtual void onTtl(uint32_t) = 0;
    virtual void onFirstAcquirer(bool) = 0;
    virtual void onDeliveryCount(uint32_t) = 0;

    //properties:
    virtual void onMessageId(uint64_t) = 0;
    virtual void onMessageId(const CharSequence&, qpid::types::VariantType) = 0;
    virtual void onUserId(const CharSequence&) = 0;
    virtual void onTo(const CharSequence&) = 0;
    virtual void onSubject(const CharSequence&) = 0;
    virtual void onReplyTo(const CharSequence&) = 0;
    virtual void onCorrelationId(uint64_t) = 0;
    virtual void onCorrelationId(const CharSequence&, qpid::types::VariantType) = 0;
    virtual void onContentType(const CharSequence&) = 0;
    virtual void onContentEncoding(const CharSequence&) = 0;
    virtual void onAbsoluteExpiryTime(int64_t) = 0;
    virtual void onCreationTime(int64_t) = 0;
    virtual void onGroupId(const CharSequence&) = 0;
    virtual void onGroupSequence(uint32_t) = 0;
    virtual void onReplyToGroupId(const CharSequence&) = 0;

    virtual void onApplicationProperties(const CharSequence&) = 0;
    virtual void onDeliveryAnnotations(const CharSequence&) = 0;
    virtual void onMessageAnnotations(const CharSequence&) = 0;
    virtual void onBody(const CharSequence&, const Descriptor&) = 0;
    virtual void onBody(const qpid::types::Variant&, const Descriptor&) = 0;
    virtual void onFooter(const CharSequence&) = 0;

    QPID_COMMON_EXTERN CharSequence getBareMessage() const;

  private:
    /*
    class DurableReader : public Reader
    {
      public:
        DurableReader(MessageReader&);
        void onBoolean(bool v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class PriorityReader : public Reader
    {
      public:
        PriorityReader(MessageReader&);
        void onUByte(uint8_t v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class TtlReader : public Reader
    {
      public:
        TtlReader(MessageReader&);
        void onUInt(uint32_t v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class FirstAcquirerReader : public Reader
    {
      public:
        FirstAcquirerReader(MessageReader&);
        void onBoolean(bool v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class DeliveryCountReader : public Reader
    {
      public:
        DeliveryCountReader(MessageReader&);
        void onUInt(uint32_t v, const Descriptor*);
      private:
        MessageReader& parent;
    };

    class MessageIdReader : public Reader
    {
      public:
        MessageIdReader(MessageReader&);
        void onUuid(const qpid::types::Uuid& v, const Descriptor*);
        void onULong(uint64_t v, const Descriptor*);
        void onString(const CharSequence& v, const Descriptor*);
        void onBinary(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class UserIdReader : public Reader
    {
      public:
        UserIdReader(MessageReader&);
        void onBinary(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class ToReader : public Reader
    {
      public:
        ToReader(MessageReader&);
        void onString(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class SubjectReader : public Reader
    {
      public:
        SubjectReader(MessageReader&);
        void onString(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class ReplyToReader : public Reader
    {
      public:
        ReplyToReader(MessageReader&);
        void onString(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class CorrelationIdReader : public Reader
    {
      public:
        CorrelationIdReader(MessageReader&);
        void onUuid(const qpid::types::Uuid& v, const Descriptor*);
        void onULong(uint64_t v, const Descriptor*);
        void onString(const CharSequence& v, const Descriptor*);
        void onBinary(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class ContentTypeReader : public Reader
    {
      public:
        ContentTypeReader(MessageReader&);
        void onString(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class ContentEncodingReader : public Reader
    {
      public:
        ContentEncodingReader(MessageReader&);
        void onString(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class AbsoluteExpiryTimeReader : public Reader
    {
      public:
        AbsoluteExpiryTimeReader(MessageReader&);
        void onTimestamp(int64_t v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class CreationTimeReader : public Reader
    {
      public:
        CreationTimeReader(MessageReader&);
        void onTimestamp(int64_t v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class GroupIdReader : public Reader
    {
      public:
        GroupIdReader(MessageReader&);
        void onString(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class GroupSequenceReader : public Reader
    {
      public:
        GroupSequenceReader(MessageReader&);
        void onUInt(uint32_t v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    class ReplyToGroupIdReader : public Reader
    {
      public:
        ReplyToGroupIdReader(MessageReader&);
        void onString(const CharSequence& v, const Descriptor*);
      private:
        MessageReader& parent;
    };
    */

    class HeaderReader : public Reader //public ListReader
    {
      public:
        //Reader& getReader(size_t index);

        HeaderReader(MessageReader&);
        void onBoolean(bool v, const Descriptor*);  // durable, first-acquirer
        void onUByte(uint8_t v, const Descriptor*); // priority
        void onUInt(uint32_t v, const Descriptor*); // ttl, delivery-count
        void onNull(const Descriptor*);
      private:
        MessageReader& parent;
        size_t index;
    };
    class PropertiesReader : public Reader //public ListReader
    {
      public:
        //Reader& getReader(size_t index);

        PropertiesReader(MessageReader&);
        void onUuid(const CharSequence& v, const Descriptor*); // message-id, correlation-id
        void onULong(uint64_t v, const Descriptor*); // message-id, correlation-id
        void onBinary(const CharSequence& v, const Descriptor*); // message-id, correlation-id, user-id
        void onString(const CharSequence& v, const Descriptor*); // message-id, correlation-id, group-id, reply-to-group-id, subject, to, reply-to
        void onSymbol(const CharSequence& v, const Descriptor*); // content-type, content-encoding
        void onTimestamp(int64_t v, const Descriptor*); // absolute-expiry-time, creation-time
        void onUInt(uint32_t v, const Descriptor*); // group-sequence
        void onNull(const Descriptor*);
      private:
        MessageReader& parent;
        size_t index;
    };
    HeaderReader headerReader;
    PropertiesReader propertiesReader;
    Reader* delegate;
    CharSequence bare;
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_MESSAGEREADER_H*/
