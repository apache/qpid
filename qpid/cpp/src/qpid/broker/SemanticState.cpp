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
#include "BrokerAdapter.h"
#include "Queue.h"
#include "Connection.h"
#include "DeliverableMessage.h"
#include "DtxAck.h"
#include "DtxTimeout.h"
#include "Message.h"
#include "SemanticHandler.h"
#include "SessionHandler.h"
#include "TxAck.h"
#include "TxPublish.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/ptr_map.h"

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
using std::bind2nd;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
using namespace qpid::ptr_map;

SemanticState::SemanticState(DeliveryAdapter& da, SessionState& ss)
    : session(ss),
      deliveryAdapter(da),
      prefetchSize(0),
      prefetchCount(0),
      tagGenerator("sgen"),
      dtxSelected(false),
      accumulatedAck(0),
      flowActive(true),
      outputTasks(ss)
{
    outstanding.reset();
}

SemanticState::~SemanticState() {
    //cancel all consumers
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        cancel(*get_pointer(i));
    }

    if (dtxBuffer.get()) {
        dtxBuffer->fail();
    }
    recover(true);
}

bool SemanticState::exists(const string& consumerTag){
    return consumers.find(consumerTag) != consumers.end();
}

void SemanticState::consume(DeliveryToken::shared_ptr token, string& tagInOut, 
                      Queue::shared_ptr queue, bool nolocal, bool acks, bool acquire,
                      bool exclusive, const FieldTable*)
{
    if(tagInOut.empty())
        tagInOut = tagGenerator.generate();
    std::auto_ptr<ConsumerImpl> c(new ConsumerImpl(this, token, tagInOut, queue, acks, nolocal, acquire));
    queue->consume(*c, exclusive);//may throw exception
    outputTasks.addOutputTask(c.get());
    consumers.insert(tagInOut, c.release());
}

