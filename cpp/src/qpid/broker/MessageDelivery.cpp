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
#include "MessageDelivery.h"

#include "DeliveryToken.h"
#include "Message.h"
#include "Queue.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/BasicXDeliverBody.h"
#include "qpid/framing/BasicXGetOkBody.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/MessageXTransferBody.h"


using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;

namespace qpid{
namespace broker{

struct BaseToken : DeliveryToken
{
    virtual ~BaseToken() {}
    virtual AMQFrame sendMethod(intrusive_ptr<Message> msg, DeliveryId id) = 0;
};

struct BasicGetToken : BaseToken
{
    typedef boost::shared_ptr<BasicGetToken> shared_ptr;

    Queue::shared_ptr queue;

    BasicGetToken(Queue::shared_ptr q) : queue(q) {}

    AMQFrame sendMethod(intrusive_ptr<Message> msg, DeliveryId id)
    {
        return AMQFrame(in_place<BasicXGetOkBody>(
            ProtocolVersion(), id.getValue(),
            msg->getRedelivered(), msg->getExchangeName(),
            msg->getRoutingKey(), queue->getMessageCount())); 
    }
};

struct BasicConsumeToken : BaseToken 
{
    typedef boost::shared_ptr<BasicConsumeToken> shared_ptr;

    const string consumer;

    BasicConsumeToken(const string c) : consumer(c) {}

    AMQFrame sendMethod(intrusive_ptr<Message> msg, DeliveryId id)
    {
        return AMQFrame(in_place<BasicXDeliverBody>(
            ProtocolVersion(), consumer, id.getValue(),
            msg->getRedelivered(), msg->getExchangeName(),
            msg->getRoutingKey()));
    }

};

struct MessageDeliveryToken : BaseToken
{
    const std::string destination;
    const u_int8_t confirmMode;
    const u_int8_t acquireMode;
    const bool isPreview;

    MessageDeliveryToken(const std::string& d, u_int8_t c, u_int8_t a, bool p) : 
        destination(d), confirmMode(c), acquireMode(a), isPreview(p) {}

    AMQFrame sendMethod(intrusive_ptr<Message> msg, DeliveryId /*id*/)
    {
        //may need to set the redelivered flag:
        if (isPreview) {
            if (msg->getRedelivered()){
                msg->getProperties<PreviewDeliveryProperties>()->setRedelivered(true);
            }
            return AMQFrame(in_place<MessageXTransferBody>(
                ProtocolVersion(), 0, destination,
                confirmMode, acquireMode));
        } else {
            if (msg->getRedelivered()){
                msg->getProperties<DeliveryProperties>()->setRedelivered(true);
            }
            return AMQFrame(in_place<MessageTransferBody>(
                ProtocolVersion(), destination, confirmMode, acquireMode));
        }
    }
};

}
}

DeliveryToken::shared_ptr MessageDelivery::getBasicGetToken(Queue::shared_ptr queue)
{
    return DeliveryToken::shared_ptr(new BasicGetToken(queue));
}

DeliveryToken::shared_ptr MessageDelivery::getBasicConsumeToken(const string& consumer)
{
    return DeliveryToken::shared_ptr(new BasicConsumeToken(consumer));
}

DeliveryToken::shared_ptr MessageDelivery::getMessageDeliveryToken(const std::string& destination, 
                                                                  u_int8_t confirmMode, u_int8_t acquireMode)
{
    return DeliveryToken::shared_ptr(new MessageDeliveryToken(destination, confirmMode, acquireMode, false));
}

DeliveryToken::shared_ptr MessageDelivery::getPreviewMessageDeliveryToken(const std::string& destination, 
                                                                  u_int8_t confirmMode, u_int8_t acquireMode)
{
    return DeliveryToken::shared_ptr(new MessageDeliveryToken(destination, confirmMode, acquireMode, true));
}

void MessageDelivery::deliver(QueuedMessage& msg, 
                              framing::FrameHandler& handler, 
                              DeliveryId id, 
                              DeliveryToken::shared_ptr token, 
                              uint16_t framesize)
{
    //currently a message published from one class and delivered to
    //another may well have the wrong headers; however we will only
    //have one content class for 0-10 proper

    boost::shared_ptr<BaseToken> t = dynamic_pointer_cast<BaseToken>(token);
    AMQFrame method = t->sendMethod(msg.payload, id);
    method.setEof(false);
    handler.handle(method);
    msg.payload->sendHeader(handler, framesize);
    msg.payload->sendContent(*(msg.queue), handler, framesize);
}
