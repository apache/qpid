#ifndef _QMF_QUERY_IMPL_H_
#define _QMF_QUERY_IMPL_H_
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
#include "qmf/Query.h"
#include "qmf/DataAddr.h"
#include "qmf/SchemaId.h"
#include "qmf/Expression.h"
#include "qpid/types/Variant.h"
#include <boost/shared_ptr.hpp>

namespace qmf {
    class QueryImpl : public virtual qpid::RefCounted {
    public:
        //
        // Public impl-only methods
        //
        QueryImpl(const qpid::types::Variant::Map&);
        qpid::types::Variant::Map asMap() const;

        //
        // Methods from API handle
        //
        QueryImpl(QueryTarget t, const std::string& pr) : target(t), predicateCompiled(false) { parsePredicate(pr); }
        QueryImpl(QueryTarget t, const std::string& c, const std::string& p, const std::string& pr) :
            target(t), schemaId(SCHEMA_TYPE_DATA, p, c), predicateCompiled(false) { parsePredicate(pr); }
        QueryImpl(QueryTarget t, const SchemaId& s, const std::string& pr) :
            target(t), schemaId(s), predicateCompiled(false) { parsePredicate(pr); }
        QueryImpl(const DataAddr& a) : target(QUERY_OBJECT), dataAddr(a), predicateCompiled(false) {}

        QueryTarget getTarget() const { return target; }
        const DataAddr& getDataAddr() const { return dataAddr; }
        const SchemaId& getSchemaId() const { return schemaId; }
        void setPredicate(const qpid::types::Variant::List& pr) { predicate = pr; }
        const qpid::types::Variant::List& getPredicate() const { return predicate; }
        bool matchesPredicate(const qpid::types::Variant::Map& map) const;

    private:
        QueryTarget target;
        SchemaId schemaId;
        DataAddr dataAddr;
        qpid::types::Variant::List predicate;
        mutable bool predicateCompiled;
        mutable boost::shared_ptr<Expression> expression;

        void parsePredicate(const std::string& s);
    };

    struct QueryImplAccess
    {
        static QueryImpl& get(Query&);
        static const QueryImpl& get(const Query&);
    };
}

#endif

