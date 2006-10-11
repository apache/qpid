/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _TopicExchange_
#define _TopicExchange_

#include <tr1/unordered_map>
#include <vector>
#include "Exchange.h"
#include "FieldTable.h"
#include "Message.h"
#include "MonitorImpl.h"
#include "Queue.h"

namespace qpid {
namespace broker {

/** A vector of string tokens */
class Tokens : public std::vector<std::string> {
  public:
    Tokens() {};
    // Default copy, assign, dtor are sufficient.

    /** Tokenize s, provides automatic conversion of string to Tokens */
    Tokens(const std::string& s) { operator=(s); }
    /** Tokenize s */
    Tokens & operator=(const std::string& s);

    struct Hash { size_t operator()(const Tokens&) const; };
    typedef std::equal_to<Tokens> Equal;
};

/**
 * Tokens that have been normalized as a pattern and can be matched
 * with topic Tokens.  Normalized meands all sequences of mixed * and
 * # are reduced to a series of * followed by at most one #.
 */
class TopicPattern : public Tokens
{
  public:
    TopicPattern() {}
    // Default copy, assign, dtor are sufficient.
    TopicPattern(const Tokens& tokens) { operator=(tokens); }
    TopicPattern(const std::string& str) { operator=(str); }
    TopicPattern& operator=(const Tokens&);
    TopicPattern& operator=(const std::string& str) { return operator=(Tokens(str)); }
    
    /** Match a topic */
    bool match(const std::string& topic) { return match(Tokens(topic)); }
    bool match(const Tokens& topic) const;

  private:
    void normalize();
};

class TopicExchange : public virtual Exchange{
    typedef std::tr1::unordered_map<TopicPattern, Queue::vector, TopicPattern::Hash> BindingMap;
    BindingMap bindings;
    qpid::concurrent::MonitorImpl lock;

  public:
    static const std::string typeName;

    TopicExchange(const string& name);
        
    virtual void bind(Queue::shared_ptr queue, const string& routingKey, qpid::framing::FieldTable* args);

    virtual void unbind(Queue::shared_ptr queue, const string& routingKey, qpid::framing::FieldTable* args);

    virtual void route(Message::shared_ptr& msg, const string& routingKey, qpid::framing::FieldTable* args);

    virtual ~TopicExchange();
};



}
}

#endif
