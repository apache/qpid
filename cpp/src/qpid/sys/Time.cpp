/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "Time.h"

namespace qpid {
namespace sys {

// APR ================================================================
#if USE_APR

Time Time::now() {
    return Time(apr_time_now(), NSEC_PER_USEC);
}

void Time::set(int64_t ticks, long nsec_per_tick) {
    time = (ticks * nsec_per_tick) / NSEC_PER_USEC;
}

int64_t Time::nsecs() const {
    return time * NSEC_PER_USEC;
}

// POSIX================================================================
#else 

Time Time::now() {
    Time t;
    clock_gettime(CLOCK_REALTIME, &t.time);
    return t;
}

void Time::set(int64_t ticks, long nsec_per_tick) {
    int64_t ns = ticks * nsec_per_tick;
    time.tv_sec  = ns / NSEC_PER_SEC;
    time.tv_nsec = ns % NSEC_PER_SEC;
}

int64_t Time::nsecs() const {
    return time.tv_sec * NSEC_PER_SEC + time.tv_nsec;
}

#endif
}}

