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
#include <assert.h>

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


SchemaMethodImpl::SchemaMethodImpl(const string& _name, const Variant::Map& map)
{
    Variant::Map::const_iterator iter;
    name = _name;

    iter = map.find(Protocol::SCHEMA_ELT_DESC);
    if (iter != map.end())
        description = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_ARGS);
    if (iter != map.end() || iter->second.getType() != VAR_MAP) {
        Variant::Map argMap(iter->second.asMap());
        for (Variant::Map::const_iterator aiter = argMap.begin(); aiter != argMap.end(); aiter++) {
            const string& name(aiter->first);
            SchemaProperty* arg = SchemaPropertyImpl::factory(name, aiter->second.asMap());
            addProperty(arg);
        }
    }
}

SchemaMethod* SchemaMethodImpl::factory(const string& name, const Variant::Map& map)
{
    SchemaMethodImpl* impl(new SchemaMethodImpl(name, map));
    return new SchemaMethod(impl);
}

Variant::Map SchemaMethodImpl::asMap() const
{
    Variant::Map map;

    if (!description.empty())
        map[Protocol::SCHEMA_ELT_DESC] = Variant(description);

    Variant::List list;
    for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++)
        list.push_back((*iter)->impl->asMap());
    map[Protocol::SCHEMA_ARGS] = list;

    return map;
}

void SchemaMethodImpl::addProperty(const SchemaProperty* property)
{
    properties.push_back(property);
}

const SchemaProperty* SchemaMethodImpl::getProperty(int idx) const
{
    int count = 0;
    for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++, count++)
        if (idx == count)
            return (*iter);
    return 0;
}

void SchemaMethodImpl::updateHash(SchemaHash& hash) const
{
    hash.update(name);
    hash.update(description);
    for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++)
        (*iter)->impl->updateHash(hash);
}

SchemaPropertyImpl::SchemaPropertyImpl(const string& _name, const Variant::Map& map)
{
    Variant::Map::const_iterator iter;
    name = _name;

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

SchemaProperty* SchemaPropertyImpl::factory(const string& name, const Variant::Map& map)
{
    SchemaPropertyImpl* impl(new SchemaPropertyImpl(name, map));
    return new SchemaProperty(impl);
}

Variant::Map SchemaPropertyImpl::asMap() const
{
    Variant::Map map;

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
    hash.update(dir);
    hash.update(unit);
    hash.update(description);
}

SchemaClassKeyImpl::SchemaClassKeyImpl(ClassKind k, const string& p, const string& n) :
    kind(k), package(p), name(n) {}

SchemaClassKeyImpl::SchemaClassKeyImpl(const Variant::Map& map)
{
    Variant::Map::const_iterator iter;

    iter = map.find(Protocol::SCHEMA_CLASS_KIND);
    if (iter == map.end())
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_CLASS_KIND);
    string kindName = iter->second.asString();
    if (kindName == CLASS_DATA)
        kind = CLASS_DATA;
    else if (kindName == CLASS_EVENT)
        kind = CLASS_EVENT;
    else
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_CLASS_KIND);

    iter = map.find(Protocol::SCHEMA_PACKAGE);
    if (iter == map.end())
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_PACKAGE);
    package = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_CLASS);
    if (iter == map.end())
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_CLASS);
    name = iter->second.asString();

    iter = map.find(Protocol::SCHEMA_HASH);
    if (iter == map.end())
        throw SchemaException("SchemaClassKey", Protocol::SCHEMA_HASH);
    hash.set(iter->second.asUuid().data());
}

SchemaClassKey* SchemaClassKeyImpl::factory(ClassKind k, const string& p, const string& n)
{
    SchemaClassKeyImpl* impl(new SchemaClassKeyImpl(k, p, n));
    return new SchemaClassKey(impl);
}

SchemaClassKey* SchemaClassKeyImpl::factory(const Variant::Map& map)
{
    SchemaClassKeyImpl* impl(new SchemaClassKeyImpl(map));
    return new SchemaClassKey(impl);
}

