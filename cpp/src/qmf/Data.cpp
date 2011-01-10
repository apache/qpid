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

#include "qmf/DataImpl.h"
#include "qmf/DataAddrImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/SchemaIdImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/SchemaProperty.h"

using namespace std;
using namespace qmf;
using qpid::types::Variant;

typedef PrivateImplRef<Data> PI;

Data::Data(DataImpl* impl) { PI::ctor(*this, impl); }
Data::Data(const Data& s) : qmf::Handle<DataImpl>() { PI::copy(*this, s); }
Data::~Data() { PI::dtor(*this); }
Data& Data::operator=(const Data& s) { return PI::assign(*this, s); }

Data::Data(const Schema& s) { PI::ctor(*this, new DataImpl(s)); }
void Data::setAddr(const DataAddr& a) { impl->setAddr(a); }
void Data::setProperty(const string& k, const qpid::types::Variant& v) { impl->setProperty(k, v); }
void Data::overwriteProperties(const qpid::types::Variant::Map& m) { impl->overwriteProperties(m); }
bool Data::hasSchema() const { return impl->hasSchema(); }
bool Data::hasAddr() const { return impl->hasAddr(); }
const SchemaId& Data::getSchemaId() const { return impl->getSchemaId(); }
const DataAddr& Data::getAddr() const { return impl->getAddr(); }
const Variant& Data::getProperty(const string& k) const { return impl->getProperty(k); }
const Variant::Map& Data::getProperties() const { return impl->getProperties(); }
bool Data::hasAgent() const { return impl->hasAgent(); }
const Agent& Data::getAgent() const { return impl->getAgent(); }


void DataImpl::overwriteProperties(const Variant::Map& m) {
    for (Variant::Map::const_iterator iter = m.begin(); iter != m.end(); iter++)
        properties[iter->first] = iter->second;
}

const Variant& DataImpl::getProperty(const string& k) const {
    Variant::Map::const_iterator iter = properties.find(k);
    if (iter == properties.end())
        throw KeyNotFound(k);
    return iter->second;
}


DataImpl::DataImpl(const qpid::types::Variant::Map& map, const Agent& a)
{
    Variant::Map::const_iterator iter;

    agent = a;

    iter = map.find("_values");
    if (iter != map.end())
        properties = iter->second.asMap();

    iter = map.find("_object_id");
    if (iter != map.end())
        dataAddr = DataAddr(new DataAddrImpl(iter->second.asMap()));

    iter = map.find("_schema_id");
    if (iter != map.end())
        schemaId = SchemaId(new SchemaIdImpl(iter->second.asMap()));
}


Variant::Map DataImpl::asMap() const
{
    Variant::Map result;

    result["_values"] = properties;

    if (hasAddr()) {
        const DataAddrImpl& aImpl(DataAddrImplAccess::get(getAddr()));
        result["_object_id"] = aImpl.asMap();
    }

    if (hasSchema()) {
        const SchemaIdImpl& sImpl(SchemaIdImplAccess::get(getSchemaId()));
        result["_schema_id"] = sImpl.asMap();
    }

    return result;
}


void DataImpl::setProperty(const std::string& k, const qpid::types::Variant& v)
{
    if (schema.isValid()) {
        //
        //  If we have a valid schema, make sure that the property is included in the 
        //  schema and that the variant type is compatible with the schema type.
        //
        if (!SchemaImplAccess::get(schema).isValidProperty(k, v))
            throw QmfException("Property '" + k + "' either not in the schema or value is of incompatible type");
    }
    properties[k] = v;
}


DataImpl& DataImplAccess::get(Data& item)
{
    return *item.impl;
}


const DataImpl& DataImplAccess::get(const Data& item)
{
    return *item.impl;
}
