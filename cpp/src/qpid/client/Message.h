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
#include <string>
#include "qpid/framing/amqp_framing.h"

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

        std::string& getContentType();
        std::string& getContentEncoding();
        qpid::framing::FieldTable& getHeaders();
        u_int8_t getDeliveryMode();
        u_int8_t getPriority();
        std::string& getCorrelationId();
        std::string& getReplyTo();
        std::string& getExpiration();
        std::string& getMessageId();
        u_int64_t getTimestamp();
        std::string& getType();
        std::string& getUserId();
        std::string& getAppId();
        std::string& getClusterId();

	void setContentType(std::string& type);
	void setContentEncoding(std::string& encoding);
	void setHeaders(qpid::framing::FieldTable& headers);
	void setDeliveryMode(u_int8_t mode);
	void setPriority(u_int8_t priority);
	void setCorrelationId(std::string& correlationId);
	void setReplyTo(std::string& replyTo);
	void setExpiration(std::string&  expiration);
	void setMessageId(std::string& messageId);
	void setTimestamp(u_int64_t timestamp);
	void setType(std::string& type);
	void setUserId(std::string& userId);
	void setAppId(std::string& appId);
	void setClusterId(std::string& clusterId);


	friend class Channel;
    };

}
}


#endif
