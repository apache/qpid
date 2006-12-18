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
#include <IncomingMessage.h>
#include <QpidError.h>
#include <iostream>

using namespace qpid::client;
using namespace qpid::framing;

IncomingMessage::IncomingMessage(BasicDeliverBody::shared_ptr intro) : delivered(intro){}
IncomingMessage::IncomingMessage(BasicReturnBody::shared_ptr intro): returned(intro){}
IncomingMessage::IncomingMessage(BasicGetOkBody::shared_ptr intro): response(intro){}

IncomingMessage::~IncomingMessage(){
}

void IncomingMessage::setHeader(AMQHeaderBody::shared_ptr _header){
    this->header = _header;
}

void IncomingMessage::addContent(AMQContentBody::shared_ptr _content){
    this->content.push_back(_content);
}

bool IncomingMessage::isComplete(){
    return header != 0 && header->getContentSize() == contentSize();
}

bool IncomingMessage::isReturn(){
    return returned;
}

bool IncomingMessage::isDelivery(){
    return delivered;
}

bool IncomingMessage::isResponse(){
    return response;
}

const string& IncomingMessage::getConsumerTag(){
    if(!isDelivery()) THROW_QPID_ERROR(CLIENT_ERROR, "Consumer tag only valid for delivery");
    return delivered->getConsumerTag();
}

u_int64_t IncomingMessage::getDeliveryTag(){
    if(!isDelivery()) THROW_QPID_ERROR(CLIENT_ERROR, "Delivery tag only valid for delivery");
    return delivered->getDeliveryTag();
}

AMQHeaderBody::shared_ptr& IncomingMessage::getHeader(){
    return header;
}

void IncomingMessage::getData(string& s){
    int count(content.size());
    for(int i = 0; i < count; i++){
        if(i == 0) s = content[i]->getData();
	else s += content[i]->getData();
    }
}

u_int64_t IncomingMessage::contentSize(){
    u_int64_t size(0);
    u_int64_t count(content.size());
    for(u_int64_t i = 0; i < count; i++){
	size += content[i]->size();
    }
    return size;
}
