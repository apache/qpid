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
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Exchange.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/framing/reply_exceptions.h"
#include "qmf/org/apache/qpid/broker/EventQueueDeclare.h"
#include "qmf/org/apache/qpid/broker/EventQueueDelete.h"
#include <sstream>
#include <assert.h>

namespace _qmf = qmf::org::apache::qpid::broker;
using namespace qpid::broker;
using namespace qpid::sys;
using std::string;

QueueRegistry::QueueRegistry(Broker* b)
{
    setBroker(b);
}

QueueRegistry::~QueueRegistry(){}

std::pair<Queue::shared_ptr, bool>
QueueRegistry::declare(const string& name, const QueueSettings& settings,
                       boost::shared_ptr<Exchange> alternate,
                       bool recovering/*true if this declare is a
                                        result of recovering queue
                                        definition from persistent
                                        record*/,
                       const OwnershipToken* owner,
                       std::string connectionId,
                       std::string userId)
{
    std::pair<Queue::shared_ptr, bool> result;
    {
        RWlock::ScopedWlock locker(lock);
        QueueMap::iterator i =  queues.find(name);
        if (i == queues.end()) {
            Queue::shared_ptr queue = create(name, settings);
            // Allow ConfigurationObserver to modify settings before storing the message.
            if (getBroker()) getBroker()->getConfigurationObservers().queueCreate(queue);
            //Move this to factory also?
            if (alternate)
                queue->setAlternateExchange(alternate);//need to do this *before* create
            if (!recovering) {
                //create persistent record if required
                queue->create();
            }
            queues[name] = queue;
            result = std::pair<Queue::shared_ptr, bool>(queue, true);
        } else {
            result = std::pair<Queue::shared_ptr, bool>(i->second, false);
        }
        if (getBroker() && getBroker()->getManagementAgent()) {
            getBroker()->getManagementAgent()->raiseEvent(
                _qmf::EventQueueDeclare(
                    connectionId, userId, name,
                    settings.durable, owner, settings.autodelete,
                    alternate ? alternate->getName() : string(),
                    result.first->getSettings().asMap(),
                    result.second ? "created" : "existing"));
        }
    }
    return result;
}

void QueueRegistry::destroy(
    const string& name, const string& connectionId, const string& userId)
{
    Queue::shared_ptr q;
    {
        qpid::sys::RWlock::ScopedWlock locker(lock);
        QueueMap::iterator i = queues.find(name);
        if (i != queues.end()) {
            q = i->second;
            queues.erase(i);
            if (getBroker()) {
                // NOTE: queueDestroy and raiseEvent must be called with the
                // lock held in order to ensure events are generated
                // in the correct order.
                getBroker()->getConfigurationObservers().queueDestroy(q);
                if (getBroker()->getManagementAgent())
                    getBroker()->getManagementAgent()->raiseEvent(
                        _qmf::EventQueueDelete(connectionId, userId, name));
            }
        }
    }
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

Queue::shared_ptr QueueRegistry::get(const string& name) {
    Queue::shared_ptr q = find(name);
    if (!q) throw framing::NotFoundException(QPID_MSG("Queue not found: "<<name));
    return q;
}

void QueueRegistry::setStore (MessageStore* _store)
{
    QueueFactory::setStore(_store);
}

MessageStore* QueueRegistry::getStore() const
{
    return QueueFactory::getStore();
}

void QueueRegistry::setParent(qpid::management::Manageable* _parent)
{
    QueueFactory::setParent(_parent);
}
