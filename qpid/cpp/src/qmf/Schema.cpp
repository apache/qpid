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

#include "qmf/SchemaImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"
#include "qmf/SchemaTypes.h"
#include "qmf/SchemaIdImpl.h"
#include "qmf/SchemaPropertyImpl.h"
#include "qmf/SchemaMethodImpl.h"
#include "qmf/Hash.h"
#include "qpid/log/Statement.h"
#include "qpid/management/Buffer.h"
#include <list>

using namespace std;
using qpid::types::Variant;
using namespace qmf;

typedef PrivateImplRef<Schema> PI;

Schema::Schema(SchemaImpl* impl) { PI::ctor(*this, impl); }
Schema::Schema(const Schema& s) : qmf::Handle<SchemaImpl>() { PI::copy(*this, s); }
Schema::~Schema() { PI::dtor(*this); }
Schema& Schema::operator=(const Schema& s) { return PI::assign(*this, s); }

Schema::Schema(int t, const string& p, const string& c) { PI::ctor(*this, new SchemaImpl(t, p, c)); }
const SchemaId& Schema::getSchemaId() const { return impl->getSchemaId(); }
void Schema::finalize() { impl->finalize(); }
bool Schema::isFinalized() const { return impl->isFinalized(); }
void Schema::addProperty(const SchemaProperty& p) { impl->addProperty(p); }
void Schema::addMethod(const SchemaMethod& m) { impl->addMethod(m); }
void Schema::setDesc(const string& d) { impl->setDesc(d); }
const string& Schema::getDesc() const { return impl->getDesc(); }
void Schema::setDefaultSeverity(int s) { impl->setDefaultSeverity(s); }
int Schema::getDefaultSeverity() const { return impl->getDefaultSeverity(); }
uint32_t Schema::getPropertyCount() const { return impl->getPropertyCount(); }
SchemaProperty Schema::getProperty(uint32_t i) const { return impl->getProperty(i); }
uint32_t Schema::getMethodCount() const { return impl->getMethodCount(); }
SchemaMethod Schema::getMethod(uint32_t i) const { return impl->getMethod(i); }

//========================================================================================
// Impl Method Bodies
//========================================================================================

SchemaImpl::SchemaImpl(const Variant::Map& map) : finalized(false)
{
    Variant::Map::const_iterator iter;
    Variant::List::const_iterator lIter;

    iter = map.find("_schema_id");
    if (iter == map.end())
        throw QmfException("Schema map missing _schema_id element");
    schemaId = SchemaId(new SchemaIdImpl(iter->second.asMap()));

    iter = map.find("_desc");
    if (iter != map.end())
        description = iter->second.asString();

    iter = map.find("_default_severity");
    if (iter != map.end())
        defaultSeverity = int(iter->second.asUint32());

    iter = map.find("_properties");
    if (iter != map.end()) {
        const Variant::List& props(iter->second.asList());
        for (lIter = props.begin(); lIter != props.end(); lIter++)
            addProperty(SchemaProperty(new SchemaPropertyImpl(lIter->asMap())));
    }

    iter = map.find("_methods");
    if (iter != map.end()) {
        const Variant::List& meths(iter->second.asList());
        for (lIter = meths.begin(); lIter != meths.end(); lIter++)
            addMethod(SchemaMethod(new SchemaMethodImpl(lIter->asMap())));
    }

    finalized = true;
}


Variant::Map SchemaImpl::asMap() const
{
    Variant::Map map;
    Variant::List propList;
    Variant::List methList;

    checkNotFinal();

    map["_schema_id"] = SchemaIdImplAccess::get(schemaId).asMap();
    if (!description.empty())
        map["_desc"] = description;
    if (schemaId.getType() == SCHEMA_TYPE_EVENT)
        map["_default_severity"] = uint32_t(defaultSeverity);

    for (list<SchemaProperty>::const_iterator pIter = properties.begin(); pIter != properties.end(); pIter++)
        propList.push_back(SchemaPropertyImplAccess::get(*pIter).asMap());

    for (list<SchemaMethod>::const_iterator mIter = methods.begin(); mIter != methods.end(); mIter++)
        methList.push_back(SchemaMethodImplAccess::get(*mIter).asMap());

    map["_properties"] = propList;
    map["_methods"] = methList;
    return map;
}


