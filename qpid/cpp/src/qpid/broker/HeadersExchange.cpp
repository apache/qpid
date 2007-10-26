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
#include "HeadersExchange.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"
#include <algorithm>


using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

// TODO aconway 2006-09-20: More efficient matching algorithm.
// The current search algorithm really sucks.
// Fieldtables are heavy, maybe use shared_ptr to do handle-body.

using namespace qpid::broker;

namespace {
    const StringValue all("all");
    const StringValue any("any");
    const std::string x_match("x-match");
}

HeadersExchange::HeadersExchange(const string& _name) : Exchange(_name) { }
HeadersExchange::HeadersExchange(const std::string& _name, bool _durable, const FieldTable& _args) : Exchange(_name, _durable, _args) {}

bool HeadersExchange::bind(Queue::shared_ptr queue, const string& /*routingKey*/, const FieldTable* args){
    RWlock::ScopedWlock locker(lock);
    FieldTable::ValuePtr what = args->get(x_match);
    if (!what || (*what != all && *what != any)) 
        throw InternalErrorException(QPID_MSG("Invalid x-match value binding to headers exchange."));
    Binding binding(*args, queue);
    Bindings::iterator i =
        std::find(bindings.begin(),bindings.end(), binding);
    if (i == bindings.end()) {
        bindings.push_back(binding);
        return true;
    } else {
        return false;
    }
}

bool HeadersExchange::unbind(Queue::shared_ptr queue, const string& /*routingKey*/, const FieldTable* args){
    RWlock::ScopedWlock locker(lock);
    Bindings::iterator i =
        std::find(bindings.begin(),bindings.end(), Binding(*args, queue));
    if (i != bindings.end()) {
        bindings.erase(i);
        return true;
    } else {
        return false;
    }
}


void HeadersExchange::route(Deliverable& msg, const string& /*routingKey*/, const FieldTable* args){
    RWlock::ScopedRlock locker(lock);;
    for (Bindings::iterator i = bindings.begin(); i != bindings.end(); ++i) {
        if (match(i->first, *args)) msg.deliverTo(i->second);
    }
}


bool HeadersExchange::isBound(Queue::shared_ptr queue, const string* const, const FieldTable* const args)
{
    for (Bindings::iterator i = bindings.begin(); i != bindings.end(); ++i) {
        if ( (!args || equal(i->first, *args)) && (!queue || i->second == queue)) {
            return true;
        }
    }
    return false;
}

HeadersExchange::~HeadersExchange() {}

const std::string HeadersExchange::typeName("headers");

namespace 
{

    bool match_values(const FieldValue& bind, const FieldValue& msg) {
        return  bind.empty() || bind == msg;
    }

}


bool HeadersExchange::match(const FieldTable& bind, const FieldTable& msg) {
    typedef FieldTable::ValueMap Map;
    FieldTable::ValuePtr what = bind.get(x_match);
    if (!what) {
        return false;
    } else if (*what == all) {
        for (Map::const_iterator i = bind.begin();
             i != bind.end();
             ++i)
        {
            if (i->first != x_match) 
            {
                Map::const_iterator j = msg.find(i->first);
                if (j == msg.end()) return false;
                if (!match_values(*(i->second), *(j->second))) return false;
            }
        }
        return true;
    } else if (*what == any) {
        for (Map::const_iterator i = bind.begin();
             i != bind.end();
             ++i)
        {
            if (i->first != x_match) 
            {
                Map::const_iterator j = msg.find(i->first);
                if (j != msg.end()) {
                    if (match_values(*(i->second), *(j->second))) return true;
                }
            }
        }
        return false;
    } else {
        return false;
    }
}

bool HeadersExchange::equal(const FieldTable& a, const FieldTable& b) {
    typedef FieldTable::ValueMap Map;
    for (Map::const_iterator i = a.begin();
         i != a.end();
         ++i)
    {
        Map::const_iterator j = b.find(i->first);
        if (j == b.end()) return false;
        if (!match_values(*(i->second), *(j->second))) return false;
    }
    return true;
}



