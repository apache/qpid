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

#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"
#include "qmf/QueryImpl.h"
#include "qmf/DataAddrImpl.h"
#include "qmf/SchemaIdImpl.h"
#include "qpid/messaging/AddressParser.h"

using namespace std;
using namespace qmf;
using qpid::types::Variant;

typedef PrivateImplRef<Query> PI;

Query::Query(QueryImpl* impl) { PI::ctor(*this, impl); }
Query::Query(const Query& s) : qmf::Handle<QueryImpl>() { PI::copy(*this, s); }
Query::~Query() { PI::dtor(*this); }
Query& Query::operator=(const Query& s) { return PI::assign(*this, s); }

Query::Query(QueryTarget t, const string& pr) { PI::ctor(*this, new QueryImpl(t, pr)); }
Query::Query(QueryTarget t, const string& c, const string& p, const string& pr) { PI::ctor(*this, new QueryImpl(t, c, p, pr)); }
Query::Query(QueryTarget t, const SchemaId& s, const string& pr) { PI::ctor(*this, new QueryImpl(t, s, pr)); }
Query::Query(const DataAddr& a) { PI::ctor(*this, new QueryImpl(a)); }

QueryTarget Query::getTarget() const { return impl->getTarget(); }
const DataAddr& Query::getDataAddr() const { return impl->getDataAddr(); }
const SchemaId& Query::getSchemaId() const { return impl->getSchemaId(); }
void Query::setPredicate(const Variant::List& pr) { impl->setPredicate(pr); }
const Variant::List& Query::getPredicate() const { return impl->getPredicate(); }
bool Query::matchesPredicate(const qpid::types::Variant::Map& map) const { return impl->matchesPredicate(map); }


QueryImpl::QueryImpl(const Variant::Map& map) : predicateCompiled(false)
{
    Variant::Map::const_iterator iter;
    
    iter = map.find("_what");
    if (iter == map.end())
        throw QmfException("Query missing _what element");

    const string& targetString(iter->second.asString());
    if      (targetString == "OBJECT")    target = QUERY_OBJECT;
    else if (targetString == "OBJECT_ID") target = QUERY_OBJECT_ID;
    else if (targetString == "SCHEMA")    target = QUERY_SCHEMA;
    else if (targetString == "SCHEMA_ID") target = QUERY_SCHEMA_ID;
    else
        throw QmfException("Query with invalid _what value: " + targetString);

    iter = map.find("_object_id");
    if (iter != map.end()) {
        auto_ptr<DataAddrImpl> addrImpl(new DataAddrImpl(iter->second.asMap()));
        dataAddr = DataAddr(addrImpl.release());
    }

    iter = map.find("_schema_id");
    if (iter != map.end()) {
        auto_ptr<SchemaIdImpl> sidImpl(new SchemaIdImpl(iter->second.asMap()));
        schemaId = SchemaId(sidImpl.release());
    }

    iter = map.find("_where");
    if (iter != map.end())
        predicate = iter->second.asList();
}


Variant::Map QueryImpl::asMap() const
{
    Variant::Map map;
    string targetString;

    switch (target) {
    case QUERY_OBJECT    : targetString = "OBJECT";    break;
    case QUERY_OBJECT_ID : targetString = "OBJECT_ID"; break;
    case QUERY_SCHEMA    : targetString = "SCHEMA";    break;
    case QUERY_SCHEMA_ID : targetString = "SCHEMA_ID"; break;
    }

    map["_what"] = targetString;

    if (dataAddr.isValid())
        map["_object_id"] = DataAddrImplAccess::get(dataAddr).asMap();

    if (schemaId.isValid())
        map["_schema_id"] = SchemaIdImplAccess::get(schemaId).asMap();

    if (!predicate.empty())
        map["_where"] = predicate;

    return map;
}


bool QueryImpl::matchesPredicate(const qpid::types::Variant::Map& data) const
{
    if (predicate.empty())
        return true;

    if (!predicateCompiled) {
        expression.reset(new Expression(predicate));
        predicateCompiled = true;
    }

    return expression->evaluate(data);
}


void QueryImpl::parsePredicate(const string& pred)
{
    if (pred.empty())
        return;

    if (pred[0] == '[') {
        //
        // Parse this as an AddressParser list.
        //
        qpid::messaging::AddressParser parser(pred);
        parser.parseList(predicate);
    } else
        throw QmfException("Invalid predicate format");
}


QueryImpl& QueryImplAccess::get(Query& item)
{
    return *item.impl;
}


const QueryImpl& QueryImplAccess::get(const Query& item)
{
    return *item.impl;
}
