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

#include "qpid/log/Statement.h"
#include "qpid/framing/reply_exceptions.h"
#include "Broker.h"
#include "Queue.h"
#include "Exchange.h"
#include "DeliverableMessage.h"
#include "MessageStore.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"
#include <iostream>
#include <boost/bind.hpp>
#include "QueueRegistry.h"


using namespace qpid::broker;
using namespace qpid::sys;
using namespace qpid::framing;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;

Queue::Queue(const string& _name, bool _autodelete, 
             MessageStore* const _store,
             const ConnectionToken* const _owner,
             Manageable* parent) :

    name(_name), 
    autodelete(_autodelete),
    store(_store),
    owner(_owner), 
    next(0),
    persistenceId(0),
    serializer(false),
    dispatchCallback(*this)
{
    if (parent != 0)
    {
        mgmtObject = management::Queue::shared_ptr
            (new management::Queue (this, parent, _name, _store != 0, _autodelete, 0));

        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        agent->addObject (mgmtObject);
    }
}

Queue::~Queue()
{
    if (mgmtObject.get () != 0)
        mgmtObject->resourceDestroy ();
}

void Queue::notifyDurableIOComplete()
{
    // signal SemanticHander to ack completed dequeues
    // then dispatch to ack...
  serializer.execute(dispatchCallback);
}


void Queue::deliver(intrusive_ptr<Message>& msg){
    if (msg->isImmediate() && getConsumerCount() == 0) {
        if (alternateExchange) {
            DeliverableMessage deliverable(msg);
            alternateExchange->route(deliverable, msg->getRoutingKey(), msg->getApplicationHeaders());
        }
    } else {


        // if no store then mark as enqueued
        if (!enqueue(0, msg)){
            push(msg);
            msg->enqueueComplete();
            if (mgmtObject != 0) {
                mgmtObject->inc_msgTotalEnqueues ();
                mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
                mgmtObject->inc_msgDepth ();
                mgmtObject->inc_byteDepth (msg->contentSize ());
            }
        }else {
            if (mgmtObject != 0) {
                mgmtObject->inc_msgTotalEnqueues ();
                mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
                mgmtObject->inc_msgDepth ();
                mgmtObject->inc_byteDepth (msg->contentSize ());
                mgmtObject->inc_msgPersistEnqueues ();
                mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
            }
            push(msg);
        }
        QPID_LOG(debug, "Message " << msg << " enqueued on " << name << "[" << this << "]");
	serializer.execute(dispatchCallback);
    }
}


void Queue::recover(intrusive_ptr<Message>& msg){
    push(msg);
    msg->enqueueComplete(); // mark the message as enqueued
    if (mgmtObject != 0) {
        mgmtObject->inc_msgTotalEnqueues ();
        mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
        mgmtObject->inc_msgPersistEnqueues ();
        mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
        mgmtObject->inc_msgDepth ();
        mgmtObject->inc_byteDepth (msg->contentSize ());
    }

    if (store && !msg->isContentLoaded()) {
        //content has not been loaded, need to ensure that lazy loading mode is set:
        //TODO: find a nicer way to do this
        msg->releaseContent(store);
    }
}

void Queue::process(intrusive_ptr<Message>& msg){
    push(msg);
    if (mgmtObject != 0) {
        mgmtObject->inc_msgTotalEnqueues ();
        mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
        mgmtObject->inc_msgTxnEnqueues ();
        mgmtObject->inc_byteTxnEnqueues (msg->contentSize ());
        mgmtObject->inc_msgDepth ();
        mgmtObject->inc_byteDepth (msg->contentSize ());
        if (msg->isPersistent ()) {
            mgmtObject->inc_msgPersistEnqueues ();
            mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
        }
    }
    serializer.execute(dispatchCallback);
   
}

void Queue::requeue(const QueuedMessage& msg){
    {
        Mutex::ScopedLock locker(messageLock);
        msg.payload->enqueueComplete(); // mark the message as enqueued
        messages.push_front(msg);
    }
    serializer.execute(dispatchCallback);
}

