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

#include "BrokerQueue.h"
#include "MessageStore.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"
#include <iostream>
#include "QueueRegistry.h"

using namespace qpid::broker;
using namespace qpid::sys;
using namespace qpid::framing;
using boost::format;

Queue::Queue(const string& _name, uint32_t _autodelete, 
             MessageStore* const _store,
             const ConnectionToken* const _owner) :

    name(_name), 
    autodelete(_autodelete),
    store(_store),
    owner(_owner), 
    queueing(false),
    dispatching(false),
    next(0),
    lastUsed(0),
    exclusive(0),
    persistenceId(0)
{
    if(autodelete) lastUsed = now()/TIME_MSEC;
}

Queue::~Queue(){}

void Queue::deliver(Message::shared_ptr& msg){
    enqueue(0, msg);
    process(msg);
}

void Queue::recover(Message::shared_ptr& msg){
    push(msg);
    if (store && msg->expectedContentSize() != msg->encodedContentSize()) {
        //content has not been loaded, need to ensure that lazy loading mode is set:
        //TODO: find a nicer way to do this
        msg->releaseContent(store);
    }
}

void Queue::process(Message::shared_ptr& msg){
    Mutex::ScopedLock locker(lock);
    if(queueing || !dispatch(msg)){
        push(msg);
    }
}

bool Queue::dispatch(Message::shared_ptr& msg){
    if(consumers.empty()){
        return false;
    }else if(exclusive){
        if(!exclusive->deliver(msg)){
            std::cout << "WARNING: Dropping undeliverable message from queue with exclusive consumer." << std::endl;
        }
        return true;
    }else{
        //deliver to next consumer
        next = next % consumers.size();
        Consumer* c = consumers[next];
        int start = next;
        while(c){
            next++;
            if(c->deliver(msg)) return true;            

            next = next % consumers.size();
            c = next == start ? 0 : consumers[next];            
        }
        return false;
    }
}

bool Queue::startDispatching(){
    Mutex::ScopedLock locker(lock);
    if(queueing && !dispatching){
        dispatching = true;
        return true;
    }else{
        return false;
    }
}

void Queue::dispatch(){
    bool proceed = startDispatching();
    while(proceed){
        Mutex::ScopedLock locker(lock);
        if(!messages.empty() && dispatch(messages.front())){
            pop();
        }else{
            dispatching = false;
            proceed = false;
            queueing = !messages.empty();
        }
    }
}

void Queue::consume(Consumer* c, bool requestExclusive){
    Mutex::ScopedLock locker(lock);
    if(exclusive) 
        throw ChannelException(
            403, format("Queue '%s' has an exclusive consumer."
                        " No more consumers allowed.") % getName());
    if(requestExclusive) {
        if(!consumers.empty())
            throw ChannelException(
                403, format("Queue '%s' already has conumers."
                            "Exclusive access denied.") %getName());
        exclusive = c;
    }
    if(autodelete && consumers.empty()) lastUsed = 0;
    consumers.push_back(c);
}

void Queue::cancel(Consumer* c){
    Mutex::ScopedLock locker(lock);
    Consumers::iterator i = std::find(consumers.begin(), consumers.end(), c);
    if (i != consumers.end()) 
        consumers.erase(i);
    if(autodelete && consumers.empty()) lastUsed = now()*TIME_MSEC;
    if(exclusive == c) exclusive = 0;
}

Message::shared_ptr Queue::dequeue(){
    Mutex::ScopedLock locker(lock);
    Message::shared_ptr msg;
    if(!messages.empty()){
        msg = messages.front();
        pop();
    }
    return msg;
}

uint32_t Queue::purge(){
    Mutex::ScopedLock locker(lock);
    int count = messages.size();
    while(!messages.empty()) pop();
    return count;
}

void Queue::pop(){
    if (policy.get()) policy->dequeued(messages.front()->contentSize());
    messages.pop();    
}

void Queue::push(Message::shared_ptr& msg){
    queueing = true;
    messages.push(msg);
    if (policy.get()) {
        policy->enqueued(msg->contentSize());
        if (policy->limitExceeded()) {
            msg->releaseContent(store);
        }
    }
}

uint32_t Queue::getMessageCount() const{
    Mutex::ScopedLock locker(lock);
    return messages.size();
}

uint32_t Queue::getConsumerCount() const{
    Mutex::ScopedLock locker(lock);
    return consumers.size();
}

bool Queue::canAutoDelete() const{
    Mutex::ScopedLock locker(lock);
    return lastUsed && (now()*TIME_MSEC - lastUsed > autodelete);
}

void Queue::enqueue(TransactionContext* ctxt, Message::shared_ptr& msg)
{
    if (msg->isPersistent() && store) {
        store->enqueue(ctxt, *msg.get(), *this);
    }
}

void Queue::dequeue(TransactionContext* ctxt, Message::shared_ptr& msg)
{
    if (msg->isPersistent() && store) {
        store->dequeue(ctxt, *msg.get(), *this);
    }
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
    if (store) {
        store->destroy(*this);
    }
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
    buffer.putFieldTable(settings);
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
    buffer.getFieldTable(result.first->settings);
    result.first->configure(result.first->settings);
    return result.first;
}

