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

#include "qmf/SchemaPropertyImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"
#include "qmf/SchemaTypes.h"
#include "qmf/SchemaProperty.h"
#include "qmf/Hash.h"
#include "qpid/messaging/AddressParser.h"
#include <list>
#include <iostream>

using namespace std;
using qpid::types::Variant;
using namespace qmf;

typedef PrivateImplRef<SchemaProperty> PI;

SchemaProperty::SchemaProperty(SchemaPropertyImpl* impl) { PI::ctor(*this, impl); }
SchemaProperty::SchemaProperty(const SchemaProperty& s) : qmf::Handle<SchemaPropertyImpl>() { PI::copy(*this, s); }
SchemaProperty::~SchemaProperty() { PI::dtor(*this); }
SchemaProperty& SchemaProperty::operator=(const SchemaProperty& s) { return PI::assign(*this, s); }

SchemaProperty::SchemaProperty(const string& n, int t, const string& o) { PI::ctor(*this, new SchemaPropertyImpl(n, t, o)); }

void SchemaProperty::setAccess(int a) { impl->setAccess(a); }
void SchemaProperty::setIndex(bool i) { impl->setIndex(i); }
void SchemaProperty::setOptional(bool o) { impl->setOptional(o); }
void SchemaProperty::setUnit(const string& u) { impl->setUnit(u); }
void SchemaProperty::setDesc(const string& d) { impl->setDesc(d); }
void SchemaProperty::setSubtype(const string& s) { impl->setSubtype(s); }
void SchemaProperty::setDirection(int d) { impl->setDirection(d); }

const string& SchemaProperty::getName() const { return impl->getName(); }
int SchemaProperty::getType() const { return impl->getType(); }
int SchemaProperty::getAccess() const { return impl->getAccess(); }
bool SchemaProperty::isIndex() const { return impl->isIndex(); }
bool SchemaProperty::isOptional() const { return impl->isOptional(); }
const string& SchemaProperty::getUnit() const { return impl->getUnit(); }
const string& SchemaProperty::getDesc() const { return impl->getDesc(); }
const string& SchemaProperty::getSubtype() const { return impl->getSubtype(); }
int SchemaProperty::getDirection() const { return impl->getDirection(); }

//========================================================================================
// Impl Method Bodies
//========================================================================================

SchemaPropertyImpl::SchemaPropertyImpl(const string& n, int t, const string options) :
    name(n), dataType(t), access(ACCESS_READ_ONLY), index(false),
    optional(false), direction(DIR_IN)
{
    if (!options.empty()) {
        qpid::messaging::AddressParser parser = qpid::messaging::AddressParser(options);
        Variant::Map optMap;
        Variant::Map::iterator iter;

        parser.parseMap(optMap);
        
        iter = optMap.find("access");
        if (iter != optMap.end()) {
            const string& v(iter->second.asString());
            if      (v == "RC") access = ACCESS_READ_CREATE;
            else if (v == "RO") access = ACCESS_READ_ONLY;
            else if (v == "RW") access = ACCESS_READ_WRITE;
            else
                throw QmfException("Invalid value for 'access' option.  Expected RC, RO, or RW");
            optMap.erase(iter);
        }

        iter = optMap.find("index");
        if (iter != optMap.end()) {
            index = iter->second.asBool();
            optMap.erase(iter);
        }

        iter = optMap.find("optional");
        if (iter != optMap.end()) {
            optional = iter->second.asBool();
            optMap.erase(iter);
        }

        iter = optMap.find("unit");
        if (iter != optMap.end()) {
            unit = iter->second.asString();
            optMap.erase(iter);
        }

        iter = optMap.find("desc");
        if (iter != optMap.end()) {
            desc = iter->second.asString();
            optMap.erase(iter);
        }

        iter = optMap.find("subtype");
        if (iter != optMap.end()) {
            subtype = iter->second.asString();
            optMap.erase(iter);
        }

        iter = optMap.find("dir");
        if (iter != optMap.end()) {
            const string& v(iter->second.asString());
            if      (v == "IN")    direction = DIR_IN;
            else if (v == "OUT")   direction = DIR_OUT;
            else if (v == "INOUT") direction = DIR_IN_OUT;
            else
                throw QmfException("Invalid value for 'dir' option.  Expected IN, OUT, or INOUT");
            optMap.erase(iter);
        }

        if (!optMap.empty())
            throw QmfException("Unexpected option: " + optMap.begin()->first);
    }
}