bool Queue::acquire(const QueuedMessage& msg) {
    Mutex::ScopedLock locker(messageLock);
    for (Messages::iterator i = messages.begin(); i != messages.end(); i++) {
        if (i->position == msg.position) {
            messages.erase(i);
            return true;
        }
    }
    return false;
}

void Queue::requestDispatch(Consumer::ptr c){
    if (!c || c->preAcquires()) {
      serializer.execute(dispatchCallback);
    } else {
        DispatchFunctor f(*this, c);
        serializer.execute(f);
    }
}

void Queue::flush(DispatchCompletion& completion)
{
    DispatchFunctor f(*this, &completion);
    serializer.execute(f);
}

/**
 * Return true if the message can be excluded. This is currently the
 * case if the queue has an exclusive consumer that will never want
 * the message, or if the queue is exclusive to a single connection
 * and has a single consumer (covers the JMS topic case).
 */
bool Queue::exclude(intrusive_ptr<Message> msg)
{
    RWlock::ScopedWlock locker(consumerLock);
    if (exclusive) {
        return !exclusive->filter(msg);
    } else if (hasExclusiveOwner() && acquirers.size() == 1) {
        return !acquirers[0]->filter(msg);
    } else {
        return false;
    }
}

Consumer::ptr Queue::allocate()
{
    RWlock::ScopedWlock locker(consumerLock);
 
    if (acquirers.empty()) {
        return Consumer::ptr();
    } else if (exclusive){
        return exclusive;
    } else {
        next = next % acquirers.size();
        return acquirers[next++];
    }
}

bool Queue::dispatch(QueuedMessage& msg)
{
    QPID_LOG(info, "Dispatch message " << msg.position << " from queue " << name);
    //additions to the acquirers will result in a separate dispatch
    //request, so won't result in anyone being missed
    uint counter = getAcquirerCount();
    Consumer::ptr c = allocate();
    while (c && counter--){
        if (c->deliver(msg)) {
            return true;
        } else {
            c = allocate();
        }
    }
    return false;
}

bool Queue::getNextMessage(QueuedMessage& msg)
{
    Mutex::ScopedLock locker(messageLock);
    if (messages.empty()) { 
        QPID_LOG(debug, "No messages to dispatch on queue '" << name << "'");
        return false;
    } else {
        msg = messages.front();
        return true;
    }
}

void Queue::dispatch()
{
     QueuedMessage msg(this);
     while (getNextMessage(msg) && msg.payload->isEnqueueComplete()){
         if (dispatch(msg)) {
             pop();
         } else if (exclude(msg.payload)) {
             pop();
             dequeue(0, msg.payload);
             QPID_LOG(debug, "Message " << msg.payload << " filtered out of " << name << "[" << this << "]");        
         } else {            
             break;
         }        
     }
     serviceAllBrowsers();
}

void Queue::serviceAllBrowsers()
{
     Consumers copy;
     {
         RWlock::ScopedRlock locker(consumerLock);
         if (browsers.empty()) return;//shortcut
         copy = browsers;
     }
     for (Consumers::iterator i = copy.begin(); i != copy.end(); i++) {
         serviceBrowser(*i);
     }
}

void Queue::serviceBrowser(Consumer::ptr browser)
{
    QueuedMessage msg(this);
    while (seek(msg, browser->position) && browser->deliver(msg)) {
        browser->position = msg.position;
    }
}

bool Queue::seek(QueuedMessage& msg, const framing::SequenceNumber& position) {
    Mutex::ScopedLock locker(messageLock);
    if (!messages.empty() && messages.back().position > position) {
        if (position < messages.front().position) {
            msg = messages.front();
            return true;
        } else {        
            uint index = (position - messages.front().position) + 1;
            if (index < messages.size()) {
                msg = messages[index];
                return true;
            } 
        }
    }
    return false;
}

