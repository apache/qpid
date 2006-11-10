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
#include <qpid/framing/amqp_framing.h>

#ifndef _Message_
#define _Message_


namespace qpid {
namespace client {

    class Message{
	qpid::framing::AMQHeaderBody::shared_ptr header;
	string data;
	bool redelivered;
        u_int64_t deliveryTag;

        qpid::framing::BasicHeaderProperties* getHeaderProperties();
	Message(qpid::framing::AMQHeaderBody::shared_ptr& header);
    public:
	Message();
	~Message();
	
	inline std::string getData(){ return data; }
	inline void setData(const std::string& _data){ data = _data; }

	inline bool isRedelivered(){ return redelivered; }
	inline void setRedelivered(bool _redelivered){  redelivered = _redelivered; }

        inline u_int64_t getDeliveryTag(){ return deliveryTag; }

        const std::string& getContentType();
        const std::string& getContentEncoding();
        qpid::framing::FieldTable& getHeaders();
        u_int8_t getDeliveryMode();
        u_int8_t getPriority();
        const std::string& getCorrelationId();
        const std::string& getReplyTo();
        const std::string& getExpiration();
        const std::string& getMessageId();
        u_int64_t getTimestamp();
        const std::string& getType();
        const std::string& getUserId();
        const std::string& getAppId();
        const std::string& getClusterId();

	void setContentType(const std::string& type);
	void setContentEncoding(const std::string& encoding);
	void setHeaders(const qpid::framing::FieldTable& headers);
	void setDeliveryMode(u_int8_t mode);
	void setPriority(u_int8_t priority);
	void setCorrelationId(const std::string& correlationId);
	void setReplyTo(const std::string& replyTo);
	void setExpiration(const std::string&  expiration);
	void setMessageId(const std::string& messageId);
	void setTimestamp(u_int64_t timestamp);
	void setType(const std::string& type);
	void setUserId(const std::string& userId);
	void setAppId(const std::string& appId);
	void setClusterId(const std::string& clusterId);


	friend class Channel;
    };

}
}


#endif
