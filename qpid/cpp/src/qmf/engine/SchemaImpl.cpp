/*
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
 */

#include "qmf/engine/SchemaImpl.h"
#include "qmf/Protocol.h"
#include <string.h>
#include <string>
#include <vector>

using namespace std;
using namespace qmf::engine;
using namespace qpid::messaging;

SchemaHash::SchemaHash()
{
    for (int idx = 0; idx < 16; idx++)
        hash[idx] = 0x5A;
}

void SchemaHash::update(uint8_t data)
{
    update((char*) &data, 1);
}

void SchemaHash::update(const char* data, uint32_t len)
{
    uint64_t* first  = (uint64_t*) hash;
    uint64_t* second = (uint64_t*) hash + 1;

    for (uint32_t idx = 0; idx < len; idx++) {
        *first = *first ^ (uint64_t) data[idx];
        *second = *second << 1;
        *second |= ((*first & 0x8000000000000000LL) >> 63);
        *first = *first << 1;
        *first = *first ^ *second;
    }
}

bool SchemaHash::operator==(const SchemaHash& other) const
{
    return ::memcmp(&hash, &other.hash, 16) == 0;
}

bool SchemaHash::operator<(const SchemaHash& other) const
{
    return ::memcmp(&hash, &other.hash, 16) < 0;
}

bool SchemaHash::operator>(const SchemaHash& other) const
{
    return ::memcmp(&hash, &other.hash, 16) > 0;
}


SchemaArgumentImpl::SchemaArgumentImpl(const Variant::Map& map)
{
    Variant::Map::const_iterator iter;

    iter = map.find(Protocol::SCHEMA_ELT_NAME);
    if (iter == map.end())
        throw SchemaException("SchemaArgument", Protocol::SCHEMA_ELT_NAME);
    name = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ELT_TYPE);
    if (iter == map.end())
        throw SchemaException("SchemaArgument", Protocol::SCHEMA_ELT_TYPE);
    typecode = (Typecode) iter->second.asUint8();

    iter = map.find(Protocol::SCHEMA_ELT_UNIT);
    if (iter != map.end())
        unit = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ELT_DESC);
    if (iter != map.end())
        description = iter->second.asString();

    dir = DIR_IN;
    iter = map.find(Protocol::SCHEMA_ELT_DIR);
    if (iter != map.end()) {
        string dstr(iter->second.asString());
        if (dstr == "O")
            dir = DIR_OUT;
        else if (dstr == "IO")
            dir = DIR_IN_OUT;
    }
}

SchemaArgument* SchemaArgumentImpl::factory(Variant::Map& map)
{
    SchemaArgumentImpl* impl(new SchemaArgumentImpl(map));
    return new SchemaArgument(impl);
}

Variant::Map SchemaArgumentImpl::asMap() const
{
    Variant::Map map;

    map[Protocol::SCHEMA_ELT_NAME] = Variant(name);
    map[Protocol::SCHEMA_ELT_TYPE] = Variant((uint8_t) typecode);

    string dirStr;
    if (dir == DIR_IN)
        dirStr = "I";
    else if (dir == DIR_OUT)
        dirStr = "O";
    else
        dirStr = "IO";
    map[Protocol::SCHEMA_ELT_DIR] = Variant(dirStr);

    if (!unit.empty())
        map[Protocol::SCHEMA_ELT_UNIT] = Variant(unit);
    if (!description.empty())
        map[Protocol::SCHEMA_ELT_DESC] = Variant(description);

    return map;
}

void SchemaArgumentImpl::updateHash(SchemaHash& hash) const
{
    hash.update(name);
    hash.update(typecode);
    hash.update(dir);
    hash.update(unit);
    hash.update(description);
}

SchemaMethodImpl::SchemaMethodImpl(const Variant::Map& map)
{
    Variant::Map::const_iterator iter;

    iter = map.find(Protocol::SCHEMA_ELT_NAME);
    if (iter == map.end())
        throw SchemaException("SchemaMethod", Protocol::SCHEMA_ELT_NAME);
    name = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ELT_DESC);
    if (iter != map.end())
        description = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ARGS);
    if (iter != map.end()) {
        Variant::List list(iter->second.asList());
        for (Variant::List::const_iterator aiter = list.begin(); aiter != list.end(); aiter++) {
            Variant::Map argMap(aiter->asMap());
            SchemaArgument* arg = SchemaArgumentImpl::factory(argMap);
            addArgument(arg);
        }
    }
}