void Queue::consume(Consumer::ptr c, bool requestExclusive){
    RWlock::ScopedWlock locker(consumerLock);
    if(exclusive) {
        throw AccessRefusedException(
            QPID_MSG("Queue " << getName() << " has an exclusive consumer. No more consumers allowed."));
    }
    if(requestExclusive) {
        if(acquirers.empty() && browsers.empty()) {
            exclusive = c;
        } else {
            throw AccessRefusedException(
                QPID_MSG("Queue " << getName() << " already has consumers. Exclusive access denied."));
        }
    }
    if (c->preAcquires()) {
        acquirers.push_back(c);
    } else {
        Mutex::ScopedLock locker(messageLock);
        if (messages.empty()) {
            c->position = SequenceNumber(sequence.getValue() - 1);
        } else {
            c->position = SequenceNumber(messages.front().position.getValue() - 1);
        }
        browsers.push_back(c);
    }

    if (mgmtObject != 0){
        mgmtObject->inc_consumers ();
    }
}

void Queue::cancel(Consumer::ptr c){
    RWlock::ScopedWlock locker(consumerLock);
    if (c->preAcquires()) {
        cancel(c, acquirers);
    } else {
        cancel(c, browsers);
    }
    if (mgmtObject != 0){
        mgmtObject->dec_consumers ();
    }
    if(exclusive == c) exclusive.reset();
}

void Queue::cancel(Consumer::ptr c, Consumers& consumers)
{
    Consumers::iterator i = std::find(consumers.begin(), consumers.end(), c);
    if (i != consumers.end()) 
        consumers.erase(i);
}

QueuedMessage Queue::dequeue(){
    Mutex::ScopedLock locker(messageLock);
    QueuedMessage msg(this);

    if(!messages.empty()){
        msg = messages.front();
        pop();
        if (mgmtObject != 0){
            mgmtObject->inc_msgTotalDequeues ();
            //mgmtObject->inc_byteTotalDequeues (msg->contentSize ());
            mgmtObject->dec_msgDepth ();
            //mgmtObject->dec_byteDepth (msg->contentSize ());
            if (0){//msg->isPersistent ()) {
                mgmtObject->inc_msgPersistDequeues ();
                //mgmtObject->inc_bytePersistDequeues (msg->contentSize ());
            }
        }
    }
    return msg;
}

uint32_t Queue::purge(){
    Mutex::ScopedLock locker(messageLock);
    int count = messages.size();
    while(!messages.empty()) pop();
    return count;
}

void Queue::pop(){
    Mutex::ScopedLock locker(messageLock);
    if (policy.get()) policy->dequeued(messages.front().payload->contentSize());
    messages.pop_front();
}

void Queue::push(intrusive_ptr<Message>& msg){
    Mutex::ScopedLock locker(messageLock);
    messages.push_back(QueuedMessage(this, msg, ++sequence));
    if (policy.get()) {
        policy->enqueued(msg->contentSize());
        if (policy->limitExceeded()) {
            msg->releaseContent(store);
        }
    }
}

/** function only provided for unit tests, or code not in critical message path */
uint32_t Queue::getMessageCount() const{
    Mutex::ScopedLock locker(messageLock);
  
    uint32_t count =0;
    for ( Messages::const_iterator i = messages.begin(); i != messages.end(); ++i ) {
        if ( i->payload->isEnqueueComplete() ) count ++;
    }
    
    return count;
}

uint32_t Queue::getConsumerCount() const{
    RWlock::ScopedRlock locker(consumerLock);
    return acquirers.size() + browsers.size();
}

uint32_t Queue::getAcquirerCount() const{
    RWlock::ScopedRlock locker(consumerLock);
    return acquirers.size();
}

bool Queue::canAutoDelete() const{
    RWlock::ScopedRlock locker(consumerLock);
    return autodelete && acquirers.empty() && browsers.empty();
}

// return true if store exists, 
bool Queue::enqueue(TransactionContext* ctxt, intrusive_ptr<Message> msg)
{
    if (msg->isPersistent() && store) {
        msg->enqueueAsync(this, store); //increment to async counter -- for message sent to more than one queue
        store->enqueue(ctxt, *msg.get(), *this);
        return true;
    }
    return false;
}

// return true if store exists, 
bool Queue::dequeue(TransactionContext* ctxt, intrusive_ptr<Message> msg)
{
    if (msg->isPersistent() && store) {
        msg->dequeueAsync(this, store); //increment to async counter -- for message sent to more than one queue
        store->dequeue(ctxt, *msg.get(), *this);
        return true;
    }
    return false;
}


