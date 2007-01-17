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

#include "BrokerAdapter.h"
#include "Connection.h"
#include "Exception.h"

namespace qpid {
namespace broker {

using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;

BrokerAdapter::BrokerAdapter(Connection& c) :
    connection(c),
    basicHandler(c),
    channelHandler(c),
    connectionHandler(c),
    exchangeHandler(c),
    messageHandler(c),
    queueHandler(c),
    txHandler(c)    
{}

typedef qpid::framing::AMQP_ServerOperations Ops;

Ops::ChannelHandler* BrokerAdapter::getChannelHandler() {
    return &channelHandler;
}
Ops::ConnectionHandler* BrokerAdapter::getConnectionHandler() {
    return &connectionHandler;
}
Ops::BasicHandler* BrokerAdapter::getBasicHandler() {
    return &basicHandler;
}
Ops::ExchangeHandler* BrokerAdapter::getExchangeHandler() {
    return &exchangeHandler;
}
Ops::QueueHandler* BrokerAdapter::getQueueHandler() {
    return &queueHandler;
}
Ops::TxHandler* BrokerAdapter::getTxHandler() {
    return &txHandler; 
}
Ops::MessageHandler* BrokerAdapter::getMessageHandler() {
    return &messageHandler; 
}
Ops::AccessHandler* BrokerAdapter::getAccessHandler() {
    throw ConnectionException(540, "Access class not implemented"); 
}
Ops::FileHandler* BrokerAdapter::getFileHandler() {
    throw ConnectionException(540, "File class not implemented"); 
}
Ops::StreamHandler* BrokerAdapter::getStreamHandler() {
    throw ConnectionException(540, "Stream class not implemented"); 
}
Ops::DtxHandler* BrokerAdapter::getDtxHandler() {
    throw ConnectionException(540, "Dtx class not implemented"); 
}
Ops::TunnelHandler* BrokerAdapter::getTunnelHandler() {
    throw ConnectionException(540, "Tunnel class not implemented");
}

void BrokerAdapter::ConnectionHandlerImpl::startOk(
    u_int16_t /*channel*/, const FieldTable& /*clientProperties*/, const string& /*mechanism*/, 
    const string& /*response*/, const string& /*locale*/){
    connection.client->getConnection().tune(0, 100, connection.framemax, connection.heartbeat);
}
        
void BrokerAdapter::ConnectionHandlerImpl::secureOk(u_int16_t /*channel*/, const string& /*response*/){}
        
void BrokerAdapter::ConnectionHandlerImpl::tuneOk(u_int16_t /*channel*/, u_int16_t /*channelmax*/, u_int32_t framemax, u_int16_t heartbeat){
    connection.framemax = framemax;
    connection.heartbeat = heartbeat;
}
        
void BrokerAdapter::ConnectionHandlerImpl::open(u_int16_t /*channel*/, const string& /*virtualHost*/, const string& /*capabilities*/, bool /*insist*/){
    string knownhosts;
    connection.client->getConnection().openOk(0, knownhosts);
}
        
void BrokerAdapter::ConnectionHandlerImpl::close(
    u_int16_t /*channel*/, u_int16_t /*replyCode*/, const string& /*replyText*/, 
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    connection.client->getConnection().closeOk(0);
    connection.context->close();
} 
        
void BrokerAdapter::ConnectionHandlerImpl::closeOk(u_int16_t /*channel*/){
    connection.context->close();
} 
              
void BrokerAdapter::ChannelHandlerImpl::open(
    u_int16_t channel, const string& /*outOfBand*/){
    connection.openChannel(channel);
    // FIXME aconway 2007-01-04: provide valid channel Id as per ampq 0-9
    connection.client->getChannel().openOk(channel, std::string()/* ID */);
} 
        
void BrokerAdapter::ChannelHandlerImpl::flow(u_int16_t /*channel*/, bool /*active*/){}         
void BrokerAdapter::ChannelHandlerImpl::flowOk(u_int16_t /*channel*/, bool /*active*/){} 
        
void BrokerAdapter::ChannelHandlerImpl::close(u_int16_t channel, u_int16_t /*replyCode*/, const string& /*replyText*/, 
                                                   u_int16_t /*classId*/, u_int16_t /*methodId*/){
    connection.closeChannel(channel);
    connection.client->getChannel().closeOk(channel);
} 
        
void BrokerAdapter::ChannelHandlerImpl::closeOk(u_int16_t /*channel*/){} 
              


void BrokerAdapter::ExchangeHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, const string& exchange, const string& type, 
                                                      bool passive, bool /*durable*/, bool /*autoDelete*/, bool /*internal*/, bool nowait, 
                                                      const FieldTable& /*arguments*/){

    if(passive){
        if(!connection.broker.getExchanges().get(exchange)){
            throw ChannelException(404, "Exchange not found: " + exchange);            
        }
    }else{        
        try{
            std::pair<Exchange::shared_ptr, bool> response = connection.broker.getExchanges().declare(exchange, type);
            if(!response.second && response.first->getType() != type){
                throw ConnectionException(507, "Exchange already declared to be of type " 
                                          + response.first->getType() + ", requested " + type);
            }
        }catch(UnknownExchangeTypeException& e){
            throw ConnectionException(503, "Exchange type not implemented: " + type);
        }
    }
    if(!nowait){
        connection.client->getExchange().declareOk(channel);
    }
}

                
void BrokerAdapter::ExchangeHandlerImpl::unbind(
    u_int16_t /*channel*/,
    u_int16_t /*ticket*/,
    const string& /*queue*/,
    const string& /*exchange*/,
    const string& /*routingKey*/,
    const qpid::framing::FieldTable& /*arguments*/ )
{
    assert(0);            // FIXME aconway 2007-01-04: 0-9 feature
}


                
void BrokerAdapter::ExchangeHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, 
                                                      const string& exchange, bool /*ifUnused*/, bool nowait){

    //TODO: implement unused
    connection.broker.getExchanges().destroy(exchange);
    if(!nowait) connection.client->getExchange().deleteOk(channel);
} 

