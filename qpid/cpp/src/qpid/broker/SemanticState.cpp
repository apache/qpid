/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "SessionState.h"
#include "Connection.h"
#include "DeliverableMessage.h"
#include "DtxAck.h"
#include "DtxTimeout.h"
#include "Message.h"
#include "Queue.h"
#include "SessionContext.h"
#include "TxAccept.h"
#include "TxPublish.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include "qpid/ptr_map.h"
#include "AclModule.h"

#include <boost/bind.hpp>
#include <boost/format.hpp>

#include <iostream>
#include <sstream>
#include <algorithm>
#include <functional>

#include <assert.h>


namespace qpid {
namespace broker {

using std::mem_fun_ref;
using boost::intrusive_ptr;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
using qpid::ptr_map_ptr;

SemanticState::SemanticState(DeliveryAdapter& da, SessionContext& ss)
    : session(ss),
      deliveryAdapter(da),
      tagGenerator("sgen"),
      dtxSelected(false),
      outputTasks(ss),
      authMsg(getSession().getBroker().getOptions().auth && !getSession().getConnection().isFederationLink()),
      userID(getSession().getConnection().getUserId().substr(0,getSession().getConnection().getUserId().find('@')))
{
    acl = getSession().getBroker().getAcl();
}

SemanticState::~SemanticState() {
    //cancel all consumers
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        cancel(i->second);
    }