namespace 
{
    const std::string qpidMaxSize("qpid.max_size");
    const std::string qpidMaxCount("qpid.max_count");
}

void Queue::create(const FieldTable& _settings)
{
    settings = _settings;
    //TODO: hold onto settings and persist them as part of encode
    //      in fact settings should be passed in on construction
    if (store) {
        store->create(*this);
    }
    configure(_settings);
}

void Queue::configure(const FieldTable& _settings)
{
    std::auto_ptr<QueuePolicy> _policy(new QueuePolicy(_settings));
    if (_policy->getMaxCount() || _policy->getMaxSize()) 
        setPolicy(_policy);
}

void Queue::destroy()
{
    if (alternateExchange.get()) {
        Mutex::ScopedLock locker(messageLock);
        while(!messages.empty()){
            DeliverableMessage msg(messages.front().payload);
            alternateExchange->route(msg, msg.getMessage().getRoutingKey(),
                                     msg.getMessage().getApplicationHeaders());
            pop();
        }
        alternateExchange->decAlternateUsers();
    }

    if (store) {
        store->destroy(*this);
    }
}

void Queue::bound(const string& exchange, const string& key, const FieldTable& args)
{
    bindings.add(exchange, key, args);
}

void Queue::unbind(ExchangeRegistry& exchanges, Queue::shared_ptr shared_ref)
{
    bindings.unbind(exchanges, shared_ref);
}

void Queue::setPolicy(std::auto_ptr<QueuePolicy> _policy)
{
    policy = _policy;
}

const QueuePolicy* const Queue::getPolicy()
{
    return policy.get();
}

uint64_t Queue::getPersistenceId() const 
{ 
    return persistenceId; 
}

void Queue::setPersistenceId(uint64_t _persistenceId) const
{ 
    persistenceId = _persistenceId; 
}

void Queue::encode(framing::Buffer& buffer) const 
{
    buffer.putShortString(name);
    buffer.put(settings);
}

uint32_t Queue::encodedSize() const
{
    return name.size() + 1/*short string size octet*/ + settings.size();
}

Queue::shared_ptr Queue::decode(QueueRegistry& queues, framing::Buffer& buffer)
{
    string name;
    buffer.getShortString(name);
    std::pair<Queue::shared_ptr, bool> result = queues.declare(name, true);
    buffer.get(result.first->settings);
    result.first->configure(result.first->settings);
    return result.first;
}


void Queue::setAlternateExchange(boost::shared_ptr<Exchange> exchange)
{
    alternateExchange = exchange;
}

boost::shared_ptr<Exchange> Queue::getAlternateExchange()
{
    return alternateExchange;
}

void Queue::tryAutoDelete(Broker& broker, Queue::shared_ptr queue)
{
    if (broker.getQueues().destroyIf(queue->getName(), 
                                     boost::bind(boost::mem_fn(&Queue::canAutoDelete), queue))) {
        queue->unbind(broker.getExchanges(), queue);
        queue->destroy();
    }

}

bool Queue::isExclusiveOwner(const ConnectionToken* const o) const 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    return o == owner; 
}

void Queue::releaseExclusiveOwnership() 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    owner = 0; 
}

bool Queue::setExclusiveOwner(const ConnectionToken* const o) 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    if (owner) {
        return false;
    } else {
        owner = o; 
        return true;
    }
}

bool Queue::hasExclusiveOwner() const 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    return owner != 0; 
}

bool Queue::hasExclusiveConsumer() const 
{ 
    return exclusive; 
}

void Queue::DispatchFunctor::operator()()
{
    try {
        if (consumer && !consumer->preAcquires()) {
            queue.serviceBrowser(consumer);                        
        }else{
            queue.dispatch(); 
        }
    } catch (const std::exception& e) {
        QPID_LOG(error, "Exception on dispatch: " << e.what());
    }
    
    if (sync) sync->completed();
}

ManagementObject::shared_ptr Queue::GetManagementObject (void) const
{
    return dynamic_pointer_cast<ManagementObject> (mgmtObject);
}

Manageable::status_t Queue::ManagementMethod (uint32_t /*methodId*/,
                                              Args&    /*args*/)
{
    return Manageable::STATUS_OK;
}
