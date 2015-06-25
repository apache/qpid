#ifndef TESTS_MESSAGEUTILS_H
#define TESTS_MESSAGEUTILS_H

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
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/Uuid.h"
#include "qpid/types/Variant.h"
#include "qpid/amqp_0_10/Codecs.h"

using namespace qpid;
using namespace broker;
using namespace framing;

namespace qpid {
namespace tests {

struct MessageUtils
{
    static Message createMessage(const qpid::types::Variant::Map& properties,
                                 const std::string& content="",
                                 const std::string& destination = "",
                                 bool replaceHeaders = false
                                )
    {
        boost::intrusive_ptr<broker::amqp_0_10::MessageTransfer> msg(new broker::amqp_0_10::MessageTransfer());

        AMQFrame method(( MessageTransferBody(ProtocolVersion(), destination, 0, 0)));
        AMQFrame header((AMQHeaderBody()));

        msg->getFrames().append(method);
        msg->getFrames().append(header);
        if (content.size()) {
            msg->getFrames().getHeaders()->get<MessageProperties>(true)->setContentLength(content.size());
            AMQFrame data((AMQContentBody(content)));
            msg->getFrames().append(data);
        }
        if (!replaceHeaders) {
            for (qpid::types::Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
                if (i->first == "routing-key" && !i->second.isVoid()) {
                    msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setRoutingKey(i->second);
                } else if (i->first == "message-id" && !i->second.isVoid()) {
                    qpid::types::Uuid id = i->second;
                    qpid::framing::Uuid id2(id.data());
                    msg->getFrames().getHeaders()->get<MessageProperties>(true)->setMessageId(id2);
                } else if (i->first == "ttl" && !i->second.isVoid()) {
                    msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setTtl(i->second);
                } else if (i->first == "priority" && !i->second.isVoid()) {
                    msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setPriority(i->second);
                } else if (i->first == "durable" && !i->second.isVoid()) {
                    msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setDeliveryMode(i->second.asBool() ? 2 : 1);
                } else {
                    msg->getFrames().getHeaders()->get<MessageProperties>(true)->getApplicationHeaders().setString(i->first, i->second);
                }
            }
        } else {
            framing::FieldTable newHeaders;
            qpid::amqp_0_10::translate(properties, newHeaders);
            msg->getFrames().getHeaders()->get<MessageProperties>(true)->getApplicationHeaders() = newHeaders;
        }
        return Message(msg, msg);
    }


    static Message createMessage(const std::string& exchange="", const std::string& routingKey="",
                                 uint64_t ttl = 0, bool durable = false, const Uuid& messageId=Uuid(true),
                                 const std::string& content="")
    {
        boost::intrusive_ptr<broker::amqp_0_10::MessageTransfer> msg(new broker::amqp_0_10::MessageTransfer());

        AMQFrame method(( MessageTransferBody(ProtocolVersion(), exchange, 0, 0)));
        AMQFrame header((AMQHeaderBody()));

        msg->getFrames().append(method);
        msg->getFrames().append(header);
        MessageProperties* props = msg->getFrames().getHeaders()->get<MessageProperties>(true);
        props->setContentLength(content.size());
        props->setMessageId(messageId);
        msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setRoutingKey(routingKey);
        if (durable)
            msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setDeliveryMode(2);
        if (ttl)
            msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setTtl(ttl);
        if (content.size()) {
            AMQFrame data((AMQContentBody(content)));
            msg->getFrames().append(data);
        }
        if (ttl) msg->computeExpiration();
        return Message(msg, msg);
    }
};

}} // namespace qpid::tests

#endif  /*!TESTS_MESSAGEUTILS_H*/