    if (dtxBuffer.get()) {
        dtxBuffer->fail();
    }
    recover(true);
}

bool SemanticState::exists(const string& consumerTag){
    return consumers.find(consumerTag) != consumers.end();
}

void SemanticState::consume(const string& tag, 
                            Queue::shared_ptr queue, bool ackRequired, bool acquire,
                            bool exclusive, const string& resumeId, uint64_t resumeTtl, const FieldTable& arguments)
{
    ConsumerImpl::shared_ptr c(new ConsumerImpl(this, tag, queue, ackRequired, acquire, exclusive, resumeId, resumeTtl, arguments));
    queue->consume(c, exclusive);//may throw exception
    outputTasks.addOutputTask(c.get());
    consumers[tag] = c;
}

void SemanticState::cancel(const string& tag){
    ConsumerImplMap::iterator i = consumers.find(tag);
    if (i != consumers.end()) {
        cancel(i->second);
        consumers.erase(i); 
        //should cancel all unacked messages for this consumer so that
        //they are not redelivered on recovery
        for_each(unacked.begin(), unacked.end(), boost::bind(&DeliveryRecord::cancel, _1, tag));
        
    }
}


void SemanticState::startTx()
{
    txBuffer = TxBuffer::shared_ptr(new TxBuffer());
}

void SemanticState::commit(MessageStore* const store)
{
    if (!txBuffer) throw
        CommandInvalidException(QPID_MSG("Session has not been selected for use with transactions"));

    TxOp::shared_ptr txAck(static_cast<TxOp*>(new TxAccept(accumulatedAck, unacked)));
    txBuffer->enlist(txAck);
    if (txBuffer->commitLocal(store)) {
        accumulatedAck.clear();
    } else {
        throw InternalErrorException(QPID_MSG("Commit failed"));
    }
}

void SemanticState::rollback()
{
    if (!txBuffer)
        throw CommandInvalidException(QPID_MSG("Session has not been selected for use with transactions"));

    txBuffer->rollback();
    accumulatedAck.clear();
}

void SemanticState::selectDtx()
{
    dtxSelected = true;
}

void SemanticState::startDtx(const std::string& xid, DtxManager& mgr, bool join)
{
    if (!dtxSelected) {
        throw CommandInvalidException(QPID_MSG("Session has not been selected for use with dtx"));
    }
    dtxBuffer = DtxBuffer::shared_ptr(new DtxBuffer(xid));
    txBuffer = static_pointer_cast<TxBuffer>(dtxBuffer);
    if (join) {
        mgr.join(xid, dtxBuffer);
    } else {
        mgr.start(xid, dtxBuffer);
    }
}

void SemanticState::endDtx(const std::string& xid, bool fail)
{
    if (!dtxBuffer) {
        throw IllegalStateException(QPID_MSG("xid " << xid << " not associated with this session"));
    }
    if (dtxBuffer->getXid() != xid) {
        throw CommandInvalidException(
            QPID_MSG("xid specified on start was " << dtxBuffer->getXid() << ", but " << xid << " specified on end"));

    }

    txBuffer.reset();//ops on this session no longer transactional

    checkDtxTimeout();
    if (fail) {
        dtxBuffer->fail();
    } else {
        dtxBuffer->markEnded();
    }    
    dtxBuffer.reset();
}

void SemanticState::suspendDtx(const std::string& xid)
{
    if (dtxBuffer->getXid() != xid) {
        throw CommandInvalidException(
            QPID_MSG("xid specified on start was " << dtxBuffer->getXid() << ", but " << xid << " specified on suspend"));
    }
    txBuffer.reset();//ops on this session no longer transactional

    checkDtxTimeout();
    dtxBuffer->setSuspended(true);
    suspendedXids[xid] = dtxBuffer;
    dtxBuffer.reset();
}

void SemanticState::resumeDtx(const std::string& xid)
{
    if (!dtxSelected) {
        throw CommandInvalidException(QPID_MSG("Session has not been selected for use with dtx"));
    }

    dtxBuffer = suspendedXids[xid];
    if (!dtxBuffer) {
        throw CommandInvalidException(QPID_MSG("xid " << xid << " not attached"));
    } else {
        suspendedXids.erase(xid);
    }

    if (dtxBuffer->getXid() != xid) {
        throw CommandInvalidException(
            QPID_MSG("xid specified on start was " << dtxBuffer->getXid() << ", but " << xid << " specified on resume"));

    }
    if (!dtxBuffer->isSuspended()) {
        throw CommandInvalidException(QPID_MSG("xid " << xid << " not suspended"));
    }

    checkDtxTimeout();
    dtxBuffer->setSuspended(false);
    txBuffer = static_pointer_cast<TxBuffer>(dtxBuffer);
}

void SemanticState::checkDtxTimeout()
{
    if (dtxBuffer->isExpired()) {
        dtxBuffer.reset();
        throw DtxTimeoutException();
    }
}

void SemanticState::record(const DeliveryRecord& delivery)
{
    unacked.push_back(delivery);
}

SemanticState::ConsumerImpl::ConsumerImpl(SemanticState* _parent, 
                                          const string& _name, 
                                          Queue::shared_ptr _queue, 
                                          bool ack,
                                          bool _acquire,
                                          bool _exclusive,
                                          const string& _resumeId,
                                          uint64_t _resumeTtl,
                                          const framing::FieldTable& _arguments


) : 
    Consumer(_acquire),
    parent(_parent), 
    name(_name), 
    queue(_queue), 
    ackExpected(ack), 
    acquire(_acquire),
    blocked(true), 
    windowing(true),
    exclusive(_exclusive),
    resumeId(_resumeId),
    resumeTtl(_resumeTtl),
    arguments(_arguments),
    msgCredit(0), 
    byteCredit(0),
    notifyEnabled(true),
    syncFrequency(_arguments.getAsInt("qpid.sync_frequency")),
    deliveryCount(0) {}

OwnershipToken* SemanticState::ConsumerImpl::getSession()
{
    return &(parent->session);
}

bool SemanticState::ConsumerImpl::deliver(QueuedMessage& msg)
{
    allocateCredit(msg.payload);
    DeliveryRecord record(msg, queue, name, acquire, !ackExpected, windowing);
    bool sync = syncFrequency && ++deliveryCount >= syncFrequency;
    if (sync) deliveryCount = 0;//reset
    parent->deliver(record, sync);
    if (!ackExpected) record.setEnded();//allows message to be released now its been delivered
    if (windowing || ackExpected || !acquire) {
        parent->record(record);
    } 
    if (acquire && !ackExpected) {
        queue->dequeue(0, msg);
    }
    return true;
}

bool SemanticState::ConsumerImpl::filter(intrusive_ptr<Message>)
{
    return true;
}

bool SemanticState::ConsumerImpl::accept(intrusive_ptr<Message> msg)
{
    blocked = !(filter(msg) && checkCredit(msg));
    return !blocked;
}

void SemanticState::ConsumerImpl::allocateCredit(intrusive_ptr<Message>& msg)
{
    uint32_t originalMsgCredit = msgCredit;
    uint32_t originalByteCredit = byteCredit;        
    if (msgCredit != 0xFFFFFFFF) {
        msgCredit--;
    }
    if (byteCredit != 0xFFFFFFFF) {
        byteCredit -= msg->getRequiredCredit();
    }
    QPID_LOG(debug, "Credit allocated for '" << name << "' on " << parent
             << ", was " << " bytes: " << originalByteCredit << " msgs: " << originalMsgCredit
             << " now bytes: " << byteCredit << " msgs: " << msgCredit);
    
}

bool SemanticState::ConsumerImpl::checkCredit(intrusive_ptr<Message>& msg)
{
    if (msgCredit == 0 || (byteCredit != 0xFFFFFFFF && byteCredit < msg->getRequiredCredit())) {
        QPID_LOG(debug, "Not enough credit for '" << name  << "' on " << parent 
                 << ", bytes: " << byteCredit << " msgs: " << msgCredit);
        return false;
    } else {
        QPID_LOG(debug, "Credit available for '" << name << "' on " << parent
                 << " bytes: " << byteCredit << " msgs: " << msgCredit);
        return true;
    }
}

SemanticState::ConsumerImpl::~ConsumerImpl() {}

void SemanticState::cancel(ConsumerImpl::shared_ptr c)
{
    c->disableNotify();
    outputTasks.removeOutputTask(c.get());
    Queue::shared_ptr queue = c->getQueue();
    if(queue) {
        queue->cancel(c);
        if (queue->canAutoDelete() && !queue->hasExclusiveOwner()) {            
            Queue::tryAutoDelete(session.getBroker(), queue);
        }
    }
}

void SemanticState::handle(intrusive_ptr<Message> msg) {
    if (txBuffer.get()) {
        TxPublish* deliverable(new TxPublish(msg));
        TxOp::shared_ptr op(deliverable);
        route(msg, *deliverable);
        txBuffer->enlist(op);
    } else {
        DeliverableMessage deliverable(msg);
        route(msg, deliverable);
    }
}

namespace
{
const std::string nullstring;
}

void SemanticState::route(intrusive_ptr<Message> msg, Deliverable& strategy) {
    std::string exchangeName = msg->getExchangeName();
    //TODO: the following should be hidden behind message (using MessageAdapter or similar)

    if (msg->isA<MessageTransferBody>()) {
        // Do not replace the delivery-properties.exchange if it is is already set.
        // This is used internally (by the cluster) to force the exchange name on a message.
        // The client library ensures this is always empty for messages from normal clients.
        if (!msg->hasProperties<DeliveryProperties>() || msg->getProperties<DeliveryProperties>()->getExchange().empty())
            msg->getProperties<DeliveryProperties>()->setExchange(exchangeName);
        msg->setTimestamp(getSession().getBroker().getExpiryPolicy());
    }
    if (!cacheExchange || cacheExchange->getName() != exchangeName){
        cacheExchange = session.getBroker().getExchanges().get(exchangeName);
    }

    /* verify the userid if specified: */
    std::string id =
    	msg->hasProperties<MessageProperties>() ? msg->getProperties<MessageProperties>()->getUserId() : nullstring;

    if (authMsg &&  !id.empty() && id != userID )
    {
        QPID_LOG(debug, "authorised user id : " << userID << " but user id in message declared as " << id);
        throw UnauthorizedAccessException(QPID_MSG("authorised user id : " << userID << " but user id in message declared as " << id));
    }

    if (acl && acl->doTransferAcl())
    {
        if (!acl->authorise(getSession().getConnection().getUserId(),acl::ACT_PUBLISH,acl::OBJ_EXCHANGE,exchangeName, msg->getRoutingKey() ))
            throw NotAllowedException(QPID_MSG(getSession().getConnection().getUserId() << " cannot publish to " <<
                                               exchangeName << " with routing-key " << msg->getRoutingKey()));
    }

    cacheExchange->route(strategy, msg->getRoutingKey(), msg->getApplicationHeaders());

    if (!strategy.delivered) {
        //TODO:if discard-unroutable, just drop it
        //TODO:else if accept-mode is explicit, reject it 
        //else route it to alternate exchange
        if (cacheExchange->getAlternate()) {
            cacheExchange->getAlternate()->route(strategy, msg->getRoutingKey(), msg->getApplicationHeaders());
        }
        if (!strategy.delivered) {
            msg->destroy();
        }
    }

}

void SemanticState::requestDispatch()
{    
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        requestDispatch(*(i->second));
    }
}

