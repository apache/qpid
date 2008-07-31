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
#include "Queue.h"
#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/constants.h"
#include "qpid/log/Statement.h"
#include "qpid/amqp_0_10/exceptions.h"
#include "qpid/framing/SequenceSet.h"
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
    messageImpl(s),
    executionImpl(s),
    txImpl(s),
    dtxImpl(s)
{}


void SessionAdapter::ExchangeHandlerImpl::declare(const string& exchange, const string& type, 
                                                  const string& alternateExchange, 
                                                  bool passive, bool durable, bool /*autoDelete*/, const FieldTable& args){

	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
        std::map<std::string, std::string> params;
		params.insert(make_pair("TYPE", type));
		params.insert(make_pair("ALT", alternateExchange));
		params.insert(make_pair("PAS", std::string(passive ? "Y" : "N") ));
		params.insert(make_pair("DURA", std::string(durable ? "Y" : "N")));
	    if (!acl->authorise(getConnection().getUserId(),acl::CREATE,acl::EXCHANGE,exchange,&params) )
	        throw NotAllowedException("ACL denied exhange declare request");
    }
	
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

	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
	    if (!acl->authorise(getConnection().getUserId(),acl::DELETE,acl::EXCHANGE,name,NULL) )
	        throw NotAllowedException("ACL denied exhange delete request");
    }


    //TODO: implement unused
    Exchange::shared_ptr exchange(getBroker().getExchanges().get(name));
    if (exchange->inUseAsAlternate()) throw NotAllowedException(QPID_MSG("Exchange in use as alternate-exchange."));
    if (exchange->isDurable()) getBroker().getStore().destroy(*exchange);
    if (exchange->getAlternate()) exchange->getAlternate()->decAlternateUsers();
    getBroker().getExchanges().destroy(name);
} 

ExchangeQueryResult SessionAdapter::ExchangeHandlerImpl::query(const string& name)
{

	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
	    if (!acl->authorise(getConnection().getUserId(),acl::ACCESS,acl::EXCHANGE,name,NULL) )
	        throw NotAllowedException("ACL denied exhange query request");
    }

    try {
        Exchange::shared_ptr exchange(getBroker().getExchanges().get(name));
        return ExchangeQueryResult(exchange->getType(), exchange->isDurable(), false, exchange->getArgs());
    } catch (const NotFoundException& e) {
        return ExchangeQueryResult("", false, true, FieldTable());        
    }
}
void SessionAdapter::ExchangeHandlerImpl::bind(const string& queueName, 
                                           const string& exchangeName, const string& routingKey, 
                                           const FieldTable& arguments){

	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
        std::map<std::string, std::string> params;
		params.insert(make_pair("RKEY", routingKey));
	    if (!acl->authorise(getConnection().getUserId(),acl::BIND,acl::EXCHANGE,exchangeName,&params) )
	        throw NotAllowedException("ACL denied exhange bind request");
    }

    Queue::shared_ptr queue = getQueue(queueName);
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

	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
        std::map<std::string, std::string> params;
		params.insert(make_pair("QN", queueName));
		params.insert(make_pair("RKEY", routingKey));
	    if (!acl->authorise(getConnection().getUserId(),acl::UNBIND,acl::EXCHANGE,exchangeName,&params) )
	        throw NotAllowedException("ACL denied exchange unbind request");
    }


    Queue::shared_ptr queue = getQueue(queueName);
    if (!queue.get()) throw NotFoundException("Unbind failed. No such exchange: " + exchangeName);

    Exchange::shared_ptr exchange = getBroker().getExchanges().get(exchangeName);
    if (!exchange.get()) throw NotFoundException("Unbind failed. No such exchange: " + exchangeName);

    //TODO: revise unbind to rely solely on binding key (not args)
    if (exchange->unbind(queue, routingKey, 0) && exchange->isDurable() && queue->isDurable()) {
        getBroker().getStore().unbind(*exchange, *queue, routingKey, FieldTable());
    }

}

