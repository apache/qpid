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

class Time
{
  public:
    static Time now();
 
    enum {
        NSEC_PER_SEC=1000*1000*1000,
        NSEC_PER_MSEC=1000*1000,
        NSEC_PER_USEC=1000
    };

    inline Time(int64_t ticks=0, long nsec_per_tick=1);

    void set(int64_t ticks, long nsec_per_tick=1);
        
    inline int64_t msecs() const;
    inline int64_t usecs() const;
    int64_t nsecs() const;

#ifndef USE_APR
    const struct timespec& getTimespec() const { return time; }
    struct timespec& getTimespec() { return time; }
#endif
    
  private:
#ifdef USE_APR
    apr_time_t time;
#else
    struct timespec time;
#endif
};

Time::Time(int64_t ticks, long nsec_per_tick) { set(ticks, nsec_per_tick); }

int64_t Time::msecs() const { return nsecs() / NSEC_PER_MSEC; }

int64_t Time::usecs() const { return nsecs() / NSEC_PER_USEC; }

}}

#endif  /*!_sys_Time_h*/
