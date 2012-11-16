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
#include "qpid/messaging/amqp/AddressHelper.h"
#include "qpid/messaging/Address.h"
#include <vector>
#include <boost/assign.hpp>
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {

using qpid::types::Variant;

namespace {
//policy types
const std::string CREATE("create");
const std::string ASSERT("assert");
const std::string DELETE("delete");

//policy values
const std::string ALWAYS("always");
const std::string NEVER("never");
const std::string RECEIVER("receiver");
const std::string SENDER("sender");

const std::string NODE("node");
const std::string LINK("link");

const std::string TYPE("type");
const std::string TOPIC("topic");
const std::string QUEUE("queue");

//distribution modes:
const std::string MOVE("move");
const std::string COPY("copy");

const std::string SUPPORTED_DIST_MODES("supported-dist-modes");


const std::vector<std::string> RECEIVER_MODES = boost::assign::list_of<std::string>(ALWAYS) (RECEIVER);
const std::vector<std::string> SENDER_MODES = boost::assign::list_of<std::string>(ALWAYS) (SENDER);

pn_bytes_t convert(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}

bool bind(const Variant::Map& options, const std::string& name, std::string& variable)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return false;
    } else {
        variable = j->second.asString();
        return true;
    }
}

bool bind(const Variant::Map& options, const std::string& name, Variant::Map& variable)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return false;
    } else {
        variable = j->second.asMap();
        return true;
    }
}

bool bind(const Address& address, const std::string& name, std::string& variable)
{
    return bind(address.getOptions(), name, variable);
}

bool bind(const Address& address, const std::string& name, Variant::Map& variable)
{
    return bind(address.getOptions(), name, variable);
}

bool in(const std::string& value, const std::vector<std::string>& choices)
{
    for (std::vector<std::string>::const_iterator i = choices.begin(); i != choices.end(); ++i) {
        if (value == *i) return true;
    }
    return false;
}
}

AddressHelper::AddressHelper(const Address& address)
{
    bind(address, CREATE, createPolicy);
    bind(address, DELETE, deletePolicy);
    bind(address, ASSERT, assertPolicy);

    bind(address, NODE, node);
    bind(address, LINK, link);
}

bool AddressHelper::createEnabled(CheckMode mode) const
{
    return enabled(createPolicy, mode);
}
bool AddressHelper::deleteEnabled(CheckMode mode) const
{
    return enabled(deletePolicy, mode);
}
bool AddressHelper::assertEnabled(CheckMode mode) const
{
    return enabled(assertPolicy, mode);
}
bool AddressHelper::enabled(const std::string& policy, CheckMode mode) const
{
    bool result = false;
    switch (mode) {
      case FOR_RECEIVER:
        result = in(policy, RECEIVER_MODES);
        break;
      case FOR_SENDER:
        result = in(policy, SENDER_MODES);
        break;
    }
    return result;
}

const qpid::types::Variant::Map& AddressHelper::getNodeProperties() const
{
    return node;
}
const qpid::types::Variant::Map& AddressHelper::getLinkProperties() const
{
    return link;
}

void AddressHelper::setNodeProperties(pn_terminus_t* terminus)
{
    pn_terminus_set_dynamic(terminus, true);

    //properties for dynamically created node:
    pn_data_t* data = pn_terminus_properties(terminus);
    if (node.size()) {
        pn_data_put_map(data);
        pn_data_enter(data);
    }
    for (qpid::types::Variant::Map::const_iterator i = node.begin(); i != node.end(); ++i) {
        if (i->first == TYPE) {
            pn_data_put_symbol(data, convert(SUPPORTED_DIST_MODES));
            pn_data_put_string(data, convert(i->second == TOPIC ? COPY : MOVE));
        } else {
            pn_data_put_symbol(data, convert(i->first));
            pn_data_put_string(data, convert(i->second.asString()));
        }
    }
    if (node.size()) {
        pn_data_exit(data);
    }
}

}}} // namespace qpid::messaging::amqp