Variant::Map SchemaClassKeyImpl::asMap() const
{
    Variant::Map map;

    if (kind == CLASS_DATA)
        map[Protocol::SCHEMA_CLASS_KIND] = Protocol::SCHEMA_CLASS_KIND_DATA;
    else if (kind == CLASS_EVENT)
        map[Protocol::SCHEMA_CLASS_KIND] = Protocol::SCHEMA_CLASS_KIND_EVENT;
    else
        assert(0);

    map[Protocol::SCHEMA_PACKAGE] = Variant(package);
    map[Protocol::SCHEMA_CLASS] = Variant(name);
    map[Protocol::SCHEMA_HASH] = Uuid(hash.get());

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

SchemaClassImpl::SchemaClassImpl(const Variant::Map& map) : hasHash(true)
{
    Variant::Map::const_iterator iter;

    iter = map.find(Protocol::SCHEMA_ID);
    if (iter == map.end() || iter->second.getType() != VAR_MAP)
        throw SchemaException("SchemaClass", Protocol::SCHEMA_ID);
    classKey.reset(SchemaClassKeyImpl::factory(iter->second.asMap()));

    iter = map.find(Protocol::VALUES);
    if (iter == map.end() || iter->second.getType() != VAR_MAP)
        throw SchemaException("SchemaClass", Protocol::VALUES);
    Variant::Map valMap(iter->second.asMap());

    iter = map.find(Protocol::SUBTYPES);
    if (iter == map.end() || iter->second.getType() != VAR_MAP)
        throw SchemaException("SchemaClass", Protocol::SUBTYPES);
    Variant::Map subtypeMap(iter->second.asMap());

    for (iter = valMap.begin(); iter != valMap.end(); iter++) {
        const string& name(iter->first);
        bool isMethod = false;
        Variant::Map::const_iterator subtypeIter = subtypeMap.find(name);
        if (subtypeIter != subtypeMap.end() &&
            subtypeIter->second.asString() == Protocol::SUBTYPE_SCHEMA_METHOD)
            isMethod = true;

        if (isMethod)
            addMethod(SchemaMethodImpl::factory(name, iter->second.asMap()));
        else
            addProperty(SchemaPropertyImpl::factory(name, iter->second.asMap()));
    }
}

SchemaClass* SchemaClassImpl::factory(const Variant::Map& map)
{
    SchemaClassImpl* impl(new SchemaClassImpl(map));
    return new SchemaClass(impl);
}

Variant::Map SchemaClassImpl::asMap() const
{
    Variant::Map map;
    Variant::Map values;
    Variant::Map subtypes;

    map[Protocol::SCHEMA_ID] = classKey->impl->asMap();

    for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++) {
        values[(*iter)->getName()] = (*iter)->impl->asMap();
        subtypes[(*iter)->getName()] = Protocol::SUBTYPE_SCHEMA_PROPERTY;
    }

    for (vector<const SchemaMethod*>::const_iterator iter = methods.begin();
         iter != methods.end(); iter++) {
        values[(*iter)->getName()] = (*iter)->impl->asMap();
        subtypes[(*iter)->getName()] = Protocol::SUBTYPE_SCHEMA_METHOD;
    }

    map[Protocol::VALUES] = values;
    map[Protocol::SUBTYPES] = subtypes;

    return map;
}

const SchemaClassKey* SchemaClassImpl::getClassKey() const
{
    if (!hasHash) {
        hasHash = true;
        SchemaHash& hash(classKey->impl->getHash());
        hash.update(classKey->getPackageName());
        hash.update(classKey->getClassName());
        for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
             iter != properties.end(); iter++)
            (*iter)->impl->updateHash(hash);
        for (vector<const SchemaMethod*>::const_iterator iter = methods.begin();
             iter != methods.end(); iter++)
            (*iter)->impl->updateHash(hash);
    }

    return classKey.get();
}

void SchemaClassImpl::addProperty(const SchemaProperty* property)
{
    properties.push_back(property);
}

void SchemaClassImpl::addMethod(const SchemaMethod* method)
{
    methods.push_back(method);
}

const SchemaProperty* SchemaClassImpl::getProperty(int idx) const
{
    int count = 0;
    for (vector<const SchemaProperty*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++, count++)
        if (idx == count)
            return *iter;
    return 0;
}

const SchemaMethod* SchemaClassImpl::getMethod(int idx) const
{
    int count = 0;
    for (vector<const SchemaMethod*>::const_iterator iter = methods.begin();
         iter != methods.end(); iter++, count++)
        if (idx == count)
            return *iter;
    return 0;
}


//==================================================================
// Wrappers
//==================================================================

