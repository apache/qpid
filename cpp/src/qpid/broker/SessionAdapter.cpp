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
#include "SessionAdapter.h"
#include "Connection.h"
#include "DeliveryToken.h"
#include "MessageDelivery.h"
#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"
#include <boost/format.hpp>
#include <boost/cast.hpp>
#include <boost/bind.hpp>

namespace qpid {
namespace broker {

using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr> QueueVector;

SessionAdapter::SessionAdapter(SemanticState& s) :
    HandlerImpl(s),
    exchangeImpl(s),
    queueImpl(s),
    messageImpl(s)
{}


void SessionAdapter::ExchangeHandlerImpl::declare(const string& exchange, const string& type, 
                                                  const string& alternateExchange, 
                                                  bool passive, bool durable, bool /*autoDelete*/, const FieldTable& args){

    //TODO: implement autoDelete
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
                    getBroker().getStore().create(*response.first);
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

void SessionAdapter::ExchangeHandlerImpl::checkType(Exchange::shared_ptr exchange, const std::string& type)
{
    if (!type.empty() && exchange->getType() != type) {
        throw NotAllowedException(QPID_MSG("Exchange declared to be of type " << exchange->getType() << ", requested " << type));
    }
}

void SessionAdapter::ExchangeHandlerImpl::checkAlternate(Exchange::shared_ptr exchange, Exchange::shared_ptr alternate)
{
    if (alternate && alternate != exchange->getAlternate()) 
        throw NotAllowedException(
            QPID_MSG("Exchange declared with alternate-exchange "
                     << exchange->getAlternate()->getName() << ", requested " 
                     << alternate->getName()));
}
                
void SessionAdapter::ExchangeHandlerImpl::delete_(const string& name, bool /*ifUnused*/){
    //TODO: implement unused
    Exchange::shared_ptr exchange(getBroker().getExchanges().get(name));
    if (exchange->inUseAsAlternate()) throw NotAllowedException(QPID_MSG("Exchange in use as alternate-exchange."));
    if (exchange->isDurable()) getBroker().getStore().destroy(*exchange);
    if (exchange->getAlternate()) exchange->getAlternate()->decAlternateUsers();
    getBroker().getExchanges().destroy(name);
} 

Exchange010QueryResult SessionAdapter::ExchangeHandlerImpl::query(const string& name)
{
    try {
        Exchange::shared_ptr exchange(getBroker().getExchanges().get(name));
        return Exchange010QueryResult(exchange->getType(), exchange->isDurable(), false, exchange->getArgs());
    } catch (const ChannelException& e) {
        return Exchange010QueryResult("", false, true, FieldTable());        
    }
}
void SessionAdapter::ExchangeHandlerImpl::bind(const string& queueName, 
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
SessionAdapter::ExchangeHandlerImpl::unbind(const string& queueName,
                                        const string& exchangeName,
                                        const string& routingKey)
{
    Queue::shared_ptr queue = state.getQueue(queueName);
    if (!queue.get()) throw NotFoundException("Unbind failed. No such exchange: " + exchangeName);

    Exchange::shared_ptr exchange = getBroker().getExchanges().get(exchangeName);
    if (!exchange.get()) throw NotFoundException("Unbind failed. No such exchange: " + exchangeName);

    //TODO: revise unbind to rely solely on binding key (not args)
    if (exchange->unbind(queue, routingKey, 0) && exchange->isDurable() && queue->isDurable()) {
        getBroker().getStore().unbind(*exchange, *queue, routingKey, FieldTable());
    }

}

Exchange010BoundResult SessionAdapter::ExchangeHandlerImpl::bound(const std::string& exchangeName,
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
        return Exchange010BoundResult(true, false, false, false, false);
    } else if (!queueName.empty() && !queue) {
        return Exchange010BoundResult(false, true, false, false, false);
    } else if (exchange->isBound(queue, key.empty() ? 0 : &key, args.count() > 0 ? &args : &args)) {
        return Exchange010BoundResult(false, false, false, false, false);
    } else {
        //need to test each specified option individually
        bool queueMatched = queueName.empty() || exchange->isBound(queue, 0, 0);
        bool keyMatched = key.empty() || exchange->isBound(Queue::shared_ptr(), &key, 0);
        bool argsMatched = args.count() == 0 || exchange->isBound(Queue::shared_ptr(), 0, &args);

        return Exchange010BoundResult(false, false, !queueMatched, !keyMatched, !argsMatched);
    }
}

Queue010QueryResult SessionAdapter::QueueHandlerImpl::query(const string& name)
{
    Queue::shared_ptr queue = state.getQueue(name);
    Exchange::shared_ptr alternateExchange = queue->getAlternateExchange();

    return Queue010QueryResult(queue->getName(), 
                            alternateExchange ? alternateExchange->getName() : "", 
                            queue->isDurable(), 
                            queue->hasExclusiveOwner(),
                            queue->isAutoDelete(),
                            queue->getSettings(),
                            queue->getMessageCount(),
                            queue->getConsumerCount());
}

void SessionAdapter::QueueHandlerImpl::declare(const string& name, const string& alternateExchange,
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
        
        
void SessionAdapter::QueueHandlerImpl::purge(const string& queue){
    state.getQueue(queue)->purge();
} 
        
void SessionAdapter::QueueHandlerImpl::delete_(const string& queue, bool ifUnused, bool ifEmpty){
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


SessionAdapter::MessageHandlerImpl::MessageHandlerImpl(SemanticState& s) : 
    HandlerImpl(s),
    releaseOp(boost::bind(&SemanticState::release, &state, _1, _2)),
    rejectOp(boost::bind(&SemanticState::reject, &state, _1, _2)),
    acceptOp(boost::bind(&SemanticState::accepted, &state, _1, _2))
 {}

//
// Message class method handlers
//

void SessionAdapter::MessageHandlerImpl::transfer(const string& /*destination*/,
                                  uint8_t /*acceptMode*/,
                                  uint8_t /*acquireMode*/)
{
    //not yet used (content containing assemblies treated differently at present
}

    void SessionAdapter::MessageHandlerImpl::release(const SequenceSet& transfers, bool /*setRedelivered*/)
{
    transfers.for_each(releaseOp);
}

void
SessionAdapter::MessageHandlerImpl::subscribe(const string& queueName,
                              const string& destination,
                              uint8_t acceptMode,
                              uint8_t acquireMode,
                              bool exclusive,
                              const string& /*resumeId*/,//TODO implement resume behaviour
                              uint64_t /*resumeTtl*/,
                              const FieldTable& arguments)
{
    Queue::shared_ptr queue = state.getQueue(queueName);
    if(!destination.empty() && state.exists(destination))
        throw NotAllowedException(QPID_MSG("Consumer tags must be unique"));

    string tag = destination;
    state.consume(MessageDelivery::getMessageDeliveryToken(destination, acceptMode, acquireMode), 
                  tag, queue, false, //TODO get rid of no-local
                  acceptMode == 1, acquireMode == 0, exclusive, &arguments);
}

void
SessionAdapter::MessageHandlerImpl::cancel(const string& destination )
{
    state.cancel(destination);
}

void
SessionAdapter::MessageHandlerImpl::reject(const SequenceSet& transfers, uint16_t /*code*/, const string& /*text*/ )
{
    transfers.for_each(rejectOp);
}

void SessionAdapter::MessageHandlerImpl::flow(const std::string& destination, u_int8_t unit, u_int32_t value)
{
    if (unit == 0) {
        //message
        state.addMessageCredit(destination, value);
    } else if (unit == 1) {
        //bytes
        state.addByteCredit(destination, value);
    } else {
        //unknown
        throw SyntaxErrorException(QPID_MSG("Invalid value for unit " << unit));
    }
    
}
    
void SessionAdapter::MessageHandlerImpl::setFlowMode(const std::string& destination, u_int8_t mode)
{
    if (mode == 0) {
        //credit
        state.setCreditMode(destination);
    } else if (mode == 1) {
        //window
        state.setWindowMode(destination);
    } else{
        throw SyntaxErrorException(QPID_MSG("Invalid value for mode " << mode));        
    }
}
    
void SessionAdapter::MessageHandlerImpl::flush(const std::string& destination)
{
    state.flush(destination);        
}

void SessionAdapter::MessageHandlerImpl::stop(const std::string& destination)
{
    state.stop(destination);        
}

void SessionAdapter::MessageHandlerImpl::accept(const framing::SequenceSet& commands)
{

    commands.for_each(acceptOp);
}

/*
void SessionAdapter::MessageHandlerImpl::acquire(const SequenceSet& transfers)
{
    SequenceNumberSet results;
    RangedOperation op = boost::bind(&SemanticState::acquire, &state, _1, _2, boost::ref(results));
    transfers.processRanges(op);
    results = results.condense();
    getProxy().getMessage().acquired(results);
}
*/

}} // namespace qpid::broker


