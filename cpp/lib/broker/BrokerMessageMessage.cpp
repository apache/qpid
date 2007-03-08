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

// TODO: astitcher 1-Mar-2007: This code desperately needs better factoring
void MessageMessage::transferMessage(
    framing::ChannelAdapter& channel, 
    const std::string& consumerTag, 
    u_int32_t framesize)
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
			u_int32_t sizeleft = (*a)->size();
			const string& content = (*a)->getBytes();
			// Calculate overhead bytes
			// Assume that the overhead is constant as the reference name doesn't change
			u_int32_t overhead = sizeleft - content.size();
			string::size_type contentStart = 0;
			while (sizeleft) {
				string::size_type contentSize = sizeleft <= framesize ? sizeleft : framesize-overhead;
				channel.send(new MessageAppendBody(channel.getVersion(), reference->getId(),
						string(content, contentStart, contentSize)));
					sizeleft -= contentSize;
					contentStart += contentSize;
			}
		}
	}
	
	// The transfer
	if ( transfer->size()<=framesize ) {
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
	} else {
		// Thing to do here is to construct a simple reference message then deliver that instead
		// fragmentation will be taken care of in the delivery if necessary;
		string content = body.getValue();
		string refname = "dummy";
		TransferPtr newTransfer(
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
	                                framing::Content(REFERENCE, refname),
	                                transfer->getMandatory()));
		ReferencePtr newRef(new Reference(refname));
		Reference::AppendPtr newAppend(new MessageAppendBody(channel.getVersion(), refname, content));
		newRef->append(newAppend);
		MessageMessage newMsg(const_cast<ConnectionToken*>(getPublisher()), 0, newTransfer, newRef);
		newMsg.transferMessage(channel, consumerTag, framesize);
		return;
	}
	// Close any reference data
	if (!body.isInline()){
		// Close
		channel.send(new MessageCloseBody(channel.getVersion(), reference->getId()));
	}
}

void MessageMessage::deliver(
    framing::ChannelAdapter& channel, 
    const std::string& consumerTag, 
    u_int64_t /*deliveryTag*/, 
    u_int32_t framesize)
{
	transferMessage(channel, consumerTag, framesize);
}

void MessageMessage::sendGetOk(
    const framing::MethodContext& context,
	const std::string& destination,
    u_int32_t /*messageCount*/,
    u_int64_t /*deliveryTag*/, 
    u_int32_t framesize)
{
	framing::ChannelAdapter* channel = context.channel;
	transferMessage(*channel, destination, framesize);
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
