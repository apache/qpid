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

#include "Session.h"

#include "BrokerAdapter.h"
#include "BrokerQueue.h"
#include "Connection.h"
#include "DeliverableMessage.h"
#include "DtxAck.h"
#include "DtxTimeout.h"
#include "Message.h"
#include "SemanticHandler.h"
#include "SessionAdapter.h"
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

Session::Session(SessionAdapter& a, uint32_t t)
    : adapter(&a),
      broker(adapter->getConnection().broker),
      timeout(t),
      prefetchSize(0),
      prefetchCount(0),
      tagGenerator("sgen"),
      dtxSelected(false),
      accumulatedAck(0),
      flowActive(true)
{
    outstanding.reset();
    // FIXME aconway 2007-08-29: handler to get Session, not connection.
    std::auto_ptr<SemanticHandler> semantic(new SemanticHandler(*this));
    deliveryAdapter=semantic.get();
    // FIXME aconway 2007-08-31: Remove, workaround.
    semanticHandler=semantic.get();
    handlers.push_back(semantic.release());
    in = &handlers[0];
    out = &adapter->out;
    // FIXME aconway 2007-08-31: handlerupdater->sessionupdater,
    // create a SessionManager in the broker for all session related
    // stuff: suspended sessions, handler updaters etc.
    // FIXME aconway 2007-08-31: Shouldn't be passing channel ID
    broker.update(a.getChannel(), *this);       
}

Session::~Session() {
    close();
}

bool Session::exists(const string& consumerTag){
    return consumers.find(consumerTag) != consumers.end();
}

void Session::consume(DeliveryToken::shared_ptr token, string& tagInOut, 
                      Queue::shared_ptr queue, bool nolocal, bool acks, bool acquire,
                      bool exclusive, const FieldTable*)
{
    if(tagInOut.empty())
        tagInOut = tagGenerator.generate();
    std::auto_ptr<ConsumerImpl> c(new ConsumerImpl(this, token, tagInOut, queue, acks, nolocal, acquire));
    queue->consume(c.get(), exclusive);//may throw exception
    consumers.insert(tagInOut, c.release());
}

void Session::cancel(const string& tag){
    // consumers is a ptr_map so erase will delete the consumer
    // which will call cancel.
    ConsumerImplMap::iterator i = consumers.find(tag);
    if (i != consumers.end())
        consumers.erase(i); 
}

void Session::close()
{
    opened = false;
    consumers.clear();
    if (dtxBuffer.get()) {
        dtxBuffer->fail();
    }
    recover(true);
}

void Session::startTx()
{
    txBuffer = TxBuffer::shared_ptr(new TxBuffer());
}

void Session::commit(MessageStore* const store)
{
    if (!txBuffer) throw ConnectionException(503, "Session has not been selected for use with transactions");

    TxOp::shared_ptr txAck(new TxAck(accumulatedAck, unacked));
    txBuffer->enlist(txAck);
    if (txBuffer->commitLocal(store)) {
        accumulatedAck.clear();
    }
}

void Session::rollback()
{
    if (!txBuffer) throw ConnectionException(503, "Session has not been selected for use with transactions");

    txBuffer->rollback();
    accumulatedAck.clear();
}

void Session::selectDtx()
{
    dtxSelected = true;
}

void Session::startDtx(const std::string& xid, DtxManager& mgr, bool join)
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

void Session::endDtx(const std::string& xid, bool fail)
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

void Session::suspendDtx(const std::string& xid)
{
    if (dtxBuffer->getXid() != xid) {
        throw ConnectionException(503, boost::format("xid specified on start was %1%, but %2% specified on suspend") 
                                  % dtxBuffer->getXid() % xid);
    }
    txBuffer.reset();//ops on this session no longer transactional

    checkDtxTimeout();
    dtxBuffer->setSuspended(true);
}

void Session::resumeDtx(const std::string& xid)
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

void Session::checkDtxTimeout()
{
    if (dtxBuffer->isExpired()) {
        dtxBuffer.reset();
        throw DtxTimeoutException();
    }
}

void Session::record(const DeliveryRecord& delivery)
{
    unacked.push_back(delivery);
    delivery.addTo(outstanding);
}

