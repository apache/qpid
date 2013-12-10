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

#include "qmf/exceptions.h"
#include "qmf/Expression.h"
#include <iostream>

using namespace std;
using namespace qmf;
using namespace qpid::types;

Expression::Expression(const Variant::List& expr)
{
    static int level(0);
    level++;
    Variant::List::const_iterator iter(expr.begin());
    string op(iter->asString());
    iter++;

    if      (op == "not") logicalOp = LOGICAL_NOT;
    else if (op == "and") logicalOp = LOGICAL_AND;
    else if (op == "or")  logicalOp = LOGICAL_OR;
    else {
        logicalOp = LOGICAL_ID;
        if      (op == "eq")       boolOp = BOOL_EQ;
        else if (op == "ne")       boolOp = BOOL_NE;
        else if (op == "lt")       boolOp = BOOL_LT;
        else if (op == "le")       boolOp = BOOL_LE;
        else if (op == "gt")       boolOp = BOOL_GT;
        else if (op == "ge")       boolOp = BOOL_GE;
        else if (op == "re_match") boolOp = BOOL_RE_MATCH;
        else if (op == "exists")   boolOp = BOOL_EXISTS;
        else if (op == "true")     boolOp = BOOL_TRUE;
        else if (op == "false")    boolOp = BOOL_FALSE;
        else
            throw QmfException("Invalid operator in predicate expression");
    }

    if (logicalOp == LOGICAL_ID) {
        switch (boolOp) {
        case BOOL_EQ:
        case BOOL_NE:
        case BOOL_LT:
        case BOOL_LE:
        case BOOL_GT:
        case BOOL_GE:
        case BOOL_RE_MATCH:
            //
            // Binary operator: get two operands.
            //
            operandCount = 2;
            break;

        case BOOL_EXISTS:
            //
            // Unary operator: get one operand.
            //
            operandCount = 1;
            break;

        case BOOL_TRUE:
        case BOOL_FALSE:
            //
            // Literal operator: no operands.
            //
            operandCount = 0;
            break;
        }

        for (int idx = 0; idx < operandCount; idx++) {
            if (iter == expr.end())
                throw QmfException("Too few operands for operation: " + op);
            if (iter->getType() == VAR_STRING) {
                quoted[idx] = false;
                operands[idx] = *iter;
            } else if (iter->getType() == VAR_LIST) {
                const Variant::List& sublist(iter->asList());
                Variant::List::const_iterator subIter(sublist.begin());
                if (subIter != sublist.end() && subIter->asString() == "quote") {
                    quoted[idx] = true;
                    subIter++;
                    if (subIter != sublist.end()) {
                        operands[idx] = *subIter;
                        subIter++;
                        if (subIter != sublist.end())
                            throw QmfException("Extra tokens at end of 'quote'");
                    }
                } else
                    throw QmfException("Expected '[quote, <token>]'");
            } else
                throw QmfException("Expected string or list as operand for: " + op);
            iter++;
        }

        if (iter != expr.end())
            throw QmfException("Too many operands for operation: " + op);

    } else {
        //
        // This is a logical expression, collect sub-expressions
        //
        while (iter != expr.end()) {
            if (iter->getType() != VAR_LIST)
                throw QmfException("Operands of " + op + " must be lists");
            expressionList.push_back(boost::shared_ptr<Expression>(new Expression(iter->asList())));
            iter++;
        }
    }
    level--;
}


bool Expression::evaluate(const Variant::Map& data) const
{
    list<boost::shared_ptr<Expression> >::const_iterator iter;

    switch (logicalOp) {
    case LOGICAL_ID:
        return boolEval(data);

    case LOGICAL_NOT:
        for (iter = expressionList.begin(); iter != expressionList.end(); iter++)
            if ((*iter)->evaluate(data))
                return false;
        return true;

    case LOGICAL_AND:
        for (iter = expressionList.begin(); iter != expressionList.end(); iter++)
            if (!(*iter)->evaluate(data))
                return false;
        return true;

    case LOGICAL_OR:
        for (iter = expressionList.begin(); iter != expressionList.end(); iter++)
            if ((*iter)->evaluate(data))
                return true;
        return false;
    }

    return false;
}


