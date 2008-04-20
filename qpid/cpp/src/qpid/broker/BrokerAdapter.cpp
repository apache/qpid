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
#include "DeliveryToken.h"
#include "MessageDelivery.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace broker {

using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr> QueueVector;

// TODO aconway 2007-08-31: now that functionality is distributed
// between different handlers, BrokerAdapter should be dropped.
// Instead the individual class Handler interfaces can be implemented
// by the handlers responsible for those classes.
//

BrokerAdapter::BrokerAdapter(SemanticState& s) :
    HandlerImpl(s),
    basicHandler(s),
    exchangeHandler(s),
    bindingHandler(s),
    messageHandler(s),
    queueHandler(s),
    txHandler(s),
    dtxHandler(s)
{}


void BrokerAdapter::ExchangeHandlerImpl::declare(uint16_t /*ticket*/, const string& exchange, const string& type, 
                                                 const string& alternateExchange, 
                                                 bool passive, bool durable, bool /*autoDelete*/, const FieldTable& args){
    Exchange::shared_ptr alternate;
    if (!alternateExchange.empty()) {
        alternate = getBroker().getExchanges().get(alternateExchange);
    }
    if(passive){
        Exchange::shared_ptr actual(getBroker().getExchanges().get(exchange));
        checkType(actual, type);
        checkAlternate(actual, alternate);
    }else{        
        try{
            std::pair<Exchange::shared_ptr, bool> response = getBroker().getExchanges().declare(exchange, type, durable, args);
            if (response.second) {
                if (durable) {
                    getBroker().getStore().create(*response.first, args);
                }
                if (alternate) {
                    response.first->setAlternate(alternate);
                    alternate->incAlternateUsers();
                }
            } else {
                checkType(response.first, type);
                checkAlternate(response.first, alternate);
            }
        }catch(UnknownExchangeTypeException& e){
            throw CommandInvalidException(QPID_MSG("Exchange type not implemented: " << type));
        }
    }
}

void BrokerAdapter::ExchangeHandlerImpl::checkType(Exchange::shared_ptr exchange, const std::string& type)
{
    if (!type.empty() && exchange->getType() != type) {
        throw NotAllowedException(QPID_MSG("Exchange declared to be of type " << exchange->getType() << ", requested " << type));
    }
}

void BrokerAdapter::ExchangeHandlerImpl::checkAlternate(Exchange::shared_ptr exchange, Exchange::shared_ptr alternate)
{
    if (alternate && alternate != exchange->getAlternate()) 
        throw NotAllowedException(
            QPID_MSG("Exchange declared with alternate-exchange "
                     << exchange->getAlternate()->getName() << ", requested " 
                     << alternate->getName()));
}
                
void BrokerAdapter::ExchangeHandlerImpl::delete_(uint16_t /*ticket*/, const string& name, bool /*ifUnused*/){
    //TODO: implement unused
    Exchange::shared_ptr exchange(getBroker().getExchanges().get(name));
    if (exchange->inUseAsAlternate()) throw NotAllowedException(QPID_MSG("Exchange in use as alternate-exchange."));
    if (exchange->isDurable()) getBroker().getStore().destroy(*exchange);
    if (exchange->getAlternate()) exchange->getAlternate()->decAlternateUsers();
    getBroker().getExchanges().destroy(name);
} 

ExchangeXQueryResult BrokerAdapter::ExchangeHandlerImpl::query(u_int16_t /*ticket*/, const string& name)
{
    try {
        Exchange::shared_ptr exchange(getBroker().getExchanges().get(name));
        return ExchangeXQueryResult(exchange->getType(), exchange->isDurable(), false, exchange->getArgs());
    } catch (const ChannelException& e) {
        return ExchangeXQueryResult("", false, true, FieldTable());        
    }
}

