#ifndef QPID_BROKER_SELECTORVALUE_H
#define QPID_BROKER_SELECTORVALUE_H

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

#include "qpid/sys/IntegerTypes.h"

#include <iosfwd>
#include <string>

namespace qpid {
namespace broker {

enum BoolOrNone {
    BN_FALSE = false,
    BN_TRUE = true,
    BN_UNKNOWN
};

// The user of the Value class for strings must ensure that
// the string has a lifetime longer than the string used and
// is responsible for managing its lifetime.
class Value {
public:
    union {
        bool               b;
        int64_t            i;
        double             x;
        const std::string* s;
    };
    enum {
        T_UNKNOWN,
        T_BOOL,
        T_STRING,
        T_EXACT,
        T_INEXACT
    } type;

    // Default copy contructor
    // Default assignment operator
    // Default destructor
    Value() :
        type(T_UNKNOWN)
    {}

    Value(const std::string& s0) :
        s(&s0),
        type(T_STRING)
    {}

    Value(const int64_t i0) :
        i(i0),
        type(T_EXACT)
    {}

    Value(const int32_t i0) :
        i(i0),
        type(T_EXACT)
    {}

    Value(const double x0) :
        x(x0),
        type(T_INEXACT)
    {}

    Value(bool b0) :
        b(b0),
        type(T_BOOL)
    {}

    Value(BoolOrNone bn) :
        b(bn),
        type(bn==BN_UNKNOWN ? T_UNKNOWN : T_BOOL)
    {}
};

inline bool unknown(const Value& v) {
    return v.type == Value::T_UNKNOWN;
}

inline bool numeric(const Value& v) {
    return v.type == Value::T_EXACT || v.type == Value::T_INEXACT;
}

inline bool sameType(const Value& v1, const Value& v2) {
    return v1.type == v2.type;
}

std::ostream& operator<<(std::ostream& os, const Value& v);

bool operator==(const Value&, const Value&);
bool operator!=(const Value&, const Value&);
bool operator<(const Value&, const Value&);
bool operator>(const Value&, const Value&);
bool operator<=(const Value&, const Value&);
bool operator>=(const Value&, const Value&);

Value operator+(const Value&, const Value&);
Value operator-(const Value&, const Value&);
Value operator*(const Value&, const Value&);
Value operator/(const Value&, const Value&);
Value operator-(const Value&);

}}

#endif
