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
#include "Connection.h"
#include "Broker.h"
namespace qpid {
namespace broker {

//
// Message class method handlers
//
void
MessageHandlerImpl::append( u_int16_t /*channel*/,
                                           const string& /*reference*/,
                                           const string& /*bytes*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}


void
MessageHandlerImpl::cancel( u_int16_t channel,
                                           const string& destination )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature

    connection.getChannel(channel).cancel(destination);

    connection.client->getMessageHandler()->ok(channel);
}

void
MessageHandlerImpl::checkpoint( u_int16_t /*channel*/,
                                               const string& /*reference*/,
                                               const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::close( u_int16_t /*channel*/,
                                          const string& /*reference*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::consume( u_int16_t /*channel*/,
                                            u_int16_t /*ticket*/,
                                            const string& queueName,
                                            const string& destination,
                                            bool noLocal,
                                            bool noAck,
                                            bool exclusive,
                                            const qpid::framing::FieldTable& filter )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
	
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());    
    Channel& channel = connection.getChannel(channel.getId());
    if(!destination.empty() && channel.exists(destination)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        string newTag = destination;
        channel.consume(newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &filter);

        connection.client->getMessageHandler()->ok(channel.getId());

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive) throw ChannelException(403, "Exclusive access cannot be granted");
        else throw ChannelException(403, "Access would violate previously granted exclusivity");
    }
}

void
MessageHandlerImpl::empty( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::get( u_int16_t /*channelId*/,
                                        u_int16_t /*ticket*/,
                                        const string& queueName,
                                        const string& /*destination*/,
                                        bool noAck )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature

    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    
    // FIXME: get is probably Basic specific
    if(!connection.getChannel(channel.getId()).get(queue, !noAck)){

        connection.client->getMessageHandler()->empty(channel.getId());
    }
    
}

void
MessageHandlerImpl::offset( u_int16_t /*channel*/,
                                           u_int64_t /*value*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::ok( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::open( u_int16_t /*channel*/,
                                         const string& /*reference*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::qos( u_int16_t /*channel*/,
                         u_int32_t prefetchSize,
                         u_int16_t prefetchCount,
                         bool /*global*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
    
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
    
    connection.client->getMessageHandler()->ok(channel.getId());
}

void
MessageHandlerImpl::recover( u_int16_t /*channel*/,
                             bool requeue )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature

    channel.recover(requeue);
    
}

void
MessageHandlerImpl::reject( u_int16_t /*channel*/,
                            u_int16_t /*code*/,
                            const string& /*text*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::resume( u_int16_t /*channel*/,
                            const string& /*reference*/,
                            const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
MessageHandlerImpl::transfer( u_int16_t /*channel*/,
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
                              qpid::framing::Content /*body*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature

    Exchange::shared_ptr exchange = exchangeName.empty() ?
        broker.getExchanges().getDefault() : broker.getExchanges().get(exchangeName);
    if(exchange){
        Message* msg = new Message(&connection, exchangeName, routingKey, false /*mandatory?*/, immediate);
        channel.handlePublish(msg, exchange);
    }else{
        throw ChannelException(404, "Exchange not found '" + exchangeName + "'");
    }
}

}} // namespace qpid::broker
