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
#include "qpid/broker/Queue.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/concurrent/Monitor.h"
#include <iostream>

using namespace qpid::broker;
using namespace qpid::concurrent;

Queue::Queue(const string& _name, bool _durable, u_int32_t _autodelete, 
             MessageStore* const _store,
             const ConnectionToken* const _owner) :

    name(_name), 
    autodelete(_autodelete),
    durable(_durable), 
    store(_store),
    owner(_owner), 
    queueing(false),
    dispatching(false),
    next(0),
    lastUsed(0),
    exclusive(0)
{
    if(autodelete) lastUsed = apr_time_as_msec(apr_time_now());
}

Queue::~Queue(){
    for(Binding* b = bindings.front(); !bindings.empty(); b = bindings.front()){
        b->cancel();
        bindings.pop();
    }
}

void Queue::bound(Binding* b){
    bindings.push(b);
}

void Queue::deliver(Message::shared_ptr& msg){
    enqueue(msg, 0);
    process(msg);
}

void Queue::process(Message::shared_ptr& msg){
    Locker locker(lock);
    if(queueing || !dispatch(msg)){
        queueing = true;
        messages.push(msg);
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
    Locker locker(lock);
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
        Locker locker(lock);
        if(!messages.empty() && dispatch(messages.front())){
            messages.pop();
        }else{
            dispatching = false;
            proceed = false;
            queueing = !messages.empty();
        }
    }
}

void Queue::consume(Consumer* c, bool requestExclusive){
    Locker locker(lock);
    if(exclusive) throw ExclusiveAccessException();
    if(requestExclusive){
        if(!consumers.empty()) throw ExclusiveAccessException();
        exclusive = c;
    }

    if(autodelete && consumers.empty()) lastUsed = 0;
    consumers.push_back(c);
}

void Queue::cancel(Consumer* c){
    Locker locker(lock);
    consumers.erase(find(consumers.begin(), consumers.end(), c));
    if(autodelete && consumers.empty()) lastUsed = apr_time_as_msec(apr_time_now());
    if(exclusive == c) exclusive = 0;
}

Message::shared_ptr Queue::dequeue(){
    Locker locker(lock);
    Message::shared_ptr msg;
    if(!messages.empty()){
        msg = messages.front();
        messages.pop();
    }
    return msg;
}

u_int32_t Queue::purge(){
    Locker locker(lock);
    int count = messages.size();
    while(!messages.empty()) messages.pop();
    return count;
}

u_int32_t Queue::getMessageCount() const{
    Locker locker(lock);
    return messages.size();
}

u_int32_t Queue::getConsumerCount() const{
    Locker locker(lock);
    return consumers.size();
}

bool Queue::canAutoDelete() const{
    Locker locker(lock);
    return lastUsed && ((apr_time_as_msec(apr_time_now()) - lastUsed) > autodelete);
}

void Queue::enqueue(Message::shared_ptr& msg, const string * const xid){
    if(store){
        store->enqueue(msg, name, xid);
    }
}

void Queue::dequeue(Message::shared_ptr& msg, const string * const xid){
    if(store){
        store->dequeue(msg, name, xid);
    }
}
