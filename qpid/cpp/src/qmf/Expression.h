#ifndef _QMF_EXPRESSION_H_
#define _QMF_EXPRESSION_H_
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

#include "qpid/types/Variant.h"
#include <string>
#include <list>
#include <boost/shared_ptr.hpp>

namespace qmf {

    enum LogicalOp {
    LOGICAL_ID  = 1,
    LOGICAL_NOT = 2,
    LOGICAL_AND = 3,
    LOGICAL_OR  = 4
    };

    enum BooleanOp {
    BOOL_EQ       = 1,
    BOOL_NE       = 2,
    BOOL_LT       = 3,
    BOOL_LE       = 4,
    BOOL_GT       = 5,
    BOOL_GE       = 6,
    BOOL_RE_MATCH = 7,
    BOOL_EXISTS   = 8,
    BOOL_TRUE     = 9,
    BOOL_FALSE    = 10
    };

    class Expression {
    public:
        Expression(const qpid::types::Variant::List& expr);
        bool evaluate(const qpid::types::Variant::Map& data) const;
    private:
        LogicalOp logicalOp;
        BooleanOp boolOp;
        int operandCount;
        qpid::types::Variant operands[2];
        bool quoted[2];
        std::list<boost::shared_ptr<Expression> > expressionList;

        bool boolEval(const qpid::types::Variant::Map& data) const;
        bool lessThan(const qpid::types::Variant& left, const qpid::types::Variant& right) const;
        bool lessEqual(const qpid::types::Variant& left, const qpid::types::Variant& right) const;
        bool greaterThan(const qpid::types::Variant& left, const qpid::types::Variant& right) const;
        bool greaterEqual(const qpid::types::Variant& left, const qpid::types::Variant& right) const;
    };

}

#endif

