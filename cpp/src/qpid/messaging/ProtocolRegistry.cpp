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
#include "ProtocolRegistry.h"
#include "qpid/Exception.h"
#include "qpid/client/amqp0_10/ConnectionImpl.h"
#include "qpid/client/LoadPlugins.h"
#include <map>

using qpid::types::Variant;

namespace qpid {
namespace messaging {
namespace {
typedef std::map<std::string, ProtocolRegistry::Factory*> Registry;

Registry& theRegistry()
{
    static Registry factories;
    return factories;
}

bool extract(const std::string& key, Variant& value, const Variant::Map& in, Variant::Map& out)
{
    bool matched = false;
    for (Variant::Map::const_iterator i = in.begin(); i != in.end(); ++i) {
        if (i->first == key) {
            value = i->second;
            matched = true;
        } else {
            out.insert(*i);
        }
    }
    return matched;
}
}

ConnectionImpl* ProtocolRegistry::create(const std::string& url, const Variant::Map& options)
{
    qpid::client::theModuleLoader();//ensure modules are loaded
    Variant name;
    Variant::Map stripped;
    if (extract("protocol", name, options, stripped)) {
        Registry::const_iterator i = theRegistry().find(name.asString());
        if (i != theRegistry().end()) return (i->second)(url, stripped);
        else if (name.asString() == "amqp0-10") return new qpid::client::amqp0_10::ConnectionImpl(url, stripped);
        else throw qpid::Exception("Unsupported protocol: " + name.asString());
    }
    return 0;
}
void ProtocolRegistry::add(const std::string& name, Factory* factory)
{
    theRegistry()[name] = factory;
}

}} // namespace qpid::messaging
