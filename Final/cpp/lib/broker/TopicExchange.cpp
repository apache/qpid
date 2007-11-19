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
#include <TopicExchange.h>
#include <ExchangeBinding.h>
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

TopicExchange::TopicExchange(const string& _name) : Exchange(_name) { }

void TopicExchange::bind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* args){
    Monitor::ScopedLock l(lock);
    TopicPattern routingPattern(routingKey);
    bindings[routingPattern].push_back(queue);
    queue->bound(new ExchangeBinding(this, queue, routingKey, args));
}

void TopicExchange::unbind(Queue::shared_ptr queue, const string& routingKey, const FieldTable* /*args*/){
    Monitor::ScopedLock l(lock);
    BindingMap::iterator bi = bindings.find(TopicPattern(routingKey));
    Queue::vector& qv(bi->second);
    if (bi == bindings.end()) return;
    Queue::vector::iterator q = find(qv.begin(), qv.end(), queue);
    if(q == qv.end()) return;
    qv.erase(q);
    if(qv.empty()) bindings.erase(bi);
}


void TopicExchange::route(Deliverable& msg, const string& routingKey, const FieldTable* /*args*/){
    Monitor::ScopedLock l(lock);
    for (BindingMap::iterator i = bindings.begin(); i != bindings.end(); ++i) {
        if (i->first.match(routingKey)) {
            Queue::vector& qv(i->second);
            for(Queue::vector::iterator j = qv.begin(); j != qv.end(); j++){
                msg.deliverTo(*j);
            }
        }
    }
}

TopicExchange::~TopicExchange() {}

const std::string TopicExchange::typeName("topic");


