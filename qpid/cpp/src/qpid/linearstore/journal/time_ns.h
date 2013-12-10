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

#ifndef QPID_LINEARSTORE_JOURNAL_TIME_NS_H
#define QPID_LINEARSTORE_JOURNAL_TIME_NS_H

#include <cerrno>
#include <ctime>
#include <string>

namespace qpid {
namespace linearstore {
namespace journal {

struct time_ns : public timespec
{
    inline time_ns() { tv_sec = 0; tv_nsec = 0; }
    inline time_ns(const std::time_t sec, const long nsec = 0) { tv_sec = sec; tv_nsec = nsec; }
    inline time_ns(const time_ns& t) { tv_sec = t.tv_sec; tv_nsec = t.tv_nsec; }

    inline void set_zero() { tv_sec = 0; tv_nsec = 0; }
    inline bool is_zero() const { return tv_sec == 0 && tv_nsec == 0; }
    inline int now() { if(::clock_gettime(CLOCK_REALTIME, this)) return errno; return 0; }
    const std::string str(int precision = 6) const;

    inline time_ns& operator=(const time_ns& rhs)
        { tv_sec = rhs.tv_sec; tv_nsec = rhs.tv_nsec; return *this; }
    inline time_ns& operator+=(const time_ns& rhs)
    {
        tv_nsec += rhs.tv_nsec;
        if (tv_nsec >= 1000000000L) { tv_sec++; tv_nsec -= 1000000000L; }
        tv_sec += rhs.tv_sec;
        return *this;
    }
    inline time_ns& operator+=(const long ns)
    {
        tv_nsec += ns;
        if (tv_nsec >= 1000000000L) { tv_sec++; tv_nsec -= 1000000000L; }
        return *this;
    }
    inline time_ns& operator-=(const long ns)
    {
        tv_nsec -= ns;
        if (tv_nsec < 0) { tv_sec--; tv_nsec += 1000000000L; }
        return *this;
    }
    inline time_ns& operator-=(const time_ns& rhs)
    {
        tv_nsec -= rhs.tv_nsec;
        if (tv_nsec < 0) { tv_sec--; tv_nsec += 1000000000L; }
        tv_sec -= rhs.tv_sec;
        return *this;
    }
    inline const time_ns operator+(const time_ns& rhs)
        { time_ns t(*this); t += rhs; return t; }
    inline const time_ns operator-(const time_ns& rhs)
        { time_ns t(*this); t -= rhs; return t; }
    inline bool operator==(const time_ns& rhs)
       { return tv_sec == rhs.tv_sec && tv_nsec == rhs.tv_nsec; }
    inline bool operator!=(const time_ns& rhs)
       { return tv_sec != rhs.tv_sec || tv_nsec != rhs.tv_nsec; }
    inline bool operator>(const time_ns& rhs)
       { if(tv_sec == rhs.tv_sec) return tv_nsec > rhs.tv_nsec; return tv_sec > rhs.tv_sec; }
    inline bool operator>=(const time_ns& rhs)
       { if(tv_sec == rhs.tv_sec) return tv_nsec >= rhs.tv_nsec; return tv_sec >= rhs.tv_sec; }
    inline bool operator<(const time_ns& rhs)
       { if(tv_sec == rhs.tv_sec) return tv_nsec < rhs.tv_nsec; return tv_sec < rhs.tv_sec; }
    inline bool operator<=(const time_ns& rhs)
       { if(tv_sec == rhs.tv_sec) return tv_nsec <= rhs.tv_nsec; return tv_sec <= rhs.tv_sec; }
};

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_TIME_NS_H
