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

#include <qpid/broker/Message.h>
#include <qpid/broker/Queue.h>
#include <qpid/broker/amqp_0_10/MessageTransfer.h>
#include <qpid/framing/AMQFrame.h>
#include <qpid/framing/all_method_bodies.h>
#include <qpid/framing/Uuid.h>

using namespace qpid::broker;
using namespace qpid::framing;

struct MessageUtils
{
    static Message createMessage(const std::string& exchange, const std::string& routingKey,
                                 const Uuid& messageId=Uuid(), const bool durable = false,
                                 const uint64_t contentSize = 0, const std::string& correlationId = std::string())
    {
        boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> msg(new qpid::broker::amqp_0_10::MessageTransfer());

        AMQFrame method(( MessageTransferBody(ProtocolVersion(), exchange, 0, 0)));
        AMQFrame header((AMQHeaderBody()));

        msg->getFrames().append(method);
        msg->getFrames().append(header);
        MessageProperties* props = msg->getFrames().getHeaders()->get<MessageProperties>(true);
        props->setContentLength(contentSize);
        props->setMessageId(messageId);
        props->setCorrelationId(correlationId);
        msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setRoutingKey(routingKey);
        if (durable)
            msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setDeliveryMode(PERSISTENT);
        return Message(msg, msg);
    }

    static void addContent(Message msg, const std::string& data)
    {
        AMQFrame content((AMQContentBody(data)));
        qpid::broker::amqp_0_10::MessageTransfer::get(msg).getFrames().append(content);
    }

    struct MessageRetriever :  public Consumer
    {
        MessageRetriever(Queue& q) : Consumer("test", CONSUMER), queue(q) {};

        bool deliver(const QueueCursor& c, const Message& m)
        {
            message = m;
            cursor = c;
            return true;
        };
        void notify() {}
        void cancel() {}
        void acknowledged(const DeliveryRecord&) {}
        OwnershipToken* getSession() { return 0; }

        const Queue& queue;
        Message message;
        QueueCursor cursor;
    };

    static Message get(Queue& queue, QueueCursor* cursor = 0)
    {
        boost::shared_ptr<MessageRetriever> consumer(new MessageRetriever(queue));
        if (!queue.dispatch(consumer))throw qpid::Exception("No message found!");
        if (cursor) *cursor = consumer->cursor;
        return consumer->message;
    }

    static Uuid getMessageId(const Message& message)
    {
        return qpid::broker::amqp_0_10::MessageTransfer::get(message).getProperties<MessageProperties>()->getMessageId();
    }

    static std::string getCorrelationId(const Message& message)
    {
        return qpid::broker::amqp_0_10::MessageTransfer::get(message).getProperties<MessageProperties>()->getCorrelationId();
    }

    static void deliver(Message& msg, FrameHandler& h, uint16_t framesize)
    {
        qpid::broker::amqp_0_10::MessageTransfer::get(msg).sendHeader(h, framesize, false, 0, qpid::types::Variant::Map());
        qpid::broker::amqp_0_10::MessageTransfer::get(msg).sendContent(h, framesize);
    }

};