SchemaMethod* SchemaMethodImpl::factory(Variant::Map& map)
{
    SchemaMethodImpl* impl(new SchemaMethodImpl(map));
    return new SchemaMethod(impl);
}

Variant::Map SchemaMethodImpl::asMap() const
{
    Variant::Map map;

    map[Protocol::SCHEMA_ELT_NAME] = Variant(name);
    if (!description.empty())
        map[Protocol::SCHEMA_ELT_DESC] = Variant(description);

    Variant::List list;
    for (vector<const SchemaArgument*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++)
        list.push_back((*iter)->impl->asMap());
    map[Protocol::SCHEMA_ARGS] = list;

    return map;
}

void SchemaMethodImpl::addArgument(const SchemaArgument* argument)
{
    arguments.push_back(argument);
}

const SchemaArgument* SchemaMethodImpl::getArgument(int idx) const
{
    int count = 0;
    for (vector<const SchemaArgument*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++, count++)
        if (idx == count)
            return (*iter);
    return 0;
}

void SchemaMethodImpl::updateHash(SchemaHash& hash) const
{
    hash.update(name);
    hash.update(description);
    for (vector<const SchemaArgument*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++)
        (*iter)->impl->updateHash(hash);
}

SchemaPropertyImpl::SchemaPropertyImpl(const Variant::Map& map)
{
    Variant::Map::const_iterator iter;

    iter = map.find(Protocol::SCHEMA_ELT_NAME);
    if (iter == map.end())
        throw SchemaException("SchemaProperty", Protocol::SCHEMA_ELT_NAME);
    name = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ELT_TYPE);
    if (iter == map.end())
        throw SchemaException("SchemaProperty", Protocol::SCHEMA_ELT_TYPE);
    typecode = (Typecode) iter->second.asUint8();

    iter = map.find(Protocol::SCHEMA_ELT_ACCESS);
    if (iter != map.end())
        access = (Access) iter->second.asUint8();

    iter = map.find(Protocol::SCHEMA_ELT_UNIT);
    if (iter != map.end())
        unit = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ELT_DESC);
    if (iter != map.end())
        description = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ELT_OPTIONAL);
    if (iter != map.end())
        optional = true;
}

SchemaProperty* SchemaPropertyImpl::factory(Variant::Map& map)
{
    SchemaPropertyImpl* impl(new SchemaPropertyImpl(map));
    return new SchemaProperty(impl);
}

Variant::Map SchemaPropertyImpl::asMap() const
{
    Variant::Map map;

    map[Protocol::SCHEMA_ELT_NAME] = Variant(name);
    map[Protocol::SCHEMA_ELT_TYPE] = Variant((uint8_t) typecode);
    map[Protocol::SCHEMA_ELT_ACCESS] = Variant((uint8_t) access);
    if (optional)
        map[Protocol::SCHEMA_ELT_OPTIONAL] = Variant();
    if (!unit.empty())
        map[Protocol::SCHEMA_ELT_UNIT] = Variant(unit);
    if (!description.empty())
        map[Protocol::SCHEMA_ELT_DESC] = Variant(description);

    return map;
}

void SchemaPropertyImpl::updateHash(SchemaHash& hash) const
{
    hash.update(name);
    hash.update(typecode);
    hash.update(access);
    hash.update(index);
    hash.update(optional);
    hash.update(unit);
    hash.update(description);
}

SchemaClassKeyImpl::SchemaClassKeyImpl(const string& p, const string& n, const SchemaHash& h) :
    package(p), name(n), hash(h) {}

SchemaClassKeyImpl::SchemaClassKeyImpl(const Variant::Map& map) :
    package(packageContainer), name(nameContainer), hash(hashContainer)
{
    Variant::Map::const_iterator iter;

    iter = map.find(Protocol::SCHEMA_PACKAGE);
    if (iter == map.end())
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_PACKAGE);
    packageContainer = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_CLASS);
    if (iter == map.end())
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_CLASS);
    nameContainer = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_HASH);
    if (iter == map.end())
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_HASH);
    hashContainer.set(iter->second.asUuid().data());
}

SchemaClassKey* SchemaClassKeyImpl::factory(const string& package, const string& name, const SchemaHash& hash)
{
    SchemaClassKeyImpl* impl(new SchemaClassKeyImpl(package, name, hash));
    return new SchemaClassKey(impl);
}

