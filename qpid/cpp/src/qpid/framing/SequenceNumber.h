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
#ifndef _framing_SequenceNumber_h
#define _framing_SequenceNumber_h

#include "amqp_types.h"
#include <iosfwd>

namespace qpid {
namespace framing {

class Buffer;

/**
 * 4-byte sequence number that 'wraps around'.
 */
class SequenceNumber
{
    int32_t value;

 public:
    SequenceNumber();
    SequenceNumber(uint32_t v);

    SequenceNumber& operator++();//prefix ++
    const SequenceNumber operator++(int);//postfix ++
    SequenceNumber& operator--();//prefix ++
    bool operator==(const SequenceNumber& other) const;
    bool operator!=(const SequenceNumber& other) const;
    bool operator<(const SequenceNumber& other) const;
    bool operator>(const SequenceNumber& other) const;
    bool operator<=(const SequenceNumber& other) const;
    bool operator>=(const SequenceNumber& other) const;
    uint32_t getValue() const { return (uint32_t) value; }
    operator uint32_t() const { return (uint32_t) value; }

    friend int32_t operator-(const SequenceNumber& a, const SequenceNumber& b);

    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);
    uint32_t encodedSize() const;   

    template <class S> void serialize(S& s) { s(value); }
};    

struct Window 
{
    SequenceNumber hwm;
    SequenceNumber lwm;
};

std::ostream& operator<<(std::ostream& o, const SequenceNumber& n);

}} // namespace qpid::framing


#endif
