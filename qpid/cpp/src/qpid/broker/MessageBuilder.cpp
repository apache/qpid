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
#include "qpid/broker/MessageBuilder.h"

#include "qpid/broker/Message.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"

using boost::intrusive_ptr;
using namespace qpid::broker;
using namespace qpid::framing;

namespace
{
    std::string type_str(uint8_t type);
    const std::string QPID_MANAGEMENT("qpid.management");
}

MessageBuilder::MessageBuilder() : state(DORMANT), copyExchange(true) {}

void MessageBuilder::handle(AMQFrame& frame)
{
    uint8_t type = frame.getBody()->type();
    switch(state) {
    case METHOD:
        checkType(METHOD_BODY, type);
        if (!frame.getMethod()->isA<qpid::framing::MessageTransferBody>())
            throw NotImplementedException(QPID_MSG("Unexpected method: " << *(frame.getMethod())));

        exchange = frame.castBody<qpid::framing::MessageTransferBody>()->getDestination();
        state = HEADER;
        break;
    case HEADER:
        if (type == CONTENT_BODY) {
            //TODO: rethink how to handle non-existent headers(?)...
            //didn't get a header: add in a dummy
            AMQFrame header((AMQHeaderBody()));
            header.setBof(false);
            header.setEof(false);
            message->getFrames().append(header);
        } else if (type == HEADER_BODY) {
            if (copyExchange) {
                frame.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true)->
                    setExchange(exchange);
            }
        } else {
            throw CommandInvalidException(
                QPID_MSG("Invalid frame sequence for message, expected header or content got "
                         << type_str(type) << ")"));
        }
        state = CONTENT;
        break;
    case CONTENT:
        checkType(CONTENT_BODY, type);
        break;
    default:
        throw CommandInvalidException(QPID_MSG("Invalid frame sequence for message (state=" << state << ")"));
    }
    message->getFrames().append(frame);
}

void MessageBuilder::end()
{
    message->computeRequiredCredit();
    message = 0;
    state = DORMANT;
}

void MessageBuilder::start(const SequenceNumber& id)
{
    message = intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer>(new qpid::broker::amqp_0_10::MessageTransfer(id));
    state = METHOD;
}

namespace {

const std::string HEADER_BODY_S = "HEADER";
const std::string METHOD_BODY_S = "METHOD";
const std::string CONTENT_BODY_S = "CONTENT";
const std::string HEARTBEAT_BODY_S = "HEARTBEAT";
const std::string UNKNOWN = "unknown";

std::string type_str(uint8_t type)
{
    switch(type) {
    case METHOD_BODY: return METHOD_BODY_S;
    case HEADER_BODY: return HEADER_BODY_S;
    case CONTENT_BODY: return CONTENT_BODY_S;
    case HEARTBEAT_BODY: return HEARTBEAT_BODY_S;
    }
    return UNKNOWN;
}

}

void MessageBuilder::checkType(uint8_t expected, uint8_t actual)
{
    if (expected != actual) {
        throw CommandInvalidException(QPID_MSG("Invalid frame sequence for message (expected "
                                               << type_str(expected) << " got " << type_str(actual) << ")"));
    }
}

boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> MessageBuilder::getMessage() { return message; }
