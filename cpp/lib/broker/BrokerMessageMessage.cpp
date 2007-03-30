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
#include "framing/AMQFrame.h"
#include "framing/FieldTable.h"
#include "framing/BasicHeaderProperties.h"
#include "RecoveryManagerImpl.h"

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

/**
 * Currently used by message store impls to recover messages 
 */
MessageMessage::MessageMessage() : transfer(new MessageTransferBody(qpid::framing::highestProtocolVersion)) {}

// TODO: astitcher 1-Mar-2007: This code desperately needs better factoring
void MessageMessage::transferMessage(
    framing::ChannelAdapter& channel, 
    const std::string& consumerTag, 
    uint32_t framesize)
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
			uint32_t sizeleft = (*a)->size();
			const string& content = (*a)->getBytes();
			// Calculate overhead bytes
			// Assume that the overhead is constant as the reference name doesn't change
			uint32_t overhead = sizeleft - content.size();
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
    uint64_t /*deliveryTag*/, 
    uint32_t framesize)
{
	transferMessage(channel, consumerTag, framesize);
}

void MessageMessage::sendGetOk(
    const framing::MethodContext& context,
	const std::string& destination,
    uint32_t /*messageCount*/,
    uint64_t /*deliveryTag*/, 
    uint32_t framesize)
{
	framing::ChannelAdapter* channel = context.channel;
	transferMessage(*channel, destination, framesize);
}

bool MessageMessage::isComplete()
{
    return true;
}

uint64_t MessageMessage::contentSize() const
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

uint32_t MessageMessage::encodedSize() const
{
    return encodedHeaderSize() + encodedContentSize();
}

uint32_t MessageMessage::encodedHeaderSize() const
{
    return RecoveryManagerImpl::encodedMessageTypeSize() + transfer->size() - transfer->baseSize(); 
}

uint32_t MessageMessage::encodedContentSize() const
{
    return 0;
}

uint64_t MessageMessage::expectedContentSize()
{
    return 0;
}

void MessageMessage::encode(Buffer& buffer) const
{
    encodeHeader(buffer);
}

void MessageMessage::encodeHeader(Buffer& buffer) const
{
    RecoveryManagerImpl::encodeMessageType(*this, buffer);
    if (transfer->getBody().isInline()) {
        transfer->encodeContent(buffer);
    } else {
        string data;
        for(Reference::Appends::const_iterator a = reference->getAppends().begin(); a != reference->getAppends().end(); ++a) {
            data += (*a)->getBytes();
        }
        framing::Content body(INLINE, data);
        std::auto_ptr<MessageTransferBody> copy(copyTransfer(transfer->version, transfer->getDestination(), body));
        copy->encodeContent(buffer);
    }
}

void MessageMessage::decodeHeader(Buffer& buffer)
{
    //don't care about the type here, but want encode/decode to be symmetric
    RecoveryManagerImpl::decodeMessageType(buffer);    

    transfer->decodeContent(buffer);
}

void MessageMessage::decodeContent(Buffer& /*buffer*/, uint32_t /*chunkSize*/)
{    
}


MessageTransferBody* MessageMessage::copyTransfer(const ProtocolVersion& version,
                                                  const string& destination, 
                                                  const framing::Content& body) const
{
    return new MessageTransferBody(version, 
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
                                   body,
                                   transfer->getMandatory());

}
}} // namespace qpid::broker
