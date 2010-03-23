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
#include "qpid/broker/HeadersExchange.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include <algorithm>


using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
namespace _qmf = qmf::org::apache::qpid::broker;

// TODO aconway 2006-09-20: More efficient matching algorithm.
// The current search algorithm really sucks.
// Fieldtables are heavy, maybe use shared_ptr to do handle-body.

using namespace qpid::broker;

namespace {
    const std::string x_match("x-match");
    // possible values for x-match
    const std::string all("all");
    const std::string any("any");
    const std::string empty;

    // federation related args and values
    const std::string qpidFedOp("qpid.fed.op");
    const std::string qpidFedTags("qpid.fed.tags");
    const std::string qpidFedOrigin("qpid.fed.origin");

    const std::string fedOpBind("B");
    const std::string fedOpUnbind("U");
    const std::string fedOpReorigin("R");
    const std::string fedOpHello("H");
}

HeadersExchange::HeadersExchange(const string& _name, Manageable* _parent, Broker* b) :
    Exchange(_name, _parent, b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

HeadersExchange::HeadersExchange(const std::string& _name, bool _durable,
                                 const FieldTable& _args, Manageable* _parent, Broker* b) :
    Exchange(_name, _durable, _args, _parent, b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

std::string HeadersExchange::getMatch(const FieldTable* args)
{
    if (!args) {
        throw InternalErrorException(QPID_MSG("No arguments given."));
    }
    FieldTable::ValuePtr what = args->get(x_match);
    if (!what) {
        return empty;
    }
    if (!what->convertsTo<std::string>()) {
        throw InternalErrorException(QPID_MSG("Invalid x-match binding format to headers exchange. Must be a string [\"all\" or \"any\"]"));
    }
    return what->get<std::string>();
}

bool HeadersExchange::bind(Queue::shared_ptr queue, const string& bindingKey, const FieldTable* args)
{
    string fedOp(fedOpBind);
    string fedTags;
    string fedOrigin;
    if (args) {
        fedOp = args->getAsString(qpidFedOp);
        fedTags = args->getAsString(qpidFedTags);
        fedOrigin = args->getAsString(qpidFedOrigin);
    }
    bool propagate = false;

    // The federation args get propagated directly, so we need to identify
    // the non feteration args in case a federated propagate is needed
    FieldTable extra_args;
    getNonFedArgs(args, extra_args);
    
    if (fedOp.empty() || fedOp == fedOpBind) {
        // x-match arg MUST be present for a bind call
        std::string x_match_value = getMatch(args);

        if (x_match_value != all && x_match_value != any) {
            throw InternalErrorException(QPID_MSG("Invalid or missing x-match value binding to headers exchange. Must be a string [\"all\" or \"any\"]"));
        }

        {
            Mutex::ScopedLock l(lock);
            Binding::shared_ptr binding (new Binding (bindingKey, queue, this, *args));
            BoundKey bk(binding);
            if (bindings.add_unless(bk, MatchArgs(queue, args))) {
                binding->startManagement();
                propagate = bk.fedBinding.addOrigin(fedOrigin);
                if (mgmtExchange != 0) {
                    mgmtExchange->inc_bindingCount();
                }
            } else {
                return false;
            }
        } // lock dropped

    } else if (fedOp == fedOpUnbind) {
        Mutex::ScopedLock l(lock);
 
        FedUnbindModifier modifier(fedOrigin);
        bindings.modify_if(MatchKey(queue, bindingKey), modifier);
        propagate = modifier.shouldPropagate;
        if (modifier.shouldUnbind) {
          unbind(queue, bindingKey, args);
        }
        
    } else if (fedOp == fedOpReorigin) {
        Bindings::ConstPtr p = bindings.snapshot();
        if (p.get())
        {
            Mutex::ScopedLock l(lock);
            for (std::vector<BoundKey>::const_iterator i = p->begin(); i != p->end(); ++i)
            {
                if ((*i).fedBinding.hasLocal()) {
                    propagateFedOp( (*i).binding->key, string(), fedOpBind, string());
                }
            }
        }
    }
    routeIVE();
    if (propagate) {
        FieldTable * prop_args = (extra_args.count() != 0 ? &extra_args : 0);
        propagateFedOp(bindingKey, fedTags, fedOp, fedOrigin, prop_args);
    }

    return true;
}

bool HeadersExchange::unbind(Queue::shared_ptr queue, const string& bindingKey, const FieldTable*){
    bool propagate = false;
    {
        Mutex::ScopedLock l(lock);

        FedUnbindModifier modifier;
        MatchKey match_key(queue, bindingKey);
        bindings.modify_if(match_key, modifier);
        propagate = modifier.shouldPropagate;
        if (modifier.shouldUnbind) {
            if (bindings.remove_if(match_key)) {
                if (mgmtExchange != 0) {
                    mgmtExchange->dec_bindingCount();
                }
            } else {
                return false;
            }
        }
    }

    if (propagate) {
        propagateFedOp(bindingKey, string(), fedOpUnbind, string());
    }
    return true;
}


void HeadersExchange::route(Deliverable& msg, const string& /*routingKey*/, const FieldTable* args)
{
    if (!args) {
        //can't match if there were no headers passed in
        if (mgmtExchange != 0) {
            mgmtExchange->inc_msgReceives();
            mgmtExchange->inc_byteReceives(msg.contentSize());
            mgmtExchange->inc_msgDrops();
            mgmtExchange->inc_byteDrops(msg.contentSize());
        }
        return;
    }

    PreRoute pr(msg, this);

    BindingList b(new std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> >);
    Bindings::ConstPtr p = bindings.snapshot();
    if (p.get()) {
        for (std::vector<BoundKey>::const_iterator i = p->begin(); i != p->end(); ++i) {
            if (match((*i).binding->args, *args)) {
                b->push_back((*i).binding);
            }
        }
    }
    doRoute(msg, b);
}


bool HeadersExchange::isBound(Queue::shared_ptr queue, const string* const, const FieldTable* const args)
{
    Bindings::ConstPtr p = bindings.snapshot();
    if (p.get()){
        for (std::vector<BoundKey>::const_iterator i = p->begin(); i != p->end(); ++i) {
            if ( (!args || equal((*i).binding->args, *args)) && (!queue || (*i).binding->queue == queue)) {
                return true;
            }
        }
    }
    return false;
}

void HeadersExchange::getNonFedArgs(const FieldTable* args, FieldTable& nonFedArgs)
{
    if (!args)
    {
        return;
    }

    for (qpid::framing::FieldTable::ValueMap::const_iterator i=args->begin(); i != args->end(); ++i)
    {
        const string & name(i->first);
        if (name == qpidFedOp ||
            name == qpidFedTags ||
            name == qpidFedOrigin)
        {
            continue;
        }
        nonFedArgs.insert((*i));
    }
}

HeadersExchange::~HeadersExchange() {}

const std::string HeadersExchange::typeName("headers");

namespace 
{

    bool match_values(const FieldValue& bind, const FieldValue& msg) {
        return  bind.getType() == 0xf0 || bind == msg;
    }

}


bool HeadersExchange::match(const FieldTable& bind, const FieldTable& msg) {
    typedef FieldTable::ValueMap Map;
    std::string what = getMatch(&bind);
    if (what == all) {
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
    } else if (what == any) {
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

//---------
HeadersExchange::MatchArgs::MatchArgs(Queue::shared_ptr q, const qpid::framing::FieldTable* a) : queue(q), args(a) {}

bool HeadersExchange::MatchArgs::operator()(BoundKey & bk)
{
    return bk.binding->queue == queue && bk.binding->args == *args;
}

//---------
HeadersExchange::MatchKey::MatchKey(Queue::shared_ptr q, const std::string& k) : queue(q), key(k) {}

bool HeadersExchange::MatchKey::operator()(BoundKey & bk)
{
    return bk.binding->queue == queue && bk.binding->key == key;
}

//----------
HeadersExchange::FedUnbindModifier::FedUnbindModifier(string & origin) : fedOrigin(origin), shouldUnbind(false), shouldPropagate(false) {}
HeadersExchange::FedUnbindModifier::FedUnbindModifier() : shouldUnbind(false), shouldPropagate(false) {}

bool HeadersExchange::FedUnbindModifier::operator()(BoundKey & bk)
{
    if ("" == fedOrigin) {
        shouldPropagate = bk.fedBinding.delOrigin();
    } else {
        shouldPropagate = bk.fedBinding.delOrigin(fedOrigin);
    }
    if (bk.fedBinding.count() == 0)
    {
        shouldUnbind = true;
    }
    return true;
}

