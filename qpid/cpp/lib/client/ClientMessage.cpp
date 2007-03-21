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
#include <ClientMessage.h>
using namespace qpid::client;
using namespace qpid::framing;

Message::Message(const std::string& d)
    : header(new AMQHeaderBody(BASIC))
{
    setData(d);
}

void Message::setData(const std::string& d) {
    data = d;
    header->setContentSize(d.size());
}

Message::Message(AMQHeaderBody::shared_ptr& _header) : header(_header){
}

Message::~Message(){
}
	
BasicHeaderProperties* Message::getHeaderProperties() const {
    return dynamic_cast<BasicHeaderProperties*>(header->getProperties());
}

const std::string& Message::getContentType() const { 
    return getHeaderProperties()->getContentType(); 
}

const std::string& Message::getContentEncoding() const { 
    return getHeaderProperties()->getContentEncoding(); 
}

FieldTable& Message::getHeaders() const { 
    return getHeaderProperties()->getHeaders(); 
}

uint8_t Message::getDeliveryMode() const { 
    return getHeaderProperties()->getDeliveryMode(); 
}

uint8_t Message::getPriority() const { 
    return getHeaderProperties()->getPriority(); 
}

const std::string& Message::getCorrelationId() const {
    return getHeaderProperties()->getCorrelationId(); 
}

const std::string& Message::getReplyTo() const { 
    return getHeaderProperties()->getReplyTo(); 
}

const std::string& Message::getExpiration() const { 
    return getHeaderProperties()->getExpiration(); 
}

const std::string& Message::getMessageId() const {
    return getHeaderProperties()->getMessageId(); 
}

uint64_t Message::getTimestamp() const { 
    return getHeaderProperties()->getTimestamp(); 
}

const std::string& Message::getType() const { 
    return getHeaderProperties()->getType(); 
}

const std::string& Message::getUserId() const { 
    return getHeaderProperties()->getUserId(); 
}

const std::string& Message::getAppId() const { 
    return getHeaderProperties()->getAppId(); 
}

const std::string& Message::getClusterId() const { 
    return getHeaderProperties()->getClusterId(); 
}

void Message::setContentType(const std::string& type){ 
    getHeaderProperties()->setContentType(type); 
}

void Message::setContentEncoding(const std::string& encoding){ 
    getHeaderProperties()->setContentEncoding(encoding); 
}

void Message::setHeaders(const FieldTable& headers){ 
    getHeaderProperties()->setHeaders(headers); 
}

void Message::setDeliveryMode(uint8_t mode){ 
    getHeaderProperties()->setDeliveryMode(mode); 
}

void Message::setPriority(uint8_t priority){ 
    getHeaderProperties()->setPriority(priority); 
}

void Message::setCorrelationId(const std::string& correlationId){ 
    getHeaderProperties()->setCorrelationId(correlationId); 
}

void Message::setReplyTo(const std::string& replyTo){ 
    getHeaderProperties()->setReplyTo(replyTo);
}

void Message::setExpiration(const std::string&  expiration){ 
    getHeaderProperties()->setExpiration(expiration); 
}

void Message::setMessageId(const std::string& messageId){ 
    getHeaderProperties()->setMessageId(messageId); 
}

void Message::setTimestamp(uint64_t timestamp){ 
    getHeaderProperties()->setTimestamp(timestamp); 
}

void Message::setType(const std::string& type){ 
    getHeaderProperties()->setType(type); 
}

void Message::setUserId(const std::string& userId){ 
    getHeaderProperties()->setUserId(userId); 
}

void Message::setAppId(const std::string& appId){
    getHeaderProperties()->setAppId(appId); 
}

void Message::setClusterId(const std::string& clusterId){ 
    getHeaderProperties()->setClusterId(clusterId); 
}


uint64_t Message::getDeliveryTag() const {
    BasicDeliverBody* deliver=dynamic_cast<BasicDeliverBody*>(method.get());
    return deliver ? deliver->getDeliveryTag() : 0;
}