SchemaClassKey* SchemaClassKeyImpl::factory(Variant::Map& map)
{
    SchemaClassKeyImpl* impl(new SchemaClassKeyImpl(map));
    return new SchemaClassKey(impl);
}

Variant::Map SchemaClassKeyImpl::asMap() const
{
    Variant::Map map;

    map[Protocol::SCHEMA_PACKAGE] = Variant(package);
    map[Protocol::SCHEMA_CLASS] = Variant(name);
    map[Protocol::SCHEMA_HASH] = Variant();  // TODO: use UUID type when available

    return map;
}

bool SchemaClassKeyImpl::operator==(const SchemaClassKeyImpl& other) const
{
    return package == other.package &&
        name == other.name &&
        hash == other.hash;
}

bool SchemaClassKeyImpl::operator<(const SchemaClassKeyImpl& other) const
{
    if (package < other.package) return true;
    if (package > other.package) return false;
    if (name < other.name) return true;
    if (name > other.name) return false;
    return hash < other.hash;
}

const string& SchemaClassKeyImpl::str() const
{
    Uuid printableHash(hash.get());
    stringstream str;
    str << package << ":" << name << "(" << printableHash << ")";
    repr = str.str();
    return repr;
}

SchemaObjectClassImpl::SchemaObjectClassImpl(const Variant::Map& map) :
    hasHash(true), classKey(SchemaClassKeyImpl::factory(package, name, hash))
{
    Variant::Map::const_iterator iter;

    iter = map.find(Protocol::SCHEMA_PACKAGE);
    if (iter == map.end())
        throw SchemaException("SchemaObjectClass", Protocol::SCHEMA_PACKAGE);
    package = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_CLASS);
    if (iter == map.end())
        throw SchemaException("SchemaObjectClass", Protocol::SCHEMA_CLASS);
    name = iter->second.asString();

    hash.decode(buffer);

    uint16_t propCount     = buffer.getShort();
    uint16_t statCount     = buffer.getShort();
    uint16_t methodCount   = buffer.getShort();

    for (uint16_t idx = 0; idx < propCount; idx++) {
        const SchemaProperty* property = SchemaPropertyImpl::factory(buffer);
        addProperty(property);
    }

    for (uint16_t idx = 0; idx < methodCount; idx++) {
        SchemaMethod* method = SchemaMethodImpl::factory(buffer);
        addMethod(method);
    }
}

SchemaObjectClass* SchemaObjectClassImpl::factory(Variant::Map& map)
{
    SchemaObjectClassImpl* impl(new SchemaObjectClassImpl(buffer));
    return new SchemaObjectClass(impl);
}

void SchemaObjectClassImpl::encode(Variant::Map& map) const
{
    buffer.putOctet((uint8_t) CLASS_OBJECT);
    buffer.putShortString(package);
    buffer.putShortString(name);
    hash.encode(buffer);
    //buffer.putOctet(0); // No parent class
    buffer.putShort((uint16_t) properties.size());
    buffer.putShort((uint16_t) statistics.size());
    buffer.putShort((uint16_t) methods.size());

    for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++)
        (*iter)->impl->encode(buffer);
    for (vector<const SchemaStatistic*>::const_iterator iter = statistics.begin();
         iter != statistics.end(); iter++)
        (*iter)->impl->encode(buffer);
    for (vector<const SchemaMethod*>::const_iterator iter = methods.begin();
         iter != methods.end(); iter++)
        (*iter)->impl->encode(buffer);
}

const SchemaClassKey* SchemaObjectClassImpl::getClassKey() const
{
    if (!hasHash) {
        hasHash = true;
        hash.update(package);
        hash.update(name);
        for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
             iter != properties.end(); iter++)
            (*iter)->impl->updateHash(hash);
        for (vector<const SchemaStatistic*>::const_iterator iter = statistics.begin();
             iter != statistics.end(); iter++)
            (*iter)->impl->updateHash(hash);
        for (vector<const SchemaMethod*>::const_iterator iter = methods.begin();
             iter != methods.end(); iter++)
            (*iter)->impl->updateHash(hash);
    }

    return classKey.get();
}

void SchemaObjectClassImpl::addProperty(const SchemaProperty* property)
{
    properties.push_back(property);
}

void SchemaObjectClassImpl::addStatistic(const SchemaStatistic* statistic)
{
    statistics.push_back(statistic);
}

void SchemaObjectClassImpl::addMethod(const SchemaMethod* method)
{
    methods.push_back(method);
}

