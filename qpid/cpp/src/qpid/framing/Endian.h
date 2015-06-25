#ifndef QPID_FRAMING_ENDIAN_H
#define QPID_FRAMING_ENDIAN_H

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

namespace qpid {
namespace framing {
namespace endian {

/** Decode integer from network byte order buffer to type T, buffer must be at least sizeof(T). */
template <class T> T decodeInt(const uint8_t* buffer) {
    T v = buffer[0];
    for (size_t i = 1; i < sizeof(T); ++i) {
        v <<= 8;
        v |= buffer[i];
    }
    return v;
}

/** Encode integer value to network byte order in buffer, buffer must be at least sizeof(T). */
template <class T> void encodeInt(uint8_t* buffer, T value) {
    for (size_t i = sizeof(T); i > 0; --i) {
        buffer[i-1] = value & 0XFF;
        value >>= 8;
    }
}

// Compute the int type that can hold a float type.
template <class T> struct IntBox { typedef T Type; };
template <> struct IntBox<float> { typedef uint32_t  Type; };
template <> struct IntBox<double> { typedef  uint64_t Type; };

/** Decode floating from network byte order buffer to type T, buffer must be at least sizeof(T). */
template <class T> T decodeFloat(const uint8_t* buffer) {
    typedef typename IntBox<T>::Type Box;
    union { T f; Box i; } u;
    u.i = decodeInt<Box>(buffer);
    return u.f;
}

/** Encode floating value to network byte order in buffer, buffer must be at least sizeof(T). */
template <class T> void encodeFloat(uint8_t* buffer, T value) {
    typedef typename IntBox<T>::Type Box;
    union { T f; Box i; } u;
    u.f = value;
    encodeInt(buffer, u.i);
}

}}} // namespace qpid::framing::endian

#endif  /*!QPID_FRAMING_ENDIAN_H*/