ExchangeBoundResult SessionAdapter::ExchangeHandlerImpl::bound(const std::string& exchangeName,
                                                                  const std::string& queueName,
                                                                  const std::string& key,
                                                                  const framing::FieldTable& args)
{
	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
        std::map<std::string, std::string> params;
		params.insert(make_pair("QUEUE", queueName));
		params.insert(make_pair("RKEY", queueName));
	    if (!acl->authorise(getConnection().getUserId(),acl::CREATE,acl::EXCHANGE,exchangeName,&params) )
	        throw NotAllowedException("ACL denied exhange bound request");
    }
	
    Exchange::shared_ptr exchange;
    try {
        exchange = getBroker().getExchanges().get(exchangeName);
    } catch (const NotFoundException&) {}

    Queue::shared_ptr queue;
    if (!queueName.empty()) {
        queue = getBroker().getQueues().find(queueName);
    }

    if (!exchange) {
        return ExchangeBoundResult(true, false, false, false, false);
    } else if (!queueName.empty() && !queue) {
        return ExchangeBoundResult(false, true, false, false, false);
    } else if (exchange->isBound(queue, key.empty() ? 0 : &key, args.count() > 0 ? &args : &args)) {
        return ExchangeBoundResult(false, false, false, false, false);
    } else {
        //need to test each specified option individually
        bool queueMatched = queueName.empty() || exchange->isBound(queue, 0, 0);
        bool keyMatched = key.empty() || exchange->isBound(Queue::shared_ptr(), &key, 0);
        bool argsMatched = args.count() == 0 || exchange->isBound(Queue::shared_ptr(), 0, &args);

        return ExchangeBoundResult(false, false, !queueMatched, !keyMatched, !argsMatched);
    }
}

SessionAdapter::QueueHandlerImpl::QueueHandlerImpl(SemanticState& session) : HandlerHelper(session), broker(getBroker())
{}


SessionAdapter::QueueHandlerImpl::~QueueHandlerImpl()
{
    try {
        destroyExclusiveQueues();
    } catch (std::exception& e) {
        QPID_LOG(error, e.what());
    }
}

void SessionAdapter::QueueHandlerImpl::destroyExclusiveQueues()
{
    while (!exclusiveQueues.empty()) {
        Queue::shared_ptr q(exclusiveQueues.front());
        q->releaseExclusiveOwnership();
        if (q->canAutoDelete()) {
            Queue::tryAutoDelete(broker, q);
        }
        exclusiveQueues.erase(exclusiveQueues.begin());
    }
}

    
bool SessionAdapter::QueueHandlerImpl::isLocal(const ConnectionToken* t) const 
{ 
    return session.isLocal(t); 
}


QueueQueryResult SessionAdapter::QueueHandlerImpl::query(const string& name)
{
	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
	    if (!acl->authorise(getConnection().getUserId(),acl::ACCESS,acl::QUEUE,name,NULL) )
	        throw NotAllowedException("ACL denied queue query request");
    }
	
    Queue::shared_ptr queue = session.getBroker().getQueues().find(name);
    if (queue) {

        Exchange::shared_ptr alternateExchange = queue->getAlternateExchange();
        
        return QueueQueryResult(queue->getName(), 
                                alternateExchange ? alternateExchange->getName() : "", 
                                queue->isDurable(), 
                                queue->hasExclusiveOwner(),
                                queue->isAutoDelete(),
                                queue->getSettings(),
                                queue->getMessageCount(),
                                queue->getConsumerCount());
    } else {
        return QueueQueryResult();
    }
}