void BrokerAdapter::QueueHandlerImpl::declare(u_int16_t channel, u_int16_t /*ticket*/, const string& name, 
                                                   bool passive, bool durable, bool exclusive, 
                                                   bool autoDelete, bool nowait, const qpid::framing::FieldTable& arguments){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = connection.getQueue(name, channel);
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            connection.broker.getQueues().declare(name, durable, autoDelete ? connection.settings.timeout : 0, exclusive ? &connection : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue
	    connection.getChannel(channel).setDefaultQueue(queue);

            //apply settings & create persistent record if required
            queue_created.first->create(arguments);

	    //add default binding:
	    connection.broker.getExchanges().getDefault()->bind(queue, name, 0);
	    if (exclusive) {
		connection.exclusiveQueues.push_back(queue);
	    } else if(autoDelete){
		connection.broker.getCleaner().add(queue);
	    }
	}
    }
    if (exclusive && !queue->isExclusiveOwner(&connection)) {
	throw ChannelException(405, "Cannot grant exclusive access to queue");
    }
    if (!nowait) {
        string queueName = queue->getName();
        connection.client->getQueue().declareOk(channel, queueName, queue->getMessageCount(), queue->getConsumerCount());
    }
} 
        
void BrokerAdapter::QueueHandlerImpl::bind(u_int16_t channel, u_int16_t /*ticket*/, const string& queueName, 
                                                const string& exchangeName, const string& routingKey, bool nowait, 
                                                const FieldTable& arguments){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel);
    Exchange::shared_ptr exchange = connection.broker.getExchanges().get(exchangeName);
    if(exchange){
        // kpvdr - cannot use this any longer as routingKey is now const
        //        if(routingKey.empty() && queueName.empty()) routingKey = queue->getName();
        //        exchange->bind(queue, routingKey, &arguments);
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        exchange->bind(queue, exchangeRoutingKey, &arguments);
        if(!nowait) connection.client->getQueue().bindOk(channel);    
    }else{
        throw ChannelException(404, "Bind failed. No such exchange: " + exchangeName);
    }
} 
        
