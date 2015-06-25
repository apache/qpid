#ifndef QPID_MESSAGING_MESSAGEIMPL_H
#define QPID_MESSAGING_MESSAGEIMPL_H

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

#include "qpid/messaging/Address.h"
#include "qpid/types/Variant.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/messaging/amqp/EncodedMessage.h"
#include <vector>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace messaging {

class MessageImpl
{
  private:
    mutable Address replyTo;
    mutable std::string subject;
    mutable std::string contentType;
    mutable std::string messageId;
    mutable std::string userId;
    mutable std::string correlationId;
    uint8_t priority;
    uint64_t ttl;
    bool durable;
    bool redelivered;
    mutable qpid::types::Variant::Map headers;

    mutable std::string bytes;
    mutable qpid::types::Variant content;
    mutable bool contentDecoded;
    boost::shared_ptr<const qpid::messaging::amqp::EncodedMessage> encoded;

    qpid::framing::SequenceNumber internalId;

    void updated();
  public:
    MessageImpl(const std::string& c);
    MessageImpl(const char* chars, size_t count);

    void clear();
    void setReplyTo(const Address& d);
    QPID_MESSAGING_EXTERN const Address& getReplyTo() const;

    void setSubject(const std::string& s);
    QPID_MESSAGING_EXTERN const std::string& getSubject() const;

    void setContentType(const std::string& s);
    QPID_MESSAGING_EXTERN const std::string& getContentType() const;

    void setMessageId(const std::string&);
    QPID_MESSAGING_EXTERN const std::string& getMessageId() const;
    void setUserId(const std::string& );
    QPID_MESSAGING_EXTERN const std::string& getUserId() const;
    void setCorrelationId(const std::string& );
    QPID_MESSAGING_EXTERN const std::string& getCorrelationId() const;
    void setPriority(uint8_t);
    QPID_MESSAGING_EXTERN uint8_t getPriority() const;
    void setTtl(uint64_t);
    QPID_MESSAGING_EXTERN uint64_t getTtl() const;
    void setDurable(bool);
    QPID_MESSAGING_EXTERN bool isDurable() const;
    void setRedelivered(bool);
    QPID_MESSAGING_EXTERN bool isRedelivered() const;


    QPID_MESSAGING_EXTERN const qpid::types::Variant::Map& getHeaders() const;
    qpid::types::Variant::Map& getHeaders();
    void setHeader(const std::string& key, const qpid::types::Variant& val);

    void setBytes(const std::string& bytes);
    void setBytes(const char* chars, size_t count);
    QPID_MESSAGING_EXTERN const std::string& getBytes() const;
    std::string& getBytes();
    qpid::types::Variant& getContent();
    QPID_MESSAGING_EXTERN const qpid::types::Variant& getContent() const;

    QPID_MESSAGING_EXTERN void setInternalId(qpid::framing::SequenceNumber id);
    QPID_MESSAGING_EXTERN qpid::framing::SequenceNumber getInternalId();
    void setEncoded(boost::shared_ptr<const qpid::messaging::amqp::EncodedMessage> e) { encoded = e; }
    boost::shared_ptr<const qpid::messaging::amqp::EncodedMessage> getEncoded() const { return encoded; }
};

class Message;

/**
 * Provides access to the internal MessageImpl for a message which is
 * useful when accessing any message state not exposed directly
 * through the public API.
 */
struct MessageImplAccess
{
    QPID_MESSAGING_EXTERN static MessageImpl& get(Message&);
    QPID_MESSAGING_EXTERN static const MessageImpl& get(const Message&);
};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_MESSAGEIMPL_H*/