SchemaMethod::SchemaMethod(const char* name) : impl(new SchemaMethodImpl(name)) {}
SchemaMethod::SchemaMethod(SchemaMethodImpl* i) : impl(i) {}
SchemaMethod::SchemaMethod(const SchemaMethod& from) : impl(new SchemaMethodImpl(*(from.impl))) {}
SchemaMethod::~SchemaMethod() { delete impl; }
void SchemaMethod::addProperty(const SchemaProperty* property) { impl->addProperty(property); }
void SchemaMethod::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaMethod::getName() const { return impl->getName().c_str(); }
const char* SchemaMethod::getDesc() const { return impl->getDesc().c_str(); }
int SchemaMethod::getPropertyCount() const { return impl->getPropertyCount(); }
const SchemaProperty* SchemaMethod::getProperty(int idx) const { return impl->getProperty(idx); }

SchemaProperty::SchemaProperty(const char* name, Typecode typecode) : impl(new SchemaPropertyImpl(name, typecode)) {}
SchemaProperty::SchemaProperty(SchemaPropertyImpl* i) : impl(i) {}
SchemaProperty::SchemaProperty(const SchemaProperty& from) : impl(new SchemaPropertyImpl(*(from.impl))) {}
SchemaProperty::~SchemaProperty() { delete impl; }
void SchemaProperty::setAccess(Access access) { impl->setAccess(access); }
void SchemaProperty::setIndex(bool val) { impl->setIndex(val); }
void SchemaProperty::setOptional(bool val) { impl->setOptional(val); }
void SchemaProperty::setDirection(Direction dir) { impl->setDirection(dir); }
void SchemaProperty::setUnit(const char* val) { impl->setUnit(val); }
void SchemaProperty::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaProperty::getName() const { return impl->getName().c_str(); }
Typecode SchemaProperty::getType() const { return impl->getType(); }
Access SchemaProperty::getAccess() const { return impl->getAccess(); }
bool SchemaProperty::isIndex() const { return impl->isIndex(); }
bool SchemaProperty::isOptional() const { return impl->isOptional(); }
Direction SchemaProperty::getDirection() const { return impl->getDirection(); }
const char* SchemaProperty::getUnit() const { return impl->getUnit().c_str(); }
const char* SchemaProperty::getDesc() const { return impl->getDesc().c_str(); }

SchemaClassKey::SchemaClassKey(SchemaClassKeyImpl* i) : impl(i) {}
SchemaClassKey::SchemaClassKey(const SchemaClassKey& from) : impl(new SchemaClassKeyImpl(*(from.impl))) {}
SchemaClassKey::~SchemaClassKey() { delete impl; }
ClassKind SchemaClassKey::getKind() const { return impl->getKind(); }
const char* SchemaClassKey::getPackageName() const { return impl->getPackageName().c_str(); }
const char* SchemaClassKey::getClassName() const { return impl->getClassName().c_str(); }
const uint8_t* SchemaClassKey::getHashData() const { return impl->getHashData(); }
const char* SchemaClassKey::asString() const { return impl->str().c_str(); }
bool SchemaClassKey::operator==(const SchemaClassKey& other) const { return *impl == *(other.impl); }
bool SchemaClassKey::operator<(const SchemaClassKey& other) const { return *impl < *(other.impl); }

SchemaClass::SchemaClass(ClassKind kind, const char* package, const char* name) : impl(new SchemaClassImpl(kind, package, name)) {}
SchemaClass::SchemaClass(SchemaClassImpl* i) : impl(i) {}
SchemaClass::SchemaClass(const SchemaClass& from) : impl(new SchemaClassImpl(*(from.impl))) {}
SchemaClass::~SchemaClass() { delete impl; }
void SchemaClass::addProperty(const SchemaProperty* property) { impl->addProperty(property); }
void SchemaClass::addMethod(const SchemaMethod* method) { impl->addMethod(method); }
const SchemaClassKey* SchemaClass::getClassKey() const { return impl->getClassKey(); }
int SchemaClass::getPropertyCount() const { return impl->getPropertyCount(); }
int SchemaClass::getMethodCount() const { return impl->getMethodCount(); }
const SchemaProperty* SchemaClass::getProperty(int idx) const { return impl->getProperty(idx); }
const SchemaMethod* SchemaClass::getMethod(int idx) const { return impl->getMethod(idx); }

