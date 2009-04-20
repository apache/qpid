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

#include "Message.h"
#include "PrivateImplPrivate.h"
#include "MessageImpl.h"

namespace qpid {
namespace client {

template class PrivateImpl<MessageImpl>;

Message::Message(const std::string& data, const std::string& routingKey) : PrivateImpl<MessageImpl>(new MessageImpl(data, routingKey)) {}
Message::Message(MessageImpl* i) : PrivateImpl<MessageImpl>(i) {}
Message::~Message() {}

std::string Message::getDestination() const { return impl->getDestination(); }
bool Message::isRedelivered() const { return impl->isRedelivered(); }
void Message::setRedelivered(bool redelivered) { impl->setRedelivered(redelivered); }
framing::FieldTable& Message::getHeaders() { return impl->getHeaders(); }
const framing::FieldTable& Message::getHeaders() const { return impl->getHeaders(); }
const framing::SequenceNumber& Message::getId() const { return impl->getId(); }

void Message::setData(const std::string& s) { impl->setData(s); }
const std::string& Message::getData() const { return impl->getData(); }
std::string& Message::getData() { return impl->getData(); }

void Message::appendData(const std::string& s) { impl->appendData(s); }

bool Message::hasMessageProperties() const { return impl->hasMessageProperties(); }
framing::MessageProperties& Message::getMessageProperties() { return impl->getMessageProperties(); }
const framing::MessageProperties& Message::getMessageProperties() const { return impl->getMessageProperties(); }

bool Message::hasDeliveryProperties() const { return impl->hasDeliveryProperties(); }
framing::DeliveryProperties& Message::getDeliveryProperties() { return impl->getDeliveryProperties(); }
const framing::DeliveryProperties& Message::getDeliveryProperties() const { return impl->getDeliveryProperties(); }

}}
