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

// TODO aconway 2006-09-20: More efficient matching algorithm.
// Areas for improvement:
// - excessive string copying: should be 0 copy, match from original buffer.
// - match/lookup: use descision tree or other more efficient structure.

Tokens& Tokens::operator=(const std::string& s) {
    clear();
    if (s.empty()) return *this;
    std::string::const_iterator i = s.begin();
    while (true) {
        // Invariant: i is at the beginning of the next untokenized word.
        std::string::const_iterator j = find(i, s.end(), '.');
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
// TODO aconway 2006-09-20: Ineficient to convert every routingKey to a string.
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
    if (mgmtExchange.get() != 0)
        mgmtExchange->set_type (typeName);
}

TopicExchange::TopicExchange(const std::string& _name, bool _durable,
                             const FieldTable& _args, Manageable* _parent) :
    Exchange(_name, _durable, _args, _parent)
{
    if (mgmtExchange.get() != 0)
        mgmtExchange->set_type (typeName);
}

bool TopicExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* /*args*/){
    RWlock::ScopedWlock l(lock);
    TopicPattern routingPattern(routingKey);
    if (isBound(queue, routingPattern)) {
        return false;
    } else {
        Binding::shared_ptr binding (new Binding (routingKey, queue, this));
        bindings[routingPattern].push_back(binding);
        if (mgmtExchange.get() != 0) {
            mgmtExchange->inc_bindings ();
        }
        return true;
    }
}

bool TopicExchange::unbind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* /*args*/){
    RWlock::ScopedWlock l(lock);
    BindingMap::iterator bi = bindings.find(TopicPattern(routingKey));
    Binding::vector& qv(bi->second);
    if (bi == bindings.end()) return false;

    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    if(q == qv.end()) return false;
    qv.erase(q);
    if(qv.empty()) bindings.erase(bi);
    if (mgmtExchange.get() != 0) {
        mgmtExchange->dec_bindings ();
    }
    return true;
}

bool TopicExchange::isBound(Queue::shared_ptr queue, TopicPattern& pattern)
{
    BindingMap::iterator bi = bindings.find(pattern);
    if (bi == bindings.end()) return false;
    Binding::vector& qv(bi->second);
    Binding::vector::iterator q;
    for (q = qv.begin(); q != qv.end(); q++)
        if ((*q)->queue == queue)
            break;
    return q != qv.end();
}

void TopicExchange::route(Deliverable& msg, const string& routingKey, const FieldTable* /*args*/){
    RWlock::ScopedRlock l(lock);
    uint32_t count(0);
    Tokens   tokens(routingKey);

    for (BindingMap::iterator i = bindings.begin(); i != bindings.end(); ++i) {
        if (i->first.match(tokens)) {
            Binding::vector& qv(i->second);
            for(Binding::vector::iterator j = qv.begin(); j != qv.end(); j++, count++){
                msg.deliverTo((*j)->queue);
                if ((*j)->mgmtBinding.get() != 0)
                    (*j)->mgmtBinding->inc_msgMatched ();
            }
        }
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
            Binding::vector& qv(i->second);
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


