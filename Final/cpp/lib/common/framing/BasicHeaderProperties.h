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
    enum delivery_mode {TRANSIENT = 1, PERSISTENT = 2};

    //TODO: This could be easily generated from the spec
    class BasicHeaderProperties : public HeaderProperties
    {
	string contentType;
	string contentEncoding;
	FieldTable headers;
	u_int8_t deliveryMode;
	u_int8_t priority;
	string correlationId;
	string replyTo;
	string expiration;
	string messageId;
	u_int64_t timestamp;
	string type;
	string userId;
	string appId;
	string clusterId;
	
	u_int16_t getFlags() const;

    public:
	BasicHeaderProperties();
	virtual ~BasicHeaderProperties();
	virtual u_int32_t size() const;
	virtual void encode(Buffer& buffer) const;
	virtual void decode(Buffer& buffer, u_int32_t size);

	inline virtual u_int8_t classId() { return BASIC; }

	inline const string& getContentType() const { return contentType; }
	inline const string& getContentEncoding() const { return contentEncoding; }
	inline FieldTable& getHeaders() { return headers; }
	inline u_int8_t getDeliveryMode() const { return deliveryMode; }
	inline u_int8_t getPriority() const { return priority; }
	inline const string& getCorrelationId() const {return correlationId; }
	inline const string& getReplyTo() const { return replyTo; }
	inline const string& getExpiration() const { return expiration; }
	inline const string& getMessageId() const {return messageId; }
	inline u_int64_t getTimestamp() const { return timestamp; }
	inline const string& getType() const { return type; }
	inline const string& getUserId() const { return userId; }
	inline const string& getAppId() const { return appId; }
	inline const string& getClusterId() const { return clusterId; }

	void inline setContentType(const string& _type){ contentType = _type; }
	void inline setContentEncoding(const string& encoding){ contentEncoding = encoding; }
	void inline setHeaders(const FieldTable& _headers){ headers = _headers; }
	void inline setDeliveryMode(u_int8_t mode){ deliveryMode = mode; }
	void inline setPriority(u_int8_t _priority){ priority = _priority; }
	void inline setCorrelationId(const string& _correlationId){ correlationId = _correlationId; }
	void inline setReplyTo(const string& _replyTo){ replyTo = _replyTo;}
	void inline setExpiration(const string&  _expiration){ expiration = _expiration; }
	void inline setMessageId(const string& _messageId){ messageId = _messageId; }
	void inline setTimestamp(u_int64_t _timestamp){ timestamp = _timestamp; }
	void inline setType(const string& _type){ type = _type; }
	void inline setUserId(const string& _userId){ userId = _userId; }
	void inline setAppId(const string& _appId){appId = _appId; }
	void inline setClusterId(const string& _clusterId){ clusterId = _clusterId; }
    };

}
}


#endif
