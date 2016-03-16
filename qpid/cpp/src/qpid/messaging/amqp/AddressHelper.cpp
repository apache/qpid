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

#include "PnData.h"
#include "qpid/messaging/amqp/AddressHelper.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/AddressImpl.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/types/encodings.h"
#include "qpid/log/Statement.h"
#include <vector>
#include <set>
#include <sstream>
#include <boost/assign.hpp>
#include <boost/format.hpp>
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
const std::string TIMEOUT("timeout");

const std::string TYPE("type");
const std::string TOPIC("topic");
const std::string QUEUE("queue");
const std::string DURABLE("durable");
const std::string NAME("name");
const std::string RELIABILITY("reliability");
const std::string SELECTOR("selector");
const std::string FILTER("filter");
const std::string DESCRIPTOR("descriptor");
const std::string VALUE("value");
const std::string SUBJECT_FILTER("subject-filter");
const std::string SOURCE("sender-source");
const std::string TARGET("receiver-target");

//reliability options:
const std::string UNRELIABLE("unreliable");
const std::string AT_MOST_ONCE("at-most-once");
const std::string AT_LEAST_ONCE("at-least-once");
const std::string EXACTLY_ONCE("exactly-once");

//distribution modes:
const std::string MOVE("move");
const std::string COPY("copy");

const std::string SUPPORTED_DIST_MODES("supported-dist-modes");
const std::string AUTO_DELETE("auto-delete");
const std::string LIFETIME_POLICY("lifetime-policy");
const std::string DELETE_ON_CLOSE("delete-on-close");
const std::string DELETE_IF_UNUSED("delete-if-unused");
const std::string DELETE_IF_EMPTY("delete-if-empty");
const std::string DELETE_IF_UNUSED_AND_EMPTY("delete-if-unused-and-empty");
const std::string CREATE_ON_DEMAND("create-on-demand");

const std::string X_DECLARE("x-declare");
const std::string X_BINDINGS("x-bindings");
const std::string X_SUBSCRIBE("x-subscribe");
const std::string ARGUMENTS("arguments");
const std::string EXCHANGE_TYPE("exchange-type");

const std::string NULL_ADDRESS("<null>");

const std::vector<std::string> RECEIVER_MODES = boost::assign::list_of<std::string>(ALWAYS) (RECEIVER);
const std::vector<std::string> SENDER_MODES = boost::assign::list_of<std::string>(ALWAYS) (SENDER);

class Verifier
{
  public:
    Verifier();
    void verify(const Address& address) const;
  private:
    Variant::Map defined;
    void verify(const Variant::Map& allowed, const Variant::Map& actual) const;
};
const Verifier verifier;

pn_bytes_t convert(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}

std::string convert(pn_bytes_t in)
{
    return std::string(in.start, in.size);
}

bool hasWildcards(const std::string& key)
{
    return key.find('*') != std::string::npos || key.find('#') != std::string::npos;
}

uint64_t getFilterDescriptor(const std::string& key)
{
    return hasWildcards(key) ? qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE : qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE;
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

template <typename T> T get(const Variant::Map& options, const std::string& name, T defaultValue)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return defaultValue;
    } else {
        return j->second;
    }
}

bool getOption(const Variant::Map& options, const std::string& name, std::string& variable)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return false;
    } else {
        variable = j->second.asString();
        return true;
    }
}

bool getOption(const Variant::Map& options, const std::string& name, Variant::Map& variable)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return false;
    } else {
        variable = j->second.asMap();
        return true;
    }
}

bool getOption(const Variant::Map& options, const std::string& name, Variant::List& variable)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return false;
    } else {
        variable = j->second.asList();
        return true;
    }
}

bool getAddressOption(const Address& address, const std::string& name, std::string& variable)
{
    return getOption(address.getOptions(), name, variable);
}

