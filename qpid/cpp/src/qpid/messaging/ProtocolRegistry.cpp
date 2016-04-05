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
#include "qpid/messaging/exceptions.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/amqp0_10/ConnectionImpl.h"
#include "qpid/client/LoadPlugins.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
#include "qpid/Options.h"
#include "qpid/StringUtils.h"
#include "config.h"
#include <map>
#include <sstream>
#include <boost/bind.hpp>

using qpid::types::Variant;

namespace qpid {
namespace messaging {
namespace {
struct ProtocolOptions : qpid::Options
{
    std::string protocolDefaults;

    ProtocolOptions() : qpid::Options("Protocol Settings")
    {
        addOptions()
            ("protocol-defaults", optValue(protocolDefaults, "PROTOCOLS"), "Protocols to use when none are specified");
    }
};
const std::string SEPARATOR(", ");
const std::string EMPTY;
std::string join(const std::vector<std::string>& in, const std::string& base=EMPTY, const std::string& separator = SEPARATOR)
{
    std::stringstream out;
    if (!base.empty()) out << base;
    for (std::vector<std::string>::const_iterator i = in.begin(); i != in.end(); ++i) {
        if (i != in.begin()) out << separator;
        out << *i;
    }
    return out.str();
}

typedef std::map<std::string, ProtocolRegistry::Factory*> Factories;
typedef std::vector<ProtocolRegistry::Shutdown*> Shutdowns;

ConnectionImpl* create_0_10(const std::string& url, const qpid::types::Variant::Map& options)
{
    return new qpid::client::amqp0_10::ConnectionImpl(url, options);
}

class Registry
{
  public:
    Registry()
    {
        factories["amqp0-10"] = &create_0_10;
        shutdowns.push_back(&qpid::client::shutdown);
        CommonOptions common("", "", QPIDC_CONF_FILE);
        ProtocolOptions options;
        try {
            common.parse(0, 0, common.clientConfig, true);
            options.parse (0, 0, common.clientConfig, true);
        } catch (const std::exception& e) {
            throw qpid::types::Exception(QPID_MSG("Failed to parse options while initialising Protocol Registry: " << e.what()));
        }
        QPID_LOG(debug, "Protocol defaults: " << options.protocolDefaults);
        if (!options.protocolDefaults.empty()) {
            split(versions, options.protocolDefaults, ", ");
        }
    }
    ProtocolRegistry::Factory* find(const std::string& name) const
    {
        Factories::const_iterator i = factories.find(name);
        if (i == factories.end()) {
            std::stringstream error;
            error << "Unsupported protocol: " << name;
            error << " (valid values are " << getNames() << ")";
            throw MessagingException(error.str());
        } else {
            return i->second;
        }
    }
    void add(const std::string& name, ProtocolRegistry::Factory* factory, ProtocolRegistry::Shutdown* shutdown)
    {
        factories[name] = factory;
        shutdowns.push_back(shutdown);
    }
    std::string getNames() const
    {
        std::stringstream names;
        for (Factories::const_iterator i = factories.begin(); i != factories.end(); ++i) {
            if (i != factories.begin()) names << ", ";
            names << i->first;
        }
        return names.str();
    }
    void collectNames(std::vector<std::string>& names) const
    {
        for (std::vector< std::string >::const_iterator i = versions.begin(); i != versions.end(); ++i) {
            Factories::const_iterator j = factories.find(*i);
            if (j == factories.end()) {
                QPID_LOG(notice, "Unsupported protocol specified in defaults " << *i);
            } else {
                names.push_back(*i);
            }
        }
        if (names.empty()) {
            if (!versions.empty()) {
                QPID_LOG(warning, "Protocol defaults specified are not valid (" << join(versions) << ") falling back to  " << getNames());
            }
            for (Factories::const_iterator i = factories.begin(); i != factories.end(); ++i) {
                names.push_back(i->first);
            }
        }
    }
    void shutdown() {
        sys::Mutex::ScopedLock l(shutdownLock);
        while (!shutdowns.empty()) {
            shutdowns.back()();
            shutdowns.pop_back();
        }
    }
  private:
    Factories factories;
    Shutdowns shutdowns;
    sys::Mutex shutdownLock;
    std::vector<std::string> versions;
};

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
    std::vector<std::string> versions;
    if (extract("protocol", name, options, stripped)) {
        split(versions, name.asString(), ", ");
    } else {
        theRegistry().collectNames(versions);
    }
    bool debugOn;
    QPID_LOG_TEST(debug, debugOn);
    if (debugOn) {
        QPID_LOG(debug, "Trying versions " << join(versions));
    }
    return createInternal(versions, url, stripped, join(versions, "No suitable protocol version supported by peer, tried "));
}

ConnectionImpl* ProtocolRegistry::createInternal(const std::vector<std::string>& requested, const std::string& url, const Variant::Map& options, const std::string& error)
{
    std::vector<std::string>::const_iterator i = requested.begin();
    if (i == requested.end())
        throw MessagingException(error);
    std::string name = *i;
    ConnectionImpl* result = theRegistry().find(name)(url, options);
    result->next = boost::bind(&ProtocolRegistry::createInternal, std::vector<std::string>(++i, requested.end()), url, options, error);
    return result;
 }

ConnectionImpl* ProtocolRegistry::next(ConnectionImpl* last)
{
    if (last->next) {
        return last->next();
    }
    throw MessagingException("No suitable protocol version supported by peer");
}

void ProtocolRegistry::add(const std::string& name, Factory* factory, Shutdown* shutdown)
{
    theRegistry().add(name, factory, shutdown);
}

void ProtocolRegistry::shutdown() {
    theRegistry().shutdown();
}


}} // namespace qpid::messaging
