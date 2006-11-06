#ifndef _concurrent_Time_h
#define _concurrent_Time_h

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

#include <stdint.h>

namespace qpid {
namespace sys {

/**
 * Time since the epoch.
 */
class Time
{
  public:
    static const int64_t NANOS  = 1000000000;
    static const int64_t MICROS = 1000000;
    static const int64_t MILLIS = 1000;

    static Time now();

    Time(int64_t nsecs_) : ticks(nsecs_) {}

    int64_t nsecs() const { return ticks; }
    int64_t usecs() const { return nsecs()/1000; }
    int64_t msecs() const { return usecs()/1000; }
    int64_t secs() const { return msecs()/1000; }

  private:
    int64_t ticks;
};

}}

#endif  /*!_concurrent_Time_h*/