bool getAddressOption(const Address& address, const std::string& name, Variant::Map& variable)
{
    return getOption(address.getOptions(), name, variable);
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
        base.erase(i);
    }
}
bool replace(Variant::Map& map, const std::string& original, const std::string& desired)
{
    Variant::Map::iterator i = map.find(original);
    if (i != map.end()) {
        map[desired] = i->second;
        map.erase(original);
        return true;
    } else {
        return false;
    }
}

const uint32_t DEFAULT_DURABLE_TIMEOUT(2*60);//2 minutes
const uint32_t DEFAULT_TIMEOUT(0);
}

AddressHelper::AddressHelper(const Address& address) :
    isTemporary(AddressImpl::isTemporary(address)),
    name(address.getName()),
    type(address.getType()),
    durableNode(false),
    durableLink(false),
    timeout(0),
    browse(false)
{
    verifier.verify(address);
    getAddressOption(address, CREATE, createPolicy);
    getAddressOption(address, DELETE, deletePolicy);
    getAddressOption(address, ASSERT, assertPolicy);

    getAddressOption(address, NODE, node);
    getAddressOption(address, LINK, link);
    getOption(node, PROPERTIES, properties);
    getOption(node, CAPABILITIES, capabilities);
    getOption(link, RELIABILITY, reliability);
    durableNode = test(node, DURABLE);
    durableLink = test(link, DURABLE);
    timeout = get(link, TIMEOUT, durableLink && reliability != AT_LEAST_ONCE ? DEFAULT_DURABLE_TIMEOUT : DEFAULT_TIMEOUT);
    std::string mode;
    if (getAddressOption(address, MODE, mode)) {
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
        replace(x_declare, TYPE, EXCHANGE_TYPE);
        flatten(x_declare, ARGUMENTS);
        add(properties, x_declare);
        node.erase(i);
    }
    //for temp queues, if neither lifetime-policy nor autodelete are specified, assume delete-on-close
    if (isTemporary && properties.find(LIFETIME_POLICY) == properties.end() && properties.find(AUTO_DELETE) == properties.end()) {
        properties[LIFETIME_POLICY] = DELETE_ON_CLOSE;
    }

    if (properties.size() && !(isTemporary || !createPolicy.empty() || !assertPolicy.empty())) {
        QPID_LOG(warning, "Properties will be ignored! " << address);
    }

    qpid::types::Variant::Map::const_iterator selector = link.find(SELECTOR);
    if (selector != link.end()) {
        addFilter(SELECTOR, qpid::amqp::filters::SELECTOR_FILTER_CODE, selector->second);
    }
    if (!address.getSubject().empty()) {
        addFilter(SUBJECT_FILTER, getFilterDescriptor(address.getSubject()), address.getSubject());
    }
    qpid::types::Variant::Map::const_iterator filter = link.find(FILTER);
    if (filter != link.end()) {
        if (filter->second.getType() == qpid::types::VAR_MAP) {
            addFilter(filter->second.asMap());
        } else if (filter->second.getType() == qpid::types::VAR_LIST) {
            addFilters(filter->second.asList());
        } else {
            throw qpid::messaging::AddressError("Filter must be a map or a list of maps, each containing name, descriptor and value.");
        }
    }
}

void AddressHelper::addFilters(const qpid::types::Variant::List& f)
{
    for (qpid::types::Variant::List::const_iterator i = f.begin(); i != f.end(); ++i) {
        addFilter(i->asMap());
    }
}

void AddressHelper::addFilter(const qpid::types::Variant::Map& f)
{
    qpid::types::Variant::Map::const_iterator name = f.find(NAME);
    qpid::types::Variant::Map::const_iterator descriptor = f.find(DESCRIPTOR);
    qpid::types::Variant::Map::const_iterator value = f.find(VALUE);
    //all fields are required at present (may relax this at a later stage):
    if (name == f.end()) {
        throw qpid::messaging::AddressError("Filter entry must specify name");
    }
    if (descriptor == f.end()) {
        throw qpid::messaging::AddressError("Filter entry must specify descriptor");
    }
    if (value == f.end()) {
        throw qpid::messaging::AddressError("Filter entry must specify value");
    }
    try {
        addFilter(name->second.asString(), descriptor->second.asUint64(), value->second);
    } catch (const qpid::types::InvalidConversion&) {
        addFilter(name->second.asString(), descriptor->second.asString(), value->second);
    }

}

