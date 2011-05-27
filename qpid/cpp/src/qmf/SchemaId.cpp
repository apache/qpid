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

#include "qmf/SchemaIdImpl.h"
#include "qmf/PrivateImplRef.h"

using namespace std;
using namespace qmf;
using qpid::types::Variant;

typedef PrivateImplRef<SchemaId> PI;

SchemaId::SchemaId(SchemaIdImpl* impl) { PI::ctor(*this, impl); }
SchemaId::SchemaId(const SchemaId& s) : qmf::Handle<SchemaIdImpl>() { PI::copy(*this, s); }
SchemaId::~SchemaId() { PI::dtor(*this); }
SchemaId& SchemaId::operator=(const SchemaId& s) { return PI::assign(*this, s); }

SchemaId::SchemaId(int t, const string& p, const string& n) { PI::ctor(*this, new SchemaIdImpl(t, p, n)); }
void SchemaId::setHash(const qpid::types::Uuid& h) { impl->setHash(h); }
int SchemaId::getType() const { return impl->getType(); }
const string& SchemaId::getPackageName() const { return impl->getPackageName(); }
const string& SchemaId::getName() const { return impl->getName(); }
const qpid::types::Uuid& SchemaId::getHash() const { return impl->getHash(); }


SchemaIdImpl::SchemaIdImpl(const Variant::Map& map)
{
    Variant::Map::const_iterator iter;

    iter = map.find("_package_name");
    if (iter != map.end())
        package = iter->second.asString();

    iter = map.find("_class_name");
    if (iter != map.end())
        name = iter->second.asString();

    iter = map.find("_type");
    if (iter != map.end()) {
        const string& stype = iter->second.asString();
        if (stype == "_data")
            sType = SCHEMA_TYPE_DATA;
        else if (stype == "_event")
            sType = SCHEMA_TYPE_EVENT;
    }

    iter = map.find("_hash");
    if (iter != map.end())
        hash = iter->second.asUuid();
}


Variant::Map SchemaIdImpl::asMap() const
{
    Variant::Map result;

    result["_package_name"] = package;
    result["_class_name"] = name;
    if (sType == SCHEMA_TYPE_DATA)
        result["_type"] = "_data";
    else
        result["_type"] = "_event";
    if (!hash.isNull())
        result["_hash"] = hash;
    return result;
}


SchemaIdImpl& SchemaIdImplAccess::get(SchemaId& item)
{
    return *item.impl;
}


const SchemaIdImpl& SchemaIdImplAccess::get(const SchemaId& item)
{
    return *item.impl;
}
