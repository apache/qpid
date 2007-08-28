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
#include "MessageBuilder.h"

#include "Message.h"
#include "MessageStore.h"
#include "qpid/Exception.h"
#include "qpid/framing/AMQFrame.h"

using namespace qpid::broker;
using namespace qpid::framing;

MessageBuilder::MessageBuilder(MessageStore* const _store, uint64_t _stagingThreshold) : 
    state(DORMANT), store(_store), stagingThreshold(_stagingThreshold), staging(false) {}

void MessageBuilder::handle(AMQFrame& frame)
{
    switch(state) {
    case METHOD:
        checkType(METHOD_BODY, frame.getBody()->type());
        state = HEADER;
        break;
    case HEADER:
        checkType(HEADER_BODY, frame.getBody()->type());
        state = CONTENT;
        break;
    case CONTENT:
        checkType(CONTENT_BODY, frame.getBody()->type());
        break;
    default:
        throw ConnectionException(504, "Invalid frame sequence for message.");        
    }
    if (staging) {
        store->appendContent(*message, frame.castBody<AMQContentBody>()->getData());
    } else {
        message->getFrames().append(frame);
        //have we reached the staging limit? if so stage message and release content
        if (state == CONTENT && stagingThreshold && message->getFrames().getContentSize() >= stagingThreshold) {
            store->stage(*message);
            message->releaseContent(store);
            staging = true;
        }
    }
}

void MessageBuilder::checkType(uint8_t expected, uint8_t actual)
{
    if (expected != actual) {
        throw ConnectionException(504, "Invalid frame sequence for message.");
    }
}

void MessageBuilder::end()
{
    message.reset();
    state = DORMANT;
    staging = false;
}

void MessageBuilder::start(const SequenceNumber& id)
{
    message = Message::shared_ptr(new Message(id));
    state = METHOD;
    staging = false;
}
