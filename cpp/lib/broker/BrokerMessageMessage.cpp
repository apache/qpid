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
#include "QpidError.h"
#include "BrokerMessageMessage.h"
#include "ChannelAdapter.h"
#include "MessageTransferBody.h"
#include "MessageAppendBody.h"
#include "Reference.h"
#include "framing/FieldTable.h"
#include "framing/BasicHeaderProperties.h"

#include <iostream>

using namespace std;
using namespace qpid::framing;

namespace qpid {
namespace broker {
	
MessageMessage::MessageMessage(
    ConnectionToken* publisher, TransferPtr transfer_
) : Message(publisher, transfer_->getDestination(),
            transfer_->getRoutingKey(),
            transfer_->getMandatory(),
            transfer_->getImmediate(),
            transfer_),
    transfer(transfer_)
{}

void MessageMessage::deliver(
    framing::ChannelAdapter& channel, 
    const std::string& consumerTag, 
    u_int64_t /*deliveryTag*/, 
    u_int32_t /*framesize*/)
{
    channel.send(
    	new MessageTransferBody(channel.getVersion(), 
                                transfer->getTicket(),
                                consumerTag,
                                getRedelivered(),
                                transfer->getImmediate(),
                                transfer->getTtl(),
                                transfer->getPriority(),
                                transfer->getTimestamp(),
                                transfer->getDeliveryMode(),
                                transfer->getExpiration(),
                                getExchange(),
                                getRoutingKey(),
                                transfer->getMessageId(),
                                transfer->getCorrelationId(),
                                transfer->getReplyTo(),
                                transfer->getContentType(),
                                transfer->getContentEncoding(),
                                transfer->getUserId(),
                                transfer->getAppId(),
                                transfer->getTransactionId(),
                                transfer->getSecurityToken(),
                                transfer->getApplicationHeaders(),
                                transfer->getBody(),
                                transfer->getMandatory()));
}

void MessageMessage::sendGetOk(
    const framing::MethodContext& /*context*/, 
    u_int32_t /*messageCount*/,
    u_int64_t /*deliveryTag*/, 
    u_int32_t /*framesize*/)
{
    // FIXME aconway 2007-02-05: 
}

bool MessageMessage::isComplete()
{
    return true;
}

u_int64_t MessageMessage::contentSize() const
{
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unfinished");
    return 0;               // FIXME aconway 2007-02-05: 
}

qpid::framing::BasicHeaderProperties* MessageMessage::getHeaderProperties()
{
    return 0;               // FIXME aconway 2007-02-05: 
}

const FieldTable& MessageMessage::getApplicationHeaders()
{
    return transfer->getApplicationHeaders();
}
bool MessageMessage::isPersistent()
{
    return transfer->getDeliveryMode() == PERSISTENT;
}

u_int32_t MessageMessage::encodedSize()
{
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unfinished");
    return 0;               // FIXME aconway 2007-02-05: 
}

u_int32_t MessageMessage::encodedHeaderSize()
{
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unfinished");
    return 0;               // FIXME aconway 2007-02-05: 
}

u_int32_t MessageMessage::encodedContentSize()
{
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unfinished");
    return 0;               // FIXME aconway 2007-02-05: 
}

u_int64_t MessageMessage::expectedContentSize()
{
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unfinished");
    return 0;               // FIXME aconway 2007-02-05: 
}


}} // namespace qpid::broker
