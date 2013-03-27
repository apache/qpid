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
#include "qpid/messaging/Address.h"
#include "qpid/messaging/AddressImpl.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/framing/Uuid.h"
#include <sstream>
#include <boost/format.hpp>

namespace qpid {
namespace messaging {

using namespace qpid::types;

namespace {
const std::string SUBJECT_DIVIDER = "/";
const std::string OPTIONS_DIVIDER = ";";
const std::string SPACE = " ";
const std::string TYPE = "type";
}
Address::Address() : impl(new AddressImpl()) {}
Address::Address(const std::string& address) : impl(new AddressImpl())
{
    AddressParser parser(address);
    parser.parse(*this);
}
Address::Address(const std::string& name, const std::string& subject, const Variant::Map& options,
                 const std::string& type)
    : impl(new AddressImpl(name, subject, options)) { setType(type); }
Address::Address(const Address& a) :
    impl(new AddressImpl(a.impl->name, a.impl->subject, a.impl->options)) { impl->temporary = a.impl->temporary; }
Address::~Address() { delete impl; }

Address& Address::operator=(const Address& a) { *impl = *a.impl; return *this; }


std::string Address::str() const
{
    std::stringstream out;
    out << impl->name;
    if (!impl->subject.empty()) out << SUBJECT_DIVIDER << impl->subject;
    if (!impl->options.empty()) out << OPTIONS_DIVIDER << impl->options;
    return out.str();
}
Address::operator bool() const { return !impl->name.empty(); }
bool Address::operator !() const { return impl->name.empty(); }

const std::string& Address::getName() const { return impl->name; }
void Address::setName(const std::string& name) { impl->name = name; }
const std::string& Address::getSubject() const { return impl->subject; }
void Address::setSubject(const std::string& subject) { impl->subject = subject; }
const Variant::Map& Address::getOptions() const { return impl->options; }
Variant::Map& Address::getOptions() { return impl->options; }
void Address::setOptions(const Variant::Map& options) { impl->options = options; }


namespace{
const Variant EMPTY_VARIANT;
const std::string EMPTY_STRING;
const std::string NODE_PROPERTIES="node";
}

const Variant& find(const Variant::Map& map, const std::string& key)
{
    Variant::Map::const_iterator i = map.find(key);
    if (i == map.end()) return EMPTY_VARIANT;
    else return i->second;
}

std::string Address::getType() const
{
    const Variant& props = find(impl->options, NODE_PROPERTIES);
    if (props.getType() == VAR_MAP) {
        const Variant& type = find(props.asMap(), TYPE);
        if (!type.isVoid()) return type.asString();
    }
    return EMPTY_STRING;
}

void Address::setType(const std::string& type)
{ 
    Variant& props = impl->options[NODE_PROPERTIES];
    if (props.isVoid()) props = Variant::Map();
    props.asMap()[TYPE] = type;
}

std::ostream& operator<<(std::ostream& out, const Address& address)
{
    out << address.str();
    return out;
}

}} // namespace qpid::messaging
