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

#include "../QpidError.h"
#include "MessageHandlerImpl.h"
#include "BrokerChannel.h"
#include "../framing/FramingContent.h"
#include "Connection.h"
#include "Broker.h"
#include "BrokerMessageMessage.h"
#include "../gen/MessageAppendBody.h"
#include "../gen/MessageTransferBody.h"
#include "BrokerAdapter.h"

namespace qpid {
namespace broker {

using namespace framing;

MessageHandlerImpl::MessageHandlerImpl(CoreRefs& parent)
    : HandlerImplType(parent) {}

//
// Message class method handlers
//

void
MessageHandlerImpl::cancel(const MethodContext& context,
                           const string& destination )
{
    channel.cancel(destination);
    client.ok(context.getRequestId());
}

void
MessageHandlerImpl::open(const MethodContext& context,
                         const string& reference)
{
    references.open(reference);
    client.ok(context.getRequestId());
}

void
MessageHandlerImpl::append(const MethodContext& context,
                           const string& reference,
                           const string& /*bytes*/ )
{
    references.get(reference)->append(
        boost::shared_polymorphic_downcast<MessageAppendBody>(
            context.methodBody));
    client.ok(context.getRequestId());
}

void
MessageHandlerImpl::close(const MethodContext& context,
                          const string& reference)
{
	Reference::shared_ptr ref = references.get(reference);
    client.ok(context.getRequestId());
    
    // Send any transfer messages to their correct exchanges and okay them
    const Reference::Messages& msgs = ref->getMessages();
    for (Reference::Messages::const_iterator m = msgs.begin(); m != msgs.end(); ++m) {
        channel.handleInlineTransfer(*m);
    	client.ok((*m)->getRequestId());
    }
    ref->close();
}

void
MessageHandlerImpl::checkpoint(const MethodContext& context,
                               const string& /*reference*/,
                               const string& /*identifier*/ )
{
    // Initial implementation (which is conforming) is to do nothing here
    // and return offset zero for the resume
    client.ok(context.getRequestId());
}

void
MessageHandlerImpl::resume(const MethodContext& context,
                           const string& reference,
                           const string& /*identifier*/ )
{
    // Initial (null) implementation
    // open reference and return 0 offset
    references.open(reference);
    client.offset(0, context.getRequestId());
}

void
MessageHandlerImpl::offset(const MethodContext&,
                           uint64_t /*value*/ )
{
    // Shouldn't ever receive this as it is reponse to resume
    // which is never sent
    // TODO astitcher 2007-02-16 What is the correct exception to throw here?    
    THROW_QPID_ERROR(INTERNAL_ERROR, "impossible");
}

void
MessageHandlerImpl::consume(const MethodContext& context,
                            uint16_t /*ticket*/,
                            const string& queueName,
                            const string& destination,
                            bool noLocal,
                            bool noAck,
                            bool exclusive,
                            const framing::FieldTable& filter )
{
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    if(!destination.empty() && channel.exists(destination))
        throw ConnectionException(530, "Consumer tags must be unique");
    string tag = destination;
    channel.consume(
        tag, queue, !noAck, exclusive,
        noLocal ? &connection : 0, &filter);
    client.ok(context.getRequestId());
    // Dispatch messages as there is now a consumer.
    queue->dispatch();
}

void
MessageHandlerImpl::get( const MethodContext& context,
                         uint16_t /*ticket*/,
                         const string& queueName,
                         const string& destination,
                         bool noAck )
{
    Queue::shared_ptr queue =
        connection.getQueue(queueName, context.channel->getId());
    
    if(channel.get(queue, destination, !noAck))
        client.ok(context.getRequestId());
    else 
        client.empty(context.getRequestId());
}

void
MessageHandlerImpl::empty( const MethodContext& )
{
    // Shouldn't ever receive this as it is a response to get
    // which is never sent
    // TODO astitcher 2007-02-09 What is the correct exception to throw here?
    THROW_QPID_ERROR(INTERNAL_ERROR, "Impossible");
}

void
MessageHandlerImpl::ok(const MethodContext& /*context*/)
{
    channel.ack();
}

void
MessageHandlerImpl::qos(const MethodContext& context,
                        uint32_t prefetchSize,
                        uint16_t prefetchCount,
                        bool /*global*/ )
{
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
    client.ok(context.getRequestId());
}

void
MessageHandlerImpl::recover(const MethodContext& context,
                            bool requeue)
{
    channel.recover(requeue);
    client.ok(context.getRequestId());
}

void
MessageHandlerImpl::reject(const MethodContext& /*context*/,
                           uint16_t /*code*/,
                           const string& /*text*/ )
{
    channel.ack();
    // channel.requeue();
}

void
MessageHandlerImpl::transfer(const MethodContext& context,
                             uint16_t /*ticket*/,
                             const string& /* destination */,
                             bool /*redelivered*/,
                             bool /*immediate*/,
                             uint64_t /*ttl*/,
                             uint8_t /*priority*/,
                             uint64_t /*timestamp*/,
                             uint8_t /*deliveryMode*/,
                             uint64_t /*expiration*/,
                             const string& /*exchangeName*/,
                             const string& /*routingKey*/,
                             const string& /*messageId*/,
                             const string& /*correlationId*/,
                             const string& /*replyTo*/,
                             const string& /*contentType*/,
                             const string& /*contentEncoding*/,
                             const string& /*userId*/,
                             const string& /*appId*/,
                             const string& /*transactionId*/,
                             const string& /*securityToken*/,
                             const framing::FieldTable& /*applicationHeaders*/,
                             const framing::Content& body,
                             bool /*mandatory*/)
{
    MessageTransferBody::shared_ptr transfer(
        boost::shared_polymorphic_downcast<MessageTransferBody>(
            context.methodBody));
    RequestId requestId = context.getRequestId();
    
    if (body.isInline()) {
	    MessageMessage::shared_ptr message(
	        new MessageMessage(&connection, requestId, transfer));
        channel.handleInlineTransfer(message);
	    client.ok(requestId);
    } else { 
        Reference::shared_ptr ref(references.get(body.getValue()));
	    MessageMessage::shared_ptr message(
	        new MessageMessage(&connection, requestId, transfer, ref));
        ref->addMessage(message);
    }
}


}} // namespace qpid::broker
