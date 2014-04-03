#ifndef QPID_HA_HASH_H
#define QPID_HA_HASH_H

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

#include "qpid/types/Uuid.h"
#include <boost/shared_ptr.hpp>
#include <utility>

namespace qpid {
namespace ha {

// TODO aconway 2013-08-06: would like to use boost::hash or std::hash here
// but not available/working on some older compilers.
// Add overloads as needed.

inline std::size_t hashValue(bool v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(char v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(unsigned char v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(signed char v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(short v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(unsigned short v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(int v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(unsigned int v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(long v) { return static_cast<std::size_t>(v); }
inline std::size_t hashValue(unsigned long v) { return static_cast<std::size_t>(v); }

inline std::size_t hashValue(const types::Uuid& v) { return v.hash(); }

template <class T> inline std::size_t hashValue(T* v) {
    std::size_t x = static_cast<std::size_t>(reinterpret_cast<std::ptrdiff_t>(v));
    return x + (x >> 3);
}

template <class T> inline std::size_t hashValue(boost::shared_ptr<T> v) {
    return hashValue(v.get());
}

template <class T> inline void hashCombine(std::size_t& seed, const T& v) {
    seed ^= hashValue(v) + 0x9e3779b9 + (seed<<6) + (seed>>2);
}

template <class T, class U> inline size_t hashValue(const std::pair<T,U>& v) {
    std::size_t seed = 0;
    hashCombine(seed, v.first);
    hashCombine(seed, v.second);
    return seed;
}

template<class T> struct Hasher {
    size_t  operator()(const T& v) const  { return hashValue(v); }
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_HASH_H*/
