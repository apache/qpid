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
#include "QueueRegistry.h"
#include "ManagementAgent.h"
#include "ManagementObjectQueue.h"
#include <sstream>
#include <assert.h>

using namespace qpid::broker;
using namespace qpid::sys;

QueueRegistry::QueueRegistry(MessageStore* const _store) :
    counter(1), store(_store) {}

QueueRegistry::~QueueRegistry(){}

std::pair<Queue::shared_ptr, bool>
QueueRegistry::declare(const string& declareName, bool durable, 
                       bool autoDelete, const ConnectionToken* owner)
{
    RWlock::ScopedWlock locker(lock);
    string name = declareName.empty() ? generateName() : declareName;
    assert(!name.empty());
    QueueMap::iterator i =  queues.find(name);

    if (i == queues.end()) {
	Queue::shared_ptr queue(new Queue(name, autoDelete, durable ? store : 0, owner));
	queues[name] = queue;

	if (managementAgent){
	    ManagementObjectQueue::shared_ptr mgmtObject(new ManagementObjectQueue (name, durable, autoDelete));

	    queue->setMgmt (mgmtObject);
	    managementAgent->addObject(dynamic_pointer_cast<ManagementObject>(mgmtObject));
	}
	
	return std::pair<Queue::shared_ptr, bool>(queue, true);
    } else {
	return std::pair<Queue::shared_ptr, bool>(i->second, false);
    }
}

void QueueRegistry::destroy(const string& name){
    RWlock::ScopedWlock locker(lock);

    if (managementAgent){
	ManagementObjectQueue::shared_ptr mgmtObject;
	QueueMap::iterator i = queues.find(name);

	if (i != queues.end()){
	    mgmtObject = i->second->getMgmt ();
	    managementAgent->deleteObject (dynamic_pointer_cast<ManagementObject>(mgmtObject));
	}
    }

    queues.erase(name);
}

Queue::shared_ptr QueueRegistry::find(const string& name){
    RWlock::ScopedRlock locker(lock);
    QueueMap::iterator i = queues.find(name);
    
    if (i == queues.end()) {
	return Queue::shared_ptr();
    } else {
	return i->second;
    }
}

string QueueRegistry::generateName(){
    string name;
    do {
	std::stringstream ss;
	ss << "tmp_" << counter++;
	name = ss.str();
	// Thread safety: Private function, only called with lock held
	// so this is OK.
    } while(queues.find(name) != queues.end());
    return name;
}

MessageStore* const QueueRegistry::getStore() const {
    return store;
}

void QueueRegistry::setManagementAgent (ManagementAgent::shared_ptr agent)
{
    managementAgent = agent;
}

ManagementAgent::shared_ptr QueueRegistry::getManagementAgent (void)
{
    return managementAgent;
}

