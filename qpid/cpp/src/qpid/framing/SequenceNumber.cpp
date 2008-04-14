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

#include "SequenceNumber.h"

using qpid::framing::SequenceNumber;

SequenceNumber::SequenceNumber() : value(0 - 1) {}

SequenceNumber::SequenceNumber(uint32_t v) : value((int32_t) v) {}

bool SequenceNumber::operator==(const SequenceNumber& other) const
{
    return value == other.value;
}

bool SequenceNumber::operator!=(const SequenceNumber& other) const
{
    return !(value == other.value);
}


SequenceNumber& SequenceNumber::operator++()
{
    value = value + 1;
    return *this;
}

const SequenceNumber SequenceNumber::operator++(int)
{
    SequenceNumber old(value);
    value = value + 1;
    return old;
}

SequenceNumber& SequenceNumber::operator--()
{
    value = value - 1;
    return *this;
}

bool SequenceNumber::operator<(const SequenceNumber& other) const
{
    return (value - other.value) < 0;
}

bool SequenceNumber::operator>(const SequenceNumber& other) const
{
    return other < *this;
}

bool SequenceNumber::operator<=(const SequenceNumber& other) const
{
    return *this == other || *this < other; 
}

bool SequenceNumber::operator>=(const SequenceNumber& other) const
{
    return *this == other || *this > other; 
}

namespace qpid {
namespace framing {

int32_t operator-(const SequenceNumber& a, const SequenceNumber& b)
{
    int32_t result = a.value - b.value;    
    return result;
}

}}
