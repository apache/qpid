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

#include "qmf/QueryImpl.h"

using namespace std;
using namespace qmf;

//==================================================================
// Wrappers
//==================================================================

Query::Query() : impl(new QueryImpl(this)) {}
Query::Query(QueryImpl* i) : impl(i) {}

Query::~Query()
{
    delete impl;
}

const char* Query::getPackage() const
{
    return impl->getPackage();
}

const char* Query::getClass() const
{
    return impl->getClass();
}

const ObjectId* Query::getObjectId() const
{
    return impl->getObjectId();
}

int Query::whereCount() const
{
    return impl->whereCount();
}

Query::Oper Query::whereOper() const
{
    return impl->whereOper();
}

const char* Query::whereKey() const
{
    return impl->whereKey();
}

const Value* Query::whereValue() const
{
    return impl->whereValue();
}

