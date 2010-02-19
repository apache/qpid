#ifndef _QmfEngineQueryImpl_
#define _QmfEngineQueryImpl_

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

#include "qmf/engine/Query.h"
#include <qpid/messaging/Variant.h>
#include <string>
#include <boost/shared_ptr.hpp>

namespace qmf {
namespace engine {

    struct QueryImpl {
        QueryImpl(const char* _target) : target(_target), resultLimit(0) {}
        QueryImpl(const char* _target, const qpid::messaging::Variant::List& _predicate) :
            target(_target), predicate(_predicate), resultLimit(0) {}
        QueryImpl(const char* _target, const char* expression) :
            target(_target), resultLimit(0) { parsePredicate(expression); }
        ~QueryImpl() {}

        void where(const qpid::messaging::Variant::List& _predicate) { predicate = _predicate; }
        void where(const char* expression) { parsePredicate(expression); }
        void limit(uint32_t maxResults) { resultLimit = maxResults; }
        void orderBy(const char* attrName, bool decreasing) { sortAttr = attrName; orderDecreasing = decreasing; }

        bool havePredicate() const { return !predicate.empty(); }
        bool haveLimit() const { return resultLimit != 0; }
        bool haveOrderBy() const { return !sortAttr.empty(); }
        const qpid::messaging::Variant::List& getPredicate() const { return predicate; }
        uint32_t getLimit() const { return resultLimit; }
        const char* getOrderBy() const { return sortAttr.c_str(); }
        bool getDecreasing() const { return orderDecreasing; }
        bool matches(const Object& object) const;

        void parsePredicate(const std::string& expression);

        const std::string target;
        qpid::messaging::Variant::List predicate;
        uint32_t resultLimit;
        std::string sortAttr;
        bool orderDecreasing;
    };
}
}

#endif
