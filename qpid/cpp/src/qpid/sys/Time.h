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
#include <limits>

namespace qpid {
namespace sys {

class Duration;

/** Times in nanoseconds */
class AbsTime {
    static int64_t max() { return std::numeric_limits<int64_t>::max(); }
    int64_t time_ns;
    
  friend class Duration;
	
  public:
    inline AbsTime() {}
    inline AbsTime(const AbsTime& time0, const Duration& duration);
    // Default asignment operation fine
    // Default copy constructor fine
    inline void reset(const AbsTime& time0, const Duration& duration);
	 
    static AbsTime now();
    inline static AbsTime FarFuture();
    int64_t timeValue() const { return time_ns; }
    bool operator==(const AbsTime& t) const { return t.time_ns == time_ns; }
    template <class S> void serialize(S& s) { s(time_ns); }

  friend bool operator<(const AbsTime& a, const AbsTime& b);
  friend bool operator>(const AbsTime& a, const AbsTime& b);
};

class Duration {
    static int64_t max() { return std::numeric_limits<int64_t>::max(); }
    int64_t nanosecs;

  friend class AbsTime;

  public:
    inline Duration(int64_t time0);
    inline explicit Duration(const AbsTime& time0);
    inline explicit Duration(const AbsTime& start, const AbsTime& finish);
    inline operator int64_t() const;
};


AbsTime::AbsTime(const AbsTime& t, const Duration& d) :
    time_ns(d == Duration::max() ? max() : t.time_ns+d.nanosecs)
{}

void AbsTime::reset(const AbsTime& t, const Duration& d)
{
    time_ns = d == Duration::max() ? max() : t.time_ns+d.nanosecs;
}

AbsTime AbsTime::FarFuture() { AbsTime ff; ff.time_ns = max(); return ff;}

inline AbsTime now() { return AbsTime::now(); }

inline bool operator<(const AbsTime& a, const AbsTime& b) { return a.time_ns < b.time_ns; }
inline bool operator>(const AbsTime& a, const AbsTime& b) { return a.time_ns > b.time_ns; }

Duration::Duration(int64_t time0) :
    nanosecs(time0)
{}

Duration::Duration(const AbsTime& time0) :
    nanosecs(time0.time_ns)
{}

Duration::Duration(const AbsTime& start, const AbsTime& finish) :
    nanosecs(finish.time_ns - start.time_ns)
{}

Duration::operator int64_t() const
{ return nanosecs; }

/** Nanoseconds per second. */
const Duration TIME_SEC  = 1000*1000*1000;
/** Nanoseconds per millisecond */
const Duration TIME_MSEC = 1000*1000;
/** Nanoseconds per microseconds. */
const Duration TIME_USEC = 1000;
/** Nanoseconds per nanosecond. */
const Duration TIME_NSEC = 1;

/** Value to represent an infinite timeout */
const Duration TIME_INFINITE = std::numeric_limits<int64_t>::max();

/** Time greater than any other time */
const AbsTime FAR_FUTURE = AbsTime::FarFuture();

}}

#endif  /*!_sys_Time_h*/
