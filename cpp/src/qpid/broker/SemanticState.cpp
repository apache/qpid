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
#include "qpid/QpidError.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"

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

SemanticState::SemanticState(DeliveryAdapter& da, SessionState& ss)
    : session(ss),
      deliveryAdapter(da),
      prefetchSize(0),
      prefetchCount(0),
      tagGenerator("sgen"),
      dtxSelected(false),
      accumulatedAck(0),
      flowActive(true)
{
    outstanding.reset();
}

SemanticState::~SemanticState() {
    consumers.clear();
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
    queue->consume(c.get(), exclusive);//may throw exception
    consumers.insert(tagInOut, c.release());
}

void SemanticState::cancel(const string& tag){
    // consumers is a ptr_map so erase will delete the consumer
    // which will call cancel.
    ConsumerImplMap::iterator i = consumers.find(tag);
    if (i != consumers.end()) {
        consumers.erase(i); 
        //should cancel all unacked messages for this consumer so that
        //they are not redelivered on recovery
        Mutex::ScopedLock locker(deliveryLock);   
        for_each(unacked.begin(), unacked.end(), boost::bind(mem_fun_ref(&DeliveryRecord::cancel), _1, tag));
        
    }
}


void SemanticState::startTx()
{
    txBuffer = TxBuffer::shared_ptr(new TxBuffer());
}

void SemanticState::commit(MessageStore* const store)
{
    if (!txBuffer) throw ConnectionException(503, "Session has not been selected for use with transactions");

    TxOp::shared_ptr txAck(new TxAck(accumulatedAck, unacked));
    txBuffer->enlist(txAck);
    if (txBuffer->commitLocal(store)) {
        accumulatedAck.clear();
    }
}

void SemanticState::rollback()
{
    if (!txBuffer) throw ConnectionException(503, "Session has not been selected for use with transactions");

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
        throw ConnectionException(503, "Session has not been selected for use with dtx");
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
        throw ConnectionException(503, boost::format("xid %1% not associated with this session") % xid);
    }
    if (dtxBuffer->getXid() != xid) {
        throw ConnectionException(503, boost::format("xid specified on start was %1%, but %2% specified on end") 
                                  % dtxBuffer->getXid() % xid);
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
        throw ConnectionException(503, boost::format("xid specified on start was %1%, but %2% specified on suspend") 
                                  % dtxBuffer->getXid() % xid);
    }
    txBuffer.reset();//ops on this session no longer transactional

    checkDtxTimeout();
    dtxBuffer->setSuspended(true);
}

void SemanticState::resumeDtx(const std::string& xid)
{
    if (dtxBuffer->getXid() != xid) {
        throw ConnectionException(503, boost::format("xid specified on start was %1%, but %2% specified on resume") 
                                  % dtxBuffer->getXid() % xid);
    }
    if (!dtxBuffer->isSuspended()) {
        throw ConnectionException(503, boost::format("xid %1% not suspended")% xid);
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

bool SemanticState::checkPrefetch(Message::shared_ptr& msg)
{
    Mutex::ScopedLock locker(deliveryLock);
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
    blocked(false), 
    windowing(true), 
    msgCredit(0), 
    byteCredit(0) {}

bool SemanticState::ConsumerImpl::deliver(QueuedMessage& msg)
{
    if (nolocal &&
        &parent->getSession().getConnection() == msg.payload->getPublisher()) {
        return false;
    } else {
        if (!checkCredit(msg.payload) || !parent->flowActive || (ackExpected && !parent->checkPrefetch(msg.payload))) {
            blocked = true;
        } else {
            blocked = false;

            Mutex::ScopedLock locker(parent->deliveryLock);

            DeliveryId deliveryTag =
                parent->deliveryAdapter.deliver(msg.payload, token);
            if (windowing || ackExpected) {
                parent->record(DeliveryRecord(msg, queue, name, token, deliveryTag, acquire, !ackExpected));
            }
        }
        return !blocked;
    }
}

bool SemanticState::ConsumerImpl::checkCredit(Message::shared_ptr& msg)
{
    Mutex::ScopedLock l(lock);
    if (msgCredit == 0 || (byteCredit != 0xFFFFFFFF && byteCredit < msg->getRequiredCredit())) {
        QPID_LOG(debug, "Not enough credit for '" << name  << "' on " << parent 
                 << ", bytes: " << byteCredit << " msgs: " << msgCredit);
        return false;
    } else {
        uint32_t originalMsgCredit = msgCredit;
        uint32_t originalByteCredit = byteCredit;        

        if (msgCredit != 0xFFFFFFFF) {
            msgCredit--;
        }
        if (byteCredit != 0xFFFFFFFF) {
            byteCredit -= msg->getRequiredCredit();
        }
        QPID_LOG(debug, "Credit available for '" << name << "' on " << parent
                 << ", was " << " bytes: " << originalByteCredit << " msgs: " << originalMsgCredit
                 << " now bytes: " << byteCredit << " msgs: " << msgCredit);
        return true;
    }
}

SemanticState::ConsumerImpl::~ConsumerImpl() {
    cancel();
}

void SemanticState::ConsumerImpl::cancel()
{
    if(queue) {
        queue->cancel(this);
        if (queue->canAutoDelete() && !queue->hasExclusiveOwner()) {            
            parent->getSession().getBroker().getQueues().destroyIf(
                queue->getName(), 
                boost::bind(boost::mem_fn(&Queue::canAutoDelete), queue));
        }
    }
}

void SemanticState::ConsumerImpl::requestDispatch()
{
    if(blocked)
        queue->requestDispatch(this);
}

void SemanticState::handle(Message::shared_ptr msg) {
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

void SemanticState::route(Message::shared_ptr msg, Deliverable& strategy) {
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
        Mutex::ScopedLock locker(deliveryLock);//need to synchronize with possible concurrent delivery
        
        ack_iterator start = cumulative ? unacked.begin() : 
            find_if(unacked.begin(), unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::matchOrAfter), first));
        ack_iterator end = start;
        
        if (cumulative || first != last) {
            //need to find end (position it just after the last record in range)
            end = find_if(start, unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::after), last));
        } else {
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
    for_each(consumers.begin(), consumers.end(), boost::bind(&ConsumerImpl::requestDispatch, _1));
}

void SemanticState::acknowledged(const DeliveryRecord& delivery)
{    
    delivery.subtractFrom(outstanding);
    ConsumerImplMap::iterator i = consumers.find(delivery.getTag());
    if (i != consumers.end()) {
        i->acknowledged(delivery);
    }
}

void SemanticState::ConsumerImpl::acknowledged(const DeliveryRecord& delivery)
{
    if (windowing) {
        Mutex::ScopedLock l(lock);
        if (msgCredit != 0xFFFFFFFF) msgCredit++;
        if (byteCredit != 0xFFFFFFFF) delivery.updateByteCredit(byteCredit);
    }
}

void SemanticState::recover(bool requeue)
{
    Mutex::ScopedLock locker(deliveryLock);//need to synchronize with possible concurrent delivery

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
        Mutex::ScopedLock locker(deliveryLock);
        DeliveryId myDeliveryTag = deliveryAdapter.deliver(msg.payload, token);
        if(ackExpected){
            unacked.push_back(DeliveryRecord(msg, queue, myDeliveryTag));
        }
        return true;
    }else{
        return false;
    }
}

