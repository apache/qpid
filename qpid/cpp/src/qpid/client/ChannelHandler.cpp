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

#include "ChannelHandler.h"
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/all_method_bodies.h"

using namespace qpid::client;
using namespace qpid::framing;
using namespace boost;

ChannelHandler::ChannelHandler() : StateManager(CLOSED), id(0) {}

void ChannelHandler::incoming(AMQFrame& frame)
{
    AMQBody* body = frame.getBody();
    if (getState() == OPEN) {
        ChannelCloseBody* closeBody=
            dynamic_cast<ChannelCloseBody*>(body->getMethod());
        if (closeBody) {
            setState(CLOSED_BY_PEER);
            code = closeBody->getReplyCode();
            text = closeBody->getReplyText();
            if (onClose) {
                onClose(closeBody->getReplyCode(), closeBody->getReplyText());
            }
        } else {
            try {
                in(frame);
            }catch(ChannelException& e){
                AMQMethodBody* method=body->getMethod();
                if (method)
                    close(e.code, e.toString(),
                          method->amqpClassId(), method->amqpMethodId());
                else
                    close(e.code, e.toString(), 0, 0);
            }
        }
    } else {
        if (body->getMethod()) 
            handleMethod(body->getMethod());
        else
            throw new ConnectionException(504, "Channel not open.");
    }
}

void ChannelHandler::outgoing(AMQFrame& frame)
{
    if (getState() == OPEN) {
        frame.setChannel(id);
        out(frame);
    } else if (getState() == CLOSED) {
        throw Exception("Channel not open");
    } else if (getState() == CLOSED_BY_PEER) {
        throw ChannelException(code, text);
    }
}

void ChannelHandler::open(uint16_t _id)
{
    id = _id;

    setState(OPENING);
    AMQFrame f(id, ChannelOpenBody(version));
    out(f);

    std::set<int> states;
    states.insert(OPEN);
    states.insert(CLOSED_BY_PEER);
    waitFor(states);
    if (getState() != OPEN) {
        throw Exception("Failed to open channel.");
    }
}

void ChannelHandler::close(uint16_t code, const std::string& message, uint16_t classId, uint16_t methodId)
{
    setState(CLOSING);
    AMQFrame f(id, ChannelCloseBody(version, code, message, classId, methodId));
    out(f);
}

void ChannelHandler::close()
{
    close(200, "OK", 0, 0);
    waitFor(CLOSED);
}

void ChannelHandler::handleMethod(AMQMethodBody* method)
{
    switch (getState()) {
      case OPENING:
        if (method->isA<ChannelOpenOkBody>()) {
            setState(OPEN);
        } else {
            throw ConnectionException(504, "Channel not opened.");
        }
        break;
      case CLOSING:
        if (method->isA<ChannelCloseOkBody>()) {
            setState(CLOSED);
        } //else just ignore it
        break;
      case CLOSED:
        throw ConnectionException(504, "Channel not opened.");
      default:
        throw Exception("Unexpected state encountered in ChannelHandler!");
    }
}
