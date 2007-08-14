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
#include "qpid/QpidError.h"
#include "BrokerMessageMessage.h"
#include "qpid/framing/ChannelAdapter.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/MessageOpenBody.h"
#include "qpid/framing/MessageCloseBody.h"
#include "qpid/framing/MessageAppendBody.h"
#include "Reference.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/BasicHeaderProperties.h"
#include "RecoveryManagerImpl.h"

#include <algorithm>

using namespace std;
using namespace boost;
using namespace qpid::framing;

namespace qpid {
namespace broker {

struct MessageDeliveryToken : public DeliveryToken
{
    const std::string destination;

    MessageDeliveryToken(const std::string& d) : destination(d) {}
};
	
MessageMessage::MessageMessage(
    ConnectionToken* publisher, TransferPtr transfer_
) : Message(publisher, transfer_->getDestination(),
            transfer_->getRoutingKey(),
            transfer_->getRejectUnroutable(),
            transfer_->getImmediate(),
            transfer_),
    transfer(transfer_)
{
    assert(transfer->getBody().isInline());
}

MessageMessage::MessageMessage(
    ConnectionToken* publisher, TransferPtr transfer_, ReferencePtr reference_
) : Message(publisher, transfer_->getDestination(),
            transfer_->getRoutingKey(),
            transfer_->getRejectUnroutable(),
            transfer_->getImmediate(),
            transfer_),
    transfer(transfer_),
    reference(reference_)
{
    assert(!transfer->getBody().isInline());
    assert(reference_);
}

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
    ReferencePtr ref= getReference();
    if (ref){

        // Open
        channel.send(make_shared_ptr(new MessageOpenBody(channel.getVersion(), ref->getId())));
        // Appends
        for(Reference::Appends::const_iterator a = ref->getAppends().begin();
            a != ref->getAppends().end();
            ++a) {
            uint32_t sizeleft = (*a)->size();
            const string& content = (*a)->getBytes();
            // Calculate overhead bytes
            // Assume that the overhead is constant as the reference name doesn't change
            uint32_t overhead = sizeleft - content.size();
            string::size_type contentStart = 0;
            while (sizeleft) {
                string::size_type contentSize = sizeleft <= framesize ? sizeleft : framesize-overhead;
                channel.send(make_shared_ptr(new MessageAppendBody(channel.getVersion(), ref->getId(),
                                                                   string(content, contentStart, contentSize))));
                sizeleft -= contentSize;
                contentStart += contentSize;
            }
        }
    }
	
    // The transfer
    if ( transfer->size()<=framesize ) {
    	channel.send(make_shared_ptr(
            new MessageTransferBody(channel.getVersion(), 
                                    transfer->getTicket(),
                                    consumerTag,
                                    getRedelivered(),
                                    transfer->getRejectUnroutable(),
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
                                    0, /*content-length*/
                                    string(), /*type*/
                                    transfer->getUserId(),
                                    transfer->getAppId(),
                                    transfer->getTransactionId(),
                                    transfer->getSecurityToken(),
                                    transfer->getApplicationHeaders(),
                                    body)));
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
                                    transfer->getRejectUnroutable(),
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
                                    0, /*content-length*/
                                    string(), /*type*/
                                    transfer->getUserId(),
                                    transfer->getAppId(),
                                    transfer->getTransactionId(),
                                    transfer->getSecurityToken(),
                                    transfer->getApplicationHeaders(),
                                    framing::Content(REFERENCE, refname)));
        ReferencePtr newRef(new Reference(refname));
        Reference::AppendPtr newAppend(new MessageAppendBody(channel.getVersion(), refname, content));
        newRef->append(newAppend);
        MessageMessage newMsg(const_cast<ConnectionToken*>(getPublisher()), newTransfer, newRef);
        newMsg.transferMessage(channel, consumerTag, framesize);
        return;
    }
    // Close any reference data
    if (ref)
        channel.send(make_shared_ptr(new MessageCloseBody(channel.getVersion(), ref->getId())));
}


void MessageMessage::deliver(ChannelAdapter& channel, DeliveryId, DeliveryToken::shared_ptr token, uint32_t framesize)
{
    transferMessage(channel, shared_polymorphic_cast<MessageDeliveryToken>(token)->destination, framesize);
}

void MessageMessage::deliver(ChannelAdapter& channel, const std::string& destination, uint32_t framesize)
{
    transferMessage(channel, destination, framesize);
}

bool MessageMessage::isComplete()
{
    return true;
}

uint64_t MessageMessage::contentSize() const
{
    if (transfer->getBody().isInline())
        return transfer->getBody().getValue().size();
    else {
        assert(getReference());
    	return getReference()->getSize();
    }
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
        assert(getReference());
        string data;
        const Reference::Appends& appends = getReference()->getAppends();
        for(Reference::Appends::const_iterator a = appends.begin(); a != appends.end(); ++a) {
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
                                   transfer->getRejectUnroutable(),
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
                                    0, /*content-length*/
                                    string(), /*type*/
                                   transfer->getUserId(),
                                   transfer->getAppId(),
                                   transfer->getTransactionId(),
                                   transfer->getSecurityToken(),
                                   transfer->getApplicationHeaders(),
                                   body);

}

MessageMessage::ReferencePtr MessageMessage::getReference() const {
    return reference;
}

uint32_t MessageMessage::getRequiredCredit() const
{
    //TODO: change when encoding changes. Should be the payload of any
    //header & body frames.
    return transfer->size();
}


DeliveryToken::shared_ptr MessageMessage::getToken(const std::string& destination)
{
    return DeliveryToken::shared_ptr(new MessageDeliveryToken(destination));
}

}} // namespace qpid::broker

