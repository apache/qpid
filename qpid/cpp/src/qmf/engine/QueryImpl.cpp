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

#include "qmf/engine/QueryImpl.h"

using namespace std;
using namespace qmf::engine;
using namespace qpid::messaging;

bool QueryImpl::matches(const Object&) const
{
    return true;
}


void QueryImpl::parsePredicate(const std::string&)
{
    predicate.clear();
}


//==================================================================
// Wrappers
//==================================================================

Query::Query(const char* target) : impl(new QueryImpl(target)) {}
Query::Query(const char* target, const Variant::List& predicate) : impl(new QueryImpl(target, predicate)) {}
Query::Query(const char* target, const char* expression) : impl(new QueryImpl(target, expression)) {}
Query::Query(const Query& from) : impl(new QueryImpl(*(from.impl))) {}
Query::~Query() { delete impl; }
void Query::where(const Variant::List& predicate) { impl->where(predicate); }
void Query::where(const char* expression) { impl->where(expression); }
void Query::limit(uint32_t maxResults) { impl->limit(maxResults); }
void Query::orderBy(const char* attrName, bool decreasing) { impl->orderBy(attrName, decreasing); }
bool Query::havePredicate() const { return impl->havePredicate(); }
bool Query::haveLimit() const { return impl->haveLimit(); }
bool Query::haveOrderBy() const { return impl->haveOrderBy(); }
const Variant::List& Query::getPredicate() const { return impl->getPredicate(); }
uint32_t Query::getLimit() const { return impl->getLimit(); }
const char* Query::getOrderBy() const { return impl->getOrderBy(); }
bool Query::getDecreasing() const { return impl->getDecreasing(); }
bool Query::matches(const Object& object) const { return impl->matches(object); }