SchemaImpl::SchemaImpl(qpid::management::Buffer& buffer) : finalized(false)
{
    int schemaType;
    string packageName;
    string className;
    uint8_t hash[16];

    schemaType = int(buffer.getOctet());
    buffer.getShortString(packageName);
    buffer.getShortString(className);
    buffer.getBin128(hash);
    schemaId = SchemaId(schemaType, packageName, className);
    schemaId.setHash(qpid::types::Uuid(hash));

    if (schemaType == SCHEMA_TYPE_DATA) {
        uint16_t propCount(buffer.getShort());
        uint16_t statCount(buffer.getShort());
        uint16_t methCount(buffer.getShort());
        for (uint16_t idx = 0; idx < propCount + statCount; idx++)
            addProperty(new SchemaPropertyImpl(buffer));
        for (uint16_t idx = 0; idx < methCount; idx++)
            addMethod(new SchemaMethodImpl(buffer));
    }

    finalized = true;
}


string SchemaImpl::asV1Content(uint32_t sequence) const
{
#define RAW_BUF_SIZE 65536
    char rawBuf[RAW_BUF_SIZE];
    qpid::management::Buffer buffer(rawBuf, RAW_BUF_SIZE);

    //
    // Encode the QMFv1 Header
    //
    buffer.putOctet('A');
    buffer.putOctet('M');
    buffer.putOctet('2');
    buffer.putOctet('s');
    buffer.putLong(sequence);

    //
    // Encode the common schema information
    //
    buffer.putOctet(uint8_t(schemaId.getType()));
    buffer.putShortString(schemaId.getPackageName());
    buffer.putShortString(schemaId.getName());
    buffer.putBin128(schemaId.getHash().data());

    if (schemaId.getType() == SCHEMA_TYPE_DATA) {
        buffer.putShort(properties.size());
        buffer.putShort(0);
        buffer.putShort(methods.size());
        for (list<SchemaProperty>::const_iterator pIter = properties.begin(); pIter != properties.end(); pIter++)
            SchemaPropertyImplAccess::get(*pIter).encodeV1(buffer, false, false);
        for (list<SchemaMethod>::const_iterator mIter = methods.begin(); mIter != methods.end(); mIter++)
            SchemaMethodImplAccess::get(*mIter).encodeV1(buffer);
    } else {
        buffer.putShort(properties.size());
        for (list<SchemaProperty>::const_iterator pIter = properties.begin(); pIter != properties.end(); pIter++)
            SchemaPropertyImplAccess::get(*pIter).encodeV1(buffer, true, false);
    }

    return string(rawBuf, buffer.getPosition());
}


bool SchemaImpl::isValidProperty(const std::string& k, const Variant& v) const
{
    for (list<SchemaProperty>::const_iterator iter = properties.begin(); iter != properties.end(); iter++)
        if (iter->getName() == k)
            return (isCompatibleType(iter->getType(), v.getType()));
    return false;
}


bool SchemaImpl::isValidMethodInArg(const std::string& m, const std::string& k, const Variant& v) const
{
    for (list<SchemaMethod>::const_iterator mIter = methods.begin(); mIter != methods.end(); mIter++) {
        if (mIter->getName() == m) {
            uint32_t count(mIter->getArgumentCount());
            for (uint32_t i = 0; i < count; i++) {
                const SchemaProperty prop(mIter->getArgument(i));
                if (prop.getName() == k) {
                    if (prop.getDirection() == DIR_IN || prop.getDirection() == DIR_IN_OUT)
                        return (isCompatibleType(prop.getType(), v.getType()));
                    else
                        return false;
                }
            }
        }
    }
    return false;
}