SchemaPropertyImpl::SchemaPropertyImpl(const Variant::Map& map) :
    access(ACCESS_READ_ONLY), index(false), optional(false), direction(DIR_IN)
{
    Variant::Map::const_iterator iter;

    iter = map.find("_name");
    if (iter == map.end())
        throw QmfException("SchemaProperty without a _name element");
    name = iter->second.asString();

    iter = map.find("_type");
    if (iter == map.end())
        throw QmfException("SchemaProperty without a _type element");
    const string& ts(iter->second.asString());
    if      (ts == "TYPE_VOID")   dataType = SCHEMA_DATA_VOID;
    else if (ts == "TYPE_BOOL")   dataType = SCHEMA_DATA_BOOL;
    else if (ts == "TYPE_INT")    dataType = SCHEMA_DATA_INT;
    else if (ts == "TYPE_FLOAT")  dataType = SCHEMA_DATA_FLOAT;
    else if (ts == "TYPE_STRING") dataType = SCHEMA_DATA_STRING;
    else if (ts == "TYPE_MAP")    dataType = SCHEMA_DATA_MAP;
    else if (ts == "TYPE_LIST")   dataType = SCHEMA_DATA_LIST;
    else if (ts == "TYPE_UUID")   dataType = SCHEMA_DATA_UUID;
    else
        throw QmfException("SchemaProperty with an invalid type code: " + ts);

    iter = map.find("_access");
    if (iter != map.end()) {
        const string& as(iter->second.asString());
        if      (as == "RO") access = ACCESS_READ_ONLY;
        else if (as == "RC") access = ACCESS_READ_CREATE;
        else if (as == "RW") access = ACCESS_READ_WRITE;
        else
            throw QmfException("SchemaProperty with an invalid access code: " + as);
    }

    iter = map.find("_unit");
    if (iter != map.end())
        unit = iter->second.asString();

    iter = map.find("_dir");
    if (iter != map.end()) {
        const string& ds(iter->second.asString());
        if      (ds == "I")  direction = DIR_IN;
        else if (ds == "O")  direction = DIR_OUT;
        else if (ds == "IO") direction = DIR_IN_OUT;
        else
            throw QmfException("SchemaProperty with an invalid direction code: " + ds);
    }

    iter = map.find("_desc");
    if (iter != map.end())
        desc = iter->second.asString();

    iter = map.find("_index");
    if (iter != map.end())
        index = iter->second.asBool();

    iter = map.find("_subtype");
    if (iter != map.end())
        subtype = iter->second.asString();
}


Variant::Map SchemaPropertyImpl::asMap() const
{
    Variant::Map map;
    string ts;

    map["_name"] = name;

    switch (dataType) {
    case SCHEMA_DATA_VOID:   ts = "TYPE_VOID";   break;
    case SCHEMA_DATA_BOOL:   ts = "TYPE_BOOL";   break;
    case SCHEMA_DATA_INT:    ts = "TYPE_INT";    break;
    case SCHEMA_DATA_FLOAT:  ts = "TYPE_FLOAT";  break;
    case SCHEMA_DATA_STRING: ts = "TYPE_STRING"; break;
    case SCHEMA_DATA_MAP:    ts = "TYPE_MAP";    break;
    case SCHEMA_DATA_LIST:   ts = "TYPE_LIST";   break;
    case SCHEMA_DATA_UUID:   ts = "TYPE_UUID";   break;
    }
    map["_type"] = ts;

    switch (access) {
    case ACCESS_READ_ONLY:   ts = "RO"; break;
    case ACCESS_READ_CREATE: ts = "RC"; break;
    case ACCESS_READ_WRITE:  ts = "RW"; break;
    }
    map["_access"] = ts;

    if (!unit.empty())
        map["_unit"] = unit;

    switch (direction) {
    case DIR_IN:     ts = "I";  break;
    case DIR_OUT:    ts = "O";  break;
    case DIR_IN_OUT: ts = "IO"; break;
    }
    map["_dir"] = ts;

    if (!desc.empty())
        map["_desc"] = desc;

    if (index)
        map["_index"] = true;

    if (!subtype.empty())
        map["_subtype"] = subtype;

    return map;
}


