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
#include "qpid/messaging/AddressImpl.h"
#include "qpid/log/Statement.h"
#include <vector>
#include <set>
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
const std::string CAPABILITIES("capabilities");
const std::string PROPERTIES("properties");
const std::string MODE("mode");
const std::string BROWSE("browse");
const std::string CONSUME("consume");

const std::string TYPE("type");
const std::string TOPIC("topic");
const std::string QUEUE("queue");
const std::string DURABLE("durable");

//distribution modes:
const std::string MOVE("move");
const std::string COPY("copy");

const std::string SUPPORTED_DIST_MODES("supported-dist-modes");
const std::string CREATE_ON_DEMAND("create-on-demand");

const std::string DUMMY(".");

const std::string X_DECLARE("x-declare");
const std::string X_BINDINGS("x-bindings");
const std::string X_SUBSCRIBE("x-subscribe");
const std::string ARGUMENTS("arguments");

const std::vector<std::string> RECEIVER_MODES = boost::assign::list_of<std::string>(ALWAYS) (RECEIVER);
const std::vector<std::string> SENDER_MODES = boost::assign::list_of<std::string>(ALWAYS) (SENDER);

pn_bytes_t convert(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}

bool contains(const Variant::List& list, const std::string& item)
{
    for (Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
        if (*i == item) return true;
    }
    return false;
}

bool test(const Variant::Map& options, const std::string& name)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return false;
    } else {
        return j->second;
    }
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

bool bind(const Variant::Map& options, const std::string& name, Variant::List& variable)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return false;
    } else {
        variable = j->second.asList();
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
void add(Variant::Map& target, const Variant::Map& source)
{
    for (Variant::Map::const_iterator i = source.begin(); i != source.end(); ++i) {
        target[i->first] = i->second;
    }
}
void flatten(Variant::Map& base, const std::string& nested)
{
    Variant::Map::iterator i = base.find(nested);
    if (i != base.end()) {
        add(base, i->second.asMap());
    }
    base.erase(i);
}
}

AddressHelper::AddressHelper(const Address& address) :
    isTemporary(AddressImpl::isTemporary(address)),
    name(address.getName()),
    type(address.getType()),
    durableNode(false),
    durableLink(false),
    browse(false)
{
    bind(address, CREATE, createPolicy);
    bind(address, DELETE, deletePolicy);
    bind(address, ASSERT, assertPolicy);

    bind(address, NODE, node);
    bind(address, LINK, link);
    bind(node, PROPERTIES, properties);
    bind(node, CAPABILITIES, capabilities);
    durableNode = test(node, DURABLE);
    durableLink = test(link, DURABLE);
    std::string mode;
    if (bind(address, MODE, mode)) {
        if (mode == BROWSE) {
            browse = true;
        } else if (mode != CONSUME) {
            throw qpid::messaging::AddressError("Invalid value for mode; must be 'browse' or 'consume'.");
        }
    }

    if (!deletePolicy.empty()) {
        throw qpid::messaging::AddressError("Delete policies not supported over AMQP 1.0.");
    }
    if (node.find(X_BINDINGS) != node.end()) {
        throw qpid::messaging::AddressError("Node scoped x-bindings element not supported over AMQP 1.0.");
    }
    if (link.find(X_BINDINGS) != link.end()) {
        throw qpid::messaging::AddressError("Link scoped x-bindings element not supported over AMQP 1.0.");
    }
    if (link.find(X_SUBSCRIBE) != link.end()) {
        throw qpid::messaging::AddressError("Link scoped x-subscribe element not supported over AMQP 1.0.");
    }
    if (link.find(X_DECLARE) != link.end()) {
        throw qpid::messaging::AddressError("Link scoped x-declare element not supported over AMQP 1.0.");
    }
    //massage x-declare into properties
    Variant::Map::iterator i = node.find(X_DECLARE);
    if (i != node.end()) {
        Variant::Map x_declare = i->second.asMap();
        flatten(x_declare, ARGUMENTS);
        add(properties, x_declare);
        node.erase(i);
    }

    if (properties.size() && !(isTemporary || createPolicy.size())) {
        QPID_LOG(warning, "Properties will be ignored! " << address);
    }
}

void AddressHelper::checkAssertion(pn_terminus_t* terminus, CheckMode mode)
{
    if (assertEnabled(mode)) {
        QPID_LOG(debug, "checking assertions: " << capabilities);
        //ensure all desired capabilities have been offerred
        std::set<std::string> desired;
        if (type.size()) desired.insert(type);
        if (durableNode) desired.insert(DURABLE);
        for (Variant::List::const_iterator i = capabilities.begin(); i != capabilities.end(); ++i) {
            desired.insert(i->asString());
        }
        pn_data_t* data = pn_terminus_capabilities(terminus);
        while (pn_data_next(data)) {
            pn_bytes_t c = pn_data_get_symbol(data);
            std::string s(c.start, c.size);
            desired.erase(s);
        }

        if (desired.size()) {
            std::stringstream missing;
            missing << "Desired capabilities not met: ";
            bool first(true);
            for (std::set<std::string>::const_iterator i = desired.begin(); i != desired.end(); ++i) {
                if (first) first = false;
                else missing << ", ";
                missing << *i;
            }
            throw qpid::messaging::AssertionFailed(missing.str());
        }
    }
}

bool AddressHelper::createEnabled(CheckMode mode) const
{
    return enabled(createPolicy, mode);
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

void AddressHelper::configure(pn_terminus_t* terminus, CheckMode mode)
{
    bool createOnDemand(false);
    if (isTemporary) {
        //application expects a name to be generated
        pn_terminus_set_address(terminus, DUMMY.c_str());//workaround for PROTON-277
        pn_terminus_set_dynamic(terminus, true);
        setNodeProperties(terminus);
    } else {
        pn_terminus_set_address(terminus, name.c_str());
        if (createEnabled(mode)) {
            //application expects name of node to be as specified
            setNodeProperties(terminus);
            createOnDemand = true;
        }
    }
    setCapabilities(terminus, createOnDemand);
    if (durableLink) {
        pn_terminus_set_durability(terminus, PN_DELIVERIES);
    }
    if (mode == FOR_RECEIVER && browse) {
        //when PROTON-139 is resolved, set the required delivery-mode
    }
}

void AddressHelper::setCapabilities(pn_terminus_t* terminus, bool create)
{
    pn_data_t* data = pn_terminus_capabilities(terminus);
    if (create) pn_data_put_symbol(data, convert(CREATE_ON_DEMAND));
    if (type.size()) pn_data_put_symbol(data, convert(type));
    if (durableNode) pn_data_put_symbol(data, convert(DURABLE));
    for (qpid::types::Variant::List::const_iterator i = capabilities.begin(); i != capabilities.end(); ++i) {
        pn_data_put_symbol(data, convert(i->asString()));
    }
}

void AddressHelper::setNodeProperties(pn_terminus_t* terminus)
{
    if (properties.size() || type.size()) {
        pn_data_t* data = pn_terminus_properties(terminus);
        pn_data_put_map(data);
        pn_data_enter(data);
        if (type.size()) {
            pn_data_put_symbol(data, convert(SUPPORTED_DIST_MODES));
            pn_data_put_string(data, convert(type == TOPIC ? COPY : MOVE));
        }
        if (durableNode) {
            pn_data_put_symbol(data, convert(DURABLE));
            pn_data_put_bool(data, true);
        }
        for (qpid::types::Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            pn_data_put_symbol(data, convert(i->first));
            pn_data_put_string(data, convert(i->second.asString()));
        }
        pn_data_exit(data);
    }
}

}}} // namespace qpid::messaging::amqp
