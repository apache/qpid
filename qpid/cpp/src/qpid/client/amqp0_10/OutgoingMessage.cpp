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
#include "qpid/client/amqp0_10/OutgoingMessage.h"
#include "qpid/client/amqp0_10/AddressResolution.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/types/Variant.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/framing/enum.h"
#include <sstream>

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::messaging::Address;
using qpid::messaging::MessageImplAccess;
using qpid::types::Variant;
using namespace qpid::framing::message;
using namespace qpid::amqp_0_10;

namespace {
//TODO: unify conversion to and from 0-10 message that is currently
//split between IncomingMessages and OutgoingMessage
const std::string SUBJECT("qpid.subject");
const std::string X_APP_ID("x-amqp-0-10.app-id");
const std::string X_ROUTING_KEY("x-amqp-0-10.routing-key");
const std::string X_CONTENT_ENCODING("x-amqp-0-10.content-encoding");
}

void OutgoingMessage::convert(const qpid::messaging::Message& from)
{
    //TODO: need to avoid copying as much as possible
    if (from.getContentObject().getType() == qpid::types::VAR_MAP) {
        std::string content;
        qpid::amqp_0_10::MapCodec::encode(from.getContentObject().asMap(), content);
        message.getMessageProperties().setContentType(qpid::amqp_0_10::MapCodec::contentType);
        message.setData(content);
    } else if (from.getContentObject().getType() == qpid::types::VAR_LIST) {
        std::string content;
        qpid::amqp_0_10::ListCodec::encode(from.getContentObject().asList(), content);
        message.getMessageProperties().setContentType(qpid::amqp_0_10::ListCodec::contentType);
        message.setData(content);
    } else {
        message.setData(from.getContent());
        message.getMessageProperties().setContentType(from.getContentType());
    }
    if ( !from.getCorrelationId().empty() )
        message.getMessageProperties().setCorrelationId(from.getCorrelationId());
    message.getMessageProperties().setUserId(from.getUserId());
    const Address& address = from.getReplyTo();
    if (address) {
        message.getMessageProperties().setReplyTo(AddressResolution::convert(address));
    }
    if (!subject.empty()) {
        Variant v(subject); v.setEncoding("utf8");
        translate(from.getProperties(), SUBJECT, v, message.getMessageProperties().getApplicationHeaders());
    } else {
        translate(from.getProperties(), message.getMessageProperties().getApplicationHeaders());
    }
    if (from.getTtl().getMilliseconds()) {
        message.getDeliveryProperties().setTtl(from.getTtl().getMilliseconds());
    }
    if (from.getDurable()) {
        message.getDeliveryProperties().setDeliveryMode(DELIVERY_MODE_PERSISTENT);
    }
    if (from.getRedelivered()) {
        message.getDeliveryProperties().setRedelivered(true);
    }
    if (from.getPriority()) message.getDeliveryProperties().setPriority(from.getPriority());

    //allow certain 0-10 specific items to be set through special properties: 
    //    message-id, app-id, content-encoding
    if (from.getMessageId().size()) {
        qpid::framing::Uuid uuid;
        std::istringstream data(from.getMessageId());
        data >> uuid;
        message.getMessageProperties().setMessageId(uuid);
    }
    Variant::Map::const_iterator i;
    i = from.getProperties().find(X_APP_ID);
    if (i != from.getProperties().end()) {
        message.getMessageProperties().setAppId(i->second.asString());
    }
    i = from.getProperties().find(X_CONTENT_ENCODING);
    if (i != from.getProperties().end()) {
        message.getMessageProperties().setContentEncoding(i->second.asString());
    }
}

void OutgoingMessage::setSubject(const std::string& s)
{
    subject = s;
}

std::string OutgoingMessage::getSubject() const
{
    return subject;
}

}}} // namespace qpid::client::amqp0_10