DeliveryId SemanticState::redeliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token)
{
    Mutex::ScopedLock locker(deliveryLock);
    return deliveryAdapter.deliver(msg, token);
}

void SemanticState::flow(bool active)
{
    Mutex::ScopedLock locker(deliveryLock);
    bool requestDelivery(!flowActive && active);
    flowActive = active;
    if (requestDelivery) {
        //there may be messages that can be now be delivered
        std::for_each(consumers.begin(), consumers.end(), boost::bind(&ConsumerImpl::requestDispatch, _1));
    }
}


SemanticState::ConsumerImpl& SemanticState::find(const std::string& destination)
{
    ConsumerImplMap::iterator i = consumers.find(destination);
    if (i == consumers.end()) {
        throw NotFoundException(QPID_MSG("Unknown destination " << destination));
    } else {
        return *i;
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
    find(destination).addByteCredit(value);
}


void SemanticState::addMessageCredit(const std::string& destination, uint32_t value)
{
    find(destination).addMessageCredit(value);
}

void SemanticState::flush(const std::string& destination)
{
    ConsumerImpl& c = find(destination);
    c.flush();
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
    {
        Mutex::ScopedLock l(lock);
        if (byteCredit != 0xFFFFFFFF) {
            byteCredit += value;
        }
    }
    requestDispatch();
}

void SemanticState::ConsumerImpl::addMessageCredit(uint32_t value)
{
    {
        Mutex::ScopedLock l(lock);
        if (msgCredit != 0xFFFFFFFF) {
            msgCredit += value;
        }
    }
    requestDispatch();
}

void SemanticState::ConsumerImpl::flush()
{
    //need to prevent delivery after requestDispatch returns but
    //before credit is reduced to zero
    FlushCompletion completion(*this);
    queue->flush(completion);
    completion.wait();
}

void SemanticState::ConsumerImpl::stop()
{
    Mutex::ScopedLock l(lock);
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

void SemanticState::acquire(DeliveryId first, DeliveryId last, std::vector<DeliveryId>& acquired)
{
    Mutex::ScopedLock locker(deliveryLock);
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, AcquireFunctor(acquired));
}

void SemanticState::release(DeliveryId first, DeliveryId last)
{
    Mutex::ScopedLock locker(deliveryLock);
    AckRange range = findRange(first, last);
    //release results in the message being added to the head so want
    //to release in reverse order to keep the original transfer order
    DeliveryRecords::reverse_iterator start(range.end);
    DeliveryRecords::reverse_iterator end(range.start);
    for_each(start, end, mem_fun_ref(&DeliveryRecord::release));
}

void SemanticState::reject(DeliveryId first, DeliveryId last)
{
    Mutex::ScopedLock locker(deliveryLock);
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, mem_fun_ref(&DeliveryRecord::reject));
    //need to remove the delivery records as well
    unacked.erase(range.start, range.end);
}


void SemanticState::FlushCompletion::wait()
{
    Monitor::ScopedLock locker(lock);
    while (!complete) lock.wait();
}

void SemanticState::FlushCompletion::completed()
{
    Monitor::ScopedLock locker(lock);
    consumer.stop();
    complete = true;
    lock.notifyAll();
}

}} // namespace qpid::broker