void SemanticState::requestDispatch(ConsumerImpl& c)
{    
    if(c.isBlocked())
        outputTasks.activateOutput();
}

void SemanticState::complete(DeliveryRecord& delivery)
{    
    ConsumerImplMap::iterator i = consumers.find(delivery.getTag());
    if (i != consumers.end()) {
        i->second->complete(delivery);
    }
}

void SemanticState::ConsumerImpl::complete(DeliveryRecord& delivery)
{
    if (!delivery.isComplete()) {
        delivery.complete();
        if (windowing) {
            if (msgCredit != 0xFFFFFFFF) msgCredit++;
            if (byteCredit != 0xFFFFFFFF) byteCredit += delivery.getCredit();
        }
    }
}

void SemanticState::recover(bool requeue)
{
    if(requeue){
        //take copy and clear unacked as requeue may result in redelivery to this session
        //which will in turn result in additions to unacked
        DeliveryRecords copy = unacked;
        unacked.clear();
        for_each(copy.rbegin(), copy.rend(), mem_fun_ref(&DeliveryRecord::requeue));
    }else{
        for_each(unacked.begin(), unacked.end(), boost::bind(&DeliveryRecord::redeliver, _1, this));        
        //unconfirmed messages re redelivered and therefore have their
        //id adjusted, confirmed messages are not and so the ordering
        //w.r.t id is lost
        unacked.sort();
    }
}