bool Expression::boolEval(const Variant::Map& data) const
{
    Variant val[2];
    bool exists[2];

    for (int idx = 0; idx < operandCount; idx++) {
        if (quoted[idx]) {
            exists[idx] = true;
            val[idx] = operands[idx];
        } else {
            Variant::Map::const_iterator mIter(data.find(operands[idx].asString()));
            if (mIter == data.end()) {
                exists[idx] = false;
            } else {
                exists[idx] = true;
                val[idx] = mIter->second;
            }
        }
    }

    switch (boolOp) {
    case BOOL_EQ:       return (exists[0] && exists[1] && (val[0].asString() == val[1].asString()));
    case BOOL_NE:       return (exists[0] && exists[1] && (val[0].asString() != val[1].asString()));
    case BOOL_LT:       return (exists[0] && exists[1] && lessThan(val[0], val[1]));
    case BOOL_LE:       return (exists[0] && exists[1] && lessEqual(val[0], val[1]));
    case BOOL_GT:       return (exists[0] && exists[1] && greaterThan(val[0], val[1]));
    case BOOL_GE:       return (exists[0] && exists[1] && greaterEqual(val[0], val[1]));
    case BOOL_RE_MATCH: return false; // TODO
    case BOOL_EXISTS:   return exists[0];
    case BOOL_TRUE:     return true;
    case BOOL_FALSE:    return false;
    }

    return false;
}

bool Expression::lessThan(const Variant& left, const Variant& right) const
{
    switch (left.getType()) {
    case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
    case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            return left.asInt64() < right.asInt64();
        case VAR_STRING:
            try {
                return left.asInt64() < right.asInt64();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_FLOAT: case VAR_DOUBLE:
        switch (right.getType()) {
        case VAR_FLOAT: case VAR_DOUBLE:
            return left.asDouble() < right.asDouble();
        case VAR_STRING:
            try {
                return left.asDouble() < right.asDouble();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_STRING:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            try {
                return left.asInt64() < right.asInt64();
            } catch (std::exception&) {}
            break;

        case VAR_FLOAT: case VAR_DOUBLE:
            try {
                return left.asDouble() < right.asDouble();
            } catch (std::exception&) {}
            break;

        case VAR_STRING:
            return left.asString() < right.asString();
        default:
            break;
        }
    default:
        break;
    }

    return false;
}


bool Expression::lessEqual(const Variant& left, const Variant& right) const
{
    switch (left.getType()) {
    case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
    case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            return left.asInt64() <= right.asInt64();
        case VAR_STRING:
            try {
                return left.asInt64() <= right.asInt64();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_FLOAT: case VAR_DOUBLE:
        switch (right.getType()) {
        case VAR_FLOAT: case VAR_DOUBLE:
            return left.asDouble() <= right.asDouble();
        case VAR_STRING:
            try {
                return left.asDouble() <= right.asDouble();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_STRING:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            try {
                return left.asInt64() <= right.asInt64();
            } catch (std::exception&) {}
            break;

        case VAR_FLOAT: case VAR_DOUBLE:
            try {
                return left.asDouble() <= right.asDouble();
            } catch (std::exception&) {}
            break;

        case VAR_STRING:
            return left.asString() <= right.asString();
        default:
            break;
        }
    default:
        break;
    }

    return false;
}


bool Expression::greaterThan(const Variant& left, const Variant& right) const
{
    switch (left.getType()) {
    case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
    case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            return left.asInt64() > right.asInt64();
        case VAR_STRING:
            try {
                return left.asInt64() > right.asInt64();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_FLOAT: case VAR_DOUBLE:
        switch (right.getType()) {
        case VAR_FLOAT: case VAR_DOUBLE:
            return left.asDouble() > right.asDouble();
        case VAR_STRING:
            try {
                return left.asDouble() > right.asDouble();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_STRING:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            try {
                return left.asInt64() > right.asInt64();
            } catch (std::exception&) {}
            break;

        case VAR_FLOAT: case VAR_DOUBLE:
            try {
                return left.asDouble() > right.asDouble();
            } catch (std::exception&) {}
            break;

        case VAR_STRING:
            return left.asString() > right.asString();
        default:
            break;
        }
    default:
        break;
    }

    return false;
}


bool Expression::greaterEqual(const Variant& left, const Variant& right) const
{
    switch (left.getType()) {
    case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
    case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            return left.asInt64() >= right.asInt64();
        case VAR_STRING:
            try {
                return left.asInt64() >= right.asInt64();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_FLOAT: case VAR_DOUBLE:
        switch (right.getType()) {
        case VAR_FLOAT: case VAR_DOUBLE:
            return left.asDouble() >= right.asDouble();
        case VAR_STRING:
            try {
                return left.asDouble() >= right.asDouble();
            } catch (std::exception&) {}
            break;
        default:
            break;
        }
        break;

    case VAR_STRING:
        switch (right.getType()) {
        case VAR_UINT8: case VAR_UINT16: case VAR_UINT32: case VAR_UINT64:
        case VAR_INT8:  case VAR_INT16:  case VAR_INT32:  case VAR_INT64:
            try {
                return left.asInt64() >= right.asInt64();
            } catch (std::exception&) {}
            break;

        case VAR_FLOAT: case VAR_DOUBLE:
            try {
                return left.asDouble() >= right.asDouble();
            } catch (std::exception&) {}
            break;

        case VAR_STRING:
            return left.asString() >= right.asString();
        default:
            break;
        }
    default:
        break;
    }

    return false;
}


