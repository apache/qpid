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
#include "TopicExchange.h"
#include <algorithm>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
namespace _qmf = qmf::org::apache::qpid::broker;

// TODO aconway 2006-09-20: More efficient matching algorithm.
// Areas for improvement:
// - excessive string copying: should be 0 copy, match from original buffer.
// - match/lookup: use descision tree or other more efficient structure.

namespace 
{
const std::string qpidFedOp("qpid.fed.op");
const std::string qpidFedTags("qpid.fed.tags");
const std::string qpidFedOrigin("qpid.fed.origin");

const std::string fedOpBind("B");
const std::string fedOpUnbind("U");
const std::string fedOpReorigin("R");
const std::string fedOpHello("H");
}

Tokens& Tokens::operator=(const std::string& s) {
    clear();
    if (s.empty()) return *this;
    std::string::const_iterator i = s.begin();
    while (true) {
        // Invariant: i is at the beginning of the next untokenized word.
        std::string::const_iterator j = std::find(i, s.end(), '.');
        push_back(std::string(i, j));
        if (j == s.end()) return *this;
        i = j + 1;
    }
    return *this;
}

TopicPattern& TopicPattern::operator=(const Tokens& tokens) {
    Tokens::operator=(tokens);
    normalize();
    return *this;
}

void Tokens::key(string& keytext) const
{
    for (std::vector<string>::const_iterator iter = begin(); iter != end(); iter++) {
        if (iter != begin())
            keytext += ".";
        keytext += *iter;
    }
}

namespace {
const std::string hashmark("#");
const std::string star("*");
}

void TopicPattern::normalize() {
    std::string word;
    Tokens::iterator i = begin();
    while (i != end()) {
        if (*i == hashmark) {
            ++i;
            while (i != end()) {
                // Invariant: *(i-1)==#, [begin()..i-1] is normalized.
                if (*i == star) { // Move * before #.
                    std::swap(*i, *(i-1));
                    ++i;
                } else if (*i == hashmark) {
                    erase(i); // Remove extra #
                } else {
                    break;
                }
            }
        } else {
            i ++;
        }
    }
}


namespace {
// TODO aconway 2006-09-20: Inefficient to convert every routingKey to a string.
// Need StringRef class that operates on a string in place witout copy.
// Should be applied everywhere strings are extracted from frames.
// 
bool do_match(Tokens::const_iterator pattern_begin,  Tokens::const_iterator pattern_end, Tokens::const_iterator target_begin,  Tokens::const_iterator target_end)
{
    // Invariant: [pattern_begin..p) matches [target_begin..t)
    Tokens::const_iterator p = pattern_begin;
    Tokens::const_iterator t = target_begin;
    while (p != pattern_end && t != target_end)
    {
        if (*p == star || *p == *t) {
            ++p, ++t;
        } else if (*p == hashmark) {
            ++p;
            if (do_match(p, pattern_end, t, target_end)) return true;
            while (t != target_end) {
                ++t;
                if (do_match(p, pattern_end, t, target_end)) return true;
            }
            return false;
        } else {
            return false;
        }
    }
    while (p != pattern_end && *p == hashmark) ++p; // Ignore trailing #
    return t == target_end && p == pattern_end;
}
}

bool TopicPattern::match(const Tokens& target)  const
{
    return do_match(begin(), end(), target.begin(), target.end());
}

TopicExchange::TopicExchange(const string& _name, Manageable* _parent) : Exchange(_name, _parent)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

TopicExchange::TopicExchange(const std::string& _name, bool _durable,
                             const FieldTable& _args, Manageable* _parent) :
    Exchange(_name, _durable, _args, _parent)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

