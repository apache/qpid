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
#include "BrokerMessageMessage.h"

using namespace qpid::broker;
	
MessageMessage::MessageMessage(
    const qpid::framing::AMQMethodBody::shared_ptr _methodBody, 
    const std::string& _exchange, const std::string& _routingKey, 
    bool _mandatory, bool _immediate) :
    Message(_exchange, _routingKey, _mandatory, _immediate, _methodBody),
    methodBody(_methodBody)
{
}

void MessageMessage::deliver(
    framing::ChannelAdapter& /*out*/, 
    const std::string& /*consumerTag*/, 
    u_int64_t /*deliveryTag*/, 
    u_int32_t /*framesize*/)
{
}

void MessageMessage::sendGetOk(
    const framing::MethodContext& /*context*/, 
    u_int32_t /*messageCount*/,
    u_int64_t /*deliveryTag*/, 
    u_int32_t /*framesize*/)
{
}

bool MessageMessage::isComplete()
{
	return true;
}

u_int64_t MessageMessage::contentSize() const
{
	return 0;
}

qpid::framing::BasicHeaderProperties* MessageMessage::getHeaderProperties()
{
	return 0;
}
bool MessageMessage::isPersistent()
{
	return false;
}

const ConnectionToken* const MessageMessage::getPublisher()
{
	return 0;
}

u_int32_t MessageMessage::encodedSize()
{
	return 0;
}

u_int32_t MessageMessage::encodedHeaderSize()
{
	return 0;
}

u_int32_t MessageMessage::encodedContentSize()
{
	return 0;
}

u_int64_t MessageMessage::expectedContentSize()
{
	return 0;
}

