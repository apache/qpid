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
#include "AMQMethodBody.h"
#include "Exception.h"
#include "MessageHandlerImpl.h"

namespace qpid {
namespace broker {

using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr> QueueVector;

void BrokerAdapter::BrokerAdapter::ConnectionHandlerImpl::startOk(
    const MethodContext& context , const FieldTable& /*clientProperties*/,
    const string& /*mechanism*/, 
    const string& /*response*/, const string& /*locale*/){
    connection.client->getConnection().tune(
        context, 100, connection.getFrameMax(), connection.getHeartbeat());
}
        
void BrokerAdapter::BrokerAdapter::ConnectionHandlerImpl::secureOk(
    const MethodContext&, const string& /*response*/){}
        
void BrokerAdapter::BrokerAdapter::ConnectionHandlerImpl::tuneOk(
    const MethodContext&, u_int16_t /*channelmax*/,
    u_int32_t framemax, u_int16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeat(heartbeat);
}
        
void BrokerAdapter::BrokerAdapter::ConnectionHandlerImpl::open(const MethodContext& context, const string& /*virtualHost*/, const string& /*capabilities*/, bool /*insist*/){
    string knownhosts;
    connection.client->getConnection().openOk(context, knownhosts);
}
        
void BrokerAdapter::BrokerAdapter::ConnectionHandlerImpl::close(
    const MethodContext& context, u_int16_t /*replyCode*/, const string& /*replyText*/, 
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    connection.client->getConnection().closeOk(context);
    connection.getOutput().close();
} 
        
void BrokerAdapter::BrokerAdapter::ConnectionHandlerImpl::closeOk(const MethodContext&){
    connection.getOutput().close();
} 
              
void BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::open(
    const MethodContext& context, const string& /*outOfBand*/){
    channel.open();
    // FIXME aconway 2007-01-04: provide valid ID as per ampq 0-9
    connection.client->getChannel().openOk(context, std::string()/* ID */);
} 
        
void BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::flow(const MethodContext&, bool /*active*/){}         
void BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::flowOk(const MethodContext&, bool /*active*/){} 
        
void BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::close(
    const MethodContext& context, u_int16_t /*replyCode*/,
    const string& /*replyText*/,
    u_int16_t /*classId*/, u_int16_t /*methodId*/)
{
    connection.client->getChannel().closeOk(context);
    // FIXME aconway 2007-01-18: Following line will "delete this". Ugly.
    connection.closeChannel(channel.getId()); 
} 
        
void BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::closeOk(const MethodContext&){} 
              


void BrokerAdapter::BrokerAdapter::ExchangeHandlerImpl::declare(const MethodContext& context, u_int16_t /*ticket*/, const string& exchange, const string& type, 
                                                                bool passive, bool /*durable*/, bool /*autoDelete*/, bool /*internal*/, bool nowait, 
                                                                const FieldTable& /*arguments*/){

    if(passive){
        if(!broker.getExchanges().get(exchange)) {
            throw ChannelException(404, "Exchange not found: " + exchange);
        }
    }else{        
        try{
            std::pair<Exchange::shared_ptr, bool> response = broker.getExchanges().declare(exchange, type);
            if(!response.second && response.first->getType() != type){
                throw ConnectionException(
                    507,
                    "Exchange already declared to be of type "
                    + response.first->getType() + ", requested " + type);
            }
        }catch(UnknownExchangeTypeException& e){
            throw ConnectionException(
                503, "Exchange type not implemented: " + type);
        }
    }
    if(!nowait){
        connection.client->getExchange().declareOk(context);
    }
}
                
void BrokerAdapter::BrokerAdapter::ExchangeHandlerImpl::delete_(const MethodContext& context, u_int16_t /*ticket*/, 
                                                                const string& exchange, bool /*ifUnused*/, bool nowait){

    //TODO: implement unused
    broker.getExchanges().destroy(exchange);
    if(!nowait) connection.client->getExchange().deleteOk(context);
} 

void BrokerAdapter::BrokerAdapter::QueueHandlerImpl::declare(const MethodContext& context, u_int16_t /*ticket*/, const string& name, 
                                                             bool passive, bool durable, bool exclusive, 
                                                             bool autoDelete, bool nowait, const qpid::framing::FieldTable& arguments){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = connection.getQueue(name, channel.getId());
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            broker.getQueues().declare(name, durable, autoDelete ? connection.settings.timeout : 0, exclusive ? &connection : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue
	    channel.setDefaultQueue(queue);

            //apply settings & create persistent record if required
            queue_created.first->create(arguments);

	    //add default binding:
	    broker.getExchanges().getDefault()->bind(queue, name, 0);
	    if (exclusive) {
		connection.exclusiveQueues.push_back(queue);
	    } else if(autoDelete){
		broker.getCleaner().add(queue);
	    }
	}
    }
    if (exclusive && !queue->isExclusiveOwner(&connection)) {
	throw ChannelException(405, "Cannot grant exclusive access to queue");
    }
    if (!nowait) {
        string queueName = queue->getName();
        connection.client->getQueue().declareOk(context, queueName, queue->getMessageCount(), queue->getConsumerCount());
    }
} 
        
void BrokerAdapter::BrokerAdapter::QueueHandlerImpl::bind(const MethodContext& context, u_int16_t /*ticket*/, const string& queueName, 
                                                          const string& exchangeName, const string& routingKey, bool nowait, 
                                                          const FieldTable& arguments){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if(exchange){
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        exchange->bind(queue, exchangeRoutingKey, &arguments);
        if(!nowait) connection.client->getQueue().bindOk(context);    
    }else{
        throw ChannelException(
            404, "Bind failed. No such exchange: " + exchangeName);
    }
}
 
void 
BrokerAdapter::BrokerAdapter::QueueHandlerImpl::unbind(
    const MethodContext& context,
    u_int16_t /*ticket*/,
    const string& queueName,
    const string& exchangeName,
    const string& routingKey,
    const qpid::framing::FieldTable& arguments )
{
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    if (!queue.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if (!exchange.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    exchange->unbind(queue, routingKey, &arguments);

    connection.client->getQueue().unbindOk(context);    
}
        
void BrokerAdapter::BrokerAdapter::QueueHandlerImpl::purge(const MethodContext& context, u_int16_t /*ticket*/, const string& queueName, bool nowait){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    int count = queue->purge();
    if(!nowait) connection.client->getQueue().purgeOk(context, count);
} 
        
void BrokerAdapter::BrokerAdapter::QueueHandlerImpl::delete_(const MethodContext& context, u_int16_t /*ticket*/, const string& queue, 
                                                             bool ifUnused, bool ifEmpty, bool nowait){
    ChannelException error(0, "");
    int count(0);
    Queue::shared_ptr q = connection.getQueue(queue, channel.getId());
    if(ifEmpty && q->getMessageCount() > 0){
        throw ChannelException(406, "Queue not empty.");
    }else if(ifUnused && q->getConsumerCount() > 0){
        throw ChannelException(406, "Queue in use.");
    }else{
        //remove the queue from the list of exclusive queues if necessary
        if(q->isExclusiveOwner(&connection)){
            QueueVector::iterator i = find(connection.exclusiveQueues.begin(), connection.exclusiveQueues.end(), q);
            if(i < connection.exclusiveQueues.end()) connection.exclusiveQueues.erase(i);
        }
        count = q->getMessageCount();
        q->destroy();
        broker.getQueues().destroy(queue);
    }

    if(!nowait) connection.client->getQueue().deleteOk(context, count);
} 
              
        


void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::qos(const MethodContext& context, u_int32_t prefetchSize, u_int16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
    connection.client->getBasic().qosOk(context);
} 
        
void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::consume(
    const MethodContext& context, u_int16_t /*ticket*/, 
    const string& queueName, const string& consumerTag, 
    bool noLocal, bool noAck, bool exclusive, 
    bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());    
    if(!consumerTag.empty() && channel.exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    try{
        string newTag = consumerTag;
        channel.consume(
            newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &fields);

        if(!nowait) connection.client->getBasic().consumeOk(context, newTag);

        //allow messages to be dispatched if required as there is now a consumer:
        queue->dispatch();
    }catch(ExclusiveAccessException& e){
        if(exclusive) throw ChannelException(403, "Exclusive access cannot be granted");
        else throw ChannelException(403, "Access would violate previously granted exclusivity");
    }

} 
        
void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::cancel(const MethodContext& context, const string& consumerTag, bool nowait){
    channel.cancel(consumerTag);

    if(!nowait) connection.client->getBasic().cancelOk(context, consumerTag);
} 
        
void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::publish(
    const MethodContext& context, u_int16_t /*ticket*/, 
    const string& exchangeName, const string& routingKey, 
    bool mandatory, bool immediate)
{

    Exchange::shared_ptr exchange = exchangeName.empty() ? broker.getExchanges().getDefault() : broker.getExchanges().get(exchangeName);
    if(exchange){
        BasicMessage* msg = new BasicMessage(
            &connection, exchangeName, routingKey, mandatory, immediate,
            context.methodBody);
        channel.handlePublish(msg, exchange);
    }else{
        throw ChannelException(
            404, "Exchange not found '" + exchangeName + "'");
    }
} 
        
void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::get(const MethodContext& context, u_int16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());    
    if(!connection.getChannel(channel.getId()).get(queue, !noAck)){
        string clusterId;//not used, part of an imatix hack

        connection.client->getBasic().getEmpty(context, clusterId);
    }
} 
        
void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::ack(const MethodContext&, u_int64_t deliveryTag, bool multiple){
    try{
        channel.ack(deliveryTag, multiple);
    }catch(InvalidAckException& e){
        throw ConnectionException(530, "Received ack for unrecognised delivery tag");
    }
} 
        
void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::reject(const MethodContext&, u_int64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void BrokerAdapter::BrokerAdapter::BasicHandlerImpl::recover(const MethodContext&, bool requeue){
    channel.recover(requeue);
} 

void BrokerAdapter::BrokerAdapter::TxHandlerImpl::select(const MethodContext& context){
    channel.begin();
    connection.client->getTx().selectOk(context);
}

void BrokerAdapter::BrokerAdapter::TxHandlerImpl::commit(const MethodContext& context){
    channel.commit();
    connection.client->getTx().commitOk(context);
}

void BrokerAdapter::BrokerAdapter::TxHandlerImpl::rollback(const MethodContext& context){
    
    channel.rollback();
    connection.client->getTx().rollbackOk(context);
    channel.recover(false);    
}
              
void
BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::ok( const MethodContext& )
{
    //no specific action required, generic response handling should be sufficient
}


//
// Message class method handlers
//
void
BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::ping( const MethodContext& context)
{
    connection.client->getChannel().ok(context);
    connection.client->getChannel().pong(context);
}


void
BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::pong( const MethodContext& context)
{
    connection.client->getChannel().ok(context);
}

void
BrokerAdapter::BrokerAdapter::ChannelHandlerImpl::resume(
    const MethodContext&,
    const string& /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

}} // namespace qpid::broker

