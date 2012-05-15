#ifndef QPID_HA_COUNTER_H
#define QPID_HA_COUNTER_H

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

#include "qpid/sys/AtomicValue.h"
#include <boost/function.hpp>

namespace qpid {
namespace ha {

/**
 * Keep a count, call a callback when it reaches 0.
 */
class Counter
{
  public:
    Counter(boost::function<void()> f) : callback(f) {}

    void operator++() { ++count; }

    void operator--() {
        size_t n = --count;
        assert(n != size_t(-1)); // No underflow
        if (n == 0) callback();
    }

    size_t get() { return count.get(); }

    Counter& operator=(size_t n) { count = n; return *this; }

  private:
    boost::function<void()> callback;
    sys::AtomicValue<size_t> count;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_COUNTER_H*/
