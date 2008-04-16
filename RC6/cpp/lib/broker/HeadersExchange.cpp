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
#include <HeadersExchange.h>
#include <ExchangeBinding.h>
#include <Value.h>
#include <QpidError.h>
#include <algorithm>


using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

// TODO aconway 2006-09-20: More efficient matching algorithm.
// The current search algorithm really sucks.
// Fieldtables are heavy, maybe use shared_ptr to do handle-body.

using namespace qpid::broker;

namespace {
    const std::string all("all");
    const std::string any("any");
    const std::string x_match("x-match");
}

HeadersExchange::HeadersExchange(const string& _name) : Exchange(_name) { }

void HeadersExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* args){
    Mutex::ScopedLock locker(lock);
    std::string what = args->getString("x-match");
    if (what != all && what != any) {
        THROW_QPID_ERROR(PROTOCOL_ERROR, "Invalid x-match value binding to headers exchange.");
    }
    bindings.push_back(Binding(*args, queue));
    queue->bound(new ExchangeBinding(this, queue, routingKey, args));
}

void HeadersExchange::unbind(Queue::shared_ptr queue, const string& /*routingKey*/, const FieldTable* args){
    Mutex::ScopedLock locker(lock);
    Bindings::iterator i =
        std::find(bindings.begin(),bindings.end(), Binding(*args, queue));
    if (i != bindings.end()) bindings.erase(i);
}


void HeadersExchange::route(Deliverable& msg, const string& /*routingKey*/, const FieldTable* args){
    Mutex::ScopedLock locker(lock);;
    for (Bindings::iterator i = bindings.begin(); i != bindings.end(); ++i) {
        if (match(i->first, *args)) msg.deliverTo(i->second);
    }
}

HeadersExchange::~HeadersExchange() {}

const std::string HeadersExchange::typeName("headers");

namespace 
{

    bool match_values(const Value& bind, const Value& msg) {
        return  dynamic_cast<const EmptyValue*>(&bind) || bind == msg;
    }

}


bool HeadersExchange::match(const FieldTable& bind, const FieldTable& msg) {
    typedef FieldTable::ValueMap Map;
    std::string what = bind.getString(x_match);
    if (what == all) {
        for (Map::const_iterator i = bind.getMap().begin();
             i != bind.getMap().end();
             ++i)
        {
            if (i->first != x_match) 
            {
                Map::const_iterator j = msg.getMap().find(i->first);
                if (j == msg.getMap().end()) return false;
                if (!match_values(*(i->second), *(j->second))) return false;
            }
        }
        return true;
    } else if (what == any) {
        for (Map::const_iterator i = bind.getMap().begin();
             i != bind.getMap().end();
             ++i)
        {
            if (i->first != x_match) 
            {
                Map::const_iterator j = msg.getMap().find(i->first);
                if (j != msg.getMap().end()) {
                    if (match_values(*(i->second), *(j->second))) return true;
                }
            }
        }
        return false;
    } else {
        return false;
    }
}