bool SchemaImpl::isValidMethodOutArg(const std::string& m, const std::string& k, const Variant& v) const
{
    for (list<SchemaMethod>::const_iterator mIter = methods.begin(); mIter != methods.end(); mIter++) {
        if (mIter->getName() == m) {
            uint32_t count(mIter->getArgumentCount());
            for (uint32_t i = 0; i < count; i++) {
                const SchemaProperty prop(mIter->getArgument(i));
                if (prop.getName() == k) {
                    if (prop.getDirection() == DIR_OUT || prop.getDirection() == DIR_IN_OUT)
                        return (isCompatibleType(prop.getType(), v.getType()));
                    else
                        return false;
                }
            }
        }
    }
    return false;
}


void SchemaImpl::finalize()
{
    Hash hash;

    hash.update((uint8_t) schemaId.getType());
    hash.update(schemaId.getPackageName());
    hash.update(schemaId.getName());

    for (list<SchemaProperty>::const_iterator pIter = properties.begin(); pIter != properties.end(); pIter++)
        SchemaPropertyImplAccess::get(*pIter).updateHash(hash);
    for (list<SchemaMethod>::const_iterator mIter = methods.begin(); mIter != methods.end(); mIter++)
        SchemaMethodImplAccess::get(*mIter).updateHash(hash);

    schemaId.setHash(hash.asUuid());
    QPID_LOG(debug, "Schema Finalized: " << schemaId.getPackageName() << ":" << schemaId.getName() << ":" <<
        schemaId.getHash());

    finalized = true;
}


SchemaProperty SchemaImpl::getProperty(uint32_t i) const
{
    uint32_t count = 0;
    for (list<SchemaProperty>::const_iterator iter = properties.begin(); iter != properties.end(); iter++)
        if (count++ == i)
            return *iter;
    throw IndexOutOfRange();
}


SchemaMethod SchemaImpl::getMethod(uint32_t i) const
{
    uint32_t count = 0;
    for (list<SchemaMethod>::const_iterator iter = methods.begin(); iter != methods.end(); iter++)
        if (count++ == i)
            return *iter;
    throw IndexOutOfRange();
}

void SchemaImpl::checkFinal() const
{
    if (finalized)
        throw QmfException("Modification of a finalized schema is forbidden");
}


void SchemaImpl::checkNotFinal() const
{
    if (!finalized)
        throw QmfException("Schema is not yet finalized/registered");
}


bool SchemaImpl::isCompatibleType(int qmfType, qpid::types::VariantType qpidType) const
{
    bool typeValid(false);

    switch (qpidType) {
    case qpid::types::VAR_VOID:
        if (qmfType == SCHEMA_DATA_VOID)
            typeValid = true;
        break;
    case qpid::types::VAR_BOOL:
        if (qmfType == SCHEMA_DATA_BOOL)
            typeValid = true;
        break;
    case qpid::types::VAR_UINT8:
    case qpid::types::VAR_UINT16:
    case qpid::types::VAR_UINT32:
    case qpid::types::VAR_UINT64:
    case qpid::types::VAR_INT8:
    case qpid::types::VAR_INT16:
    case qpid::types::VAR_INT32:
    case qpid::types::VAR_INT64:
        if (qmfType == SCHEMA_DATA_INT)
            typeValid = true;
        break;
    case qpid::types::VAR_FLOAT:
    case qpid::types::VAR_DOUBLE:
        if (qmfType == SCHEMA_DATA_FLOAT)
            typeValid = true;
        break;
    case qpid::types::VAR_STRING:
        if (qmfType == SCHEMA_DATA_STRING)
            typeValid = true;
        break;
    case qpid::types::VAR_MAP:
        if (qmfType == SCHEMA_DATA_MAP)
            typeValid = true;
        break;
    case qpid::types::VAR_LIST:
        if (qmfType == SCHEMA_DATA_LIST)
            typeValid = true;
        break;
    case qpid::types::VAR_UUID:
        if (qmfType == SCHEMA_DATA_UUID)
            typeValid = true;
        break;
    }

    return typeValid;
}


SchemaImpl& SchemaImplAccess::get(Schema& item)
{
    return *item.impl;
}


const SchemaImpl& SchemaImplAccess::get(const Schema& item)
{
    return *item.impl;
}
