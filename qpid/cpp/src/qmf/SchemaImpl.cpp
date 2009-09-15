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

#include "qmf/SchemaImpl.h"
#include <qpid/framing/Buffer.h>
#include <qpid/framing/FieldTable.h>
#include <qpid/framing/Uuid.h>
#include <string.h>
#include <string>
#include <vector>

using namespace std;
using namespace qmf;
using qpid::framing::Buffer;
using qpid::framing::FieldTable;
using qpid::framing::Uuid;

SchemaHash::SchemaHash()
{
    for (int idx = 0; idx < 16; idx++)
        hash[idx] = 0x5A;
}

void SchemaHash::encode(Buffer& buffer) const
{
    buffer.putBin128(hash);
}

void SchemaHash::decode(Buffer& buffer)
{
    buffer.getBin128(hash);
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

SchemaArgumentImpl::SchemaArgumentImpl(Buffer& buffer) : envelope(new SchemaArgument(this))
{
    FieldTable map;
    map.decode(buffer);

    name = map.getAsString("name");
    typecode = (Typecode) map.getAsInt("type");
    unit = map.getAsString("unit");
    description = map.getAsString("desc");

    dir = DIR_IN;
    string dstr(map.getAsString("dir"));
    if (dstr == "O")
        dir = DIR_OUT;
    else if (dstr == "IO")
        dir = DIR_IN_OUT;
}

void SchemaArgumentImpl::encode(Buffer& buffer) const
{
    FieldTable map;

    map.setString("name", name);
    map.setInt("type", (int) typecode);
    if (dir == DIR_IN)
        map.setString("dir", "I");
    else if (dir == DIR_OUT)
        map.setString("dir", "O");
    else
        map.setString("dir", "IO");
    if (!unit.empty())
        map.setString("unit", unit);
    if (!description.empty())
        map.setString("desc", description);

    map.encode(buffer);
}

void SchemaArgumentImpl::updateHash(SchemaHash& hash) const
{
    hash.update(name);
    hash.update(typecode);
    hash.update(dir);
    hash.update(unit);
    hash.update(description);
}

SchemaMethodImpl::SchemaMethodImpl(Buffer& buffer) : envelope(new SchemaMethod(this))
{
    FieldTable map;
    int argCount;

    map.decode(buffer);
    name = map.getAsString("name");
    argCount = map.getAsInt("argCount");
    description = map.getAsString("desc");

    for (int idx = 0; idx < argCount; idx++) {
        SchemaArgumentImpl* arg = new SchemaArgumentImpl(buffer);
        addArgument(*arg->envelope);
    }
}

void SchemaMethodImpl::encode(Buffer& buffer) const
{
    FieldTable map;

    map.setString("name", name);
    map.setInt("argCount", arguments.size());
    if (!description.empty())
        map.setString("desc", description);
    map.encode(buffer);

    for (vector<SchemaArgumentImpl*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++)
        (*iter)->encode(buffer);
}

void SchemaMethodImpl::addArgument(const SchemaArgument& argument)
{
    arguments.push_back(argument.impl);
}

const SchemaArgument* SchemaMethodImpl::getArgument(int idx) const
{
    int count = 0;
    for (vector<SchemaArgumentImpl*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++, count++)
        if (idx == count)
            return (*iter)->envelope;
    return 0;
}

void SchemaMethodImpl::updateHash(SchemaHash& hash) const
{
    hash.update(name);
    hash.update(description);
    for (vector<SchemaArgumentImpl*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++)
        (*iter)->updateHash(hash);
}

SchemaPropertyImpl::SchemaPropertyImpl(Buffer& buffer) : envelope(new SchemaProperty(this))
{
    FieldTable map;
    map.decode(buffer);

    name = map.getAsString("name");
    typecode = (Typecode) map.getAsInt("type");
    access = (Access) map.getAsInt("access");
    index = map.getAsInt("index") != 0;
    optional = map.getAsInt("optional") != 0;
    unit = map.getAsString("unit");
    description = map.getAsString("desc");
}

void SchemaPropertyImpl::encode(Buffer& buffer) const
{
    FieldTable map;

    map.setString("name", name);
    map.setInt("type", (int) typecode);
    map.setInt("access", (int) access);
    map.setInt("index", index ? 1 : 0);
    map.setInt("optional", optional ? 1 : 0);
    if (!unit.empty())
        map.setString("unit", unit);
    if (!description.empty())
        map.setString("desc", description);

    map.encode(buffer);
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

SchemaStatisticImpl::SchemaStatisticImpl(Buffer& buffer) : envelope(new SchemaStatistic(this))
{
    FieldTable map;
    map.decode(buffer);

    name = map.getAsString("name");
    typecode = (Typecode) map.getAsInt("type");
    unit = map.getAsString("unit");
    description = map.getAsString("desc");
}

void SchemaStatisticImpl::encode(Buffer& buffer) const
{
    FieldTable map;

    map.setString("name", name);
    map.setInt("type", (int) typecode);
    if (!unit.empty())
        map.setString("unit", unit);
    if (!description.empty())
        map.setString("desc", description);

    map.encode(buffer);
}

void SchemaStatisticImpl::updateHash(SchemaHash& hash) const
{
    hash.update(name);
    hash.update(typecode);
    hash.update(unit);
    hash.update(description);
}

SchemaClassKeyImpl::SchemaClassKeyImpl(const string& p, const string& n, const SchemaHash& h) :
    envelope(new SchemaClassKey(this)), package(p), name(n), hash(h) {}

SchemaClassKeyImpl::SchemaClassKeyImpl(Buffer& buffer) :
    envelope(new SchemaClassKey(this)), package(packageContainer), name(nameContainer), hash(hashContainer)
{
    buffer.getShortString(packageContainer);
    buffer.getShortString(nameContainer);
    hashContainer.decode(buffer);
}    

void SchemaClassKeyImpl::encode(Buffer& buffer) const
{
    buffer.putShortString(package);
    buffer.putShortString(name);
    hash.encode(buffer);
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

string SchemaClassKeyImpl::str() const
{
    Uuid printableHash(hash.get());
    stringstream str;
    str << package << ":" << name << "(" << printableHash << ")";
    return str.str();
}

SchemaObjectClassImpl::SchemaObjectClassImpl(Buffer& buffer) :
    envelope(new SchemaObjectClass(this)), hasHash(true), classKey(package, name, hash)
{
    buffer.getShortString(package);
    buffer.getShortString(name);
    hash.decode(buffer);

    /*uint8_t hasParentClass =*/ buffer.getOctet(); // TODO: Parse parent-class indicator
    uint16_t propCount     = buffer.getShort();
    uint16_t statCount     = buffer.getShort();
    uint16_t methodCount   = buffer.getShort();

    for (uint16_t idx = 0; idx < propCount; idx++) {
        SchemaPropertyImpl* property = new SchemaPropertyImpl(buffer);
        addProperty(*property->envelope);
    }

    for (uint16_t idx = 0; idx < statCount; idx++) {
        SchemaStatisticImpl* statistic = new SchemaStatisticImpl(buffer);
        addStatistic(*statistic->envelope);
    }

    for (uint16_t idx = 0; idx < methodCount; idx++) {
        SchemaMethodImpl* method = new SchemaMethodImpl(buffer);
        addMethod(*method->envelope);
    }
}

void SchemaObjectClassImpl::encode(Buffer& buffer) const
{
    buffer.putOctet((uint8_t) CLASS_OBJECT);
    buffer.putShortString(package);
    buffer.putShortString(name);
    hash.encode(buffer);
    buffer.putOctet(0); // No parent class
    buffer.putShort((uint16_t) properties.size());
    buffer.putShort((uint16_t) statistics.size());
    buffer.putShort((uint16_t) methods.size());

    for (vector<SchemaPropertyImpl*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++)
        (*iter)->encode(buffer);
    for (vector<SchemaStatisticImpl*>::const_iterator iter = statistics.begin();
         iter != statistics.end(); iter++)
        (*iter)->encode(buffer);
    for (vector<SchemaMethodImpl*>::const_iterator iter = methods.begin();
         iter != methods.end(); iter++)
        (*iter)->encode(buffer);
}

const SchemaClassKey* SchemaObjectClassImpl::getClassKey() const
{
    if (!hasHash) {
        hasHash = true;
        hash.update(package);
        hash.update(name);
        for (vector<SchemaPropertyImpl*>::const_iterator iter = properties.begin();
             iter != properties.end(); iter++)
            (*iter)->updateHash(hash);
        for (vector<SchemaStatisticImpl*>::const_iterator iter = statistics.begin();
             iter != statistics.end(); iter++)
            (*iter)->updateHash(hash);
        for (vector<SchemaMethodImpl*>::const_iterator iter = methods.begin();
             iter != methods.end(); iter++)
            (*iter)->updateHash(hash);
    }

    return classKey.envelope;
}

void SchemaObjectClassImpl::addProperty(const SchemaProperty& property)
{
    properties.push_back(property.impl);
}

void SchemaObjectClassImpl::addStatistic(const SchemaStatistic& statistic)
{
    statistics.push_back(statistic.impl);
}

void SchemaObjectClassImpl::addMethod(const SchemaMethod& method)
{
    methods.push_back(method.impl);
}

const SchemaProperty* SchemaObjectClassImpl::getProperty(int idx) const
{
    int count = 0;
    for (vector<SchemaPropertyImpl*>::const_iterator iter = properties.begin();
         iter != properties.end(); iter++, count++)
        if (idx == count)
            return (*iter)->envelope;
    return 0;
}

const SchemaStatistic* SchemaObjectClassImpl::getStatistic(int idx) const
{
    int count = 0;
    for (vector<SchemaStatisticImpl*>::const_iterator iter = statistics.begin();
         iter != statistics.end(); iter++, count++)
        if (idx == count)
            return (*iter)->envelope;
    return 0;
}

const SchemaMethod* SchemaObjectClassImpl::getMethod(int idx) const
{
    int count = 0;
    for (vector<SchemaMethodImpl*>::const_iterator iter = methods.begin();
         iter != methods.end(); iter++, count++)
        if (idx == count)
            return (*iter)->envelope;
    return 0;
}

SchemaEventClassImpl::SchemaEventClassImpl(Buffer& buffer) :
    envelope(new SchemaEventClass(this)), hasHash(true), classKey(package, name, hash)
{
    buffer.getShortString(package);
    buffer.getShortString(name);
    hash.decode(buffer);
    buffer.putOctet(0); // No parent class

    uint16_t argCount = buffer.getShort();

    for (uint16_t idx = 0; idx < argCount; idx++) {
        SchemaArgumentImpl* argument = new SchemaArgumentImpl(buffer);
        addArgument(*argument->envelope);
    }
}

void SchemaEventClassImpl::encode(Buffer& buffer) const
{
    buffer.putOctet((uint8_t) CLASS_EVENT);
    buffer.putShortString(package);
    buffer.putShortString(name);
    hash.encode(buffer);
    buffer.putShort((uint16_t) arguments.size());

    for (vector<SchemaArgumentImpl*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++)
        (*iter)->encode(buffer);
}

const SchemaClassKey* SchemaEventClassImpl::getClassKey() const
{
    if (!hasHash) {
        hasHash = true;
        hash.update(package);
        hash.update(name);
        for (vector<SchemaArgumentImpl*>::const_iterator iter = arguments.begin();
             iter != arguments.end(); iter++)
            (*iter)->updateHash(hash);
    }
    return classKey.envelope;
}

void SchemaEventClassImpl::addArgument(const SchemaArgument& argument)
{
    arguments.push_back(argument.impl);
}

const SchemaArgument* SchemaEventClassImpl::getArgument(int idx) const
{
    int count = 0;
    for (vector<SchemaArgumentImpl*>::const_iterator iter = arguments.begin();
         iter != arguments.end(); iter++, count++)
        if (idx == count)
            return (*iter)->envelope;
    return 0;
}


//==================================================================
// Wrappers
//==================================================================

SchemaArgument::SchemaArgument(const char* name, Typecode typecode) { impl = new SchemaArgumentImpl(this, name, typecode); }
SchemaArgument::SchemaArgument(SchemaArgumentImpl* i) : impl(i) {}
SchemaArgument::~SchemaArgument() { delete impl; }
void SchemaArgument::setDirection(Direction dir) { impl->setDirection(dir); }
void SchemaArgument::setUnit(const char* val) { impl->setUnit(val); }
void SchemaArgument::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaArgument::getName() const { return impl->getName().c_str(); }
Typecode SchemaArgument::getType() const { return impl->getType(); }
Direction SchemaArgument::getDirection() const { return impl->getDirection(); }
const char* SchemaArgument::getUnit() const { return impl->getUnit().c_str(); }
const char* SchemaArgument::getDesc() const { return impl->getDesc().c_str(); }
SchemaMethod::SchemaMethod(const char* name) { impl = new SchemaMethodImpl(this, name); }
SchemaMethod::SchemaMethod(SchemaMethodImpl* i) : impl(i) {}
SchemaMethod::~SchemaMethod() { delete impl; }
void SchemaMethod::addArgument(const SchemaArgument& argument) { impl->addArgument(argument); }
void SchemaMethod::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaMethod::getName() const { return impl->getName().c_str(); }
const char* SchemaMethod::getDesc() const { return impl->getDesc().c_str(); }
int SchemaMethod::getArgumentCount() const { return impl->getArgumentCount(); }
const SchemaArgument* SchemaMethod::getArgument(int idx) const { return impl->getArgument(idx); }
SchemaProperty::SchemaProperty(const char* name, Typecode typecode) { impl = new SchemaPropertyImpl(this, name, typecode); }
SchemaProperty::SchemaProperty(SchemaPropertyImpl* i) : impl(i) {}
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
SchemaStatistic::SchemaStatistic(const char* name, Typecode typecode) { impl = new SchemaStatisticImpl(this, name, typecode); }
SchemaStatistic::SchemaStatistic(SchemaStatisticImpl* i) : impl(i) {}
SchemaStatistic::~SchemaStatistic() { delete impl; }
void SchemaStatistic::setUnit(const char* val) { impl->setUnit(val); }
void SchemaStatistic::setDesc(const char* desc) { impl->setDesc(desc); }
const char* SchemaStatistic::getName() const { return impl->getName().c_str(); }
Typecode SchemaStatistic::getType() const { return impl->getType(); }
const char* SchemaStatistic::getUnit() const { return impl->getUnit().c_str(); }
const char* SchemaStatistic::getDesc() const { return impl->getDesc().c_str(); }
SchemaClassKey::SchemaClassKey(SchemaClassKeyImpl* i) : impl(i) {}
SchemaClassKey::~SchemaClassKey() { delete impl; }
const char* SchemaClassKey::getPackageName() const { return impl->getPackageName().c_str(); }
const char* SchemaClassKey::getClassName() const { return impl->getClassName().c_str(); }
const uint8_t* SchemaClassKey::getHash() const { return impl->getHash(); }
SchemaObjectClass::SchemaObjectClass(const char* package, const char* name) { impl = new SchemaObjectClassImpl(this, package, name); }
SchemaObjectClass::SchemaObjectClass(SchemaObjectClassImpl* i) : impl(i) {}
SchemaObjectClass::~SchemaObjectClass() { delete impl; }
void SchemaObjectClass::addProperty(const SchemaProperty& property) { impl->addProperty(property); }
void SchemaObjectClass::addStatistic(const SchemaStatistic& statistic) { impl->addStatistic(statistic); }
void SchemaObjectClass::addMethod(const SchemaMethod& method) { impl->addMethod(method); }
const SchemaClassKey* SchemaObjectClass::getClassKey() const { return impl->getClassKey(); }
int SchemaObjectClass::getPropertyCount() const { return impl->getPropertyCount(); }
int SchemaObjectClass::getStatisticCount() const { return impl->getStatisticCount(); }
int SchemaObjectClass::getMethodCount() const { return impl->getMethodCount(); }
const SchemaProperty* SchemaObjectClass::getProperty(int idx) const { return impl->getProperty(idx); }
const SchemaStatistic* SchemaObjectClass::getStatistic(int idx) const { return impl->getStatistic(idx); }
const SchemaMethod* SchemaObjectClass::getMethod(int idx) const { return impl->getMethod(idx); }
SchemaEventClass::SchemaEventClass(const char* package, const char* name) { impl = new SchemaEventClassImpl(this, package, name); }
SchemaEventClass::SchemaEventClass(SchemaEventClassImpl* i) : impl(i) {}
SchemaEventClass::~SchemaEventClass() { delete impl; }
void SchemaEventClass::addArgument(const SchemaArgument& argument) { impl->addArgument(argument); }
void SchemaEventClass::setDesc(const char* desc) { impl->setDesc(desc); }
const SchemaClassKey* SchemaEventClass::getClassKey() const { return impl->getClassKey(); }
int SchemaEventClass::getArgumentCount() const { return impl->getArgumentCount(); }
const SchemaArgument* SchemaEventClass::getArgument(int idx) const { return impl->getArgument(idx); }

