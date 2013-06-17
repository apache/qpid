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

#include <boost/shared_ptr.hpp>

namespace qpid {
namespace ha {

template<class T> struct TrivialHasher {
    size_t  operator()(T value) const  { return static_cast<size_t>(value); }
};

template<class T> struct SharedPtrHasher {
    size_t  operator()(const boost::shared_ptr<T>& ptr) const  {
        return reinterpret_cast<size_t>(ptr.get());
    }
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_HASH_H*/
