#ifndef QPID_AMQP_0_10_BUILT_IN_TYPES_H
#define QPID_AMQP_0_10_BUILT_IN_TYPES_H
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

#include "Decimal.h"
#include "SerializableString.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/Time.h"
#include <boost/array.hpp>
#include <boost/range/iterator_range.hpp>
#include <string>
#include <ostream>
#include <vector>
#include <stdint.h>

/**@file Mapping from built-in AMQP types to C++ types */

namespace qpid {
namespace amqp_0_10 {

// Fixed size types
struct EmptyType { template <class S> void serialize(S&) {} };
inline std::ostream& operator<<(std::ostream& o, const EmptyType&) { return o; } 

struct Void : public EmptyType {};
struct  Bit : public EmptyType {};

typedef bool Boolean;
typedef char Char;
typedef int8_t Int8;
typedef int16_t Int16;
typedef int32_t Int32;
typedef int64_t Int64;
typedef uint8_t Uint8;
typedef uint16_t Uint16;
typedef uint32_t Uint32;
typedef uint64_t Uint64;

// A struct to be distinct from the other 32 bit integrals.
struct CharUtf32 {
    uint32_t value;
    CharUtf32(uint32_t n=0) : value(n) {}
    operator uint32_t&() { return value; }
    operator const uint32_t&() const { return value; }
    template <class S> void serialize(S& s) { s(value); }
};

template <size_t N> struct Bin : public boost::array<char, N> {
    template <class S> void serialize(S& s) { s.raw(this->begin(), this->size()); }
};

template <size_t N> std::ostream& operator<<(std::ostream& o, const Bin<N>& b) {
    return o << boost::make_iterator_range(b.begin(), b.end());
}

template <> struct Bin<1> : public boost::array<char, 1> {
    Bin(char c=0) { this->front() = c; }
    operator char() { return this->front(); }
    template <class S> void serialize(S& s) { s(front()); }
};

typedef Bin<1> Bin8;
typedef Bin<128> Bin1024; 
typedef Bin<16> Bin128;
typedef Bin<2> Bin16;
typedef Bin<32> Bin256;
typedef Bin<4> Bin32;
typedef Bin<5> Bin40; 
typedef Bin<64> Bin512;
typedef Bin<8> Bin64;
typedef Bin<9> Bin72;

typedef double Double;
typedef float Float;
typedef framing::SequenceNumber SequenceNo;
using framing::Uuid;
typedef sys::AbsTime Datetime;

typedef Decimal<Uint8, Int32> Dec32;
typedef Decimal<Uint8, Int64> Dec64;

// Variable width types

typedef SerializableString<Uint8, Uint8> Vbin8;
typedef SerializableString<char, Uint8, 1> Str8Latin;
typedef SerializableString<char, Uint8> Str8;
typedef SerializableString<Uint16, Uint8> Str8Utf16;

typedef SerializableString<Uint8, Uint16> Vbin16;
typedef SerializableString<char, Uint16, 1> Str16Latin;
typedef SerializableString<char, Uint16> Str16;
typedef SerializableString<Uint16, Uint16> Str16Utf16;

typedef SerializableString<Uint8, Uint32> Vbin32;

// Forward declare class types.
class Map;

// FIXME aconway 2008-02-26: Unimplemented types:
template <class T> struct  ArrayDomain : public std::vector<T>  {
    template <class S> void serialize(S&) {}
};
struct Array { template <class S> void serialize(S&) {} };
struct ByteRanges { template <class S> void serialize(S&) {} };
struct SequenceSet  { template <class S> void serialize(S&) {} };
struct List  { template <class S> void serialize(S&) {} };
struct Struct32  { template <class S> void serialize(S&) {} };

// FIXME aconway 2008-03-10: dummy ostream operators
template <class T> std::ostream& operator<<(std::ostream& o, const ArrayDomain<T>&) { return o; }
inline std::ostream& operator<<(std::ostream& o, const Array&) { return o; }
inline std::ostream& operator<<(std::ostream& o, const ByteRanges&) { return o; }
inline std::ostream& operator<<(std::ostream& o, const SequenceSet&) { return o; }
inline std::ostream& operator<<(std::ostream& o, const List&) { return o; }
inline std::ostream& operator<<(std::ostream& o, const Struct32&) { return o; }

/** Serialization helper for enums */
template <class Enum, class Int=uint8_t>
struct SerializableEnum {
    Enum& value;
    SerializableEnum(Enum & e) : value(e) {}
    template <class S> void serialize(S& s) { s.split(*this); }
    template <class S> void encode(S& s) const { s(Int(value)); }
    template <class S> void decode(S& s) { Int i; s(i); value=Enum(i); }
};

enum SegmentType { CONTROL, COMMAND, HEADER, BODY };

inline SerializableEnum<SegmentType> serializable(SegmentType& st) {
    return SerializableEnum<SegmentType>(st);
}

}} // namespace qpid::amqp_0_10

#endif
