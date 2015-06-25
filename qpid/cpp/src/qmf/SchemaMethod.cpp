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

#include "qmf/SchemaMethodImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"
#include "qmf/Hash.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/management/Buffer.h"

using namespace std;
using qpid::types::Variant;
using namespace qmf;

typedef PrivateImplRef<SchemaMethod> PI;

SchemaMethod::SchemaMethod(SchemaMethodImpl* impl) { PI::ctor(*this, impl); }
SchemaMethod::SchemaMethod(const SchemaMethod& s) : qmf::Handle<SchemaMethodImpl>() { PI::copy(*this, s); }
SchemaMethod::~SchemaMethod() { PI::dtor(*this); }
SchemaMethod& SchemaMethod::operator=(const SchemaMethod& s) { return PI::assign(*this, s); }

SchemaMethod::SchemaMethod(const string& n, const string& o) { PI::ctor(*this, new SchemaMethodImpl(n, o)); }
void SchemaMethod::setDesc(const string& d) { impl->setDesc(d); }
void SchemaMethod::addArgument(const SchemaProperty& p) { impl->addArgument(p); }
const string& SchemaMethod::getName() const { return impl->getName(); }
const string& SchemaMethod::getDesc() const { return impl->getDesc(); }
uint32_t SchemaMethod::getArgumentCount() const { return impl->getArgumentCount(); }
SchemaProperty SchemaMethod::getArgument(uint32_t i) const { return impl->getArgument(i); }

//========================================================================================
// Impl Method Bodies
//========================================================================================

SchemaMethodImpl::SchemaMethodImpl(const string& n, const string& options) : name(n)
{
    if (!options.empty()) {
        qpid::messaging::AddressParser parser = qpid::messaging::AddressParser(options);
        Variant::Map optMap;
        Variant::Map::iterator iter;

        parser.parseMap(optMap);
        iter = optMap.find("desc");
        if (iter != optMap.end()) {
            desc = iter->second.asString();
            optMap.erase(iter);
        }

        if (!optMap.empty())
            throw QmfException("Unrecognized option: " + optMap.begin()->first);
    }
}


SchemaMethodImpl::SchemaMethodImpl(const qpid::types::Variant::Map& map)
{
    Variant::Map::const_iterator iter;
    Variant::List::const_iterator lIter;

    iter = map.find("_name");
    if (iter == map.end())
        throw QmfException("SchemaMethod without a _name element");
    name = iter->second.asString();

    iter = map.find("_desc");
    if (iter != map.end())
        desc = iter->second.asString();

    iter = map.find("_arguments");
    if (iter != map.end()) {
        const Variant::List& argList(iter->second.asList());
        for (lIter = argList.begin(); lIter != argList.end(); lIter++)
            addArgument(SchemaProperty(new SchemaPropertyImpl(lIter->asMap())));
    }
}


Variant::Map SchemaMethodImpl::asMap() const
{
    Variant::Map map;
    Variant::List argList;

    map["_name"] = name;

    if (!desc.empty())
        map["_desc"] = desc;

    for (list<SchemaProperty>::const_iterator iter = arguments.begin(); iter != arguments.end(); iter++)
        argList.push_back(SchemaPropertyImplAccess::get(*iter).asMap());
    map["_arguments"] = argList;

    return map;
}


SchemaMethodImpl::SchemaMethodImpl(qpid::management::Buffer& buffer)
{
    Variant::Map::const_iterator iter;
    Variant::Map argMap;

    buffer.getMap(argMap);

    iter = argMap.find("name");
    if (iter == argMap.end())
        throw QmfException("Received V1 Method without a name");
    name = iter->second.asString();

    iter = argMap.find("desc");
    if (iter != argMap.end())
        desc = iter->second.asString();

    iter = argMap.find("argCount");
    if (iter == argMap.end())
        throw QmfException("Received V1 Method without argCount");

    int64_t count = iter->second.asInt64();
    for (int idx = 0; idx < count; idx++) {
        SchemaProperty arg(new SchemaPropertyImpl(buffer));
        addArgument(arg);
    }
}


SchemaProperty SchemaMethodImpl::getArgument(uint32_t i) const
{
    uint32_t count = 0;
    for (list<SchemaProperty>::const_iterator iter = arguments.begin(); iter != arguments.end(); iter++)
        if (count++ == i)
            return *iter;
    
    throw IndexOutOfRange();
}


void SchemaMethodImpl::updateHash(Hash& hash) const
{
    hash.update(name);
    hash.update(desc);
    for (list<SchemaProperty>::const_iterator iter = arguments.begin(); iter != arguments.end(); iter++)
        SchemaPropertyImplAccess::get(*iter).updateHash(hash);
}


void SchemaMethodImpl::encodeV1(qpid::management::Buffer& buffer) const
{
    Variant::Map map;

    map["name"] = name;
    map["argCount"] = (uint64_t) arguments.size();
    if (!desc.empty())
        map["desc"] = desc;

    buffer.putMap(map);

    for (list<SchemaProperty>::const_iterator iter = arguments.begin(); iter != arguments.end(); iter++)
        SchemaPropertyImplAccess::get(*iter).encodeV1(buffer, true, true);
}


SchemaMethodImpl& SchemaMethodImplAccess::get(SchemaMethod& item)
{
    return *item.impl;
}


const SchemaMethodImpl& SchemaMethodImplAccess::get(const SchemaMethod& item)
{
    return *item.impl;
}
