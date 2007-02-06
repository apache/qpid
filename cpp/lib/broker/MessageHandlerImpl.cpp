
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
    sendOk(context);
}


void
MessageHandlerImpl::cancel(const MethodContext& context,
                           const string& destination )
{
    channel.cancel(destination);
    sendOk(context);
}

void
MessageHandlerImpl::checkpoint(const MethodContext&,
                               const string& /*reference*/,
                               const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::close(const MethodContext& context,
                          const string& reference)
{
    references.get(reference).close();
    sendOk(context);
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
    if(!destination.empty() && channel.exists(destination)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        string newTag = destination;
        channel.consume(newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &filter);

        sendOk(context);

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive)
            throw ChannelException(403, "Exclusive access cannot be granted");
        else
            throw ChannelException(
                403, "Access would violate previously granted exclusivity");
    }
}

void
MessageHandlerImpl::empty( const MethodContext& )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
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
    if(!channel.get(queue, !noAck)){
        connection.client->getMessageHandler()->empty(context);
    }
    
}

void
MessageHandlerImpl::offset(const MethodContext&,
                           u_int64_t /*value*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
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
    sendOk(context);
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
    
    sendOk(context);
}

void
MessageHandlerImpl::recover(const MethodContext&,
                            bool requeue )
{
    //assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature

    channel.recover(requeue);
    
}

void
MessageHandlerImpl::reject(const MethodContext&,
                           u_int16_t /*code*/,
                           const string& /*text*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::resume(const MethodContext&,
                           const string& /*reference*/,
                           const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::transfer(const MethodContext& context,
                             u_int16_t /*ticket*/,
                             const string& /*destination*/,
                             bool /*redelivered*/,
                             bool immediate,
                             u_int64_t /*ttl*/,
                             u_int8_t /*priority*/,
                             u_int64_t /*timestamp*/,
                             u_int8_t /*deliveryMode*/,
                             u_int64_t /*expiration*/,
                             const string& exchangeName,
                             const string& routingKey,
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
                             bool mandatory)
{
    Exchange::shared_ptr exchange = exchangeName.empty() ?
        broker.getExchanges().getDefault() : broker.getExchanges().get(exchangeName);
    boost::shared_ptr<MessageTransferBody> transfer(boost::dynamic_pointer_cast<MessageTransferBody>(context.methodBody));
    if(exchange){
    	if (body.isInline()) {
            Message::shared_ptr msg(new MessageMessage(transfer, exchangeName,
                                   routingKey, mandatory, immediate));

            channel.handleInlineTransfer(msg, exchange);
        
            connection.client->getMessageHandler()->ok(context);
    	} else {
            references.get(body.getValue()).transfer(transfer);
    	}
    }else{
        throw ChannelException(404, "Exchange not found '" + exchangeName + "'");
    }
}


void MessageHandlerImpl::sendOk(const MethodContext& context) {
    connection.client->getMessageHandler()->ok(context);
}

}} // namespace qpid::broker