void SessionAdapter::QueueHandlerImpl::declare(const string& name, const string& alternateExchange,
                                              bool passive, bool durable, bool exclusive, 
                                              bool autoDelete, const qpid::framing::FieldTable& arguments){
 
	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
        std::map<std::string, std::string> params;
		params.insert(make_pair("ALT", alternateExchange));
		params.insert(make_pair("PAS", std::string(passive ? "Y" : "N") ));
		params.insert(make_pair("DURA", std::string(durable ? "Y" : "N")));
		params.insert(make_pair("EXCLUS", std::string(exclusive ? "Y" : "N")));
		params.insert(make_pair("AUTOD", std::string(autoDelete ? "Y" : "N")));
	    if (!acl->authorise(getConnection().getUserId(),acl::CREATE,acl::QUEUE,name,&params) )
	        throw NotAllowedException("ACL denied queue create request");
    }

    Exchange::shared_ptr alternate;
    if (!alternateExchange.empty()) {
        alternate = getBroker().getExchanges().get(alternateExchange);
    }
    Queue::shared_ptr queue;
    if (passive && !name.empty()) {
	queue = getQueue(name);
        //TODO: check alternate-exchange is as expected
    } else {
	std::pair<Queue::shared_ptr, bool> queue_created =  
            getBroker().getQueues().declare(
                name, durable,
                autoDelete,
                exclusive ? this : 0);
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
		exclusiveQueues.push_back(queue);
	    }
	} else {
            if (exclusive && queue->setExclusiveOwner(this)) {
		exclusiveQueues.push_back(queue);
            }
        }
    }
    if (exclusive && !queue->isExclusiveOwner(this)) 
	throw ResourceLockedException(
            QPID_MSG("Cannot grant exclusive access to queue "
                     << queue->getName()));
} 
        
        
void SessionAdapter::QueueHandlerImpl::purge(const string& queue){
	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
 	    if (!acl->authorise(getConnection().getUserId(),acl::DELETE,acl::QUEUE,queue,NULL) )
	        throw NotAllowedException("ACL denied queue purge request");
    }
    getQueue(queue)->purge();
} 
        
void SessionAdapter::QueueHandlerImpl::delete_(const string& queue, bool ifUnused, bool ifEmpty){

	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
 	    if (!acl->authorise(getConnection().getUserId(),acl::DELETE,acl::QUEUE,queue,NULL) )
	        throw NotAllowedException("ACL denied queue delete request");
    }

    ChannelException error(0, "");
    Queue::shared_ptr q = getQueue(queue);
    if(ifEmpty && q->getMessageCount() > 0){
        throw PreconditionFailedException("Queue not empty.");
    }else if(ifUnused && q->getConsumerCount() > 0){
        throw PreconditionFailedException("Queue in use.");
    }else{
        //remove the queue from the list of exclusive queues if necessary
        if(q->isExclusiveOwner(&getConnection())){
            QueueVector::iterator i = std::find(getConnection().exclusiveQueues.begin(), getConnection().exclusiveQueues.end(), q);
            if(i < getConnection().exclusiveQueues.end()) getConnection().exclusiveQueues.erase(i);
        }
        q->destroy();
        getBroker().getQueues().destroy(queue);
        q->unbind(getBroker().getExchanges(), q);
    }
} 


SessionAdapter::MessageHandlerImpl::MessageHandlerImpl(SemanticState& s) : 
    HandlerHelper(s),
    releaseRedeliveredOp(boost::bind(&SemanticState::release, &state, _1, _2, true)),
    releaseOp(boost::bind(&SemanticState::release, &state, _1, _2, false)),
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
    std::cout << "SessionAdapter::MessageHandlerImpl::transfer() called" << std::endl;
}

