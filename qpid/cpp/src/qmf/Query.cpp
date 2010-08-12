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

#include "qpid/RefCounted.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/Query.h"
#include "qmf/DataAddr.h"
#include "qmf/SchemaId.h"

using namespace std;
using qpid::types::Variant;

namespace qmf {
    class QueryImpl : public virtual qpid::RefCounted {
    public:
        //
        // Methods from API handle
        //
        QueryImpl(const string& c, const string& p, const string&) :  packageName(p), className(c) {}
        QueryImpl(const SchemaId& s) : schemaId(s) {}
        QueryImpl(const DataAddr& a) : dataAddr(a) {}

        const DataAddr& getDataAddr() const { return dataAddr; }
        const SchemaId& getSchemaId() const { return schemaId; }
        const string& getClassName() const { return className; }
        const string& getPackageName() const { return packageName; }
        void addPredicate(const string& k, const Variant& v) { predicate[k] = v; }
        const Variant::Map& getPredicate() const { return predicate; }

    private:
        string packageName;
        string className;
        SchemaId schemaId;
        DataAddr dataAddr;
        Variant::Map predicate;
    };

    typedef PrivateImplRef<Query> PI;

    Query::Query(QueryImpl* impl) { PI::ctor(*this, impl); }
    Query::Query(const Query& s) : qmf::Handle<QueryImpl>() { PI::copy(*this, s); }
    Query::~Query() { PI::dtor(*this); }
    Query& Query::operator=(const Query& s) { return PI::assign(*this, s); }

    Query::Query(const string& c, const string& p, const string& pr) { PI::ctor(*this, new QueryImpl(c, p, pr)); }
    Query::Query(const SchemaId& s) { PI::ctor(*this, new QueryImpl(s)); }
    Query::Query(const DataAddr& a) { PI::ctor(*this, new QueryImpl(a)); }

    const DataAddr& Query::getDataAddr() const { return impl->getDataAddr(); }
    const SchemaId& Query::getSchemaId() const { return impl->getSchemaId(); }
    const string& Query::getClassName() const { return impl->getClassName(); }
    const string& Query::getPackageName() const { return impl->getPackageName(); }
    void Query::addPredicate(const string& k, const Variant& v) { impl->addPredicate(k, v); }
    const Variant::Map& Query::getPredicate() const { return impl->getPredicate(); }
}

