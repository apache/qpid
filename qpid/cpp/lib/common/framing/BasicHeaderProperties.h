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
#include <amqp_types.h>
#include <Buffer.h>
#include <FieldTable.h>
#include <HeaderProperties.h>

#ifndef _BasicHeaderProperties_
#define _BasicHeaderProperties_

namespace qpid {
namespace framing {

enum DeliveryMode { TRANSIENT = 1, PERSISTENT = 2};

class BasicHeaderProperties : public HeaderProperties
{
    string contentType;
    string contentEncoding;
    FieldTable headers;
    DeliveryMode deliveryMode;
    uint8_t priority;
    string correlationId;
    string replyTo;
    string expiration;
    string messageId;
    uint64_t timestamp;
    string type;
    string userId;
    string appId;
    string clusterId;
	
    uint16_t getFlags() const;

  public:
    BasicHeaderProperties();
    virtual ~BasicHeaderProperties();
    virtual uint32_t size() const;
    virtual void encode(Buffer& buffer) const;
    virtual void decode(Buffer& buffer, uint32_t size);

    virtual uint8_t classId() { return BASIC; }

    string getContentType() const { return contentType; }
    string getContentEncoding() const { return contentEncoding; }
    FieldTable& getHeaders() { return headers; }
    const FieldTable& getHeaders() const { return headers; }
    DeliveryMode getDeliveryMode() const { return deliveryMode; }
    uint8_t getPriority() const { return priority; }
    string getCorrelationId() const {return correlationId; }
    string getReplyTo() const { return replyTo; }
    string getExpiration() const { return expiration; }
    string getMessageId() const {return messageId; }
    uint64_t getTimestamp() const { return timestamp; }
    string getType() const { return type; }
    string getUserId() const { return userId; }
    string getAppId() const { return appId; }
    string getClusterId() const { return clusterId; }

    void setContentType(const string& _type){ contentType = _type; }
    void setContentEncoding(const string& encoding){ contentEncoding = encoding; }
    void setHeaders(const FieldTable& _headers){ headers = _headers; }
    void setDeliveryMode(DeliveryMode mode){ deliveryMode = mode; }
    void setPriority(uint8_t _priority){ priority = _priority; }
    void setCorrelationId(const string& _correlationId){ correlationId = _correlationId; }
    void setReplyTo(const string& _replyTo){ replyTo = _replyTo;}
    void setExpiration(const string&  _expiration){ expiration = _expiration; }
    void setMessageId(const string& _messageId){ messageId = _messageId; }
    void setTimestamp(uint64_t _timestamp){ timestamp = _timestamp; }
    void setType(const string& _type){ type = _type; }
    void setUserId(const string& _userId){ userId = _userId; }
    void setAppId(const string& _appId){appId = _appId; }
    void setClusterId(const string& _clusterId){ clusterId = _clusterId; }

    /** \internal
     * Template to copy between types like BasicHeaderProperties.
     */
    template <class T, class U>
    static void copy(T& to, const U& from) {
        to.setContentType(from.getContentType());
        to.setContentEncoding(from.getContentEncoding());
        to.setHeaders(from.getHeaders());
        to.setDeliveryMode(from.getDeliveryMode());
        to.setPriority(from.getPriority());
        to.setCorrelationId(from.getCorrelationId());
        to.setReplyTo(from.getReplyTo());
        to.setExpiration(from.getExpiration());
        to.setMessageId(from.getMessageId());
        to.setTimestamp(from.getTimestamp());
        to.setType(from.getType());
        to.setUserId(from.getUserId());
        to.setAppId(from.getAppId());
        to.setClusterId(from.getClusterId());
    }
};
}}
#endif