AddressHelper::Filter::Filter() : descriptorCode(0), confirmed(false) {}
AddressHelper::Filter::Filter(const std::string& n, uint64_t d, const qpid::types::Variant& v) : name(n), descriptorCode(d), value(v), confirmed(false) {}
AddressHelper::Filter::Filter(const std::string& n, const std::string& d, const qpid::types::Variant& v) : name(n), descriptorSymbol(d), descriptorCode(0), value(v), confirmed(false) {}

void AddressHelper::addFilter(const std::string& name, uint64_t descriptor, const qpid::types::Variant& value)
{
    filters.push_back(Filter(name, descriptor, value));
}
void AddressHelper::addFilter(const std::string& name, const std::string& descriptor, const qpid::types::Variant& value)
{
    filters.push_back(Filter(name, descriptor, value));
}

namespace {
bool checkLifetimePolicy(const std::string& requested, const std::string& actual)
{
    if (actual == qpid::amqp::lifetime_policy::DELETE_ON_CLOSE_SYMBOL && requested == DELETE_ON_CLOSE) return true;
    else if (actual == qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_SYMBOL && requested == DELETE_IF_UNUSED) return true;
    else if (actual == qpid::amqp::lifetime_policy::DELETE_ON_NO_MESSAGES_SYMBOL && requested == DELETE_IF_EMPTY) return true;
    else if (actual == qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_SYMBOL && requested == DELETE_IF_UNUSED_AND_EMPTY) return true;
    else return actual == requested;
}
bool checkLifetimePolicy(const std::string& requested, uint64_t actual)
{
    if (actual == qpid::amqp::lifetime_policy::DELETE_ON_CLOSE_CODE)
        return checkLifetimePolicy(requested, qpid::amqp::lifetime_policy::DELETE_ON_CLOSE_SYMBOL);
    else if (actual == qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_CODE)
        return checkLifetimePolicy(requested, qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_SYMBOL);
    else if (actual == qpid::amqp::lifetime_policy::DELETE_ON_NO_MESSAGES_CODE)
        return checkLifetimePolicy(requested, qpid::amqp::lifetime_policy::DELETE_ON_NO_MESSAGES_SYMBOL);
    else if (actual == qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_CODE)
        return checkLifetimePolicy(requested, qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_SYMBOL);
    else
        return false;
}
bool checkLifetimePolicy(const std::string& requested, pn_data_t* actual)
{
    bool result(false);
    if (pn_data_is_described(actual)) {
        pn_data_enter(actual);
        pn_data_next(actual);
        if (pn_data_type(actual) == PN_ULONG) {
            result = checkLifetimePolicy(requested, pn_data_get_ulong(actual));
        } else if (pn_data_type(actual) == PN_SYMBOL) {
            result = checkLifetimePolicy(requested, convert(pn_data_get_symbol(actual)));
        }
        pn_data_exit(actual);
    }
    return result;
}
}
void AddressHelper::checkAssertion(pn_terminus_t* terminus, CheckMode mode)
{
    if (assertEnabled(mode)) {
        QPID_LOG(debug, "checking capabilities: " << capabilities);
        //ensure all desired capabilities have been offered
        std::set<std::string> desired;
        for (Variant::List::const_iterator i = capabilities.begin(); i != capabilities.end(); ++i) {
            if (*i != CREATE_ON_DEMAND) desired.insert(i->asString());
        }
        pn_data_t* data = pn_terminus_capabilities(terminus);
        if (pn_data_next(data)) {
            pn_type_t type = pn_data_type(data);
            if (type == PN_ARRAY) {
                pn_data_enter(data);
                while (pn_data_next(data)) {
                    desired.erase(convert(pn_data_get_symbol(data)));
                }
                pn_data_exit(data);
            } else if (type == PN_SYMBOL) {
                desired.erase(convert(pn_data_get_symbol(data)));
            } else {
                QPID_LOG(error, "Skipping capabilities field of type " << pn_type_name(type));
            }
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

        //ensure all desired filters are in use
        data = pn_terminus_filter(terminus);
        if (pn_data_next(data)) {
            size_t count = pn_data_get_map(data);
            pn_data_enter(data);
            for (size_t i = 0; i < count && pn_data_next(data); ++i) {
                //skip key:
                if (!pn_data_next(data)) break;
                //expecting described value:
                if (pn_data_is_described(data)) {
                    pn_data_enter(data);
                    pn_data_next(data);
                    if (pn_data_type(data) == PN_ULONG) {
                        confirmFilter(pn_data_get_ulong(data));
                    } else if (pn_data_type(data) == PN_SYMBOL) {
                        confirmFilter(convert(pn_data_get_symbol(data)));
                    }
                    pn_data_exit(data);
                }
            }
            pn_data_exit(data);
        }
        std::stringstream missing;
        missing << "Desired filters not in use: ";
        bool first(true);
        for (std::vector<Filter>::iterator i = filters.begin(); i != filters.end(); ++i) {
            if (!i->confirmed) {
                if (first) first = false;
                else missing << ", ";
                missing << i->name << "(";
                if (i->descriptorSymbol.empty()) missing << "0x" << std::hex << i->descriptorCode;
                else missing << i->descriptorSymbol;
                missing << ")";
            }
        }
        if (!first) throw qpid::messaging::AssertionFailed(missing.str());

        //assert on properties (Note: this violates the AMQP 1.0
        //specification - as does the create option - by sending
        //node-properties even if the dynamic option is not
        //set. However this can be avoided by not specifying any node
        //properties when asserting)
        if (!type.empty() || durableNode || !properties.empty()) {
            bool isAutoDeleted = false;
            qpid::types::Variant::Map requested = properties;
            if (!type.empty()) requested[SUPPORTED_DIST_MODES] = type == TOPIC ? COPY : MOVE;
            if (durableNode) requested[DURABLE] = true;

            data = pn_terminus_properties(terminus);
            if (pn_data_next(data)) {
                size_t count = pn_data_get_map(data);
                pn_data_enter(data);
                for (size_t i = 0; i < count && pn_data_next(data); ++i) {
                    std::string key = convert(pn_data_get_symbol(data));
                    pn_data_next(data);
                    qpid::types::Variant::Map::const_iterator j = requested.find(key);
                    qpid::types::Variant v;
                    if (key == LIFETIME_POLICY) {
                        isAutoDeleted = true;
                        if (j != requested.end() && checkLifetimePolicy(j->second.asString(), data)) {
                            requested.erase(j->first);
                        }
                    } else if (key == AUTO_DELETE) {
                        PnData(data).get(v);
                        isAutoDeleted = v.asBool();
                    } else if (j != requested.end() && (PnData(data).get(v) && v.asString() == j->second.asString())) {
                        requested.erase(j->first);
                    }
                }
                pn_data_exit(data);
                qpid::types::Variant::Map::iterator i = requested.find(AUTO_DELETE);
                if (i != requested.end() && i->second.asBool() == isAutoDeleted) {
                    requested.erase(i);
                }
                if (!requested.empty()) {
                    std::stringstream missing;
                    missing << "Requested node properties not met: " << requested;
                    throw qpid::messaging::AssertionFailed(missing.str());
                }
            }
        }
    }
}

void AddressHelper::confirmFilter(const std::string& descriptor)
{
    for (std::vector<Filter>::iterator i = filters.begin(); i != filters.end(); ++i) {
        if (descriptor == i->descriptorSymbol) i->confirmed = true;
    }
}

void AddressHelper::confirmFilter(uint64_t descriptor)
{
    for (std::vector<Filter>::iterator i = filters.begin(); i != filters.end(); ++i) {
        if (descriptor == i->descriptorCode) i->confirmed = true;
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

bool AddressHelper::isNameNull() const
{
    return name == NULL_ADDRESS;
}

bool AddressHelper::isUnreliable() const
{
    return reliability == AT_MOST_ONCE || reliability == UNRELIABLE ||
        (reliability.empty() && browse); // A browser defaults to unreliable.
}

const qpid::types::Variant::Map& AddressHelper::getNodeProperties() const
{
    return node;
}
const qpid::types::Variant::Map& AddressHelper::getLinkProperties() const
{
    return link;
}

bool AddressHelper::getLinkSource(std::string& out) const
{
    return getLinkOption(SOURCE, out);
}

bool AddressHelper::getLinkTarget(std::string& out) const
{
    return getLinkOption(TARGET, out);
}

bool AddressHelper::getLinkOption(const std::string& name, std::string& out) const
{
    qpid::types::Variant::Map::const_iterator i = link.find(name);
    if (i != link.end()) {
        out = i->second.asString();
        return true;
    } else {
        return false;
    }
}

void AddressHelper::configure(pn_link_t* link, pn_terminus_t* terminus, CheckMode mode)
{
    bool createOnDemand(false);
    if (isTemporary) {
        //application expects a name to be generated
        pn_terminus_set_dynamic(terminus, true);
        setNodeProperties(terminus);
    } else if (name != NULL_ADDRESS) {
        pn_terminus_set_address(terminus, name.c_str());
        if (createEnabled(mode)) {
            //application expects name of node to be as specified
            setNodeProperties(terminus);
            createOnDemand = true;
        } else if (assertEnabled(mode)) {
            setNodeProperties(terminus);
        }
    }

    setCapabilities(terminus, createOnDemand);
    if (durableLink) {
        pn_terminus_set_durability(terminus, PN_DELIVERIES);
    }
    if (mode == FOR_RECEIVER) {
        if (timeout) pn_terminus_set_timeout(terminus, timeout);
        if (browse) {
            pn_terminus_set_distribution_mode(terminus, PN_DIST_MODE_COPY);
        }
        //set filter(s):
        if (!filters.empty()) {
            pn_data_t* filter = pn_terminus_filter(terminus);
            pn_data_put_map(filter);
            pn_data_enter(filter);
            for (std::vector<Filter>::const_iterator i = filters.begin(); i != filters.end(); ++i) {
                pn_data_put_symbol(filter, convert(i->name));
                pn_data_put_described(filter);
                pn_data_enter(filter);
                if (i->descriptorSymbol.size()) {
                    pn_data_put_symbol(filter, convert(i->descriptorSymbol));
                } else {
                    pn_data_put_ulong(filter, i->descriptorCode);
                }
                PnData(filter).put(i->value);
                pn_data_exit(filter);
            }
            pn_data_exit(filter);
        }
    }
    if (isUnreliable()) {
        pn_link_set_snd_settle_mode(link, PN_SND_SETTLED);
    } else if (!reliability.empty()) {
        if (reliability == EXACTLY_ONCE ) {
            QPID_LOG(warning, "Unsupported reliability mode: " << reliability);
        } else if (reliability != AT_LEAST_ONCE ) {
            QPID_LOG(warning, "Unrecognised reliability mode: " << reliability);
        }
        pn_link_set_snd_settle_mode(link, PN_SND_UNSETTLED);
    }
}

void AddressHelper::setCapabilities(pn_terminus_t* terminus, bool create)
{
    if (create) capabilities.push_back(CREATE_ON_DEMAND);
    if (!type.empty()) capabilities.push_back(type);
    if (durableNode) capabilities.push_back(DURABLE);

    pn_data_t* data = pn_terminus_capabilities(terminus);
    if (capabilities.size() == 1) {
        pn_data_put_symbol(data, convert(capabilities.front().asString()));
    } else if (capabilities.size() > 1) {
        pn_data_put_array(data, false, PN_SYMBOL);
        pn_data_enter(data);
        for (qpid::types::Variant::List::const_iterator i = capabilities.begin(); i != capabilities.end(); ++i) {
            pn_data_put_symbol(data, convert(i->asString()));
        }
        pn_data_exit(data);
    }
}
std::string AddressHelper::getLinkName(const Address& address)
{
    AddressHelper helper(address);
    const qpid::types::Variant::Map& linkProps = helper.getLinkProperties();
    qpid::types::Variant::Map::const_iterator i = linkProps.find(NAME);
    if (i != linkProps.end()) {
        return i->second.asString();
    } else {
        std::stringstream name;
        name << address.getName() << "_" << qpid::types::Uuid(true);
        return name.str();
    }
}
namespace {
std::string toLifetimePolicy(const std::string& value)
{
    if (value == DELETE_ON_CLOSE) return qpid::amqp::lifetime_policy::DELETE_ON_CLOSE_SYMBOL;
    else if (value == DELETE_IF_UNUSED) return qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_SYMBOL;
    else if (value == DELETE_IF_EMPTY) return qpid::amqp::lifetime_policy::DELETE_ON_NO_MESSAGES_SYMBOL;
    else if (value == DELETE_IF_UNUSED_AND_EMPTY) return qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_SYMBOL;
    else return value;//asume value is itself the symbolic descriptor
}
void putLifetimePolicy(pn_data_t* data, const std::string& value)
{
    pn_data_put_described(data);
    pn_data_enter(data);
    pn_data_put_symbol(data, convert(value));
    pn_data_put_list(data);
    pn_data_exit(data);
}
}
void AddressHelper::setNodeProperties(pn_terminus_t* terminus)
{
    if (properties.size() || type.size() || durableNode) {
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
            if (i->first == LIFETIME_POLICY) {
                pn_data_put_symbol(data, convert(i->first));
                putLifetimePolicy(data, toLifetimePolicy(i->second.asString()));
            } else {
                pn_data_put_symbol(data, convert(i->first));
                PnData(data).put(i->second);
            }
        }
        pn_data_exit(data);
    }
}

Verifier::Verifier()
{
    defined[CREATE] = true;
    defined[ASSERT] = true;
    defined[DELETE] = true;
    defined[MODE] = true;
    Variant::Map node;
    node[TYPE] = true;
    node[DURABLE] = true;
    node[PROPERTIES] = true;
    node[CAPABILITIES] = true;
    node[X_DECLARE] = true;
    node[X_BINDINGS] = true;
    defined[NODE] = node;
    Variant::Map link;
    link[NAME] = true;
    link[DURABLE] = true;
    link[RELIABILITY] = true;
    link[TIMEOUT] = true;
    link[SOURCE] = true;
    link[TARGET] = true;
    link[X_SUBSCRIBE] = true;
    link[X_DECLARE] = true;
    link[X_BINDINGS] = true;
    link[SELECTOR] = true;
    link[FILTER] = true;
    defined[LINK] = link;
}
void Verifier::verify(const Address& address) const
{
    verify(defined, address.getOptions());
}

void Verifier::verify(const Variant::Map& allowed, const Variant::Map& actual) const
{
    for (Variant::Map::const_iterator i = actual.begin(); i != actual.end(); ++i) {
        Variant::Map::const_iterator option = allowed.find(i->first);
        if (option == allowed.end()) {
            throw AddressError((boost::format("Unrecognised option: %1%") % i->first).str());
        } else if (option->second.getType() == qpid::types::VAR_MAP) {
            verify(option->second.asMap(), i->second.asMap());
        }
    }
}

}}} // namespace qpid::messaging::amqp
