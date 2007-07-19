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
#include "ConsumeAdapter.h"
#include "GetAdapter.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/Exception.h"

namespace qpid {
namespace broker {

using boost::format;
using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr> QueueVector;


    BrokerAdapter::BrokerAdapter(Channel& ch, Connection& c, Broker& b, ChannelAdapter& a) :
    CoreRefs(ch, c, b, a),
    connection(c),
    basicHandler(*this),
    channelHandler(*this),
    exchangeHandler(*this),
    bindingHandler(*this),
    messageHandler(*this),
    queueHandler(*this),
    txHandler(*this),
    dtxHandler(*this)
{}


ProtocolVersion BrokerAdapter::getVersion() const {
    return connection.getVersion();
}
              
void BrokerAdapter::ChannelHandlerImpl::open(const string& /*outOfBand*/){
    channel.open();
    // FIXME aconway 2007-01-04: provide valid ID as per ampq 0-9
    client.openOk(std::string()/* ID */);//GRS, context.getRequestId());
} 
        
void BrokerAdapter::ChannelHandlerImpl::flow(bool active){
    channel.flow(active);
    client.flowOk(active);//GRS, context.getRequestId());
}         

void BrokerAdapter::ChannelHandlerImpl::flowOk(bool /*active*/){} 
        
void BrokerAdapter::ChannelHandlerImpl::close(uint16_t /*replyCode*/,
    const string& /*replyText*/,
    uint16_t /*classId*/, uint16_t /*methodId*/)
{
    client.closeOk();//GRS context.getRequestId());
    // FIXME aconway 2007-01-18: Following line will "delete this". Ugly.
    connection.closeChannel(channel.getId()); 
} 
        
void BrokerAdapter::ChannelHandlerImpl::closeOk(){} 
              


void BrokerAdapter::ExchangeHandlerImpl::declare(uint16_t /*ticket*/, const string& exchange, const string& type, 
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
        client.declareOk();//GRS context.getRequestId());
    }
}
                
void BrokerAdapter::ExchangeHandlerImpl::delete_(uint16_t /*ticket*/, 
                                                 const string& name, bool /*ifUnused*/, bool nowait){
    //TODO: implement unused
    Exchange::shared_ptr exchange(broker.getExchanges().get(name));
    if (exchange->isDurable()) broker.getStore().destroy(*exchange);
    broker.getExchanges().destroy(name);
    if(!nowait) client.deleteOk();//GRS context.getRequestId());
} 

void BrokerAdapter::ExchangeHandlerImpl::query(u_int16_t /*ticket*/, const string& name)
{
    try {
        Exchange::shared_ptr exchange(broker.getExchanges().get(name));
        client.queryOk(exchange->getType(), exchange->isDurable(), false, exchange->getArgs());//GRS, context.getRequestId());
    } catch (const ChannelException& e) {
        client.queryOk("", false, true, FieldTable());//GRS, context.getRequestId());        
    }
}

void BrokerAdapter::BindingHandlerImpl::query(u_int16_t /*ticket*/,
                                               const std::string& exchangeName,
                                               const std::string& queueName,
                                               const std::string& key,
                                               const framing::FieldTable& args)
{
    Exchange::shared_ptr exchange;
    try {
        exchange = broker.getExchanges().get(exchangeName);
    } catch (const ChannelException&) {}

    Queue::shared_ptr queue;
    if (!queueName.empty()) {
        queue = broker.getQueues().find(queueName);
    }

    if (!exchange) {
        client.queryOk(true, false, false, false, false);//GRS, context.getRequestId());
    } else if (!queueName.empty() && !queue) {
        client.queryOk(false, true, false, false, false);//GRS, context.getRequestId());
    } else if (exchange->isBound(queue, key.empty() ? 0 : &key, args.count() > 0 ? &args : &args)) {
            client.queryOk(false, false, false, false, false);//GRS, context.getRequestId());
    } else {
        //need to test each specified option individually
        bool queueMatched = queueName.empty() || exchange->isBound(queue, 0, 0);
        bool keyMatched = key.empty() || exchange->isBound(Queue::shared_ptr(), &key, 0);
        bool argsMatched = args.count() == 0 || exchange->isBound(Queue::shared_ptr(), 0, &args);

        client.queryOk(false, false, !queueMatched, !keyMatched, !argsMatched);//GRS, context.getRequestId());
    }
}

void BrokerAdapter::QueueHandlerImpl::declare(uint16_t /*ticket*/, const string& name, 
                                              bool passive, bool durable, bool exclusive, 
                                              bool autoDelete, bool nowait, const qpid::framing::FieldTable& arguments){
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = getQueue(name);
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            broker.getQueues().declare(
                name, durable,
                autoDelete && !exclusive,
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
            queueName, queue->getMessageCount(), queue->getConsumerCount());//GRS, context.getRequestId());
    }
} 
        
void BrokerAdapter::QueueHandlerImpl::bind(uint16_t /*ticket*/, const string& queueName, 
                                           const string& exchangeName, const string& routingKey, bool nowait, 
                                           const FieldTable& arguments){

    Queue::shared_ptr queue = getQueue(queueName);
    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if(exchange){
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        if (exchange->bind(queue, exchangeRoutingKey, &arguments)) {
            queue->bound(exchangeName, routingKey, arguments);
            if (exchange->isDurable() && queue->isDurable()) {
                broker.getStore().bind(*exchange, *queue, routingKey, arguments);
            }
        }
        if(!nowait) client.bindOk();//GRS context.getRequestId());    
    }else{
        throw ChannelException(
            404, "Bind failed. No such exchange: " + exchangeName);
    }
}
 
