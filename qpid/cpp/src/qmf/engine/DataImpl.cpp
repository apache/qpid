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

#include "qmf/Protocol.h"
#include "qmf/engine/DataImpl.h"
#include <qpid/sys/Time.h>

using namespace std;
using namespace qmf::engine;
using namespace qpid::sys;
using namespace qpid::messaging;

DataImpl::DataImpl() :
    objectClass(0), createTime(uint64_t(Duration(now()))), destroyTime(0), lastUpdatedTime(createTime)
{
}


DataImpl::DataImpl(SchemaClass* type, const qpid::messaging::Variant::Map& v) :
    values(v), objectClass(type), createTime(uint64_t(Duration(now()))),
    destroyTime(0), lastUpdatedTime(createTime)
{
}


void DataImpl::touch()
{
    lastUpdatedTime = uint64_t(Duration(now()));
}


void DataImpl::destroy()
{
    destroyTime = uint64_t(Duration(now()));
}

Variant::Map DataImpl::asMap() const
{
    Variant::Map map;

    map[Protocol::VALUES] = values;
    if (!subtypes.empty())
        map[Protocol::SUBTYPES] = subtypes;
    // TODO: Add key, schema, and lifecycle data

    return map;
}


//==================================================================
// Wrappers
//==================================================================

Data::Data() : impl(new DataImpl()) {}
Data::Data(SchemaClass* type, const Variant::Map& m) : impl(new DataImpl(type, m)) {}
Data::Data(const Data& from) : impl(new DataImpl(*(from.impl))) {}
Data::~Data() { delete impl; }
const Variant::Map& Data::getValues() const { return impl->getValues(); }
Variant::Map& Data::getValues() { return impl->getValues(); }
const Variant::Map& Data::getSubtypes() const { return impl->getSubtypes(); }
Variant::Map& Data::getSubtypes() { return impl->getSubtypes(); }
const SchemaClass* Data::getSchema() const { return impl->getSchema(); }
void Data::setSchema(SchemaClass* schema) { impl->setSchema(schema); }
const char* Data::getKey() const { return impl->getKey(); }
void Data::setKey(const char* key) { impl->setKey(key); }
void Data::touch() { impl->touch(); }
void Data::destroy() { impl->destroy(); }
Variant::Map Data::asMap() const { return impl->asMap(); }
