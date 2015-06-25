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

#include "Condition.h"

namespace qpid {
namespace sys {

namespace {

struct ClockMonotonicAttr {
    ::pthread_condattr_t attr;

    ClockMonotonicAttr() {
        QPID_POSIX_ASSERT_THROW_IF(pthread_condattr_init(&attr));
        QPID_POSIX_ASSERT_THROW_IF(pthread_condattr_setclock(&attr, CLOCK_MONOTONIC));
    }
};

}

Condition::Condition() {
    static ClockMonotonicAttr attr;
    QPID_POSIX_ASSERT_THROW_IF(pthread_cond_init(&condition, &attr.attr));
}

}}
