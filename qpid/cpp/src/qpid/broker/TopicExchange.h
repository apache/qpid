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
#ifndef _TopicExchange_
#define _TopicExchange_

#include <map>
#include <vector>
#include "Exchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Monitor.h"
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
    /** Tokenizing assignment operator s */
    Tokens & operator=(const std::string& s);
    void key(std::string& key) const;
    
  private:
    size_t hash;
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

class TopicExchange : public virtual Exchange {
    struct BoundKey {
        Binding::vector bindingVector;
        FedBinding fedBinding;
    };
    typedef std::map<TopicPattern, BoundKey> BindingMap;
    BindingMap bindings;
    qpid::sys::RWlock lock;

    bool isBound(Queue::shared_ptr queue, TopicPattern& pattern);
  public:
    static const std::string typeName;

    TopicExchange(const string& name, management::Manageable* parent = 0);
    TopicExchange(const string& _name, bool _durable, 
                  const qpid::framing::FieldTable& _args, management::Manageable* parent = 0);

    virtual std::string getType() const { return typeName; }            

    virtual bool bind(Queue::shared_ptr queue, const string& routingKey, const qpid::framing::FieldTable* args);

    virtual bool unbind(Queue::shared_ptr queue, const string& routingKey, const qpid::framing::FieldTable* args);

    virtual void route(Deliverable& msg, const string& routingKey, const qpid::framing::FieldTable* args);

    virtual bool isBound(Queue::shared_ptr queue, const string* const routingKey, const qpid::framing::FieldTable* const args);

    virtual ~TopicExchange();
    virtual bool supportsDynamicBinding() { return true; }
};



}
}

#endif
