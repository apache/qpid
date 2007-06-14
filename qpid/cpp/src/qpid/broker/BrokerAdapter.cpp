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
#include <boost/format.hpp>

#include "BrokerAdapter.h"
#include "BrokerChannel.h"
#include "Connection.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/Exception.h"

namespace qpid {
namespace broker {

using boost::format;
using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr> QueueVector;


BrokerAdapter::BrokerAdapter(Channel& ch, Connection& c, Broker& b) :
    CoreRefs(ch, c, b),
    connection(c),
    basicHandler(*this),
    channelHandler(*this),
    connectionHandler(*this),
    exchangeHandler(*this),
    messageHandler(*this),
    queueHandler(*this),
    txHandler(*this),
    dtxHandler(*this)
{}


ProtocolVersion BrokerAdapter::getVersion() const {
    return connection.getVersion();
}

void BrokerAdapter::ConnectionHandlerImpl::startOk(
    const MethodContext&, const FieldTable& /*clientProperties*/,
    const string& /*mechanism*/, 
    const string& /*response*/, const string& /*locale*/)
{
    client.tune(
        100, connection.getFrameMax(), connection.getHeartbeat());
}
        
void BrokerAdapter::ConnectionHandlerImpl::secureOk(
    const MethodContext&, const string& /*response*/){}
        
void BrokerAdapter::ConnectionHandlerImpl::tuneOk(
    const MethodContext&, uint16_t /*channelmax*/,
    uint32_t framemax, uint16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeat(heartbeat);
}
        
void BrokerAdapter::ConnectionHandlerImpl::open(
    const MethodContext& context, const string& /*virtualHost*/,
    const string& /*capabilities*/, bool /*insist*/)
{
    string knownhosts;
    client.openOk(
        knownhosts, context.getRequestId());
}
        
void BrokerAdapter::ConnectionHandlerImpl::close(
    const MethodContext& context, uint16_t /*replyCode*/, const string& /*replyText*/, 
    uint16_t /*classId*/, uint16_t /*methodId*/)
{
    client.closeOk(context.getRequestId());
    connection.getOutput().close();
} 
        
void BrokerAdapter::ConnectionHandlerImpl::closeOk(const MethodContext&){
    connection.getOutput().close();
} 
              
void BrokerAdapter::ChannelHandlerImpl::open(
    const MethodContext& context, const string& /*outOfBand*/){
    channel.open();
    // FIXME aconway 2007-01-04: provide valid ID as per ampq 0-9
    client.openOk(
        std::string()/* ID */, context.getRequestId());
} 
        
void BrokerAdapter::ChannelHandlerImpl::flow(const MethodContext& context, bool active){
    channel.flow(active);
    client.flowOk(active, context.getRequestId());
}         

void BrokerAdapter::ChannelHandlerImpl::flowOk(const MethodContext&, bool /*active*/){} 
        
void BrokerAdapter::ChannelHandlerImpl::close(
    const MethodContext& context, uint16_t /*replyCode*/,
    const string& /*replyText*/,
    uint16_t /*classId*/, uint16_t /*methodId*/)
{
    client.closeOk(context.getRequestId());
    // FIXME aconway 2007-01-18: Following line will "delete this". Ugly.
    connection.closeChannel(channel.getId()); 
} 
        
void BrokerAdapter::ChannelHandlerImpl::closeOk(const MethodContext&){} 
              


void BrokerAdapter::ExchangeHandlerImpl::declare(const MethodContext& context, uint16_t /*ticket*/, const string& exchange, const string& type, 
                                                 bool passive, bool durable, bool /*autoDelete*/, bool /*internal*/, bool nowait, 
                                                 const FieldTable& args){

    if(passive){
        if(!broker.getExchanges().get(exchange)) {
            throw ChannelException(404, "Exchange not found: " + exchange);
        }
    }else{        
        try{
            std::pair<Exchange::shared_ptr, bool> response = broker.getExchanges().declare(exchange, type, durable, args);
            if (response.second) {
                if (durable) broker.getStore().create(*response.first);
            } else if (response.first->getType() != type) {
                throw ConnectionException(
                    530,
                    "Exchange already declared to be of type "
                    + response.first->getType() + ", requested " + type);
            }
        }catch(UnknownExchangeTypeException& e){
            throw ConnectionException(
                503, "Exchange type not implemented: " + type);
        }
    }
    if(!nowait){
        client.declareOk(context.getRequestId());
    }
}
                
void BrokerAdapter::ExchangeHandlerImpl::delete_(const MethodContext& context, uint16_t /*ticket*/, 
                                                 const string& name, bool /*ifUnused*/, bool nowait){

    //TODO: implement unused
    Exchange::shared_ptr exchange(broker.getExchanges().get(name));
    if (exchange->isDurable()) broker.getStore().destroy(*exchange);
    broker.getExchanges().destroy(name);
    if(!nowait) client.deleteOk(context.getRequestId());
} 

void BrokerAdapter::QueueHandlerImpl::declare(const MethodContext& context, uint16_t /*ticket*/, const string& name, 
                                              bool passive, bool durable, bool exclusive, 
                                              bool autoDelete, bool nowait, const qpid::framing::FieldTable& arguments){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = connection.getQueue(name, channel.getId());
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            broker.getQueues().declare(
                name, durable,
                autoDelete ? connection.getTimeout() : 0,
                exclusive ? &connection : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue
	    channel.setDefaultQueue(queue);

            //apply settings & create persistent record if required
            queue_created.first->create(arguments);

	    //add default binding:
	    broker.getExchanges().getDefault()->bind(queue, name, 0);
            queue->bound(broker.getExchanges().getDefault()->getName(), name, arguments);

            //handle automatic cleanup:
	    if (exclusive) {
		connection.exclusiveQueues.push_back(queue);
	    } else if(autoDelete){
		broker.getCleaner().add(queue);
	    }
	}
    }
    if (exclusive && !queue->isExclusiveOwner(&connection)) 
	throw ChannelException(
            405,
            format("Cannot grant exclusive access to queue '%s'")
            % queue->getName());
    if (!nowait) {
        string queueName = queue->getName();
        client.declareOk(
            queueName, queue->getMessageCount(), queue->getConsumerCount(),
            context.getRequestId());
    }
} 
        
void BrokerAdapter::QueueHandlerImpl::bind(const MethodContext& context, uint16_t /*ticket*/, const string& queueName, 
                                           const string& exchangeName, const string& routingKey, bool nowait, 
                                           const FieldTable& arguments){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if(exchange){
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        if (exchange->bind(queue, exchangeRoutingKey, &arguments)) {
            queue->bound(exchangeName, routingKey, arguments);
            if (exchange->isDurable() && queue->isDurable()) {
                broker.getStore().bind(*exchange, *queue, routingKey, arguments);
            }
        }
        if(!nowait) client.bindOk(context.getRequestId());    
    }else{
        throw ChannelException(
            404, "Bind failed. No such exchange: " + exchangeName);
    }
}
 
void 
BrokerAdapter::QueueHandlerImpl::unbind(
    const MethodContext& context,
    uint16_t /*ticket*/,
    const string& queueName,
    const string& exchangeName,
    const string& routingKey,
    const qpid::framing::FieldTable& arguments )
{
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    if (!queue.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if (!exchange.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    if (exchange->unbind(queue, routingKey, &arguments) && exchange->isDurable() && queue->isDurable()) {
        broker.getStore().unbind(*exchange, *queue, routingKey, arguments);
    }

    client.unbindOk(context.getRequestId());    
}
        
void BrokerAdapter::QueueHandlerImpl::purge(const MethodContext& context, uint16_t /*ticket*/, const string& queueName, bool nowait){

    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());
    int count = queue->purge();
    if(!nowait) client.purgeOk( count, context.getRequestId());
} 
        
void BrokerAdapter::QueueHandlerImpl::delete_(const MethodContext& context, uint16_t /*ticket*/, const string& queue, 
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
        q->unbind(broker.getExchanges(), q);
    }

    if(!nowait)
        client.deleteOk(count, context.getRequestId());
} 
              
        


void BrokerAdapter::BasicHandlerImpl::qos(const MethodContext& context, uint32_t prefetchSize, uint16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
    client.qosOk(context.getRequestId());
} 
        
void BrokerAdapter::BasicHandlerImpl::consume(
    const MethodContext& context, uint16_t /*ticket*/, 
    const string& queueName, const string& consumerTag, 
    bool noLocal, bool noAck, bool exclusive, 
    bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());    
    if(!consumerTag.empty() && channel.exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }

    string newTag = consumerTag;
    channel.consume(
        newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &fields);

    if(!nowait) client.consumeOk(newTag, context.getRequestId());

    //allow messages to be dispatched if required as there is now a consumer:
    queue->dispatch();
} 
        
void BrokerAdapter::BasicHandlerImpl::cancel(const MethodContext& context, const string& consumerTag, bool nowait){
    channel.cancel(consumerTag);

    if(!nowait) client.cancelOk(consumerTag, context.getRequestId());
} 
        
void BrokerAdapter::BasicHandlerImpl::publish(
    const MethodContext& context, uint16_t /*ticket*/, 
    const string& exchangeName, const string& routingKey, 
    bool mandatory, bool immediate)
{

    Exchange::shared_ptr exchange = exchangeName.empty() ? broker.getExchanges().getDefault() : broker.getExchanges().get(exchangeName);
    if(exchange){
        BasicMessage* msg = new BasicMessage(
            &connection, exchangeName, routingKey, mandatory, immediate,
            context.methodBody);
        channel.handlePublish(msg);
    }else{
        throw ChannelException(
            404, "Exchange not found '" + exchangeName + "'");
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::get(const MethodContext& context, uint16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = connection.getQueue(queueName, channel.getId());    
    if(!connection.getChannel(channel.getId()).get(queue, "", !noAck)){
        string clusterId;//not used, part of an imatix hack

        client.getEmpty(clusterId, context.getRequestId());
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::ack(const MethodContext&, uint64_t deliveryTag, bool multiple){
	channel.ack(deliveryTag, multiple);
} 
        
void BrokerAdapter::BasicHandlerImpl::reject(const MethodContext&, uint64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void BrokerAdapter::BasicHandlerImpl::recover(const MethodContext&, bool requeue){
    channel.recover(requeue);
} 

void BrokerAdapter::TxHandlerImpl::select(const MethodContext& context){
    channel.startTx();
    client.selectOk(context.getRequestId());
}

void BrokerAdapter::TxHandlerImpl::commit(const MethodContext& context){
    channel.commit();
    client.commitOk(context.getRequestId());
}

void BrokerAdapter::TxHandlerImpl::rollback(const MethodContext& context){
    
    channel.rollback();
    client.rollbackOk(context.getRequestId());
    channel.recover(false);    
}
              
void
BrokerAdapter::ChannelHandlerImpl::ok( const MethodContext& )
{
    //no specific action required, generic response handling should be sufficient
}


//
// Message class method handlers
//
void
BrokerAdapter::ChannelHandlerImpl::ping( const MethodContext& context)
{
    client.ok(context.getRequestId());
    client.pong();
}


void
BrokerAdapter::ChannelHandlerImpl::pong( const MethodContext& context)
{
    client.ok(context.getRequestId());
}

void
BrokerAdapter::ChannelHandlerImpl::resume(
    const MethodContext&,
    const string& /*channel*/ )
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

}} // namespace qpid::broker

