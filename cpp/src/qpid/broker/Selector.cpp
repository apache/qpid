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

#include "qpid/broker/Selector.h"

#include "qpid/broker/Message.h"

#include <boost/make_shared.hpp>

namespace qpid {
namespace broker {

using std::string;

Selector::Selector(const string& e) :
    expression(e)
{
}

Selector::~Selector()
{
}

bool Selector::eval(const SelectorEnv& env)
{
    // Simple test - return true if expression is a non-empty property
    return env.present(expression);
}

bool Selector::filter(const Message& msg)
{
    return eval(MessageSelectorEnv(msg));
}

MessageSelectorEnv::MessageSelectorEnv(const Message& m) :
    msg(m)
{
}

bool MessageSelectorEnv::present(const string& identifier) const
{
    // If the value we get is void then most likely the property wasn't present
    return !msg.getProperty(identifier).isVoid();
}

string MessageSelectorEnv::value(const string& identifier) const
{
    // Just return property as string
    return msg.getPropertyAsString(identifier);
}


namespace {
const boost::shared_ptr<Selector> NULL_SELECTOR = boost::shared_ptr<Selector>();
}

boost::shared_ptr<Selector> returnSelector(const string& e)
{
    if (e.empty()) return NULL_SELECTOR;
    return boost::make_shared<Selector>(e);
}


}}
