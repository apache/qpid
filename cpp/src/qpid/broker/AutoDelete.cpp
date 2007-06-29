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
#include "AutoDelete.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"

using namespace qpid::broker;
using namespace qpid::sys;

AutoDelete::AutoDelete(QueueRegistry* const _registry, uint32_t _water_mark)
    : registry(_registry), high_water_mark(_water_mark), water_mark(0) { }

void AutoDelete::add(Queue::shared_ptr const queue){
     	Mutex::ScopedLock l(lock);
    	queues.push(queue);
}

Queue::shared_ptr const AutoDelete::pop(){
    Queue::shared_ptr next;
    Mutex::ScopedLock l(lock);
    if(!queues.empty()){
        next = queues.front();
	queues.pop();
    }
    return next;
}

void AutoDelete::clean(){
    if (water_mark++ < high_water_mark)
    	return;
    water_mark =0;
    cleanNow();
}


void AutoDelete::cleanNow(){
    Queue::shared_ptr seen;
    for(Queue::shared_ptr q = pop(); q; q = pop()){
        if(seen == q){
            add(q);
            break;
        }else if(q->canAutoDelete()){
            std::string name(q->getName());
            registry->destroy(name);
            QPID_LOG(info, "Auto-deleted queue named " << name);
        }else{
            add(q);
            if(!seen) seen = q;
        }
    }
    
}




