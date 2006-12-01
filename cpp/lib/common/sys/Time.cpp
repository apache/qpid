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

#include "Time.h"

namespace qpid {
namespace sys {

// APR ================================================================
#if USE_APR

Time now() { return apr_time_now() * TIME_USEC; }

// POSIX================================================================
#else 

Time now() {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return toTime(ts);
}

struct timespec toTimespec(const Time& t) {
    struct timespec ts;
    toTimespec(ts, t);
    return ts;
}

struct timespec& toTimespec(struct timespec& ts, const Time& t) {
    ts.tv_sec  = t / TIME_SEC;
    ts.tv_nsec = t % TIME_SEC;
    return  ts;
}

Time toTime(const struct timespec& ts) {
    return ts.tv_sec*TIME_SEC + ts.tv_nsec;
}


#endif
}}