bool Session::checkPrefetch(Message::shared_ptr& msg)
{
    Mutex::ScopedLock locker(deliveryLock);
    bool countOk = !prefetchCount || prefetchCount > unacked.size();
    bool sizeOk = !prefetchSize || prefetchSize > msg->contentSize() + outstanding.size || unacked.empty();
    return countOk && sizeOk;
}

Session::ConsumerImpl::ConsumerImpl(Session* _parent, 
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
    msgCredit(0xFFFFFFFF), 
    byteCredit(0xFFFFFFFF) {}

bool Session::ConsumerImpl::deliver(QueuedMessage& msg)
{
    if (nolocal && &parent->getAdapter()->getConnection() == msg.payload->getPublisher()) {
        return false;
    } else {
        if (!checkCredit(msg.payload) || !parent->flowActive || (ackExpected && !parent->checkPrefetch(msg.payload))) {
            blocked = true;
        } else {
            blocked = false;

            Mutex::ScopedLock locker(parent->deliveryLock);

            DeliveryId deliveryTag =
                parent->deliveryAdapter->deliver(msg.payload, token);
            if (ackExpected) {
                parent->record(DeliveryRecord(msg, queue, name, deliveryTag, acquire));
            }
        }
        return !blocked;
    }
}

bool Session::ConsumerImpl::checkCredit(Message::shared_ptr& msg)
{
    Mutex::ScopedLock l(lock);
    if (msgCredit == 0 || (byteCredit != 0xFFFFFFFF && byteCredit < msg->getRequiredCredit())) {
        return false;
    } else {
        if (msgCredit != 0xFFFFFFFF) {
            msgCredit--;
        }
        if (byteCredit != 0xFFFFFFFF) {
            byteCredit -= msg->getRequiredCredit();
        }
        return true;
    }
}

void Session::ConsumerImpl::redeliver(Message::shared_ptr& msg, DeliveryId deliveryTag) {
    Mutex::ScopedLock locker(parent->deliveryLock);
    parent->deliveryAdapter->redeliver(msg, token, deliveryTag);
}

Session::ConsumerImpl::~ConsumerImpl() {
    cancel();
}

void Session::ConsumerImpl::cancel()
{
    if(queue) {
        queue->cancel(this);
        if (queue->canAutoDelete()) {            
            parent->getAdapter()->getConnection().broker.getQueues().destroyIf(queue->getName(), 
                                                                               boost::bind(boost::mem_fn(&Queue::canAutoDelete), queue));
        }
    }
}

void Session::ConsumerImpl::requestDispatch()
{
    if(blocked)
        queue->requestDispatch(this);
}

void Session::handle(Message::shared_ptr msg) {
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

void Session::route(Message::shared_ptr msg, Deliverable& strategy) {
    std::string exchangeName = msg->getExchangeName();      
    if (!cacheExchange || cacheExchange->getName() != exchangeName){
        cacheExchange = getAdapter()->getConnection().broker.getExchanges().get(exchangeName);
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

void Session::ackCumulative(DeliveryId id)
{
    ack(id, id, true);
}

void Session::ackRange(DeliveryId first, DeliveryId last)
{
    ack(first, last, false);
}

void Session::ack(DeliveryId first, DeliveryId last, bool cumulative)
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

    for_each(start, end, boost::bind(&Session::acknowledged, this, _1));
    
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
    
    //if the prefetch limit had previously been reached, or credit
    //had expired in windowing mode there may be messages that can
    //be now be delivered
    for_each(consumers.begin(), consumers.end(), boost::bind(&ConsumerImpl::requestDispatch, _1));
}

void Session::acknowledged(const DeliveryRecord& delivery)
{
    delivery.subtractFrom(outstanding);
    ConsumerImplMap::iterator i = consumers.find(delivery.getConsumerTag());
    if (i != consumers.end()) {
        i->acknowledged(delivery);
    }
}

void Session::ConsumerImpl::acknowledged(const DeliveryRecord& delivery)
{
    if (windowing) {
        if (msgCredit != 0xFFFFFFFF) msgCredit++;
        if (byteCredit != 0xFFFFFFFF) delivery.updateByteCredit(byteCredit);
    }
}

void Session::recover(bool requeue)
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
    }
}

bool Session::get(DeliveryToken::shared_ptr token, Queue::shared_ptr queue, bool ackExpected)
{
    QueuedMessage msg = queue->dequeue();
    if(msg.payload){
        Mutex::ScopedLock locker(deliveryLock);
        DeliveryId myDeliveryTag = deliveryAdapter->deliver(msg.payload, token);
        if(ackExpected){
            unacked.push_back(DeliveryRecord(msg, queue, myDeliveryTag));
        }
        return true;
    }else{
        return false;
    }
}

void Session::deliver(Message::shared_ptr& msg, const string& consumerTag,
                      DeliveryId deliveryTag)
{
    ConsumerImplMap::iterator i = consumers.find(consumerTag);
    if (i != consumers.end()){
        i->redeliver(msg, deliveryTag);
    }
}

void Session::flow(bool active)
{
    Mutex::ScopedLock locker(deliveryLock);
    bool requestDelivery(!flowActive && active);
    flowActive = active;
    if (requestDelivery) {
        //there may be messages that can be now be delivered
        std::for_each(consumers.begin(), consumers.end(), boost::bind(&ConsumerImpl::requestDispatch, _1));
    }
}


Session::ConsumerImpl& Session::find(const std::string& destination)
{
    ConsumerImplMap::iterator i = consumers.find(destination);
    if (i == consumers.end()) {
        throw NotFoundException(QPID_MSG("Unknown destination " << destination));
    } else {
        return *i;
    }
}

void Session::setWindowMode(const std::string& destination)
{
    find(destination).setWindowMode();
}

void Session::setCreditMode(const std::string& destination)
{
    find(destination).setCreditMode();
}

void Session::addByteCredit(const std::string& destination, uint32_t value)
{
    find(destination).addByteCredit(value);
}


void Session::addMessageCredit(const std::string& destination, uint32_t value)
{
    find(destination).addMessageCredit(value);
}

void Session::flush(const std::string& destination)
{
    ConsumerImpl& c = find(destination);
    c.flush();
}


void Session::stop(const std::string& destination)
{
    find(destination).stop();
}

void Session::ConsumerImpl::setWindowMode()
{
    windowing = true;
}

void Session::ConsumerImpl::setCreditMode()
{
    windowing = false;
}

void Session::ConsumerImpl::addByteCredit(uint32_t value)
{
    byteCredit += value;
    requestDispatch();
}

void Session::ConsumerImpl::addMessageCredit(uint32_t value)
{
    msgCredit += value;
    requestDispatch();
}

void Session::ConsumerImpl::flush()
{
    queue->requestDispatch(this, true);
}

void Session::ConsumerImpl::stop()
{
    msgCredit = 0;
    byteCredit = 0;
}

Queue::shared_ptr Session::getQueue(const string& name) const {
    //Note: this can be removed soon as the default queue for sessions is scrapped in 0-10
    Queue::shared_ptr queue;
    if (name.empty()) {
        queue = getDefaultQueue();
        if (!queue)
            throw NotAllowedException(QPID_MSG("No queue name specified."));
    }
    else {
        queue = getBroker().getQueues().find(name);
        if (!queue)
            throw NotFoundException(QPID_MSG("Queue not found: "<<name));
    }
    return queue;
}

AckRange Session::findRange(DeliveryId first, DeliveryId last)
{    
    ack_iterator start = find_if(unacked.begin(), unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::matchOrAfter), first));
    ack_iterator end = start;
     
    if (first == last) {
        //just acked single element (move end past it)
        ++end;
    } else {
        //need to find end (position it just after the last record in range)
        end = find_if(start, unacked.end(), bind2nd(mem_fun_ref(&DeliveryRecord::after), last));
    }
    return AckRange(start, end);
}

void Session::acquire(DeliveryId first, DeliveryId last, std::vector<DeliveryId>& acquired)
{
    Mutex::ScopedLock locker(deliveryLock);
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, AcquireFunctor(acquired));
}

void Session::release(DeliveryId first, DeliveryId last)
{
    Mutex::ScopedLock locker(deliveryLock);
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, mem_fun_ref(&DeliveryRecord::release));
}

void Session::reject(DeliveryId first, DeliveryId last)
{
    Mutex::ScopedLock locker(deliveryLock);
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, mem_fun_ref(&DeliveryRecord::reject));
    //need to remove the delivery records as well
    unacked.erase(range.start, range.end);
}

}} // namespace qpid::broker