void SemanticState::deliver(DeliveryRecord& msg, bool sync)
{
    return deliveryAdapter.deliver(msg, sync);
}

SemanticState::ConsumerImpl& SemanticState::find(const std::string& destination)
{
    ConsumerImplMap::iterator i = consumers.find(destination);
    if (i == consumers.end()) {
        throw NotFoundException(QPID_MSG("Unknown destination " << destination));
    } else {
        return *(i->second);
    }
}

void SemanticState::setWindowMode(const std::string& destination)
{
    find(destination).setWindowMode();
}

void SemanticState::setCreditMode(const std::string& destination)
{
    find(destination).setCreditMode();
}

void SemanticState::addByteCredit(const std::string& destination, uint32_t value)
{
    ConsumerImpl& c = find(destination);
    c.addByteCredit(value);
    requestDispatch(c);
}


void SemanticState::addMessageCredit(const std::string& destination, uint32_t value)
{
    ConsumerImpl& c = find(destination);
    c.addMessageCredit(value);
    requestDispatch(c);
}

void SemanticState::flush(const std::string& destination)
{
    find(destination).flush();
}


void SemanticState::stop(const std::string& destination)
{
    find(destination).stop();
}

void SemanticState::ConsumerImpl::setWindowMode()
{
    windowing = true;
}

void SemanticState::ConsumerImpl::setCreditMode()
{
    windowing = false;
}

void SemanticState::ConsumerImpl::addByteCredit(uint32_t value)
{
    if (byteCredit != 0xFFFFFFFF) {
        if (value == 0xFFFFFFFF) byteCredit = value;
        else byteCredit += value;
    }
}

void SemanticState::ConsumerImpl::addMessageCredit(uint32_t value)
{
    if (msgCredit != 0xFFFFFFFF) {
        if (value == 0xFFFFFFFF) msgCredit = value;
        else msgCredit += value;
    }
}

bool SemanticState::ConsumerImpl::haveCredit()
{
    if (msgCredit) {
        return true;
    } else {
        blocked = true;
        return false;
    }
}

void SemanticState::ConsumerImpl::flush()
{
    while(haveCredit() && queue->dispatch(shared_from_this()))
        ;
    stop();
}

