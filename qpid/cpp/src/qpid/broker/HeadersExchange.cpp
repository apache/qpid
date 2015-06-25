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

#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/MapHandler.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include <algorithm>


using namespace qpid::broker;

using std::string;
using qpid::amqp::MapHandler;

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
    const std::string QPID_RESERVED("qpid.");
    const std::string qpidFedOp("qpid.fed.op");
    const std::string qpidFedTags("qpid.fed.tags");
    const std::string qpidFedOrigin("qpid.fed.origin");

    const std::string fedOpBind("B");
    const std::string fedOpUnbind("U");
    const std::string fedOpReorigin("R");
    const std::string fedOpHello("H");

std::string getMatch(const FieldTable* args)
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
class Matcher : public MapHandler
{
  public:
    Matcher(const FieldTable& b) : binding(b), matched(0) {}
    void handleBool(const qpid::amqp::CharSequence& key, bool value) { processUint(std::string(key.data, key.size), value); }
    void handleUint8(const qpid::amqp::CharSequence& key, uint8_t value) { processUint(std::string(key.data, key.size), value); }
    void handleUint16(const qpid::amqp::CharSequence& key, uint16_t value) { processUint(std::string(key.data, key.size), value); }
    void handleUint32(const qpid::amqp::CharSequence& key, uint32_t value) { processUint(std::string(key.data, key.size), value); }
    void handleUint64(const qpid::amqp::CharSequence& key, uint64_t value) { processUint(std::string(key.data, key.size), value); }
    void handleInt8(const qpid::amqp::CharSequence& key, int8_t value) { processInt(std::string(key.data, key.size), value); }
    void handleInt16(const qpid::amqp::CharSequence& key, int16_t value) { processInt(std::string(key.data, key.size), value); }
    void handleInt32(const qpid::amqp::CharSequence& key, int32_t value) { processInt(std::string(key.data, key.size), value); }
    void handleInt64(const qpid::amqp::CharSequence& key, int64_t value) { processInt(std::string(key.data, key.size), value); }
    void handleFloat(const qpid::amqp::CharSequence& key, float value) { processFloat(std::string(key.data, key.size), value); }
    void handleDouble(const qpid::amqp::CharSequence& key, double value) { processFloat(std::string(key.data, key.size), value); }
    void handleString(const qpid::amqp::CharSequence& key, const qpid::amqp::CharSequence& value, const qpid::amqp::CharSequence& /*encoding*/)
    {
        processString(std::string(key.data, key.size), std::string(value.data, value.size));
    }
    void handleVoid(const qpid::amqp::CharSequence& key)
    {
        valueCheckRequired(std::string(key.data, key.size));
    }
    bool matches()
    {
        std::string what = getMatch(&binding);
        if (what == all) {
            //must match all entries in the binding, except the match mode indicator
            return matched == binding.size() - 1;
        } else if (what == any) {
            //match any of the entries in the binding
            return matched > 0;
        } else {
            return false;
        }
    }
  private:
    bool valueCheckRequired(const std::string& key)
    {
        FieldTable::ValuePtr v = binding.get(key);
        if (v) {
            if (v->getType() == 0xf0/*VOID*/) {
                ++matched;
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    void processString(const std::string& key, const std::string& actual)
    {
        if (valueCheckRequired(key) && binding.getAsString(key) == actual) {
            ++matched;
        }
    }
    void processFloat(const std::string& key, double actual)
    {
        double bound;
        if (valueCheckRequired(key) && binding.getDouble(key, bound) && bound == actual) {
            ++matched;
        }
    }
    void processInt(const std::string& key, int64_t actual)
    {
        if (valueCheckRequired(key) && binding.getAsInt64(key) == actual) {
            ++matched;
        }
    }
    void processUint(const std::string& key, uint64_t actual)
    {
        if (valueCheckRequired(key) && binding.getAsUInt64(key) == actual) {
            ++matched;
        }
    }
    const FieldTable& binding;
    size_t matched;
};
}

HeadersExchange::HeadersExchange(const string& _name, Manageable* _parent, Broker* b) :
    Exchange(_name, _parent, b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

HeadersExchange::HeadersExchange(const std::string& _name, bool _durable, bool autodelete,
                                 const FieldTable& _args, Manageable* _parent, Broker* b) :
    Exchange(_name, _durable, autodelete, _args, _parent, b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
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
    // the non federation args in case a federated propagate is needed
    FieldTable extra_args;
    getNonFedArgs(args, extra_args);
    
    if (fedOp.empty() || fedOp == fedOpBind) {
        // x-match arg MUST be present for a bind call
        std::string x_match_value = getMatch(args);

        if (x_match_value != all && x_match_value != any) {
            throw InternalErrorException(QPID_MSG("Invalid or missing x-match value binding to headers exchange. Must be a string [\"all\" or \"any\"]"));
        }

        Bindings::ConstPtr p = bindings.snapshot();
        if (p.get()) {
            MatchArgs matchArgs(queue, &extra_args);
            MatchKey matchKey(queue, bindingKey);
            for (std::vector<BoundKey>::const_iterator i = p->begin(); i != p->end(); ++i) {
                if (matchKey(*i) && !matchArgs(*i)) {
                    throw InternalErrorException(QPID_MSG("Exchange: " << getName()
                        << ", binding key: " << bindingKey
                        << " Duplicate binding key not allowed." ));
                }
            }
        }

        {
            Mutex::ScopedLock l(lock);
            //NOTE: do not include the fed op/tags/origin in the
            //arguments as when x-match is 'all' these would prevent
            //matching (they are internally added properties
            //controlling binding propagation but not relevant to
            //actual routing)
            Binding::shared_ptr binding (new Binding (bindingKey, queue, this, args ? *args : FieldTable()));
            BoundKey bk(binding, extra_args);
            if (bindings.add_unless(bk, MatchArgs(queue, &extra_args))) {
                binding->startManagement();
                propagate = bk.fedBinding.addOrigin(queue->getName(), fedOrigin);
                if (mgmtExchange != 0) {
                    mgmtExchange->inc_bindingCount();
                }
            } else {
                bk.fedBinding.addOrigin(queue->getName(), fedOrigin);
                return false;
            }
        } // lock dropped

    } else if (fedOp == fedOpUnbind) {
        Mutex::ScopedLock l(lock);
 
        FedUnbindModifier modifier(queue->getName(), fedOrigin);
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

bool HeadersExchange::unbind(Queue::shared_ptr queue, const string& bindingKey, const FieldTable *args){
    bool propagate = false;
    string fedOrigin(args ? args->getAsString(qpidFedOrigin) : "");
    {
        Mutex::ScopedLock l(lock);

        FedUnbindModifier modifier(queue->getName(), fedOrigin);
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
    if (bindings.empty()) checkAutodelete();
    return true;
}


void HeadersExchange::route(Deliverable& msg)
{
    PreRoute pr(msg, this);

    BindingList b(new std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> >);
    Bindings::ConstPtr p = bindings.snapshot();
    if (p.get()) {
        for (std::vector<BoundKey>::const_iterator i = p->begin(); i != p->end(); ++i) {
            if (match(i->args, msg.getMessage())) {
                /* check if a binding tothe same queue has not been already added to b */
                std::vector<boost::shared_ptr<qpid::broker::Exchange::Binding> >::iterator bi = b->begin();
                while ((bi != b->end()) && ((*bi)->queue != i->binding->queue))
                    ++bi;
                if (bi == b->end())
                    b->push_back(i->binding);
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
            if ( (!args || equal((*i).args, *args)) && (!queue || (*i).binding->queue == queue)) {
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
        if (i->first.find(QPID_RESERVED) == 0)
        {
            continue;
        }
        nonFedArgs.insert((*i));
    }
}

HeadersExchange::~HeadersExchange() {
    if (mgmtExchange != 0)
        mgmtExchange->debugStats("destroying");
}

const std::string HeadersExchange::typeName("headers");

namespace 
{

    bool match_values(const FieldValue& bind, const FieldValue& msg) {
        return  bind.getType() == 0xf0 || bind == msg;
    }

}

bool HeadersExchange::match(const FieldTable& bindArgs, const Message& msg) {
    Matcher matcher(bindArgs);
    msg.processProperties(matcher);
    return matcher.matches();
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

bool HeadersExchange::MatchArgs::operator()(const BoundKey & bk)
{
    return bk.binding->queue == queue && bk.binding->args == *args;
}

//---------
HeadersExchange::MatchKey::MatchKey(Queue::shared_ptr q, const std::string& k) : queue(q), key(k) {}

bool HeadersExchange::MatchKey::operator()(const BoundKey & bk)
{
    return bk.binding->queue == queue && bk.binding->key == key;
}

//----------
HeadersExchange::FedUnbindModifier::FedUnbindModifier(const string& queueName, const string& origin) : queueName(queueName), fedOrigin(origin), shouldUnbind(false), shouldPropagate(false) {}
HeadersExchange::FedUnbindModifier::FedUnbindModifier() : shouldUnbind(false), shouldPropagate(false) {}

bool HeadersExchange::FedUnbindModifier::operator()(BoundKey & bk)
{
    shouldPropagate = bk.fedBinding.delOrigin(queueName, fedOrigin);
    if (bk.fedBinding.countFedBindings(queueName) == 0)
    {
        shouldUnbind = true;
    }
    return true;
}

bool HeadersExchange::hasBindings()
{
    Bindings::ConstPtr ptr = bindings.snapshot();
    return ptr && !ptr->empty();
}
