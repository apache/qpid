#ifndef QPID_MESSAGING_MESSAGE_H
#define QPID_MESSAGING_MESSAGE_H

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

#include <string>
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Variant.h"
#include "qpid/messaging/ImportExport.h"

namespace qpid {
namespace messaging {

class Address;
class Codec;
struct MessageImpl;

/**
 * Representation of a message.
 */
class Message
{
  public:
    QPID_CLIENT_EXTERN Message(const std::string& bytes = std::string());
    QPID_CLIENT_EXTERN Message(const char*, size_t);
    QPID_CLIENT_EXTERN Message(const Message&);
    QPID_CLIENT_EXTERN ~Message();

    QPID_CLIENT_EXTERN Message& operator=(const Message&);

    QPID_CLIENT_EXTERN void setReplyTo(const Address&);
    QPID_CLIENT_EXTERN const Address& getReplyTo() const;

    QPID_CLIENT_EXTERN void setSubject(const std::string&);
    QPID_CLIENT_EXTERN const std::string& getSubject() const;

    QPID_CLIENT_EXTERN void setContentType(const std::string&);
    QPID_CLIENT_EXTERN const std::string& getContentType() const;

    QPID_CLIENT_EXTERN void setMessageId(const std::string&);
    QPID_CLIENT_EXTERN const std::string& getMessageId() const;

    QPID_CLIENT_EXTERN void setUserId(const std::string&);
    QPID_CLIENT_EXTERN const std::string& getUserId() const;

    QPID_CLIENT_EXTERN void setCorrelationId(const std::string&);
    QPID_CLIENT_EXTERN const std::string& getCorrelationId() const;

    /**
     * Set the time to live for this message in milliseconds.
     */
    QPID_CLIENT_EXTERN void setTtl(Duration ttl);
    /**
     *Get the time to live for this message in milliseconds.
     */
    QPID_CLIENT_EXTERN Duration getTtl() const;

    QPID_CLIENT_EXTERN void setDurable(bool durable);
    QPID_CLIENT_EXTERN bool getDurable() const;

    QPID_CLIENT_EXTERN bool isRedelivered() const;

    QPID_CLIENT_EXTERN const Variant::Map& getHeaders() const;
    QPID_CLIENT_EXTERN Variant::Map& getHeaders();

    QPID_CLIENT_EXTERN const std::string& getContent() const;
    QPID_CLIENT_EXTERN std::string& getContent();

    QPID_CLIENT_EXTERN void setContent(const std::string&);
    QPID_CLIENT_EXTERN void setContent(const char* chars, size_t count);
    QPID_CLIENT_EXTERN void getContent(std::pair<const char*, size_t>& content) const;

  private:
    MessageImpl* impl;
    friend struct MessageImplAccess;
};
}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_MESSAGE_H*/