void SessionAdapter::MessageHandlerImpl::release(const SequenceSet& transfers, bool setRedelivered)
{
    transfers.for_each(setRedelivered ? releaseRedeliveredOp : releaseOp);
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

	AclModule* acl = getBroker().getAcl();
	if (acl)
	{
	    // add flags as needed
 	    if (!acl->authorise(getConnection().getUserId(),acl::CONSUME,acl::QUEUE,queueName,NULL) )
	        throw NotAllowedException("ACL denied Queue subscribe request");
    }

    Queue::shared_ptr queue = getQueue(queueName);
    if(!destination.empty() && state.exists(destination))
        throw NotAllowedException(QPID_MSG("Consumer tags must be unique"));

    string tag = destination;
    state.consume(MessageDelivery::getMessageDeliveryToken(destination, acceptMode, acquireMode), 
                  tag, queue, false, //TODO get rid of no-local
                  acceptMode == 0, acquireMode == 0, exclusive, &arguments);
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

void SessionAdapter::MessageHandlerImpl::flow(const std::string& destination, uint8_t unit, uint32_t value)
{
    if (unit == 0) {
        //message
        state.addMessageCredit(destination, value);
    } else if (unit == 1) {
        //bytes
        state.addByteCredit(destination, value);
    } else {
        //unknown
        throw InvalidArgumentException(QPID_MSG("Invalid value for unit " << unit));
    }
    
}
    
void SessionAdapter::MessageHandlerImpl::setFlowMode(const std::string& destination, uint8_t mode)
{
    if (mode == 0) {
        //credit
        state.setCreditMode(destination);
    } else if (mode == 1) {
        //window
        state.setWindowMode(destination);
    } else{
        throw InvalidArgumentException(QPID_MSG("Invalid value for mode " << mode));        
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

framing::MessageAcquireResult SessionAdapter::MessageHandlerImpl::acquire(const framing::SequenceSet& transfers)
{
    // FIXME aconway 2008-05-12: create SequenceSet directly, no need for intermediate results vector.
    SequenceNumberSet results;
    RangedOperation f = boost::bind(&SemanticState::acquire, &state, _1, _2, boost::ref(results));
    transfers.for_each(f);

    results = results.condense();
    SequenceSet acquisitions;
    RangedOperation g = boost::bind(&SequenceSet::add, &acquisitions, _1, _2);
    results.processRanges(g);

    return MessageAcquireResult(acquisitions);
}

framing::MessageResumeResult SessionAdapter::MessageHandlerImpl::resume(const std::string& /*destination*/,
                                                                        const std::string& /*resumeId*/)
{
    throw NotImplementedException("resuming transfers not yet supported");
}
    


void SessionAdapter::ExecutionHandlerImpl::sync() {} //essentially a no-op

void SessionAdapter::ExecutionHandlerImpl::result(const SequenceNumber& /*commandId*/, const string& /*value*/)
{
    //TODO: but currently never used client->server
}

void SessionAdapter::ExecutionHandlerImpl::exception(uint16_t /*errorCode*/,
                                                     const SequenceNumber& /*commandId*/,
                                                     uint8_t /*classCode*/,
                                                     uint8_t /*commandCode*/,
                                                     uint8_t /*fieldIndex*/,
                                                     const std::string& /*description*/,
                                                     const framing::FieldTable& /*errorInfo*/)
{
    //TODO: again, not really used client->server but may be important
    //for inter-broker links
}



void SessionAdapter::TxHandlerImpl::select()
{
    state.startTx();
}

void SessionAdapter::TxHandlerImpl::commit()
{
    state.commit(&getBroker().getStore());
}

void SessionAdapter::TxHandlerImpl::rollback()
{    
    state.rollback();
}

std::string SessionAdapter::DtxHandlerImpl::convert(const framing::Xid& xid)
{
    std::string encoded;
    encode(xid, encoded);
    return encoded;
}

void SessionAdapter::DtxHandlerImpl::select()
{
    state.selectDtx();
}

XaResult SessionAdapter::DtxHandlerImpl::end(const Xid& xid,
                                                            bool fail,
                                                            bool suspend)
{
    try {
        if (fail) {
            state.endDtx(convert(xid), true);
            if (suspend) {
                throw CommandInvalidException(QPID_MSG("End and suspend cannot both be set."));
            } else {
                return XaResult(XA_RBROLLBACK);
            }
        } else {
            if (suspend) {
                state.suspendDtx(convert(xid));
            } else {
                state.endDtx(convert(xid), false);
            }
            return XaResult(XA_OK);
        }
    } catch (const DtxTimeoutException& e) {
        return XaResult(XA_RBTIMEOUT);        
    }
}

XaResult SessionAdapter::DtxHandlerImpl::start(const Xid& xid,
                                                                bool join,
                                                                bool resume)
{
    if (join && resume) {
        throw CommandInvalidException(QPID_MSG("Join and resume cannot both be set."));
    }
    try {
        if (resume) {
            state.resumeDtx(convert(xid));
        } else {
            state.startDtx(convert(xid), getBroker().getDtxManager(), join);
        }
        return XaResult(XA_OK);
    } catch (const DtxTimeoutException& e) {
        return XaResult(XA_RBTIMEOUT);        
    }
}

XaResult SessionAdapter::DtxHandlerImpl::prepare(const Xid& xid)
{
    try {
        bool ok = getBroker().getDtxManager().prepare(convert(xid));
        return XaResult(ok ? XA_OK : XA_RBROLLBACK);
    } catch (const DtxTimeoutException& e) {
        return XaResult(XA_RBTIMEOUT);        
    }
}

XaResult SessionAdapter::DtxHandlerImpl::commit(const Xid& xid,
                            bool onePhase)
{
    try {
        bool ok = getBroker().getDtxManager().commit(convert(xid), onePhase);
        return XaResult(ok ? XA_OK : XA_RBROLLBACK);
    } catch (const DtxTimeoutException& e) {
        return XaResult(XA_RBTIMEOUT);        
    }
}


XaResult SessionAdapter::DtxHandlerImpl::rollback(const Xid& xid)
{
    try {
        getBroker().getDtxManager().rollback(convert(xid));
        return XaResult(XA_OK);
    } catch (const DtxTimeoutException& e) {
        return XaResult(XA_RBTIMEOUT);        
    }
}

DtxRecoverResult SessionAdapter::DtxHandlerImpl::recover()
{
    std::set<std::string> xids;
    getBroker().getStore().collectPreparedXids(xids);        
    /*
     * create array of long structs
     */
    Array indoubt(0xAB);
    for (std::set<std::string>::iterator i = xids.begin(); i != xids.end(); i++) {
        boost::shared_ptr<FieldValue> xid(new Struct32Value(*i));
        indoubt.add(xid);
    }
    return DtxRecoverResult(indoubt);
}

void SessionAdapter::DtxHandlerImpl::forget(const Xid& xid)
{
    //Currently no heuristic completion is supported, so this should never be used.
    throw NotImplementedException(QPID_MSG("Forget not implemented. Branch with xid "  << xid << " not heuristically completed!"));
}

DtxGetTimeoutResult SessionAdapter::DtxHandlerImpl::getTimeout(const Xid& xid)
{
    uint32_t timeout = getBroker().getDtxManager().getTimeout(convert(xid));
    return DtxGetTimeoutResult(timeout);    
}


void SessionAdapter::DtxHandlerImpl::setTimeout(const Xid& xid,
                                                uint32_t timeout)
{
    getBroker().getDtxManager().setTimeout(convert(xid), timeout);
}


Queue::shared_ptr SessionAdapter::HandlerHelper::getQueue(const string& name) const {
    Queue::shared_ptr queue;
    if (name.empty()) {
        throw amqp_0_10::IllegalArgumentException(QPID_MSG("No queue name specified."));
    } else {
        queue = session.getBroker().getQueues().find(name);
        if (!queue)
            throw amqp_0_10::NotFoundException(QPID_MSG("Queue not found: "<<name));
    }
    return queue;
}

}} // namespace qpid::broker


