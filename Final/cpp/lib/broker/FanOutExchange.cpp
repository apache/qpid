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
#include <FanOutExchange.h>
#include <ExchangeBinding.h>
#include <algorithm>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

FanOutExchange::FanOutExchange(const std::string& _name) : Exchange(_name) {}

void FanOutExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* args){
    Mutex::ScopedLock locker(lock);
    // Add if not already present.
    Queue::vector::iterator i = std::find(bindings.begin(), bindings.end(), queue);
    if (i == bindings.end()) {
        bindings.push_back(queue);
        queue->bound(new ExchangeBinding(this, queue, routingKey, args));
    }
}

void FanOutExchange::unbind(Queue::shared_ptr queue, const string& /*routingKey*/, const FieldTable* /*args*/){
    Mutex::ScopedLock locker(lock);
    Queue::vector::iterator i = std::find(bindings.begin(), bindings.end(), queue);
    if (i != bindings.end()) {
        bindings.erase(i);
        // TODO aconway 2006-09-14: What about the ExchangeBinding object?
        // Don't we have to verify routingKey/args match?
    }
}

void FanOutExchange::route(Deliverable& msg, const string& /*routingKey*/, const FieldTable* /*args*/){
    Mutex::ScopedLock locker(lock);
    for(Queue::vector::iterator i = bindings.begin(); i != bindings.end(); ++i){
        msg.deliverTo(*i);
    }
}

FanOutExchange::~FanOutExchange() {}

const std::string FanOutExchange::typeName("fanout");
