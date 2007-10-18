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

#include <boost/format.hpp>

#include "qpid/log/Statement.h"
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
using boost::format;

Queue::Queue(const string& _name, bool _autodelete, 
             MessageStore* const _store,
             const ConnectionToken* const _owner) :

    name(_name), 
    autodelete(_autodelete),
    store(_store),
    owner(_owner), 
    next(0),
    persistenceId(0),
    serializer(false),
    dispatchCallback(*this)
{
}

Queue::~Queue(){}

void Queue::notifyDurableIOComplete()
{
    // signal SemanticHander to ack completed dequeues
    // then dispatch to ack...
    serializer.execute(dispatchCallback);
}


void Queue::deliver(Message::shared_ptr& msg){
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
	}else {
            push(msg);
	}
	QPID_LOG(debug, "Message " << msg << " enqueued on " << name << "[" << this << "]");
        serializer.execute(dispatchCallback);
    }
}


void Queue::recover(Message::shared_ptr& msg){
    push(msg);
    msg->enqueueComplete(); // mark the message as enqueued
    if (store && !msg->isContentLoaded()) {
        //content has not been loaded, need to ensure that lazy loading mode is set:
        //TODO: find a nicer way to do this
        msg->releaseContent(store);
    }
}

void Queue::process(Message::shared_ptr& msg){
 
    push(msg);
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
        serviceBrowser(c);
    }
}

void Queue::flush(DispatchCompletion& completion)
{
    DispatchFunctor f(*this, &completion);
    serializer.execute(f);
}

Consumer::ptr Queue::allocate()
{
    RWlock::ScopedWlock locker(consumerLock);
 
    if(acquirers.empty()){
        return Consumer::ptr();
    }else if(exclusive){
        return exclusive;
    }else{
        next = next % acquirers.size();
        return acquirers[next++];
    }
}

bool Queue::dispatch(QueuedMessage& msg)
{
    Consumer::ptr c = allocate();
    Consumer::ptr first = c;
    while(c){
        if(c->deliver(msg)) {
            return true;            
        } else {
            c = allocate();
            if (c == first) { 
                break;
            }
        }
    }
    return false;
}

void Queue::dispatch(){
     QueuedMessage msg;
     while(true){
        {
	    Mutex::ScopedLock locker(messageLock);
	    if (messages.empty()) { 
                QPID_LOG(debug, "No messages to dispatch on queue '" << name << "'");
                break; 
            }
	    msg = messages.front();
	}
        if( msg.payload->isEnqueueComplete() && dispatch(msg) ) {
            pop();
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
         copy = browsers;
     }
     for (Consumers::iterator i = copy.begin(); i != copy.end(); i++) {
         serviceBrowser(*i);
     }
}

void Queue::serviceBrowser(Consumer::ptr browser)
{
    QueuedMessage msg;
    while (seek(msg, browser->position) && browser->deliver(msg)) {
        browser->position = msg.position;
    }
}

bool Queue::seek(QueuedMessage& msg, const framing::SequenceNumber& position) {
    Mutex::ScopedLock locker(messageLock);
    if (!messages.empty() && messages.back().position > position) {
        uint index = (position - messages.front().position) + 1;
        if (index < messages.size()) {
            msg = messages[index];
            return true;
        } 
    }
    return false;
}

void Queue::consume(Consumer::ptr c, bool requestExclusive){
    RWlock::ScopedWlock locker(consumerLock);
    if(exclusive) {
        throw ChannelException(
            403, format("Queue '%s' has an exclusive consumer."
                        " No more consumers allowed.") % getName());
    }
    if(requestExclusive) {
        if(acquirers.empty() && browsers.empty()) {
            exclusive = c;
        } else {
            throw ChannelException(
                403, format("Queue '%s' already has consumers."
                            "Exclusive access denied.") % getName());
        }
    }
    if (c->preAcquires()) {
        acquirers.push_back(c);
    } else {
        browsers.push_back(c);
    }
}

void Queue::cancel(Consumer::ptr c){
    RWlock::ScopedWlock locker(consumerLock);
    if (c->preAcquires()) {
        cancel(c, acquirers);
    } else {
        cancel(c, browsers);
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
    QueuedMessage msg;
    if(!messages.empty()){
        msg = messages.front();
        pop();
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

void Queue::push(Message::shared_ptr& msg){
    Mutex::ScopedLock locker(messageLock);
    messages.push_back(QueuedMessage(msg, ++sequence));
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

bool Queue::canAutoDelete() const{
    RWlock::ScopedRlock locker(consumerLock);
    return autodelete && acquirers.empty() && browsers.empty();
}

// return true if store exists, 
bool Queue::enqueue(TransactionContext* ctxt, Message::shared_ptr msg)
{
    if (msg->isPersistent() && store) {
	msg->enqueueAsync(); //increment to async counter -- for message sent to more than one queue
        store->enqueue(ctxt, *msg.get(), *this);
	return true;
    }
    return false;
}

// return true if store exists, 
bool Queue::dequeue(TransactionContext* ctxt, Message::shared_ptr msg)
{
    if (msg->isPersistent() && store) {
	msg->dequeueAsync(); //increment to async counter -- for message sent to more than one queue
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
