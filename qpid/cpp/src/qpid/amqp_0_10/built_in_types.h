#ifndef QPID_AMQP_0_10_BUILT_IN_TYPES_H
#define QPID_AMQP_0_10_BUILT_IN_TYPES_H
// FIXME aconway 2008-02-20: separate _fwd.h from full include.
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

typedef boost::array<uint8_t,128> Bin1024; 
typedef boost::array<uint8_t,16> Bin128;
typedef boost::array<uint8_t,2> Bin16;
typedef boost::array<uint8_t,32> Bin256;
typedef boost::array<uint8_t,4> Bin32;
typedef boost::array<uint8_t,5> Bin40; 
typedef boost::array<uint8_t,64> Bin512;
typedef boost::array<uint8_t,8> Bin64;
typedef boost::array<uint8_t,9> Bin72;

typedef double Double;
typedef float Float;
typedef framing::SequenceNumber SequenceNo;
using framing::Uuid;
typedef sys::AbsTime Datetime;

typedef Decimal<Uint8, Int32> Dec32;
typedef Decimal<Uint8, Int64> Dec64;


/** Template for length-prefixed strings/arrays. */
template <class T, class SizeType>
struct CodableString : public std::basic_string<T> {};

// Variable width types
typedef CodableString<Uint8, Uint8> Vbin8;
typedef CodableString<char, Uint8> Str8Latin;
typedef CodableString<char, Uint8> Str8;
typedef CodableString<Uint16, Uint8> Str8Utf16;

typedef CodableString<Uint8, Uint16> Vbin16;
typedef CodableString<char, Uint16> Str16Latin;
typedef CodableString<char, Uint16> Str16;
typedef CodableString<Uint16, Uint16> Str16Utf16;

typedef CodableString<Uint8, Uint32> Vbin32;

// FIXME aconway 2008-02-26: array encoding
template <class T> struct Array : public std::vector<T> {};

// FIXME aconway 2008-02-26: Unimplemented types:
struct ByteRanges {};
struct SequenceSet {};
struct Map {};
struct List {};
struct Struct32 {};

// Top level enum definitions.
enum SegmentType { CONTROL, COMMAND, HEADER, BODY };

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_BUILT_IN_TYPES_H*/
