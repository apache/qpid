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
#include <MessageBuilder.h>

#include <InMemoryContent.h>
#include <LazyLoadedContent.h>

using namespace qpid::broker;
using namespace qpid::framing;
using std::auto_ptr;

MessageBuilder::MessageBuilder(CompletionHandler* _handler, MessageStore* const _store, u_int64_t _stagingThreshold) : 
    handler(_handler),
    store(_store),
    stagingThreshold(_stagingThreshold)
{}

void MessageBuilder::route(){
    if (message->isComplete()) {
        if (handler) handler->complete(message);
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
    if (stagingThreshold && header->getContentSize() >= stagingThreshold) {
        store->stage(message.get());
        message->releaseContent(store);
    } else {
        auto_ptr<Content> content(new InMemoryContent());
        message->setContent(content);
    }
    route();
}

void MessageBuilder::addContent(AMQContentBody::shared_ptr& content){
    if(!message.get()){
        THROW_QPID_ERROR(PROTOCOL_ERROR + 504, "Invalid message sequence: got content before publish.");
    }
    message->addContent(content);
    route();
}