void SemanticState::cancel(const string& tag){
    ConsumerImplMap::iterator i = consumers.find(tag);
    if (i != consumers.end()) {
        cancel(*get_pointer(i));
        consumers.erase(i); 
        //should cancel all unacked messages for this consumer so that
        //they are not redelivered on recovery
        for_each(unacked.begin(), unacked.end(), boost::bind(mem_fun_ref(&DeliveryRecord::cancel), _1, tag));
        
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

    TxOp::shared_ptr txAck(new TxAck(accumulatedAck, unacked));
    txBuffer->enlist(txAck);
    if (txBuffer->commitLocal(store)) {
        accumulatedAck.clear();
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
        throw CommandInvalidException(QPID_MSG("xid " << xid << " not associated with this session"));
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
    delivery.addTo(outstanding);
}

bool SemanticState::checkPrefetch(intrusive_ptr<Message>& msg)
{
    bool countOk = !prefetchCount || prefetchCount > unacked.size();
    bool sizeOk = !prefetchSize || prefetchSize > msg->contentSize() + outstanding.size || unacked.empty();
    return countOk && sizeOk;
}

SemanticState::ConsumerImpl::ConsumerImpl(SemanticState* _parent, 
                                    DeliveryToken::shared_ptr _token,
                                    const string& _name, 
                                    Queue::shared_ptr _queue, 
                                    bool ack,
                                    bool _nolocal,
                                    bool _acquire
                                    ) : 
    Consumer(_acquire),
    parent(_parent), 
    token(_token), 
    name(_name), 
    queue(_queue), 
    ackExpected(ack), 
    nolocal(_nolocal),
    acquire(_acquire),
    blocked(true), 
    windowing(true), 
    msgCredit(0), 
    byteCredit(0) {}

bool SemanticState::ConsumerImpl::deliver(QueuedMessage& msg)
{
    if (parent->getSession().isAttached() && accept(msg.payload)) {
        allocateCredit(msg.payload);
        DeliveryId deliveryTag =
            parent->deliveryAdapter.deliver(msg, token);
        if (windowing || ackExpected) {
            parent->record(DeliveryRecord(msg, queue, name, token, deliveryTag, acquire, !ackExpected));
        } 
        if (acquire && !ackExpected) {
            queue->dequeue(0, msg.payload);
        }
        return true;
    } else {
        QPID_LOG(debug, "Failed to deliver message to '" << name << "' on " << parent);
        return false;
    }
}

bool SemanticState::ConsumerImpl::filter(intrusive_ptr<Message> msg)
{
    return !(nolocal &&
             &parent->getSession().getConnection() == msg->getPublisher());
}

bool SemanticState::ConsumerImpl::accept(intrusive_ptr<Message> msg)
{
    //TODO: remove the now redundant checks (channel.flow & basic|message.qos removed):
    blocked = !(filter(msg) && checkCredit(msg) && parent->flowActive && (!ackExpected || parent->checkPrefetch(msg)));
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

void SemanticState::cancel(ConsumerImpl& c)
{
    outputTasks.removeOutputTask(&c);
    Queue::shared_ptr queue = c.getQueue();
    if(queue) {
        queue->cancel(c);
        if (queue->canAutoDelete() && !queue->hasExclusiveOwner()) {            
            Queue::tryAutoDelete(getSession().getBroker(), queue);
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

void SemanticState::route(intrusive_ptr<Message> msg, Deliverable& strategy) {
    std::string exchangeName = msg->getExchangeName();      
    if (!cacheExchange || cacheExchange->getName() != exchangeName){
        cacheExchange = session.getConnection().broker.getExchanges().get(exchangeName);
    }

    cacheExchange->route(strategy, msg->getRoutingKey(), msg->getApplicationHeaders());

    if (!strategy.delivered) {
        //TODO:if reject-unroutable, then reject
        //else route to alternate exchange
        if (cacheExchange->getAlternate()) {
            cacheExchange->getAlternate()->route(strategy, msg->getRoutingKey(), msg->getApplicationHeaders());
        }
    }

}

void SemanticState::ackCumulative(DeliveryId id)
{
    ack(id, id, true);
}

void SemanticState::ackRange(DeliveryId first, DeliveryId last)
{
    ack(first, last, false);
}

void SemanticState::ack(DeliveryId first, DeliveryId last, bool cumulative)
{
    {
        ack_iterator start = cumulative ? unacked.begin() : 
            find_if(unacked.begin(), unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::matchOrAfter), first));
        ack_iterator end = start;
        
        if (cumulative || first != last) {
            //need to find end (position it just after the last record in range)
            end = find_if(start, unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::after), last));
        } else if (start != unacked.end()) {
            //just acked single element (move end past it)
            ++end;
        }
        
        for_each(start, end, boost::bind(&SemanticState::acknowledged, this, _1));
        
        if (txBuffer.get()) {
            //in transactional mode, don't dequeue or remove, just
            //maintain set of acknowledged messages:
            accumulatedAck.update(cumulative ? accumulatedAck.mark : first, last);
            
            if (dtxBuffer.get()) {
                //if enlisted in a dtx, remove the relevant slice from
                //unacked and record it against that transaction
                TxOp::shared_ptr txAck(new DtxAck(accumulatedAck, unacked));
                accumulatedAck.clear();
                dtxBuffer->enlist(txAck);    
            }
        } else {
            for_each(start, end, bind2nd(mem_fun_ref(&DeliveryRecord::dequeue), 0));
            unacked.erase(start, end);
        }
    }//end of lock scope for delivery lock (TODO this is ugly, make it prettier)
    
    //if the prefetch limit had previously been reached, or credit
    //had expired in windowing mode there may be messages that can
    //be now be delivered
    requestDispatch();
}

void SemanticState::requestDispatch()
{    
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        requestDispatch(*get_pointer(i));
    }
}

void SemanticState::requestDispatch(ConsumerImpl& c)
{    
    if(c.isBlocked()) {
        c.doOutput();
    }
}

void SemanticState::acknowledged(const DeliveryRecord& delivery)
{    
    delivery.subtractFrom(outstanding);
    ConsumerImplMap::iterator i = consumers.find(delivery.getTag());
    if (i != consumers.end()) {
        get_pointer(i)->acknowledged(delivery);
    }
}

void SemanticState::ConsumerImpl::acknowledged(const DeliveryRecord& delivery)
{
    if (windowing) {
        if (msgCredit != 0xFFFFFFFF) msgCredit++;
        if (byteCredit != 0xFFFFFFFF) delivery.updateByteCredit(byteCredit);
    }
}

void SemanticState::recover(bool requeue)
{
    if(requeue){
        outstanding.reset();
        //take copy and clear unacked as requeue may result in redelivery to this session
        //which will in turn result in additions to unacked
        std::list<DeliveryRecord> copy = unacked;
        unacked.clear();
        for_each(copy.rbegin(), copy.rend(), mem_fun_ref(&DeliveryRecord::requeue));
    }else{
        for_each(unacked.begin(), unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::redeliver), this));        
        //unconfirmed messages re redelivered and therefore have their
        //id adjusted, confirmed messages are not and so the ordering
        //w.r.t id is lost
        unacked.sort();
    }
}

