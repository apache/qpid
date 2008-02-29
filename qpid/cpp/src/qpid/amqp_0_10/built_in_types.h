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
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/Time.h"
#include <boost/array.hpp>
#include <stdint.h>
#include <string>
#include <ostream>
#include <vector>

/**@file Mapping from built-in AMQP types to C++ types */

namespace qpid {
namespace amqp_0_10 {

// Fixed size types
typedef void Void;

typedef bool Bit;
typedef bool Boolean;
typedef char Char;
typedef int16_t Int16;
typedef int32_t Int32;
typedef int64_t Int64;
typedef int8_t Int8;
typedef uint16_t Uint16;
typedef uint32_t CharUtf32 ;
typedef uint32_t Uint32;
typedef uint64_t Uint64;
typedef uint8_t Bin8;
typedef uint8_t Uint8;

template <size_t N> struct Bin : public boost::array<char, N> {
    template <class S> void serialize(S& s) { s.raw(this->begin(), this->size()); }
};
        
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


/** Template for length-prefixed strings/arrays. */
template <class T, class SizeType>
struct SerializableString : public std::basic_string<T> {
    using std::basic_string<T>::operator=;
    template <class S> void serialize(S& s) {
        s(SizeType(this->size())).iterate(this->begin(), this->end());
    }
};

// TODO aconway 2008-02-29: separate ostream ops
template <class T, class SizeType>
std::ostream& operator<<(std::ostream& o, const SerializableString<T,SizeType>& s) {
    const std::basic_string<T> str(s);
    return o << str.c_str();    // TODO aconway 2008-02-29: why doesn't o<<str work?
}

// Variable width types
typedef SerializableString<Uint8, Uint8> Vbin8;
typedef SerializableString<char, Uint8> Str8Latin;
typedef SerializableString<char, Uint8> Str8;
typedef SerializableString<Uint16, Uint8> Str8Utf16;

typedef SerializableString<Uint8, Uint16> Vbin16;
typedef SerializableString<char, Uint16> Str16Latin;
typedef SerializableString<char, Uint16> Str16;
typedef SerializableString<Uint16, Uint16> Str16Utf16;

typedef SerializableString<Uint8, Uint32> Vbin32;

// FIXME aconway 2008-02-26: Unimplemented types:
template <class T> struct Array : public std::vector<T> {};
struct ByteRanges {};
struct SequenceSet {};
struct Map {};
struct List {};
struct Struct32 {};

// Top level enum definitions.
enum SegmentType { CONTROL, COMMAND, HEADER, BODY };

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_BUILT_IN_TYPES_H*/
