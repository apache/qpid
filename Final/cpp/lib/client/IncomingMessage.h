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
#include <vector>
#include <framing/amqp_framing.h>

#ifndef _IncomingMessage_
#define _IncomingMessage_

#include <ClientMessage.h>

namespace qpid {
namespace client {

    class IncomingMessage{
        //content will be preceded by one of these method frames
	qpid::framing::BasicDeliverBody::shared_ptr delivered;
	qpid::framing::BasicReturnBody::shared_ptr returned;
	qpid::framing::BasicGetOkBody::shared_ptr response;
	qpid::framing::AMQHeaderBody::shared_ptr header;
	std::vector<qpid::framing::AMQContentBody::shared_ptr> content;

	u_int64_t contentSize();
    public:
	IncomingMessage(qpid::framing::BasicDeliverBody::shared_ptr intro);
	IncomingMessage(qpid::framing::BasicReturnBody::shared_ptr intro);
	IncomingMessage(qpid::framing::BasicGetOkBody::shared_ptr intro);
        ~IncomingMessage();
	void setHeader(qpid::framing::AMQHeaderBody::shared_ptr header);
	void addContent(qpid::framing::AMQContentBody::shared_ptr content);
	bool isComplete();
	bool isReturn();
	bool isDelivery();
	bool isResponse();
	const std::string& getConsumerTag();//only relevant if isDelivery()
	qpid::framing::AMQHeaderBody::shared_ptr& getHeader();
        u_int64_t getDeliveryTag();
	void getData(std::string& data);
    };

}
}


#endif