BindingXQueryResult BrokerAdapter::BindingHandlerImpl::query(u_int16_t /*ticket*/,
                                                            const std::string& exchangeName,
                                                            const std::string& queueName,
                                                            const std::string& key,
                                                            const framing::FieldTable& args)
{
    Exchange::shared_ptr exchange;
    try {
        exchange = getBroker().getExchanges().get(exchangeName);
    } catch (const ChannelException&) {}

    Queue::shared_ptr queue;
    if (!queueName.empty()) {
        queue = getBroker().getQueues().find(queueName);
    }

    if (!exchange) {
        return BindingXQueryResult(true, false, false, false, false);
    } else if (!queueName.empty() && !queue) {
        return BindingXQueryResult(false, true, false, false, false);
    } else if (exchange->isBound(queue, key.empty() ? 0 : &key, args.count() > 0 ? &args : &args)) {
        return BindingXQueryResult(false, false, false, false, false);
    } else {
        //need to test each specified option individually
        bool queueMatched = queueName.empty() || exchange->isBound(queue, 0, 0);
        bool keyMatched = key.empty() || exchange->isBound(Queue::shared_ptr(), &key, 0);
        bool argsMatched = args.count() == 0 || exchange->isBound(Queue::shared_ptr(), 0, &args);

        return BindingXQueryResult(false, false, !queueMatched, !keyMatched, !argsMatched);
    }
}

QueueXQueryResult BrokerAdapter::QueueHandlerImpl::query(const string& name)
{
    Queue::shared_ptr queue = state.getQueue(name);
    Exchange::shared_ptr alternateExchange = queue->getAlternateExchange();

    return QueueXQueryResult(queue->getName(), 
                            alternateExchange ? alternateExchange->getName() : "", 
                            queue->isDurable(), 
                            queue->hasExclusiveOwner(),
                            queue->isAutoDelete(),
                            queue->getSettings(),
                            queue->getMessageCount(),
                            queue->getConsumerCount());
}

void BrokerAdapter::QueueHandlerImpl::declare(uint16_t /*ticket*/, const string& name, const string& alternateExchange,
                                              bool passive, bool durable, bool exclusive, 
                                              bool autoDelete, const qpid::framing::FieldTable& arguments){
 
    Exchange::shared_ptr alternate;
    if (!alternateExchange.empty()) {
        alternate = getBroker().getExchanges().get(alternateExchange);
    }
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = state.getQueue(name);
        //TODO: check alternate-exchange is as expected
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            getBroker().getQueues().declare(
                name, durable,
                autoDelete,
                exclusive ? &getConnection() : 0);
	queue = queue_created.first;
	assert(queue);
	if (queue_created.second) { // This is a new queue
            if (alternate) {
                queue->setAlternateExchange(alternate);
                alternate->incAlternateUsers();
            }

            //apply settings & create persistent record if required
            queue_created.first->create(arguments);

	    //add default binding:
	    getBroker().getExchanges().getDefault()->bind(queue, name, 0);
            queue->bound(getBroker().getExchanges().getDefault()->getName(), name, arguments);

            //handle automatic cleanup:
	    if (exclusive) {
		getConnection().exclusiveQueues.push_back(queue);
	    }
	} else {
            if (exclusive && queue->setExclusiveOwner(&getConnection())) {
		getConnection().exclusiveQueues.push_back(queue);
            }
        }
    }
    if (exclusive && !queue->isExclusiveOwner(&getConnection())) 
	throw ResourceLockedException(
            QPID_MSG("Cannot grant exclusive access to queue "
                     << queue->getName()));
} 
        
void BrokerAdapter::QueueHandlerImpl::bind(uint16_t /*ticket*/, const string& queueName, 
                                           const string& exchangeName, const string& routingKey, 
                                           const FieldTable& arguments){

    Queue::shared_ptr queue = state.getQueue(queueName);
    Exchange::shared_ptr exchange = getBroker().getExchanges().get(exchangeName);
    if(exchange){
        string exchangeRoutingKey = routingKey.empty() && queueName.empty() ? queue->getName() : routingKey;
        if (exchange->bind(queue, exchangeRoutingKey, &arguments)) {
            queue->bound(exchangeName, routingKey, arguments);
            if (exchange->isDurable() && queue->isDurable()) {
                getBroker().getStore().bind(*exchange, *queue, routingKey, arguments);
            }
        }
    }else{
        throw NotFoundException(
            "Bind failed. No such exchange: " + exchangeName);
    }
}
 
