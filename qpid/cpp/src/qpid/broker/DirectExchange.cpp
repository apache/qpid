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
#include "qpid/broker/FedOps.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/DirectExchange.h"
#include <iostream>

using namespace qpid::broker;

using std::string;

using namespace qpid::framing;
using namespace qpid::sys;
using qpid::management::Manageable;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace 
{
    const std::string qpidExclusiveBinding("qpid.exclusive-binding");
}

DirectExchange::DirectExchange(const string& _name, Manageable* _parent, Broker* b) : Exchange(_name, _parent, b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type(typeName);
}

DirectExchange::DirectExchange(const string& _name, bool _durable, bool autodelete,
                               const FieldTable& _args, Manageable* _parent, Broker* b) :
    Exchange(_name, _durable, autodelete, _args, _parent, b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type(typeName);
}

bool DirectExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* args)
{
    string fedOp(fedOpBind);
    string fedTags;
    string fedOrigin;
    bool exclusiveBinding = false;
    if (args) {
        fedOp = args->getAsString(qpidFedOp);
        fedTags = args->getAsString(qpidFedTags);
        fedOrigin = args->getAsString(qpidFedOrigin);
        exclusiveBinding = !!args->get(qpidExclusiveBinding);  // only direct exchanges take exclusive bindings
    }

    bool propagate = false;

    if (args == 0 || fedOp.empty() || fedOp == fedOpBind) {
        Mutex::ScopedLock l(lock);
        Binding::shared_ptr b(new Binding(routingKey, queue, this, args ? *args : FieldTable(), fedOrigin));
        BoundKey& bk = bindings[routingKey];
        if (exclusiveBinding) bk.queues.clear();

        QPID_LOG(debug, "Bind key [" << routingKey << "] to queue " << queue->getName()
                 << " (origin=" << fedOrigin << ")");

        if (bk.queues.add_unless(b, MatchQueue(queue))) {
            b->startManagement();
            propagate = bk.fedBinding.addOrigin(queue->getName(), fedOrigin);
            if (mgmtExchange != 0) {
                mgmtExchange->inc_bindingCount();
            }
        } else {
            // queue already present - still need to track fedOrigin
            bk.fedBinding.addOrigin(queue->getName(), fedOrigin);
            return false;
        }
    } else if (fedOp == fedOpUnbind) {
        Mutex::ScopedLock l(lock);
        BoundKey& bk = bindings[routingKey];

        QPID_LOG(debug, "Bind - fedOpUnbind key [" << routingKey << "] queue " << queue->getName()
                 << " (origin=" << fedOrigin << ")" << " (count=" << bk.fedBinding.count() << ")");

        propagate = bk.fedBinding.delOrigin(queue->getName(), fedOrigin);
        if (bk.fedBinding.countFedBindings(queue->getName()) == 0)
            unbind(queue, routingKey, args);

    } else if (fedOp == fedOpReorigin) {
        /** gather up all the keys that need rebinding in a local vector
         * while holding the lock.  Then propagate once the lock is
         * released
         */
        std::vector<std::string> keys2prop;
        {
            Mutex::ScopedLock l(lock);    
            for (Bindings::iterator iter = bindings.begin();
                 iter != bindings.end(); iter++) {
                const BoundKey& bk = iter->second;
                if (bk.fedBinding.hasLocal()) {
                    keys2prop.push_back(iter->first);
                }
            }
        }   /* lock dropped */
        for (std::vector<std::string>::const_iterator key = keys2prop.begin();
             key != keys2prop.end(); key++) {
            propagateFedOp( *key, string(), fedOpBind, string());
        }
    }

    routeIVE();
    if (propagate)
        propagateFedOp(routingKey, fedTags, fedOp, fedOrigin);
    return true;
}

bool DirectExchange::unbind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* args)
{
    string fedOrigin(args ? args->getAsString(qpidFedOrigin) : "");
    bool propagate = false;
    bool empty = false;

    QPID_LOG(debug, "Unbinding key [" << routingKey << "] from queue " << queue->getName()
             << " on exchange " << getName() << " origin=" << fedOrigin << ")" );
    {
        Mutex::ScopedLock l(lock);
        BoundKey& bk = bindings[routingKey];
        if (bk.queues.remove_if(MatchQueue(queue))) {
            propagate = bk.fedBinding.delOrigin(queue->getName(), fedOrigin);
            if (mgmtExchange != 0) {
                mgmtExchange->dec_bindingCount();
            }
            if (bk.queues.empty()) {
                bindings.erase(routingKey);
                if (bindings.empty()) empty = true;
            }
        } else {
            return false;
        }
    }

    // If I delete my local binding, propagate this unbind to any upstream brokers
    if (propagate)
        propagateFedOp(routingKey, string(), fedOpUnbind, string());
    if (empty) checkAutodelete();
    return true;
}

void DirectExchange::route(Deliverable& msg)
{
    const string& routingKey = msg.getMessage().getRoutingKey();
    PreRoute pr(msg, this);
    ConstBindingList b;
    {
        Mutex::ScopedLock l(lock);
        Bindings::iterator i = bindings.find(routingKey);
        if (i != bindings.end()) b = i->second.queues.snapshot();
    }
    doRoute(msg, b);
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

        Queues::ConstPtr p = i->second.queues.snapshot();
        return p && std::find_if(p->begin(), p->end(), MatchQueue(queue)) != p->end();
    } else if (!queue) {
        //if no queue or routing key is specified, just report whether any bindings exist
        return bindings.size() > 0;
    } else {
        for (Bindings::iterator i = bindings.begin(); i != bindings.end(); i++) {
            Queues::ConstPtr p = i->second.queues.snapshot();
            if (p && std::find_if(p->begin(), p->end(), MatchQueue(queue)) != p->end()) return true;
        }
        return false;
    }

    return false;
}

DirectExchange::~DirectExchange() {
    if (mgmtExchange != 0)
        mgmtExchange->debugStats("destroying");
}

const std::string DirectExchange::typeName("direct");

bool DirectExchange::hasBindings()
{
    Mutex::ScopedLock l(lock);
    return !bindings.empty();
}
