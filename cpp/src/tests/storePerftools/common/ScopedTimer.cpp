/*
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
 */

/**
 * \file ScopedTimer.cpp
 */

#include "tests/storePerftools/common/ScopedTimer.h"

#include "tests/storePerftools/common/ScopedTimable.h"

namespace tests {
namespace storePerftools {
namespace common {

ScopedTimer::ScopedTimer(double& elapsed) :
        m_elapsed(elapsed)
{
    ::clock_gettime(CLOCK_REALTIME, &m_startTime);
}

ScopedTimer::ScopedTimer(ScopedTimable& st) :
        m_elapsed(st.getElapsedRef())
{
    ::clock_gettime(CLOCK_REALTIME, &m_startTime);
}

ScopedTimer::~ScopedTimer() {
    ::timespec stopTime;
    ::clock_gettime(CLOCK_REALTIME, &stopTime);
    m_elapsed = _s_getDoubleTime(stopTime) - _s_getDoubleTime(m_startTime);
}

// private static
double ScopedTimer::_s_getDoubleTime(const ::timespec& ts) {
    return ts.tv_sec + (double(ts.tv_nsec) / 1e9);
}


}}} // namespace tests::storePerftools::common