const SchemaProperty* SchemaObjectClassImpl::getProperty(int idx) const
{
    int count = 0;
    for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++, count++)
        if (idx == count)
            return *iter;
    return 0;
}

const SchemaStatistic* SchemaObjectClassImpl::getStatistic(int idx) const
{
    int count = 0;
    for (vector<const SchemaStatistic*>::const_iterator iter = statistics.begin();
         iter != statistics.end(); iter++, count++)
        if (idx == count)
            return *iter;
    return 0;
}

const SchemaMethod* SchemaObjectClassImpl::getMethod(int idx) const
{
    int count = 0;
    for (vector<const SchemaMethod*>::const_iterator iter = methods.begin();
         iter != methods.end(); iter++, count++)
        if (idx == count)
            return *iter;
    return 0;
}

SchemaEventClassImpl::SchemaEventClassImpl(Variant::Map& map) : hasHash(true), classKey(SchemaClassKeyImpl::factory(package, name, hash))
{
    buffer.getShortString(package);
    buffer.getShortString(name);
    hash.decode(buffer);
    buffer.putOctet(0); // No parent class

    uint16_t argCount = buffer.getShort();

    for (uint16_t idx = 0; idx < argCount; idx++) {
        SchemaArgument* argument = SchemaArgumentImpl::factory(buffer);
        addArgument(argument);
    }
}

SchemaEventClass* SchemaEventClassImpl::factory(Variant::Map& map)
{
    SchemaEventClassImpl* impl(new SchemaEventClassImpl(buffer));
    return new SchemaEventClass(impl);
}

void SchemaEventClassImpl::encode(Variant::Map& map) const
{
    buffer.putOctet((uint8_t) CLASS_EVENT);
    buffer.putShortString(package);
    buffer.putShortString(name);
    hash.encode(buffer);
    buffer.putShort((uint16_t) arguments.size());

    for (vector<const SchemaArgument*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++)
        (*iter)->impl->encode(buffer);
}

const SchemaClassKey* SchemaEventClassImpl::getClassKey() const
{
    if (!hasHash) {
        hasHash = true;
        hash.update(package);
        hash.update(name);
        for (vector<const SchemaArgument*>::const_iterator iter = arguments.begin();
             iter != arguments.end(); iter++)
            (*iter)->impl->updateHash(hash);
    }
    return classKey.get();
}

void SchemaEventClassImpl::addArgument(const SchemaArgument* argument)
{
    arguments.push_back(argument);
}

const SchemaArgument* SchemaEventClassImpl::getArgument(int idx) const
{
    int count = 0;
    for (vector<const SchemaArgument*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++, count++)
        if (idx == count)
            return (*iter);
    return 0;
}


//==================================================================
// Wrappers
//==================================================================

SchemaArgument::SchemaArgument(const char* name, Typecode typecode) { impl = new SchemaArgumentImpl(name, typecode); }
SchemaArgument::SchemaArgument(SchemaArgumentImpl* i) : impl(i) {}
SchemaArgument::SchemaArgument(const SchemaArgument& from) : impl(new SchemaArgumentImpl(*(from.impl))) {}
SchemaArgument::~SchemaArgument() { delete impl; }
void SchemaArgument::setDirection(Direction dir) { impl->setDirection(dir); }
void SchemaArgument::setUnit(const char* val) { impl->setUnit(val); }
void SchemaArgument::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaArgument::getName() const { return impl->getName().c_str(); }
Typecode SchemaArgument::getType() const { return impl->getType(); }
Direction SchemaArgument::getDirection() const { return impl->getDirection(); }
const char* SchemaArgument::getUnit() const { return impl->getUnit().c_str(); }
const char* SchemaArgument::getDesc() const { return impl->getDesc().c_str(); }

SchemaMethod::SchemaMethod(const char* name) : impl(new SchemaMethodImpl(name)) {}
SchemaMethod::SchemaMethod(SchemaMethodImpl* i) : impl(i) {}
SchemaMethod::SchemaMethod(const SchemaMethod& from) : impl(new SchemaMethodImpl(*(from.impl))) {}
SchemaMethod::~SchemaMethod() { delete impl; }
void SchemaMethod::addArgument(const SchemaArgument* argument) { impl->addArgument(argument); }
void SchemaMethod::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaMethod::getName() const { return impl->getName().c_str(); }
const char* SchemaMethod::getDesc() const { return impl->getDesc().c_str(); }
int SchemaMethod::getArgumentCount() const { return impl->getArgumentCount(); }
const SchemaArgument* SchemaMethod::getArgument(int idx) const { return impl->getArgument(idx); }

