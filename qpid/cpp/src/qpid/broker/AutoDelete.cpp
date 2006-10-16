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
#include "qpid/broker/AutoDelete.h"

using namespace qpid::broker;

AutoDelete::AutoDelete(QueueRegistry* const _registry, u_int32_t _period) : registry(_registry), 
                                                                            period(_period), 
                                                                            stopped(true), 
                                                                            runner(0){}

void AutoDelete::add(Queue::shared_ptr const queue){
    lock.acquire();
    queues.push(queue);
    lock.release();
}

Queue::shared_ptr const AutoDelete::pop(){
    Queue::shared_ptr next;
    lock.acquire();
    if(!queues.empty()){
        next = queues.front();
	queues.pop();
    }
    lock.release();
    return next;
}

void AutoDelete::process(){
    Queue::shared_ptr seen;
    for(Queue::shared_ptr q = pop(); q; q = pop()){
        if(seen == q){
            add(q);
            break;
        }else if(q->canAutoDelete()){
            std::string name(q->getName());
            registry->destroy(name);
            std::cout << "INFO: Auto-deleted queue named " << name << std::endl;
        }else{
            add(q);
            if(!seen) seen = q;
        }
    }
}

void AutoDelete::run(){
    monitor.acquire();
    while(!stopped){
        process();
        monitor.wait(period);
    }
    monitor.release();
}

void AutoDelete::start(){
    monitor.acquire();
    if(stopped){
        runner = factory.create(this);
        stopped = false;
        monitor.release();
        runner->start();
    }else{
        monitor.release();
    }
}

void AutoDelete::stop(){
    monitor.acquire();
    if(!stopped){
        stopped = true;
        monitor.notify();
        monitor.release();
        runner->join();
        delete runner;
    }else{
        monitor.release();
    }
}
