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
#include "DirectExchange.h"
#include <iostream>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

DirectExchange::DirectExchange(const string& _name) : Exchange(_name) {}
DirectExchange::DirectExchange(const std::string& _name, bool _durable, const FieldTable& _args) : Exchange(_name, _durable, _args) {}

bool DirectExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable*){
    RWlock::ScopedWlock l(lock);
    std::vector<Queue::shared_ptr>& queues(bindings[routingKey]);
    std::vector<Queue::shared_ptr>::iterator i = find(queues.begin(), queues.end(), queue);
    if (i == queues.end()) {
        bindings[routingKey].push_back(queue);
        return true;
    } else{
        return false;
    }
}

bool DirectExchange::unbind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* /*args*/){
    RWlock::ScopedWlock l(lock);
    std::vector<Queue::shared_ptr>& queues(bindings[routingKey]);

    std::vector<Queue::shared_ptr>::iterator i = find(queues.begin(), queues.end(), queue);
    if (i < queues.end()) {
        queues.erase(i);
        if(queues.empty()){
            bindings.erase(routingKey);
        }
        return true;
    } else {
        return false;
    }
}

void DirectExchange::route(Deliverable& msg, const string& routingKey, const FieldTable* /*args*/){
    RWlock::ScopedRlock l(lock);
    std::vector<Queue::shared_ptr>& queues(bindings[routingKey]);
    int count(0);
    for(std::vector<Queue::shared_ptr>::iterator i = queues.begin(); i != queues.end(); i++, count++){
        msg.deliverTo(*i);
    }
    if(!count){
        QPID_LOG(warning, "DirectExchange " << getName() << " could not route message with key " << routingKey);
    }
}


bool DirectExchange::isBound(Queue::shared_ptr queue, const string* const routingKey, const FieldTable* const)
{
    if (routingKey) {
        Bindings::iterator i = bindings.find(*routingKey);
        return i != bindings.end() && (!queue || find(i->second.begin(), i->second.end(), queue) != i->second.end());
    } else if (!queue) {
        //if no queue or routing key is specified, just report whether any bindings exist
        return bindings.size() > 0;
    } else {
        for (Bindings::iterator i = bindings.begin(); i != bindings.end(); i++) {
            if (find(i->second.begin(), i->second.end(), queue) != i->second.end()) {
                return true;
            }
        }
        return false;
    }
}

DirectExchange::~DirectExchange(){

}


const std::string DirectExchange::typeName("direct");