void SemanticState::ConsumerImpl::stop()
{
    msgCredit = 0;
    byteCredit = 0;
}

Queue::shared_ptr SemanticState::getQueue(const string& name) const {
    Queue::shared_ptr queue;
    if (name.empty()) {
        throw NotAllowedException(QPID_MSG("No queue name specified."));
    } else {
        queue = session.getBroker().getQueues().find(name);
        if (!queue)
            throw NotFoundException(QPID_MSG("Queue not found: "<<name));
    }
    return queue;
}

AckRange SemanticState::findRange(DeliveryId first, DeliveryId last)
{   
    return DeliveryRecord::findRange(unacked, first, last);
}

void SemanticState::acquire(DeliveryId first, DeliveryId last, DeliveryIds& acquired)
{
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, AcquireFunctor(acquired));
}

void SemanticState::release(DeliveryId first, DeliveryId last, bool setRedelivered)
{
    AckRange range = findRange(first, last);
    //release results in the message being added to the head so want
    //to release in reverse order to keep the original transfer order
    DeliveryRecords::reverse_iterator start(range.end);
    DeliveryRecords::reverse_iterator end(range.start);
    for_each(start, end, boost::bind(&DeliveryRecord::release, _1, setRedelivered));
}

void SemanticState::reject(DeliveryId first, DeliveryId last)
{
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, mem_fun_ref(&DeliveryRecord::reject));
    //need to remove the delivery records as well
    unacked.erase(range.start, range.end);
}

bool SemanticState::ConsumerImpl::hasOutput() {
    return queue->checkForMessages(shared_from_this());
}

bool SemanticState::ConsumerImpl::doOutput()
{
    return haveCredit() && queue->dispatch(shared_from_this());
}

void SemanticState::ConsumerImpl::enableNotify()
{
    Mutex::ScopedLock l(lock);
    notifyEnabled = true;
}

void SemanticState::ConsumerImpl::disableNotify()
{
    Mutex::ScopedLock l(lock);
    notifyEnabled = false;
}

bool SemanticState::ConsumerImpl::isNotifyEnabled() const {
    Mutex::ScopedLock l(lock);
    return notifyEnabled;
}

void SemanticState::ConsumerImpl::notify()
{
    //TODO: alter this, don't want to hold locks across external
    //calls; for now its is required to protect the notify() from
    //having part of the object chain of the invocation being
    //concurrently deleted
    Mutex::ScopedLock l(lock);
    if (notifyEnabled) parent->outputTasks.activateOutput();
}


void SemanticState::accepted(DeliveryId first, DeliveryId last)
{
    AckRange range = findRange(first, last);
    if (txBuffer.get()) {
        //in transactional mode, don't dequeue or remove, just
        //maintain set of acknowledged messages:
        accumulatedAck.add(first, last);
        
        if (dtxBuffer.get()) {
            //if enlisted in a dtx, copy the relevant slice from
            //unacked and record it against that transaction
            TxOp::shared_ptr txAck(new DtxAck(accumulatedAck, unacked));
            accumulatedAck.clear();
            dtxBuffer->enlist(txAck);    

            //mark the relevant messages as 'ended' in unacked
            for_each(range.start, range.end, mem_fun_ref(&DeliveryRecord::setEnded));

            //if the messages are already completed, they can be
            //removed from the record
            unacked.remove_if(mem_fun_ref(&DeliveryRecord::isRedundant));

        }
    } else {
        for_each(range.start, range.end, boost::bind(&DeliveryRecord::accept, _1, (TransactionContext*) 0));
        unacked.remove_if(mem_fun_ref(&DeliveryRecord::isRedundant));
    }
}

void SemanticState::completed(DeliveryId first, DeliveryId last)
{
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, boost::bind(&SemanticState::complete, this, _1));
    unacked.remove_if(mem_fun_ref(&DeliveryRecord::isRedundant));
    requestDispatch();
}

void SemanticState::attached()
{
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        i->second->enableNotify();
    }
}

void SemanticState::detached()
{
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        i->second->disableNotify();
    }
}

}} // namespace qpid::broker
