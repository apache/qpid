#ifndef QPID_BROKER_AMQP_0_10_MESSAGETRANSFER_H
#define QPID_BROKER_AMQP_0_10_MESSAGETRANSFER_H

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
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/PersistableMessage.h"
#include "qpid/types/Variant.h"

namespace qpid {
namespace broker {
class Queue;
namespace amqp_0_10 {

/**
 *
 */
class MessageTransfer : public qpid::broker::Message::SharedStateImpl, public qpid::broker::PersistableMessage
{
  public:
    QPID_BROKER_EXTERN MessageTransfer();
    QPID_BROKER_EXTERN MessageTransfer(const qpid::framing::SequenceNumber&);

    std::string getRoutingKey() const;
    bool isPersistent() const;
    uint8_t getPriority() const;
    uint64_t getContentSize() const;
    uint64_t getMessageSize() const;
    qpid::amqp::MessageId getMessageId() const;
    qpid::amqp::MessageId getCorrelationId() const;
    std::string getPropertyAsString(const std::string& key) const;
    std::string getAnnotationAsString(const std::string& key) const;
    bool getTtl(uint64_t&) const;
    bool hasExpiration() const;
    std::string getExchangeName() const;
    void processProperties(qpid::amqp::MapHandler&) const;
    std::string printProperties() const;
    std::string getUserId() const;
    void setTimestamp();
    uint64_t getTimestamp() const;
    std::string getTo() const;
    std::string getSubject() const;
    std::string getReplyTo() const;


    bool requiresAccept() const;
    const qpid::framing::SequenceNumber& getCommandId() const;
    QPID_BROKER_EXTERN qpid::framing::FrameSet& getFrames();
    QPID_BROKER_EXTERN const qpid::framing::FrameSet& getFrames() const;

    template <class T> const T* getProperties() const {
        const qpid::framing::AMQHeaderBody* p = frames.getHeaders();
        return p->get<T>();
    }

    template <class T> const T* hasProperties() const {
        const qpid::framing::AMQHeaderBody* p = frames.getHeaders();
        return p->get<T>();
    }
    template <class T> const T* getMethod() const {
        return frames.as<T>();
    }

    template <class T> T* getMethod() {
        return frames.as<T>();
    }

    template <class T> bool isA() const {
        return frames.isA<T>();
    }

    template <class T> void eraseProperties() {
        qpid::framing::AMQHeaderBody* p = frames.getHeaders();
        p->erase<T>();
    }
    std::string getContent() const;
    uint32_t getRequiredCredit() const;
    void computeRequiredCredit();

    void clearApplicationHeadersFlag();
    void sendContent(framing::FrameHandler& out, uint16_t maxFrameSize) const;
    void sendHeader(framing::FrameHandler& out, uint16_t maxFrameSize, bool redelivered, uint64_t ttl, const qpid::types::Variant::Map& annotations) const;

    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer, size_t size);

    void encode(framing::Buffer& buffer) const;
    uint32_t encodedSize() const;

    /**
     * @returns the size of the buffer needed to encode the
     * 'header' of this message (not just the header frame,
     * but other meta data e.g.routing key and exchange)
     */
    uint32_t encodedHeaderSize() const;
    boost::intrusive_ptr<PersistableMessage> merge(const std::map<std::string, qpid::types::Variant>& annotations) const;

    QPID_BROKER_EXTERN bool isQMFv2() const;
    QPID_BROKER_EXTERN bool isLastQMFResponse(const std::string correlation) const;

    static bool isImmediateDeliveryRequired(const qpid::broker::Message& message);
    static MessageTransfer& get(qpid::broker::Message& message) {
        return *dynamic_cast<MessageTransfer*>(&message.getSharedState());
    }
    static const MessageTransfer& get(const qpid::broker::Message& message) {
        return *dynamic_cast<const MessageTransfer*>(&message.getEncoding());
    }
    QPID_BROKER_EXTERN static bool isQMFv2(const qpid::broker::Message& message);
    QPID_BROKER_EXTERN static bool isLastQMFResponse(const qpid::broker::Message& message, const std::string correlation);
  private:
    qpid::framing::FrameSet frames;
    uint32_t requiredCredit;
    bool cachedRequiredCredit;

    MessageTransfer(const qpid::framing::FrameSet&);
    void encodeHeader(framing::Buffer& buffer) const;
    uint32_t encodedContentSize() const;
    void encodeContent(framing::Buffer& buffer) const;
};
}}} // namespace qpid::broker::amqp_0_10

#endif  /*!QPID_BROKER_AMQP_0_10_MESSAGETRANSFER_H*/