void BrokerAdapter::QueueHandlerImpl::purge(u_int16_t channel, u_int16_t /*ticket*/, const string& queueName, bool nowait){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel);
    int count = queue->purge();
    if(!nowait) connection.client->getQueue().purgeOk(channel, count);
} 
        
void BrokerAdapter::QueueHandlerImpl::delete_(u_int16_t channel, u_int16_t /*ticket*/, const string& queue, 
                                                   bool ifUnused, bool ifEmpty, bool nowait){
    ChannelException error(0, "");
    int count(0);
    Queue::shared_ptr q = connection.getQueue(queue, channel);
    if(ifEmpty && q->getMessageCount() > 0){
        throw ChannelException(406, "Queue not empty.");
    }else if(ifUnused && q->getConsumerCount() > 0){
        throw ChannelException(406, "Queue in use.");
    }else{
        //remove the queue from the list of exclusive queues if necessary
        if(q->isExclusiveOwner(&connection)){
            queue_iterator i = find(connection.exclusiveQueues.begin(), connection.exclusiveQueues.end(), q);
            if(i < connection.exclusiveQueues.end()) connection.exclusiveQueues.erase(i);
        }
        count = q->getMessageCount();
        q->destroy();
        connection.broker.getQueues().destroy(queue);
    }

    if(!nowait) connection.client->getQueue().deleteOk(channel, count);
} 
              
        


void BrokerAdapter::BasicHandlerImpl::qos(u_int16_t channel, u_int32_t prefetchSize, u_int16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    connection.getChannel(channel).setPrefetchSize(prefetchSize);
    connection.getChannel(channel).setPrefetchCount(prefetchCount);
    connection.client->getBasic().qosOk(channel);
} 
        
void BrokerAdapter::BasicHandlerImpl::consume(
    u_int16_t channelId, u_int16_t /*ticket*/, 
    const string& queueName, const string& consumerTag, 
    bool noLocal, bool noAck, bool exclusive, 
    bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = connection.getQueue(queueName, channelId);    
    Channel& channel = connection.getChannel(channelId);
    if(!consumerTag.empty() && channel.exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        string newTag = consumerTag;
        channel.consume(
            newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &fields);

        if(!nowait) connection.client->getBasic().consumeOk(channelId, newTag);

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive) throw ChannelException(403, "Exclusive access cannot be granted");
        else throw ChannelException(403, "Access would violate previously granted exclusivity");
    }

} 
        
void BrokerAdapter::BasicHandlerImpl::cancel(u_int16_t channel, const string& consumerTag, bool nowait){
    connection.getChannel(channel).cancel(consumerTag);

    if(!nowait) connection.client->getBasic().cancelOk(channel, consumerTag);
} 
        
