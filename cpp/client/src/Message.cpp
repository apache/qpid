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
#include "Message.h"

using namespace qpid::client;
using namespace qpid::framing;

Message::Message(){
    header = AMQHeaderBody::shared_ptr(new AMQHeaderBody(BASIC));
}

Message::Message(AMQHeaderBody::shared_ptr& _header) : header(_header){
}

Message::~Message(){
}
	
BasicHeaderProperties* Message::getHeaderProperties(){
    return dynamic_cast<BasicHeaderProperties*>(header->getProperties());
}

std::string& Message::getContentType(){ 
    return getHeaderProperties()->getContentType(); 
}

std::string& Message::getContentEncoding(){ 
    return getHeaderProperties()->getContentEncoding(); 
}

FieldTable& Message::getHeaders(){ 
    return getHeaderProperties()->getHeaders(); 
}

u_int8_t Message::getDeliveryMode(){ 
    return getHeaderProperties()->getDeliveryMode(); 
}

u_int8_t Message::getPriority(){ 
    return getHeaderProperties()->getPriority(); 
}

std::string& Message::getCorrelationId(){
    return getHeaderProperties()->getCorrelationId(); 
}

std::string& Message::getReplyTo(){ 
    return getHeaderProperties()->getReplyTo(); 
}

std::string& Message::getExpiration(){ 
    return getHeaderProperties()->getExpiration(); 
}

std::string& Message::getMessageId(){
    return getHeaderProperties()->getMessageId(); 
}

u_int64_t Message::getTimestamp(){ 
    return getHeaderProperties()->getTimestamp(); 
}

std::string& Message::getType(){ 
    return getHeaderProperties()->getType(); 
}

std::string& Message::getUserId(){ 
    return getHeaderProperties()->getUserId(); 
}

std::string& Message::getAppId(){ 
    return getHeaderProperties()->getAppId(); 
}

std::string& Message::getClusterId(){ 
    return getHeaderProperties()->getClusterId(); 
}

void Message::setContentType(std::string& type){ 
    getHeaderProperties()->setContentType(type); 
}

void Message::setContentEncoding(std::string& encoding){ 
    getHeaderProperties()->setContentEncoding(encoding); 
}

void Message::setHeaders(FieldTable& headers){ 
    getHeaderProperties()->setHeaders(headers); 
}

void Message::setDeliveryMode(u_int8_t mode){ 
    getHeaderProperties()->setDeliveryMode(mode); 
}

void Message::setPriority(u_int8_t priority){ 
    getHeaderProperties()->setPriority(priority); 
}

void Message::setCorrelationId(std::string& correlationId){ 
    getHeaderProperties()->setCorrelationId(correlationId); 
}

void Message::setReplyTo(std::string& replyTo){ 
    getHeaderProperties()->setReplyTo(replyTo);
}

void Message::setExpiration(std::string&  expiration){ 
    getHeaderProperties()->setExpiration(expiration); 
}

void Message::setMessageId(std::string& messageId){ 
    getHeaderProperties()->setMessageId(messageId); 
}

void Message::setTimestamp(u_int64_t timestamp){ 
    getHeaderProperties()->setTimestamp(timestamp); 
}

void Message::setType(std::string& type){ 
    getHeaderProperties()->setType(type); 
}

void Message::setUserId(std::string& userId){ 
    getHeaderProperties()->setUserId(userId); 
}

void Message::setAppId(std::string& appId){
    getHeaderProperties()->setAppId(appId); 
}

void Message::setClusterId(std::string& clusterId){ 
    getHeaderProperties()->setClusterId(clusterId); 
}
