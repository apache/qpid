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

#include "qmf/engine/ObjectImpl.h"
#include <qpid/sys/Time.h>

using namespace std;
using namespace qmf::engine;
using namespace qpid::sys;
using namespace qpid::messaging;

ObjectImpl::ObjectImpl() :
    objectClass(0), createTime(uint64_t(Duration(now()))), destroyTime(0), lastUpdatedTime(createTime)
{
}


ObjectImpl::ObjectImpl(SchemaObjectClass* type) :
    objectClass(type), createTime(uint64_t(Duration(now()))), destroyTime(0), lastUpdatedTime(createTime)
{
}


void ObjectImpl::touch()
{
    lastUpdatedTime = uint64_t(Duration(now()));
}


void ObjectImpl::destroy()
{
    destroyTime = uint64_t(Duration(now()));
}


//==================================================================
// Wrappers
//==================================================================

Object::Object() : impl(new ObjectImpl()) {}
Object::Object(SchemaObjectClass* type) : impl(new ObjectImpl(type)) {}
Object::Object(const Object& from) : impl(new ObjectImpl(*(from.impl))) {}
Object::~Object() { delete impl; }
const Variant::Map& Object::getValues() const { return impl->getValues(); }
Variant::Map& Object::getValues() { return impl->getValues(); }
const SchemaObjectClass* Object::getSchema() const { return impl->getSchema(); }
void Object::setSchema(SchemaObjectClass* schema) { impl->setSchema(schema); }
const char* Object::getKey() const { return impl->getKey(); }
void Object::setKey(const char* key) { impl->setKey(key); }
void Object::touch() { impl->touch(); }
void Object::destroy() { impl->destroy(); }
