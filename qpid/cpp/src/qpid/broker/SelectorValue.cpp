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

#include "qpid/broker/SelectorValue.h"

#include <ostream>
#include <boost/scoped_ptr.hpp>

using std::ostream;

namespace qpid {
namespace broker {

ostream& operator<<(ostream& os, const Value& v)
{
    switch (v.type) {
    case Value::T_UNKNOWN: os << "UNKNOWN"; break;
    case Value::T_BOOL: os << "BOOL:" << std::boolalpha << v.b; break;
    case Value::T_EXACT: os << "EXACT:" << v.i; break;
    case Value::T_INEXACT: os << "APPROX:" << v.x; break;
    case Value::T_STRING: os << "STRING:'" << *v.s << "'"; break;
    };
    return os;
}

class NumericPairBase {
public:
    virtual ~NumericPairBase() {}
    virtual Value add() = 0;
    virtual Value sub() = 0;
    virtual Value mul() = 0;
    virtual Value div() = 0;

    virtual bool eq() = 0;
    virtual bool ne() = 0;
    virtual bool ls() = 0;
    virtual bool gr() = 0;
    virtual bool le() = 0;
    virtual bool ge() = 0;
};

template <typename T>
class NumericPair : public NumericPairBase {
    const T n1;
    const T n2;

    Value add() { return n1+n2; }
    Value sub() { return n1-n2; }
    Value mul() { return n1*n2; }
    Value div() { return n1/n2; }

    bool eq() { return n1==n2; }
    bool ne() { return n1!=n2; }
    bool ls() { return n1<n2; }
    bool gr() { return n1>n2; }
    bool le() { return n1<=n2; }
    bool ge() { return n1>=n2; }

public:
    NumericPair(T x, T y) :
        n1(x),
        n2(y)
    {}
};

NumericPairBase* promoteNumeric(const Value& v1, const Value& v2)
{
    if (!numeric(v1) || !numeric(v2)) return 0;

    if (!sameType(v1,v2)) {
        switch (v1.type) {
        case Value::T_INEXACT: return new NumericPair<double>(v1.x, v2.i);
        case Value::T_EXACT: return new NumericPair<double>(v1.i, v2.x);
        default:
            assert(false);
        }
    } else {
        switch (v1.type) {
        case Value::T_INEXACT: return new NumericPair<double>(v1.x, v2.x);
        case Value::T_EXACT: return new NumericPair<int64_t>(v1.i, v2.i);
        default:
            assert(false);
        }
    }
    // Can never get here - but this stops a warning
    return 0;
}

bool operator==(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->eq();

    if (!sameType(v1,v2)) return false;
    switch (v1.type) {
    case Value::T_BOOL: return v1.b == v2.b;
    case Value::T_STRING: return *v1.s == *v2.s;
    default: // Cannot ever get here
        return false;
    }
}

bool operator!=(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->ne();

    if (!sameType(v1,v2)) return false;
    switch (v1.type) {
    case Value::T_BOOL: return v1.b != v2.b;
    case Value::T_STRING: return *v1.s != *v2.s;
    default: // Cannot ever get here
        return false;
    }
}

bool operator<(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->ls();

    return false;
}

bool operator>(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->gr();

    return false;
}

bool operator<=(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->le();

    return false;
}

bool operator>=(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->ge();

    return false;
}

Value operator+(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->add();

    return Value();
}

Value operator-(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->sub();

    return Value();
}

Value operator*(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->mul();

    return Value();
}

Value operator/(const Value& v1, const Value& v2)
{
    boost::scoped_ptr<NumericPairBase> nbp(promoteNumeric(v1, v2));
    if (nbp) return nbp->div();

    return Value();
}

Value operator-(const Value& v)
{
    switch (v.type) {
    case Value::T_EXACT:
        return -v.i;
    case Value::T_INEXACT:
        return -v.x;
    default:
        break;
    }
    return Value();
}

}}