void BrokerAdapter::BasicHandlerImpl::publish(u_int16_t channel, u_int16_t /*ticket*/, 
                                                   const string& exchangeName, const string& routingKey, 
                                                   bool mandatory, bool immediate){

    Exchange::shared_ptr exchange = exchangeName.empty() ? connection.broker.getExchanges().getDefault() : connection.broker.getExchanges().get(exchangeName);
    if(exchange){
        Message* msg = new Message(&connection, exchangeName, routingKey, mandatory, immediate);
        connection.getChannel(channel).handlePublish(msg, exchange);
    }else{
        throw ChannelException(404, "Exchange not found '" + exchangeName + "'");
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::get(u_int16_t channelId, u_int16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = connection.getQueue(queueName, channelId);    
    if(!connection.getChannel(channelId).get(queue, !noAck)){
        string clusterId;//not used, part of an imatix hack

        connection.client->getBasic().getEmpty(channelId, clusterId);
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::ack(u_int16_t channel, u_int64_t deliveryTag, bool multiple){
    try{
        connection.getChannel(channel).ack(deliveryTag, multiple);
    }catch(InvalidAckException& e){
        throw ConnectionException(530, "Received ack for unrecognised delivery tag");
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::reject(u_int16_t /*channel*/, u_int64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void BrokerAdapter::BasicHandlerImpl::recover(u_int16_t channel, bool requeue){
    connection.getChannel(channel).recover(requeue);
} 

void BrokerAdapter::TxHandlerImpl::select(u_int16_t channel){
    connection.getChannel(channel).begin();
    connection.client->getTx().selectOk(channel);
}

void BrokerAdapter::TxHandlerImpl::commit(u_int16_t channel){
    connection.getChannel(channel).commit();
    connection.client->getTx().commitOk(channel);
}

void BrokerAdapter::TxHandlerImpl::rollback(u_int16_t channel){
    
    connection.getChannel(channel).rollback();
    connection.client->getTx().rollbackOk(channel);
    connection.getChannel(channel).recover(false);    
}
              
void
BrokerAdapter::QueueHandlerImpl::unbind(
    u_int16_t /*channel*/,
    u_int16_t /*ticket*/,
    const string& /*queue*/,
    const string& /*exchange*/,
    const string& /*routingKey*/,
    const qpid::framing::FieldTable& /*arguments*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ChannelHandlerImpl::ok( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ChannelHandlerImpl::ping( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ChannelHandlerImpl::pong( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void
BrokerAdapter::ChannelHandlerImpl::resume(
    u_int16_t /*channel*/,
    const string& /*channelId*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

// Message class method handlers
void
BrokerAdapter::MessageHandlerImpl::append( u_int16_t /*channel*/,
                                                const string& /*reference*/,
                                                const string& /*bytes*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}


void
BrokerAdapter::MessageHandlerImpl::cancel( u_int16_t /*channel*/,
                                                const string& /*destination*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::checkpoint( u_int16_t /*channel*/,
                                                    const string& /*reference*/,
                                                    const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::close( u_int16_t /*channel*/,
                                               const string& /*reference*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::consume( u_int16_t /*channel*/,
                                                 u_int16_t /*ticket*/,
                                                 const string& /*queue*/,
                                                 const string& /*destination*/,
                                                 bool /*noLocal*/,
                                                 bool /*noAck*/,
                                                 bool /*exclusive*/,
                                                 const qpid::framing::FieldTable& /*filter*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::empty( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::get( u_int16_t /*channel*/,
                                             u_int16_t /*ticket*/,
                                             const string& /*queue*/,
                                             const string& /*destination*/,
                                             bool /*noAck*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::offset( u_int16_t /*channel*/,
                                                u_int64_t /*value*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::ok( u_int16_t /*channel*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::open( u_int16_t /*channel*/,
                                              const string& /*reference*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::qos( u_int16_t /*channel*/,
                                             u_int32_t /*prefetchSize*/,
                                             u_int16_t /*prefetchCount*/,
                                             bool /*global*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::recover( u_int16_t /*channel*/,
                                                 bool /*requeue*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::reject( u_int16_t /*channel*/,
                                                u_int16_t /*code*/,
                                                const string& /*text*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::resume( u_int16_t /*channel*/,
                                                const string& /*reference*/,
                                                const string& /*identifier*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

void
BrokerAdapter::MessageHandlerImpl::transfer( u_int16_t /*channel*/,
                                                  u_int16_t /*ticket*/,
                                                  const string& /*destination*/,
                                                  bool /*redelivered*/,
                                                  bool /*immediate*/,
                                                  u_int64_t /*ttl*/,
                                                  u_int8_t /*priority*/,
                                                  u_int64_t /*timestamp*/,
                                                  u_int8_t /*deliveryMode*/,
                                                  u_int64_t /*expiration*/,
                                                  const string& /*exchange*/,
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
                                                  qpid::framing::Content /*body*/ )
{
    assert(0);                // FIXME astitcher 2007-01-11: 0-9 feature
}

}} // namespace qpid::broker