void 
BrokerAdapter::QueueHandlerImpl::unbind(uint16_t /*ticket*/,
    const string& queueName,
    const string& exchangeName,
    const string& routingKey,
    const qpid::framing::FieldTable& arguments )
{
    Queue::shared_ptr queue = getQueue(queueName);
    if (!queue.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    Exchange::shared_ptr exchange = broker.getExchanges().get(exchangeName);
    if (!exchange.get()) throw ChannelException(404, "Unbind failed. No such exchange: " + exchangeName);

    if (exchange->unbind(queue, routingKey, &arguments) && exchange->isDurable() && queue->isDurable()) {
        broker.getStore().unbind(*exchange, *queue, routingKey, arguments);
    }

    client.unbindOk();//GRS context.getRequestId());    
}
        
void BrokerAdapter::QueueHandlerImpl::purge(uint16_t /*ticket*/, const string& queueName, bool nowait){

    Queue::shared_ptr queue = getQueue(queueName);
    int count = queue->purge();
    if(!nowait) client.purgeOk( count);//GRS, context.getRequestId());
} 
        
void BrokerAdapter::QueueHandlerImpl::delete_(uint16_t /*ticket*/, const string& queue, 
                                              bool ifUnused, bool ifEmpty, bool nowait){
    ChannelException error(0, "");
    int count(0);
    Queue::shared_ptr q = getQueue(queue);
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
        client.deleteOk(count);//GRS, context.getRequestId());
} 
              
        


void BrokerAdapter::BasicHandlerImpl::qos(uint32_t prefetchSize, uint16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
    client.qosOk();//GRS context.getRequestId());
} 
        
void BrokerAdapter::BasicHandlerImpl::consume(uint16_t /*ticket*/, 
    const string& queueName, const string& consumerTag, 
    bool noLocal, bool noAck, bool exclusive, 
    bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = getQueue(queueName);    
    if(!consumerTag.empty() && channel.exists(consumerTag)){
        throw ConnectionException(530, "Consumer tags must be unique");
    }
    string newTag = consumerTag;
    //need to generate name here, so we have it for the adapter (it is
    //also version specific behaviour now)
    if (newTag.empty()) newTag = tagGenerator.generate();
    channel.consume(std::auto_ptr<DeliveryAdapter>(new ConsumeAdapter(adapter, newTag, connection.getFrameMax())),
        newTag, queue, !noAck, exclusive, noLocal ? &connection : 0, &fields);

    if(!nowait) client.consumeOk(newTag);//GRS, context.getRequestId());

    //allow messages to be dispatched if required as there is now a consumer:
    queue->requestDispatch();
} 
        
void BrokerAdapter::BasicHandlerImpl::cancel(const string& consumerTag, bool nowait){
    channel.cancel(consumerTag);

    if(!nowait) client.cancelOk(consumerTag);//GRS, context.getRequestId());
} 
        
void BrokerAdapter::BasicHandlerImpl::publish(uint16_t /*ticket*/, 
    const string& exchangeName, const string& routingKey, 
    bool mandatory, bool immediate)
{

    Exchange::shared_ptr exchange = exchangeName.empty() ? broker.getExchanges().getDefault() : broker.getExchanges().get(exchangeName);
    if(exchange){
        BasicMessage* msg = new BasicMessage(&connection, exchangeName, routingKey, mandatory, immediate);
        channel.handlePublish(msg);
    }else{
        throw ChannelException(
            404, "Exchange not found '" + exchangeName + "'");
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::get(uint16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = getQueue(queueName);    
    GetAdapter out(adapter, queue, "", connection.getFrameMax());
    if(!channel.get(out, queue, !noAck)){
        string clusterId;//not used, part of an imatix hack

        client.getEmpty(clusterId);//GRS, context.getRequestId());
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::ack(uint64_t deliveryTag, bool multiple){
	channel.ack(deliveryTag, multiple);
} 
        
void BrokerAdapter::BasicHandlerImpl::reject(uint64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void BrokerAdapter::BasicHandlerImpl::recover(bool requeue)
{
    channel.recover(requeue);
} 

void BrokerAdapter::TxHandlerImpl::select()
{
    channel.startTx();
    client.selectOk();//GRS context.getRequestId());
}

void BrokerAdapter::TxHandlerImpl::commit()
{
    channel.commit();
    client.commitOk();//GRS context.getRequestId());
}

void BrokerAdapter::TxHandlerImpl::rollback()
{    
    channel.rollback();
    client.rollbackOk();//GRS context.getRequestId());
    channel.recover(false);    
}
              
void BrokerAdapter::ChannelHandlerImpl::ok()
{
    //no specific action required, generic response handling should be sufficient
}


//
// Message class method handlers
//
void BrokerAdapter::ChannelHandlerImpl::ping()
{
    client.ok();//GRS context.getRequestId());
    client.pong();
}


void
BrokerAdapter::ChannelHandlerImpl::pong()
{
    client.ok();//GRS context.getRequestId());
}

void BrokerAdapter::ChannelHandlerImpl::resume(const string& /*channel*/)
{
    assert(0);                // FIXME aconway 2007-01-04: 0-9 feature
}

void BrokerAdapter::setResponseTo(RequestId r)
{
    basicHandler.client.setResponseTo(r);
    channelHandler.client.setResponseTo(r);
    exchangeHandler.client.setResponseTo(r);
    bindingHandler.client.setResponseTo(r);
    messageHandler.client.setResponseTo(r);
    queueHandler.client.setResponseTo(r);
    txHandler.client.setResponseTo(r);
    dtxHandler.setResponseTo(r);
}


}} // namespace qpid::broker

