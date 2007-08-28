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

#include "qpid/broker/Message.h"
#include "qpid/broker/MessageDelivery.h"
#include "qpid/framing/AMQFrame.h"

using namespace qpid::broker;
using namespace qpid::framing;

struct MessageUtils
{
    static Message::shared_ptr createMessage(const string& exchange, const string& routingKey, 
                                             const string& messageId, uint64_t contentSize = 0)
    {
        Message::shared_ptr msg(new Message());

        AMQFrame method(0, MessageTransferBody(ProtocolVersion(), 0, exchange, 0, 0));
        AMQFrame header(0, AMQHeaderBody());

        msg->getFrames().append(method);
        msg->getFrames().append(header);
        MessageProperties* props = msg->getFrames().getHeaders()->get<MessageProperties>(true);
        props->setContentLength(contentSize);        
        props->setMessageId(messageId);
        msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setRoutingKey(routingKey);
        return msg;
    }

    static void addContent(Message::shared_ptr msg, const string& data)
    {
        AMQFrame content(0, AMQContentBody(data));
        msg->getFrames().append(content);
    }
};
