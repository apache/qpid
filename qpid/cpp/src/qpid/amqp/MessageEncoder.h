#ifndef QPID_AMQP_MESSAGEENCODER_H
#define QPID_AMQP_MESSAGEENCODER_H

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
#include "qpid/amqp/Encoder.h"
#include "qpid/types/Variant.h"

namespace qpid {
namespace amqp {
class MapHandler;
/**
 *
 */
class MessageEncoder : public Encoder
{
  public:
    class Header
    {
      public:
        virtual ~Header() {}
        virtual bool isDurable() const = 0;
        virtual uint8_t getPriority() const = 0;
        QPID_COMMON_EXTERN virtual bool hasTtl() const = 0;
        virtual uint32_t getTtl() const = 0;
        virtual bool isFirstAcquirer() const = 0;
        virtual uint32_t getDeliveryCount() const = 0;
    };

    class Properties
    {
      public:
        virtual ~Properties() {}
        virtual bool hasMessageId() const = 0;
        virtual std::string getMessageId() const = 0;
        virtual bool hasUserId() const = 0;
        virtual std::string getUserId() const = 0;
        virtual bool hasTo() const = 0;
        virtual std::string getTo() const = 0;
        virtual bool hasSubject() const = 0;
        virtual std::string getSubject() const = 0;
        virtual bool hasReplyTo() const = 0;
        virtual std::string getReplyTo() const = 0;
        virtual bool hasCorrelationId() const = 0;
        virtual std::string getCorrelationId() const = 0;
        virtual bool hasContentType() const = 0;
        virtual std::string getContentType() const = 0;
        virtual bool hasContentEncoding() const = 0;
        virtual std::string getContentEncoding() const = 0;
        virtual bool hasAbsoluteExpiryTime() const = 0;
        virtual int64_t getAbsoluteExpiryTime() const = 0;
        virtual bool hasCreationTime() const = 0;
        virtual int64_t getCreationTime() const = 0;
        virtual bool hasGroupId() const = 0;
        virtual std::string getGroupId() const = 0;
        virtual bool hasGroupSequence() const = 0;
        virtual uint32_t getGroupSequence() const = 0;
        virtual bool hasReplyToGroupId() const = 0;
        virtual std::string getReplyToGroupId() const = 0;
    };

    class ApplicationProperties
    {
      public:
        virtual ~ApplicationProperties() {}
        virtual void handle(MapHandler&) const = 0;
    };

    QPID_COMMON_EXTERN MessageEncoder(char* d, size_t s) : Encoder(d, s), optimise(true) {}
    QPID_COMMON_EXTERN void writeHeader(const Header&);
    QPID_COMMON_EXTERN void writeProperties(const Properties&);
    QPID_COMMON_EXTERN void writeApplicationProperties(const ApplicationProperties&);
    QPID_COMMON_EXTERN void writeApplicationProperties(const qpid::types::Variant::Map& properties);
    QPID_COMMON_EXTERN void writeApplicationProperties(const qpid::types::Variant::Map& properties, bool useLargeMap);

    QPID_COMMON_EXTERN static size_t getEncodedSize(const Header&);
    QPID_COMMON_EXTERN static size_t getEncodedSize(const Properties&);
    QPID_COMMON_EXTERN static size_t getEncodedSize(const ApplicationProperties&);

    QPID_COMMON_EXTERN static size_t getEncodedSize(const qpid::types::Variant::List&, bool useLargeList);
    QPID_COMMON_EXTERN static size_t getEncodedSize(const qpid::types::Variant::Map&, bool useLargeMap);

    QPID_COMMON_EXTERN static size_t getEncodedSizeForValue(const qpid::types::Variant& value);
    QPID_COMMON_EXTERN static size_t getEncodedSizeForContent(const std::string&);

    //used in translating 0-10 content to 1.0, to determine buffer space needed
    QPID_COMMON_EXTERN static size_t getEncodedSize(const Properties&, const qpid::types::Variant::Map&, const std::string&);

  private:
    bool optimise;

    static size_t getEncodedSize(const Header&, const Properties&, const ApplicationProperties&, const std::string&);
    static size_t getEncodedSize(const Header&, const Properties&, const qpid::types::Variant::Map&, const std::string&);

    static size_t getEncodedSizeForElements(const qpid::types::Variant::Map&);
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_MESSAGEENCODER_H*/
