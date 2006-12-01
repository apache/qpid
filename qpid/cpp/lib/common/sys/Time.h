#ifndef _sys_Time_h
#define _sys_Time_h

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

#include <stdint.h>

#ifdef USE_APR
#  include <apr_time.h>
#else
#  include <time.h>
#endif

namespace qpid {
namespace sys {

/** Time in nanoseconds */
typedef int64_t Time;

Time now();

/** Nanoseconds per second. */
const Time TIME_SEC  = 1000*1000*1000;
/** Nanoseconds per millisecond */
const Time TIME_MSEC = 1000*1000;
/** Nanoseconds per microseconds. */
const Time TIME_USEC = 1000;
/** Nanoseconds per nanosecond. */
const Time TIME_NSEC = 1;

#ifndef USE_APR
struct timespec toTimespec(const Time& t);
struct timespec& toTimespec(struct timespec& ts, const Time& t);
Time toTime(const struct timespec& ts);
#endif

}}

#endif  /*!_sys_Time_h*/
