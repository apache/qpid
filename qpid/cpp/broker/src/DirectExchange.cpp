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
#include "DirectExchange.h"
#include "ExchangeBinding.h"
#include <iostream>

using namespace qpid::broker;
using namespace qpid::framing;

DirectExchange::DirectExchange(const string& _name) : Exchange(_name) {

}

void DirectExchange::bind(Queue::shared_ptr queue, const string& routingKey, FieldTable* args){
    lock.acquire();
    std::vector<Queue::shared_ptr>& queues(bindings[routingKey]);
    std::vector<Queue::shared_ptr>::iterator i = find(queues.begin(), queues.end(), queue);
    if(i == queues.end()){
        bindings[routingKey].push_back(queue);
        queue->bound(new ExchangeBinding(this, queue, routingKey, args));
    }
    lock.release();
}

void DirectExchange::unbind(Queue::shared_ptr queue, const string& routingKey, FieldTable* /*args*/){
    lock.acquire();
    std::vector<Queue::shared_ptr>& queues(bindings[routingKey]);

    std::vector<Queue::shared_ptr>::iterator i = find(queues.begin(), queues.end(), queue);
    if(i < queues.end()){
        queues.erase(i);
        if(queues.empty()){
            bindings.erase(routingKey);
        }
    }
    lock.release();
}

void DirectExchange::route(Message::shared_ptr& msg, const string& routingKey, FieldTable* /*args*/){
    lock.acquire();
    std::vector<Queue::shared_ptr>& queues(bindings[routingKey]);
    int count(0);
    for(std::vector<Queue::shared_ptr>::iterator i = queues.begin(); i != queues.end(); i++, count++){
        (*i)->deliver(msg);
    }
    if(!count){
        std::cout << "WARNING: DirectExchange " << getName() << " could not route message with key " << routingKey << std::endl;
    }
    lock.release();
}

DirectExchange::~DirectExchange(){

}


const std::string DirectExchange::typeName("direct");
