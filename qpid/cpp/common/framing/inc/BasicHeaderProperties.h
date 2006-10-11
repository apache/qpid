/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "amqp_types.h"
#include "amqp_methods.h"
#include "Buffer.h"
#include "HeaderProperties.h"

#ifndef _BasicHeaderProperties_
#define _BasicHeaderProperties_

namespace qpid {
namespace framing {

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

	inline virtual u_int8_t classId(){ return BASIC; }

	inline string& getContentType(){ return contentType; }
	inline string& getContentEncoding(){ return contentEncoding; }
	inline FieldTable& getHeaders(){ return headers; }
	inline u_int8_t getDeliveryMode(){ return deliveryMode; }
	inline u_int8_t getPriority(){ return priority; }
	inline string& getCorrelationId(){return correlationId; }
	inline string& getReplyTo(){ return replyTo; }
	inline string& getExpiration(){ return expiration; }
	inline string& getMessageId(){return messageId; }
	inline u_int64_t getTimestamp(){ return timestamp; }
	inline string& getType(){ return type; }
	inline string& getUserId(){ return userId; }
	inline string& getAppId(){ return appId; }
	inline string& getClusterId(){ return clusterId; }

	void inline setContentType(string& _type){ contentType = _type; }
	void inline setContentEncoding(string& encoding){ contentEncoding = encoding; }
	void inline setHeaders(FieldTable& _headers){ headers = _headers; }
	void inline setDeliveryMode(u_int8_t mode){ deliveryMode = mode; }
	void inline setPriority(u_int8_t _priority){ priority = _priority; }
	void inline setCorrelationId(string& _correlationId){ correlationId = _correlationId; }
	void inline setReplyTo(string& _replyTo){ replyTo = _replyTo;}
	void inline setExpiration(string&  _expiration){ expiration = _expiration; }
	void inline setMessageId(string& _messageId){ messageId = _messageId; }
	void inline setTimestamp(u_int64_t _timestamp){ timestamp = _timestamp; }
	void inline setType(string& _type){ type = _type; }
	void inline setUserId(string& _userId){ userId = _userId; }
	void inline setAppId(string& _appId){appId = _appId; }
	void inline setClusterId(string& _clusterId){ clusterId = _clusterId; }
    };

}
}


#endif
