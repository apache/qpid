#ifndef _QmfQueryImpl_
#define _QmfQueryImpl_

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

#include "qmf/Query.h"
#include "qmf/Schema.h"
#include <string>
#include <boost/shared_ptr.hpp>

namespace qpid {
    namespace framing {
        class Buffer;
    }
}

namespace qmf {

    struct QueryElementImpl {
        QueryElementImpl(const std::string& a, const Value* v, ValueOper o) :
            envelope(new QueryElement(this)), attrName(a), value(v), oper(o) {}
        ~QueryElementImpl() {}
        bool evaluate(const Object* object) const;

        QueryElement* envelope;
        std::string attrName;
        const Value* value;
        ValueOper oper;
    };

    struct QueryExpressionImpl {
        QueryExpressionImpl(ExprOper o, const QueryOperand* operand1, const QueryOperand* operand2) :
            envelope(new QueryExpression(this)), oper(o), left(operand1), right(operand2) {}
        ~QueryExpressionImpl() {}
        bool evaluate(const Object* object) const;

        QueryExpression* envelope;
        ExprOper oper;
        const QueryOperand* left;
        const QueryOperand* right;
    };

    struct QueryImpl {
        QueryImpl(Query* e) : envelope(e), select(0) {}
        QueryImpl(const std::string& c, const std::string& p) :
            envelope(new Query(this)), packageName(p), className(c) {}
        QueryImpl(const SchemaClassKey* key) :
            envelope(new Query(this)), packageName(key->getPackageName()), className(key->getClassName()) {}
        QueryImpl(const ObjectId* oid) :
            envelope(new Query(this)), oid(new ObjectId(*oid)) {}
        QueryImpl(qpid::framing::Buffer& buffer);
        ~QueryImpl() {};

        void setSelect(const QueryOperand* criterion) { select = criterion; }
        void setLimit(uint32_t maxResults) { resultLimit = maxResults; }
        void setOrderBy(const std::string& attrName, bool decreasing) {
            orderBy = attrName; orderDecreasing = decreasing;
        }

        const std::string& getPackage() const { return packageName; }
        const std::string&  getClass() const { return className; }
        const ObjectId* getObjectId() const { return oid.get(); }

        bool haveSelect() const { return select != 0; }
        bool haveLimit() const { return resultLimit > 0; }
        bool haveOrderBy() const { return !orderBy.empty(); }
        const QueryOperand* getSelect() const { return select; }
        uint32_t getLimit() const { return resultLimit; }
        const std::string& getOrderBy() const { return orderBy; }
        bool getDecreasing() const { return orderDecreasing; }

        void encode(qpid::framing::Buffer& buffer) const;

        Query* envelope;
        std::string packageName;
        std::string className;
        boost::shared_ptr<ObjectId> oid;
        const QueryOperand* select;
        uint32_t resultLimit;
        std::string orderBy;
        bool orderDecreasing;
    };
}

#endif
