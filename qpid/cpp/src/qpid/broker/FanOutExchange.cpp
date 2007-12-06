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
#include "FanOutExchange.h"
#include <algorithm>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

FanOutExchange::FanOutExchange(const std::string& _name, Manageable* _parent) :
    Exchange(_name, _parent)
{
    if (mgmtExchange.get() != 0)
        mgmtExchange->set_type (typeName);
}

FanOutExchange::FanOutExchange(const std::string& _name, bool _durable,
                               const FieldTable& _args, Manageable* _parent) :
    Exchange(_name, _durable, _args, _parent)
{
    if (mgmtExchange.get() != 0)
        mgmtExchange->set_type (typeName);
}

bool FanOutExchange::bind(Queue::shared_ptr queue, const string& /*routingKey*/, const FieldTable* /*args*/){
    RWlock::ScopedWlock locker(lock);
    std::vector<Binding::shared_ptr>::iterator i;

    // Add if not already present.
    for (i = bindings.begin (); i != bindings.end(); i++)
        if ((*i)->queue == queue)
            break;

    if (i == bindings.end()) {
        Binding::shared_ptr binding (new Binding ("", queue, this));
        bindings.push_back(binding);
        if (mgmtExchange.get() != 0) {
            mgmtExchange->inc_bindings ();
        }
        return true;
    } else {
        return false;
    }
}

bool FanOutExchange::unbind(Queue::shared_ptr queue, const string& /*routingKey*/, const FieldTable* /*args*/){
    RWlock::ScopedWlock locker(lock);
    std::vector<Binding::shared_ptr>::iterator i;

    for (i = bindings.begin (); i != bindings.end(); i++)
        if ((*i)->queue == queue)
            break;

    if (i != bindings.end()) {
        bindings.erase(i);
        if (mgmtExchange.get() != 0) {
            mgmtExchange->dec_bindings ();
        }
        return true;
    } else {
        return false;
    }
}

void FanOutExchange::route(Deliverable& msg, const string& /*routingKey*/, const FieldTable* /*args*/){
    RWlock::ScopedRlock locker(lock);
    uint32_t count(0);

    for(std::vector<Binding::shared_ptr>::iterator i = bindings.begin(); i != bindings.end(); ++i, count++){
        msg.deliverTo((*i)->queue);
        if ((*i)->mgmtBinding.get() != 0)
            (*i)->mgmtBinding->inc_msgMatched ();
    }

    if (mgmtExchange.get() != 0)
    {
        mgmtExchange->inc_msgReceives  ();
        mgmtExchange->inc_byteReceives (msg.contentSize ());
        if (count == 0)
        {
            mgmtExchange->inc_msgDrops  ();
            mgmtExchange->inc_byteDrops (msg.contentSize ());
        }
        else
        {
            mgmtExchange->inc_msgRoutes  (count);
            mgmtExchange->inc_byteRoutes (count * msg.contentSize ());
        }
    }
}

bool FanOutExchange::isBound(Queue::shared_ptr queue, const string* const, const FieldTable* const)
{
    std::vector<Binding::shared_ptr>::iterator i;

    for (i = bindings.begin (); i != bindings.end(); i++)
        if ((*i)->queue == queue)
            break;

    return i != bindings.end();
}


FanOutExchange::~FanOutExchange() {}

const std::string FanOutExchange::typeName("fanout");
