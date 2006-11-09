/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <qpid/broker/MessageBuilder.h>

using namespace qpid::broker;
using namespace qpid::framing;

MessageBuilder::MessageBuilder(CompletionHandler* _handler) : handler(_handler) {}

void MessageBuilder::route(){
    if(message->isComplete()){
        if(handler) handler->complete(message);
        message.reset();
    }
}

void MessageBuilder::initialise(Message::shared_ptr& msg){
    if(message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got publish before previous content was completed.");
    }
    message = msg;
}

void MessageBuilder::setHeader(AMQHeaderBody::shared_ptr& header){
    if(!message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got header before publish.");
    }
    message->setHeader(header);
    route();
}

void MessageBuilder::addContent(AMQContentBody::shared_ptr& content){
    if(!message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got content before publish.");
    }
    message->addContent(content);
    route();
}
