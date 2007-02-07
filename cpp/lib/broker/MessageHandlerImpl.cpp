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

#include "QpidError.h"
#include "MessageHandlerImpl.h"
#include "BrokerChannel.h"
#include "FramingContent.h"
#include "Connection.h"
#include "Broker.h"
#include "BrokerMessageMessage.h"
#include "MessageAppendBody.h"
#include "MessageTransferBody.h"

namespace qpid {
namespace broker {

using namespace framing;

MessageHandlerImpl::MessageHandlerImpl(Channel& ch, Connection& c, Broker& b)
    : channel(ch), connection(c), broker(b), references(ch),
      client(connection.client->getMessage())
{}

//
// Message class method handlers
//
void
MessageHandlerImpl::append(const MethodContext& context,
                           const string& reference,
                           const string& /*bytes*/ )
{
    references.get(reference).append(
        boost::shared_polymorphic_downcast<MessageAppendBody>(
            context.methodBody));
    client.ok(context);
}


void
MessageHandlerImpl::cancel(const MethodContext& context,
                           const string& destination )
{
    channel.cancel(destination);
    client.ok(context);
}

void
MessageHandlerImpl::checkpoint(const MethodContext&,
                               const string& /*reference*/,
                               const string& /*identifier*/ )
{
    // FIXME astitcher 2007-01-11: 0-9 feature
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unimplemented ");
}

void
MessageHandlerImpl::close(const MethodContext& context,
                          const string& reference)
{
    references.get(reference).close();
    client.ok(context);
}

void
MessageHandlerImpl::consume(const MethodContext& context,
                            u_int16_t /*ticket*/,
                            const string& queueName,
                            const string& destination,
                            bool noLocal,
                            bool noAck,
                            bool exclusive,
                            const qpid::framing::FieldTable& filter )
{
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    if(!destination.empty() && channel.exists(destination))
        throw ConnectionException(530, "Consumer tags must be unique");
    string tag = destination;
    channel.consume(
        tag, queue, !noAck, exclusive,
        noLocal ? &connection : 0, &filter);
    client.ok(context);
    // Dispatch messages as there is now a consumer.
    queue->dispatch();
}

void
MessageHandlerImpl::empty( const MethodContext& )
{
    // FIXME astitcher 2007-01-11: 0-9 feature
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unimplemented ");
}

void
MessageHandlerImpl::get( const MethodContext& context,
                         u_int16_t /*ticket*/,
                         const string& queueName,
                         const string& /*destination*/,
                         bool noAck )
{
    //assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature

    Queue::shared_ptr queue =
        connection.getQueue(queueName, context.channel->getId());
    
    // FIXME: get is probably Basic specific
    if(channel.get(queue, !noAck))
        client.ok(context);
    else 
        client.empty(context);
}

void
MessageHandlerImpl::offset(const MethodContext&,
                           u_int64_t /*value*/ )
{
    // FIXME astitcher 2007-01-11: 0-9 feature
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unimplemented ");
}

void
MessageHandlerImpl::ok( const MethodContext& )
{
    // TODO: Need to ack the transfers acknowledged so far for flow control purp oses
}

void
MessageHandlerImpl::open(const MethodContext& context,
                         const string& reference)
{
    references.open(reference);
    client.ok(context);
}

void
MessageHandlerImpl::qos(const MethodContext& context,
                        u_int32_t prefetchSize,
                        u_int16_t prefetchCount,
                        bool /*global*/ )
{
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
    client.ok(context);
}

void
MessageHandlerImpl::recover(const MethodContext& context,
                            bool requeue)
{
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unimplemented");
    channel.recover(requeue);
    client.ok(context);
}

void
MessageHandlerImpl::reject(const MethodContext&,
                           u_int16_t /*code*/,
                           const string& /*text*/ )
{
    // FIXME astitcher 2007-01-11: 0-9 feature
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unimplemented ");
}

void
MessageHandlerImpl::resume(const MethodContext&,
                           const string& /*reference*/,
                           const string& /*identifier*/ )
{
    // FIXME astitcher 2007-01-11: 0-9 feature
    THROW_QPID_ERROR(INTERNAL_ERROR, "Unimplemented ");
}

void
MessageHandlerImpl::transfer(const MethodContext& context,
                             u_int16_t /*ticket*/,
                             const string& destination,
                             bool /*redelivered*/,
                             bool /*immediate*/,
                             u_int64_t /*ttl*/,
                             u_int8_t /*priority*/,
                             u_int64_t /*timestamp*/,
                             u_int8_t /*deliveryMode*/,
                             u_int64_t /*expiration*/,
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
                             const qpid::framing::FieldTable& /*applicationHeaders*/,
                             qpid::framing::Content body,
                             bool /*mandatory*/)
{
    Exchange::shared_ptr exchange(
        broker.getExchanges().get(destination)); 
    MessageTransferBody::shared_ptr transfer(
        boost::shared_polymorphic_downcast<MessageTransferBody>(
            context.methodBody));
    MessageMessage::shared_ptr message(
        new MessageMessage(&connection, transfer));
    
    if (body.isInline()) 
        channel.handleInlineTransfer(message, exchange);
    else 
        references.get(body.getValue()).addMessage(message);
    client.ok(context);
}


}} // namespace qpid::broker