bool SemanticState::get(DeliveryToken::shared_ptr token, Queue::shared_ptr queue, bool ackExpected)
{
    QueuedMessage msg = queue->dequeue();
    if(msg.payload){
        DeliveryId myDeliveryTag = deliveryAdapter.deliver(msg, token);
        if(ackExpected){
            unacked.push_back(DeliveryRecord(msg, queue, myDeliveryTag));
        }
        return true;
    }else{
        return false;
    }
}

DeliveryId SemanticState::redeliver(QueuedMessage& msg, DeliveryToken::shared_ptr token)
{
    return deliveryAdapter.deliver(msg, token);
}

void SemanticState::flow(bool active)
{
    bool requestDelivery(!flowActive && active);
    flowActive = active;
    if (requestDelivery) {
        //there may be messages that can be now be delivered
        requestDispatch();
    }
}


SemanticState::ConsumerImpl& SemanticState::find(const std::string& destination)
{
    ConsumerImplMap::iterator i = consumers.find(destination);
    if (i == consumers.end()) {
        throw NotFoundException(QPID_MSG("Unknown destination " << destination));
    } else {
        return *get_pointer(i);
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
        byteCredit += value;
    }
}

void SemanticState::ConsumerImpl::addMessageCredit(uint32_t value)
{
    if (msgCredit != 0xFFFFFFFF) {
        msgCredit += value;
    }
}

void SemanticState::ConsumerImpl::flush()
{
    while(queue->dispatch(*this));
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
    ack_iterator start = find_if(unacked.begin(), unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::matchOrAfter), first));
    ack_iterator end = start;
     
    if (start != unacked.end()) {
        if (first == last) {
            //just acked single element (move end past it)
            ++end;
        } else {
            //need to find end (position it just after the last record in range)
            end = find_if(start, unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::after), last));
        }
    }
    return AckRange(start, end);
}

void SemanticState::acquire(DeliveryId first, DeliveryId last, DeliveryIds& acquired)
{
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, AcquireFunctor(acquired));
}

void SemanticState::release(DeliveryId first, DeliveryId last)
{
    AckRange range = findRange(first, last);
    //release results in the message being added to the head so want
    //to release in reverse order to keep the original transfer order
    DeliveryRecords::reverse_iterator start(range.end);
    DeliveryRecords::reverse_iterator end(range.start);
    for_each(start, end, mem_fun_ref(&DeliveryRecord::release));
}

void SemanticState::reject(DeliveryId first, DeliveryId last)
{
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, mem_fun_ref(&DeliveryRecord::reject));
    //need to remove the delivery records as well
    unacked.erase(range.start, range.end);
}

bool SemanticState::ConsumerImpl::doOutput()
{
    //TODO: think through properly
    return queue->dispatch(*this);
}

void SemanticState::ConsumerImpl::notify()
{
    //TODO: think through properly
    parent->outputTasks.activateOutput();
}

}} // namespace qpid::broker
