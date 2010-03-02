#ifndef _QmfEngineQuery_
#define _QmfEngineQuery_

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

#include <qpid/messaging/Variant.h>

namespace qmf {
namespace engine {

    class QueryImpl;

    class Query {
    public:
        Query(const char* target);
        Query(const char* target, const qpid::messaging::Variant::List& predicate);
        Query(const char* target, const char* expression);
        Query(const Query& from);
        ~Query();

        void where(const qpid::messaging::Variant::List& predicate);
        void where(const char* expression);
        void limit(uint32_t maxResults);
        void orderBy(const char* attrName, bool decreasing);

        bool havePredicate() const;
        bool haveLimit() const;
        bool haveOrderBy() const;
        const qpid::messaging::Variant::List& getPredicate() const;
        uint32_t getLimit() const;
        const char* getOrderBy() const;
        bool getDecreasing() const;

        bool matches(const qpid::messaging::Variant::Map& data) const;

    private:
        friend struct QueryImpl;
        friend struct BrokerProxyImpl;
        Query(QueryImpl*);
        QueryImpl* impl;
    };
}
}

#endif

