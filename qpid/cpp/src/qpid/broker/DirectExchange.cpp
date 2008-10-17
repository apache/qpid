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
using qpid::management::Manageable;
namespace _qmf = qmf::org::apache::qpid::broker;

DirectExchange::DirectExchange(const string& _name, Manageable* _parent) : Exchange(_name, _parent)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

DirectExchange::DirectExchange(const std::string& _name, bool _durable,
                               const FieldTable& _args, Manageable* _parent) :
    Exchange(_name, _durable, _args, _parent)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

bool DirectExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable*){
    {
        Mutex::ScopedLock l(lock);
        Binding::shared_ptr b(new Binding (routingKey, queue, this));
        if (bindings[routingKey].add_unless(b, MatchQueue(queue))) {
            if (mgmtExchange != 0) {
                mgmtExchange->inc_bindingCount();
                ((_qmf::Queue*) queue->GetManagementObject())->inc_bindingCount();
            }
        } else {
            return false;
        }
    }
    routeIVE();
    return true;
}

bool DirectExchange::unbind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* /*args*/){
    Mutex::ScopedLock l(lock);
    if (bindings[routingKey].remove_if(MatchQueue(queue))) {
        if (mgmtExchange != 0) {
            mgmtExchange->dec_bindingCount();
            ((_qmf::Queue*) queue->GetManagementObject())->dec_bindingCount();
        }
        return true;
    } else {
        return false;
    }
}

void DirectExchange::route(Deliverable& msg, const string& routingKey, const FieldTable* /*args*/){
    PreRoute pr(msg, this);
    Queues::ConstPtr p;
    {
        Mutex::ScopedLock l(lock);
        p = bindings[routingKey].snapshot();
    }
    int count(0);

    if (p) {
        for(std::vector<Binding::shared_ptr>::const_iterator i = p->begin(); i != p->end(); i++, count++) {
            msg.deliverTo((*i)->queue);
            if ((*i)->mgmtBinding != 0)
                (*i)->mgmtBinding->inc_msgMatched ();
        }
    }

    if(!count){
        QPID_LOG(warning, "DirectExchange " << getName() << " could not route message with key " << routingKey);
        if (mgmtExchange != 0) {
            mgmtExchange->inc_msgDrops  ();
            mgmtExchange->inc_byteDrops (msg.contentSize ());
        }
    } else {
        if (mgmtExchange != 0) {
            mgmtExchange->inc_msgRoutes  (count);
            mgmtExchange->inc_byteRoutes (count * msg.contentSize ());
        }
    }

    if (mgmtExchange != 0) {
        mgmtExchange->inc_msgReceives  ();
        mgmtExchange->inc_byteReceives (msg.contentSize ());
    }
}


bool DirectExchange::isBound(Queue::shared_ptr queue, const string* const routingKey, const FieldTable* const)
{
    Mutex::ScopedLock l(lock);
    if (routingKey) {
        Bindings::iterator i = bindings.find(*routingKey);

        if (i == bindings.end())
            return false;
        if (!queue)
            return true;

        Queues::ConstPtr p = i->second.snapshot();
        return p && std::find_if(p->begin(), p->end(), MatchQueue(queue)) != p->end();
    } else if (!queue) {
        //if no queue or routing key is specified, just report whether any bindings exist
        return bindings.size() > 0;
    } else {
        for (Bindings::iterator i = bindings.begin(); i != bindings.end(); i++) {
            Queues::ConstPtr p = i->second.snapshot();
            if (p && std::find_if(p->begin(), p->end(), MatchQueue(queue)) != p->end()) return true;
        }
        return false;
    }

    return false;
}

DirectExchange::~DirectExchange() {}

const std::string DirectExchange::typeName("direct");