bool TopicExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* args)
{
    string fedOp(args ? args->getAsString(qpidFedOp) : fedOpBind);
    string fedTags(args ? args->getAsString(qpidFedTags) : "");
    string fedOrigin(args ? args->getAsString(qpidFedOrigin) : "");
    bool propagate = false;
    bool reallyUnbind;
    TopicPattern routingPattern(routingKey);

    if (args == 0 || fedOp.empty() || fedOp == fedOpBind) {
        RWlock::ScopedWlock l(lock);
        if (isBound(queue, routingPattern)) {
            return false;
        } else {
            Binding::shared_ptr binding (new Binding (routingKey, queue, this, FieldTable(), fedOrigin));
            BoundKey& bk = bindings[routingPattern];
            bk.bindingVector.push_back(binding);
            propagate = bk.fedBinding.addOrigin(fedOrigin);
            if (mgmtExchange != 0) {
                mgmtExchange->inc_bindingCount();
                ((_qmf::Queue*) queue->GetManagementObject())->inc_bindingCount();
            }
        }
    } else if (fedOp == fedOpUnbind) {
        {
            RWlock::ScopedWlock l(lock);
            BoundKey& bk = bindings[routingPattern];
            propagate = bk.fedBinding.delOrigin(fedOrigin);
            reallyUnbind = bk.fedBinding.count() == 0;
        }
        if (reallyUnbind)
            unbind(queue, routingKey, 0);
    } else if (fedOp == fedOpReorigin) {
        for (std::map<TopicPattern, BoundKey>::iterator iter = bindings.begin();
             iter != bindings.end(); iter++) {
            const BoundKey& bk = iter->second;
            if (bk.fedBinding.hasLocal()) {
                string propKey;
                iter->first.key(propKey);
                propagateFedOp(propKey, string(), fedOpBind, string());
            }
        }
    }

    routeIVE();
    if (propagate)
        propagateFedOp(routingKey, fedTags, fedOp, fedOrigin);
    return true;
}

bool TopicExchange::unbind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* /*args*/){
    RWlock::ScopedWlock l(lock);
    BindingMap::iterator bi = bindings.find(TopicPattern(routingKey));
    if (bi == bindings.end()) return false;
    BoundKey& bk = bi->second;
    Binding::vector& qv(bk.bindingVector);
    bool propagate = false;

    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    if(q == qv.end()) return false;
    qv.erase(q);
    propagate = bk.fedBinding.delOrigin();
    if(qv.empty()) bindings.erase(bi);
    if (mgmtExchange != 0) {
        mgmtExchange->dec_bindingCount();
        ((_qmf::Queue*) queue->GetManagementObject())->dec_bindingCount();
    }

    if (propagate)
        propagateFedOp(routingKey, string(), fedOpUnbind, string());
    return true;
}

bool TopicExchange::isBound(Queue::shared_ptr queue, TopicPattern& pattern)
{
    BindingMap::iterator bi = bindings.find(pattern);
    if (bi == bindings.end()) return false;
    Binding::vector& qv(bi->second.bindingVector);
    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    return q != qv.end();
}

void TopicExchange::route(Deliverable& msg, const string& routingKey, const FieldTable* /*args*/){
    Binding::vector mb;
    PreRoute pr(msg, this);
    uint32_t count(0);

    {
    RWlock::ScopedRlock l(lock);
    Tokens   tokens(routingKey);

    for (BindingMap::iterator i = bindings.begin(); i != bindings.end(); ++i) {
        if (i->first.match(tokens)) {
            Binding::vector& qv(i->second.bindingVector);
            for(Binding::vector::iterator j = qv.begin(); j != qv.end(); j++, count++){
                mb.push_back(*j);
            }
        }
    }
    }
    
    for (Binding::vector::iterator j = mb.begin(); j != mb.end(); ++j) {
        msg.deliverTo((*j)->queue);
        if ((*j)->mgmtBinding != 0)
            (*j)->mgmtBinding->inc_msgMatched ();
    }

    if (mgmtExchange != 0)
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

bool TopicExchange::isBound(Queue::shared_ptr queue, const string* const routingKey, const FieldTable* const)
{
    if (routingKey && queue) {
        TopicPattern key(*routingKey);
        return isBound(queue, key);
    } else if (!routingKey && !queue) {
        return bindings.size() > 0;
    } else if (routingKey) {
        for (BindingMap::iterator i = bindings.begin(); i != bindings.end(); ++i) {
            if (i->first.match(*routingKey)) {
                return true;
            }
        }            
    } else {
        for (BindingMap::iterator i = bindings.begin(); i != bindings.end(); ++i) {
            Binding::vector& qv(i->second.bindingVector);
            Binding::vector::iterator q;
            for (q = qv.begin(); q != qv.end(); q++)
                if ((*q)->queue == queue)
                    return true;
        }
    }
    return false;
}

TopicExchange::~TopicExchange() {}

const std::string TopicExchange::typeName("topic");