SchemaProperty::SchemaProperty(const char* name, Typecode typecode) : impl(new SchemaPropertyImpl(name, typecode)) {}
SchemaProperty::SchemaProperty(SchemaPropertyImpl* i) : impl(i) {}
SchemaProperty::SchemaProperty(const SchemaProperty& from) : impl(new SchemaPropertyImpl(*(from.impl))) {}
SchemaProperty::~SchemaProperty() { delete impl; }
void SchemaProperty::setAccess(Access access) { impl->setAccess(access); }
void SchemaProperty::setIndex(bool val) { impl->setIndex(val); }
void SchemaProperty::setOptional(bool val) { impl->setOptional(val); }
void SchemaProperty::setUnit(const char* val) { impl->setUnit(val); }
void SchemaProperty::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaProperty::getName() const { return impl->getName().c_str(); }
Typecode SchemaProperty::getType() const { return impl->getType(); }
Access SchemaProperty::getAccess() const { return impl->getAccess(); }
bool SchemaProperty::isIndex() const { return impl->isIndex(); }
bool SchemaProperty::isOptional() const { return impl->isOptional(); }
const char* SchemaProperty::getUnit() const { return impl->getUnit().c_str(); }
const char* SchemaProperty::getDesc() const { return impl->getDesc().c_str(); }

SchemaClassKey::SchemaClassKey(SchemaClassKeyImpl* i) : impl(i) {}
SchemaClassKey::SchemaClassKey(const SchemaClassKey& from) : impl(new SchemaClassKeyImpl(*(from.impl))) {}
SchemaClassKey::~SchemaClassKey() { delete impl; }
const char* SchemaClassKey::getPackageName() const { return impl->getPackageName().c_str(); }
const char* SchemaClassKey::getClassName() const { return impl->getClassName().c_str(); }
const uint8_t* SchemaClassKey::getHash() const { return impl->getHash(); }
const char* SchemaClassKey::asString() const { return impl->str().c_str(); }
bool SchemaClassKey::operator==(const SchemaClassKey& other) const { return *impl == *(other.impl); }
bool SchemaClassKey::operator<(const SchemaClassKey& other) const { return *impl < *(other.impl); }

SchemaObjectClass::SchemaObjectClass(const char* package, const char* name) : impl(new SchemaObjectClassImpl(package, name)) {}
SchemaObjectClass::SchemaObjectClass(SchemaObjectClassImpl* i) : impl(i) {}
SchemaObjectClass::SchemaObjectClass(const SchemaObjectClass& from) : impl(new SchemaObjectClassImpl(*(from.impl))) {}
SchemaObjectClass::~SchemaObjectClass() { delete impl; }
void SchemaObjectClass::addProperty(const SchemaProperty* property) { impl->addProperty(property); }
void SchemaObjectClass::addMethod(const SchemaMethod* method) { impl->addMethod(method); }
const SchemaClassKey* SchemaObjectClass::getClassKey() const { return impl->getClassKey(); }
int SchemaObjectClass::getPropertyCount() const { return impl->getPropertyCount(); }
int SchemaObjectClass::getMethodCount() const { return impl->getMethodCount(); }
const SchemaProperty* SchemaObjectClass::getProperty(int idx) const { return impl->getProperty(idx); }
const SchemaMethod* SchemaObjectClass::getMethod(int idx) const { return impl->getMethod(idx); }

SchemaEventClass::SchemaEventClass(const char* package, const char* name) : impl(new SchemaEventClassImpl(package, name)) {}
SchemaEventClass::SchemaEventClass(SchemaEventClassImpl* i) : impl(i) {}
SchemaEventClass::SchemaEventClass(const SchemaEventClass& from) : impl(new SchemaEventClassImpl(*(from.impl))) {}
SchemaEventClass::~SchemaEventClass() { delete impl; }
void SchemaEventClass::addArgument(const SchemaArgument* argument) { impl->addArgument(argument); }
void SchemaEventClass::setDesc(const char* desc) { impl->setDesc(desc); }
const SchemaClassKey* SchemaEventClass::getClassKey() const { return impl->getClassKey(); }
int SchemaEventClass::getArgumentCount() const { return impl->getArgumentCount(); }
const SchemaArgument* SchemaEventClass::getArgument(int idx) const { return impl->getArgument(idx); }

