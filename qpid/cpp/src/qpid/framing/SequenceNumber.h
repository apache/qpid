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
#include "qpid/CommonImportExport.h"

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
    QPID_COMMON_EXTERN SequenceNumber();
    QPID_COMMON_EXTERN SequenceNumber(uint32_t v);

    QPID_COMMON_EXTERN SequenceNumber& operator++();//prefix ++
    QPID_COMMON_EXTERN const SequenceNumber operator++(int);//postfix ++
    QPID_COMMON_EXTERN SequenceNumber& operator--();//prefix ++
    QPID_COMMON_EXTERN bool operator==(const SequenceNumber& other) const;
    QPID_COMMON_EXTERN bool operator!=(const SequenceNumber& other) const;
    QPID_COMMON_EXTERN bool operator<(const SequenceNumber& other) const;
    QPID_COMMON_EXTERN bool operator>(const SequenceNumber& other) const;
    QPID_COMMON_EXTERN bool operator<=(const SequenceNumber& other) const;
    QPID_COMMON_EXTERN bool operator>=(const SequenceNumber& other) const;
    uint32_t getValue() const { return (uint32_t) value; }
    operator uint32_t() const { return (uint32_t) value; }

    QPID_COMMON_EXTERN friend int32_t operator-(const SequenceNumber& a, const SequenceNumber& b);

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

QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& o, const SequenceNumber& n);

}} // namespace qpid::framing


#endif
