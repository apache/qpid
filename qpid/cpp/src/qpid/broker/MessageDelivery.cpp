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
#include "qpid/framing/MessageTransferBody.h"


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
        if (msg->getRedelivered()){
            msg->getProperties<DeliveryProperties>()->setRedelivered(true);
        }
        return AMQFrame(in_place<MessageTransferBody>(
                        ProtocolVersion(), destination, confirmMode, acquireMode));
    }
};

}
}

DeliveryToken::shared_ptr MessageDelivery::getMessageDeliveryToken(const std::string& destination, 
                                                                  u_int8_t confirmMode, u_int8_t acquireMode)
{
    return DeliveryToken::shared_ptr(new MessageDeliveryToken(destination, confirmMode, acquireMode, false));
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