SchemaPropertyImpl::SchemaPropertyImpl(qpid::management::Buffer& buffer) :
    access(ACCESS_READ_ONLY), index(false), optional(false), direction(DIR_IN)
{
    Variant::Map::const_iterator iter;
    Variant::Map pmap;

    buffer.getMap(pmap);
    iter = pmap.find("name");
    if (iter == pmap.end())
        throw QmfException("Received V1 Schema property without a name");
    name = iter->second.asString();

    iter = pmap.find("type");
    if (iter == pmap.end())
        throw QmfException("Received V1 Schema property without a type");
    fromV1TypeCode(iter->second.asInt8());

    iter = pmap.find("unit");
    if (iter != pmap.end())
        unit = iter->second.asString();

    iter = pmap.find("desc");
    if (iter != pmap.end())
        desc = iter->second.asString();

    iter = pmap.find("access");
    if (iter != pmap.end()) {
        int8_t val = iter->second.asInt8();
        if (val < 1 || val > 3)
            throw QmfException("Received V1 Schema property with invalid 'access' code");
        access = val;
    }

    iter = pmap.find("index");
    if (iter != pmap.end())
        index = iter->second.asInt64() != 0;

    iter = pmap.find("optional");
    if (iter != pmap.end())
        optional = iter->second.asInt64() != 0;

    iter = pmap.find("dir");
    if (iter != pmap.end()) {
        string dirStr(iter->second.asString());
        if      (dirStr == "I")  direction = DIR_IN;
        else if (dirStr == "O")  direction = DIR_OUT;
        else if (dirStr == "IO") direction = DIR_IN_OUT;
        else
            throw QmfException("Received V1 Schema property with invalid 'dir' code");
    }
}


void SchemaPropertyImpl::updateHash(Hash& hash) const
{
    hash.update(name);
    hash.update((uint8_t) dataType);
    hash.update(subtype);
    hash.update((uint8_t) access);
    hash.update(index);
    hash.update(optional);
    hash.update(unit);
    hash.update(desc);
    hash.update((uint8_t) direction);
}


void SchemaPropertyImpl::encodeV1(qpid::management::Buffer& buffer, bool isArg, bool isMethodArg) const
{
    Variant::Map pmap;

    pmap["name"] = name;
    pmap["type"] = v1TypeCode();
    if (!unit.empty())
        pmap["unit"] = unit;
    if (!desc.empty())
        pmap["desc"] = desc;
    if (!isArg) {
        pmap["access"] = access;
        pmap["index"] = index ? 1 : 0;
        pmap["optional"] = optional ? 1 : 0;
    } else {
        if (isMethodArg) {
            string dirStr;
            switch (direction) {
            case DIR_IN     : dirStr = "I";  break;
            case DIR_OUT    : dirStr = "O";  break;
            case DIR_IN_OUT : dirStr = "IO"; break;
            }
            pmap["dir"] = dirStr;
        }
    }

    buffer.putMap(pmap);
}


uint8_t SchemaPropertyImpl::v1TypeCode() const
{
    switch (dataType) {
    case SCHEMA_DATA_VOID:    return 1;
    case SCHEMA_DATA_BOOL:    return 11;
    case SCHEMA_DATA_INT:
        if (subtype == "timestamp") return 8;
        if (subtype == "duration") return 9;
        return 19;
    case SCHEMA_DATA_FLOAT:   return 13;
    case SCHEMA_DATA_STRING:  return 7;
    case SCHEMA_DATA_LIST:    return 21;
    case SCHEMA_DATA_UUID:    return 14;
    case SCHEMA_DATA_MAP:
        if (subtype == "reference") return 10;
        if (subtype == "data") return 20;
        return 15;
    }

    return 1;
}

void SchemaPropertyImpl::fromV1TypeCode(int8_t code)
{
    switch (code) {
    case 1:  // U8
    case 2:  // U16
    case 3:  // U32
    case 4:  // U64
        dataType = SCHEMA_DATA_INT;
        break;
    case 6:  // SSTR
    case 7:  // LSTR
        dataType = SCHEMA_DATA_STRING;
        break;
    case 8:  // ABSTIME
        dataType = SCHEMA_DATA_INT;
        subtype = "timestamp";
        break;
    case 9:  // DELTATIME
        dataType = SCHEMA_DATA_INT;
        subtype = "duration";
        break;
    case 10: // REF
        dataType = SCHEMA_DATA_MAP;
        subtype = "reference";
        break;
    case 11: // BOOL
        dataType = SCHEMA_DATA_BOOL;
        break;
    case 12: // FLOAT
    case 13: // DOUBLE
        dataType = SCHEMA_DATA_FLOAT;
        break;
    case 14: // UUID
        dataType = SCHEMA_DATA_UUID;
        break;
    case 15: // FTABLE
        dataType = SCHEMA_DATA_MAP;
        break;
    case 16: // S8
    case 17: // S16
    case 18: // S32
    case 19: // S64
        dataType = SCHEMA_DATA_INT;
        break;
    case 20: // OBJECT
        dataType = SCHEMA_DATA_MAP;
        subtype = "data";
        break;
    case 21: // LIST
    case 22: // ARRAY
        dataType = SCHEMA_DATA_LIST;
        break;
    default:
        throw QmfException("Received V1 schema with an unknown data type");
    }
}


SchemaPropertyImpl& SchemaPropertyImplAccess::get(SchemaProperty& item)
{
    return *item.impl;
}


const SchemaPropertyImpl& SchemaPropertyImplAccess::get(const SchemaProperty& item)
{
    return *item.impl;
}