void 
BrokerAdapter::QueueHandlerImpl::unbind(uint16_t /*ticket*/,
                                        const string& queueName,
                                        const string& exchangeName,
                                        const string& routingKey,
                                        const qpid::framing::FieldTable& arguments )
{
    Queue::shared_ptr queue = state.getQueue(queueName);
    if (!queue.get()) throw NotFoundException("Unbind failed. No such exchange: " + exchangeName);

    Exchange::shared_ptr exchange = getBroker().getExchanges().get(exchangeName);
    if (!exchange.get()) throw NotFoundException("Unbind failed. No such exchange: " + exchangeName);

    if (exchange->unbind(queue, routingKey, &arguments) && exchange->isDurable() && queue->isDurable()) {
        getBroker().getStore().unbind(*exchange, *queue, routingKey, arguments);
    }

}
        
void BrokerAdapter::QueueHandlerImpl::purge(uint16_t /*ticket*/, const string& queue){
    state.getQueue(queue)->purge();
} 
        
void BrokerAdapter::QueueHandlerImpl::delete_(uint16_t /*ticket*/, const string& queue, bool ifUnused, bool ifEmpty){
    ChannelException error(0, "");
    Queue::shared_ptr q = state.getQueue(queue);
    if(ifEmpty && q->getMessageCount() > 0){
        throw PreconditionFailedException("Queue not empty.");
    }else if(ifUnused && q->getConsumerCount() > 0){
        throw PreconditionFailedException("Queue in use.");
    }else{
        //remove the queue from the list of exclusive queues if necessary
        if(q->isExclusiveOwner(&getConnection())){
            QueueVector::iterator i = find(getConnection().exclusiveQueues.begin(), getConnection().exclusiveQueues.end(), q);
            if(i < getConnection().exclusiveQueues.end()) getConnection().exclusiveQueues.erase(i);
        }
        q->destroy();
        getBroker().getQueues().destroy(queue);
        q->unbind(getBroker().getExchanges(), q);
    }
} 
              
        


void BrokerAdapter::BasicHandlerImpl::qos(uint32_t prefetchSize, uint16_t prefetchCount, bool /*global*/){
    //TODO: handle global
    state.setPrefetchSize(prefetchSize);
    state.setPrefetchCount(prefetchCount);
} 
        
void BrokerAdapter::BasicHandlerImpl::consume(uint16_t /*ticket*/, 
                                              const string& queueName, const string& consumerTag, 
                                              bool noLocal, bool noAck, bool exclusive, 
                                              bool nowait, const FieldTable& fields)
{
    
    Queue::shared_ptr queue = state.getQueue(queueName);    
    if(!consumerTag.empty() && state.exists(consumerTag)){
        throw NotAllowedException(QPID_MSG("Consumer tags must be unique"));
    }
    string newTag = consumerTag;
    //need to generate name here, so we have it for the adapter (it is
    //also version specific behaviour now)
    if (newTag.empty()) newTag = tagGenerator.generate();
    DeliveryToken::shared_ptr token(MessageDelivery::getBasicConsumeToken(newTag));
    state.consume(token, newTag, queue, noLocal, !noAck, true, exclusive, &fields);

    if(!nowait)
        getProxy().getBasic().consumeOk(newTag);
} 
        
void BrokerAdapter::BasicHandlerImpl::cancel(const string& consumerTag){
    state.cancel(consumerTag);
} 
        
void BrokerAdapter::BasicHandlerImpl::get(uint16_t /*ticket*/, const string& queueName, bool noAck){
    Queue::shared_ptr queue = state.getQueue(queueName);    
    DeliveryToken::shared_ptr token(MessageDelivery::getBasicGetToken(queue));
    if(!state.get(token, queue, !noAck)){
        string clusterId;//not used, part of an imatix hack

        getProxy().getBasic().getEmpty(clusterId);
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::ack(uint64_t deliveryTag, bool multiple){
    if (multiple) {
        state.ackCumulative(deliveryTag);
    } else {
        state.ackRange(deliveryTag, deliveryTag);
    }
} 
        
void BrokerAdapter::BasicHandlerImpl::reject(uint64_t /*deliveryTag*/, bool /*requeue*/){} 
        
void BrokerAdapter::BasicHandlerImpl::recover(bool requeue)
{
    state.recover(requeue);
} 

void BrokerAdapter::TxHandlerImpl::select()
{
    state.startTx();
}

void BrokerAdapter::TxHandlerImpl::commit()
{
    state.commit(&getBroker().getStore(), true);
}

void BrokerAdapter::TxHandlerImpl::rollback()
{    
    state.rollback();
    state.recover(true);    
}
              
}} // namespace qpid::broker

