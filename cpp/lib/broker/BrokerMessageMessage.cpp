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
#include "MessageOpenBody.h"
#include "MessageCloseBody.h"
#include "MessageAppendBody.h"
#include "Reference.h"
#include "framing/FieldTable.h"
#include "framing/BasicHeaderProperties.h"

#include <algorithm>

using namespace std;
using namespace qpid::framing;

namespace qpid {
namespace broker {
	
MessageMessage::MessageMessage(
    ConnectionToken* publisher, RequestId requestId_, TransferPtr transfer_
) : Message(publisher, transfer_->getDestination(),
            transfer_->getRoutingKey(),
            transfer_->getMandatory(),
            transfer_->getImmediate(),
            transfer_),
    requestId(requestId_),
    transfer(transfer_)
{}

MessageMessage::MessageMessage(
    ConnectionToken* publisher, RequestId requestId_, TransferPtr transfer_,
    ReferencePtr reference_
) : Message(publisher, transfer_->getDestination(),
            transfer_->getRoutingKey(),
            transfer_->getMandatory(),
            transfer_->getImmediate(),
            transfer_),
    requestId(requestId_),
    transfer(transfer_),
    reference(reference_)
{}

void MessageMessage::deliver(
    framing::ChannelAdapter& channel, 
    const std::string& consumerTag, 
    u_int64_t /*deliveryTag*/, 
    u_int32_t /*framesize*/)
{
	const framing::Content& body = transfer->getBody();
	
	// Send any reference data
	if (!body.isInline()){
		// Open
		channel.send(new MessageOpenBody(channel.getVersion(), reference->getId()));
		// Appends
		for(Reference::Appends::const_iterator a = reference->getAppends().begin();
			a != reference->getAppends().end();
			++a) {
			channel.send(new MessageAppendBody(*a->get()));
		}
	}
	
	// The the transfer
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
                                body,
                                transfer->getMandatory()));
	// Close any reference data
	if (!body.isInline()){
		// Close
		channel.send(new MessageCloseBody(channel.getVersion(), reference->getId()));
	}
}

void MessageMessage::sendGetOk(
    const framing::MethodContext& context,
	const std::string& destination,
    u_int32_t /*messageCount*/,
    u_int64_t /*deliveryTag*/, 
    u_int32_t /*framesize*/)
{
	framing::ChannelAdapter* channel = context.channel;
    channel->send(
    	new MessageTransferBody(channel->getVersion(), 
                                transfer->getTicket(),
                                destination,
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

bool MessageMessage::isComplete()
{
    return true;
}

u_int64_t MessageMessage::contentSize() const
{
	if (transfer->getBody().isInline())
	    return transfer->getBody().getValue().size();
	else
    	return reference->getSize();		 
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
