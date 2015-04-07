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

#include "qpid/sys/posix/PrivatePosix.h"

#include "qpid/sys/Time.h"
#include <ostream>
#include <istream>
#include <sstream>
#include <time.h>
#include <stdio.h>
#include <sys/time.h>
#include <unistd.h>
#include <iomanip>
#include <cctype>

#ifdef __MACH__
#include <mach/clock.h>
#include <mach/mach.h>
#endif

namespace {
int64_t max_abstime() { return std::numeric_limits<int64_t>::max(); }
}

namespace qpid {
namespace sys {

#ifdef __MACH__
    inline Duration toTime(const mach_timespec_t& ts) {
        return ts.tv_sec*TIME_SEC + ts.tv_nsec;
    }
#else
    inline Duration toTime(const struct timespec& ts) {
        return ts.tv_sec*TIME_SEC + ts.tv_nsec;
    }
#endif

AbsTime::AbsTime(const AbsTime& t, const Duration& d) :
    timepoint(d == Duration::max() ? max_abstime() : t.timepoint+d.nanosecs)
{}

AbsTime AbsTime::Zero() {
    AbsTime epoch; epoch.timepoint = 0;
    return epoch;
}

AbsTime AbsTime::FarFuture() {
    AbsTime ff; ff.timepoint = max_abstime(); return ff;
}

AbsTime AbsTime::now() {
    AbsTime time_now;
#ifdef __MACH__
    // SYSTEM_CLOCK is like CLOCK_MONOTONIC
    clock_serv_t cs;
    mach_timespec_t ts;
    host_get_clock_service(mach_host_self(), SYSTEM_CLOCK, &cs);
    clock_get_time(cs, &ts);
    mach_port_deallocate(mach_task_self(), cs);
#else
    struct timespec ts;
    ::clock_gettime(CLOCK_MONOTONIC, &ts);
#endif
    time_now.timepoint = toTime(ts).nanosecs;
    return time_now;
}

AbsTime AbsTime::epoch() {
    return AbsTime(now(), -Duration::FromEpoch());
}

Duration Duration::FromEpoch() {
#ifdef __MACH__
    // CALENDAR_CLOCK is like CLOCK_REALTIME
    clock_serv_t cs;
    mach_timespec_t ts;
    host_get_clock_service(mach_host_self(), CALENDAR_CLOCK, &cs);
    clock_get_time(cs, &ts);
    mach_port_deallocate(mach_task_self(), cs);
#else
    struct timespec ts;
    ::clock_gettime(CLOCK_REALTIME, &ts);
#endif
    return toTime(ts).nanosecs;
}

Duration::Duration(const AbsTime& start, const AbsTime& finish) :
    nanosecs(finish.timepoint - start.timepoint)
{}

namespace {
/** type conversion helper: an infinite timeout for time_t sized types **/
const time_t TIME_T_MAX = std::numeric_limits<time_t>::max();
}

struct timespec& toTimespec(struct timespec& ts, const AbsTime& a) {
    Duration t(ZERO, a);
    Duration secs = t / TIME_SEC;
    ts.tv_sec = (secs > TIME_T_MAX) ? TIME_T_MAX : static_cast<time_t>(secs);
    ts.tv_nsec = static_cast<long>(t % TIME_SEC);
    return ts;
}

std::ostream& operator<<(std::ostream& o, const Duration& d) {
    if (d >= TIME_SEC) return o << (double(d)/TIME_SEC) << "s";
    if (d >= TIME_MSEC) return o << (double(d)/TIME_MSEC) << "ms";
    if (d >= TIME_USEC) return o << (double(d)/TIME_USEC) << "us";
    return o << int64_t(d) << "ns";
}

std::istream& operator>>(std::istream& i, Duration& d) {
    // Don't throw, let the istream throw if it's configured to do so.
    double number;
    i >> number;
    if (i.fail()) return i;

    if (i.eof() || std::isspace(i.peek())) // No suffix
        d = int64_t(number*TIME_SEC);
    else {
        std::stringbuf suffix;
        i >> &suffix;
        if (i.fail()) return i;
	std::string suffix_str = suffix.str();
        if (suffix_str.compare("s") == 0) d = int64_t(number*TIME_SEC);
        else if (suffix_str.compare("ms") == 0) d = int64_t(number*TIME_MSEC);
        else if (suffix_str.compare("us") == 0) d = int64_t(number*TIME_USEC);
        else if (suffix_str.compare("ns") == 0) d = int64_t(number*TIME_NSEC);
        else i.setstate(std::ios::failbit);
    }
    return i;
}

namespace {
inline std::ostream& outputFormattedTime(std::ostream& o, const ::time_t* time) {
    ::tm timeinfo;
    char time_string[100];
    ::strftime(time_string, 100,
               "%Y-%m-%d %H:%M:%S",
               localtime_r(time, &timeinfo));
    return o << time_string;
}
}

std::ostream& operator<<(std::ostream& o, const AbsTime& t) {
    ::time_t rawtime(t.timepoint/TIME_SEC);
    return outputFormattedTime(o, &rawtime);
}

void outputFormattedNow(std::ostream& o) {
    ::time_t rawtime;
    ::time(&rawtime);
    outputFormattedTime(o, &rawtime);
    o << " ";
}

void outputHiresNow(std::ostream& o) {
#ifdef __MACH__
    clock_serv_t cs;
    mach_timespec_t time;
    host_get_clock_service(mach_host_self(), CALENDAR_CLOCK, &cs);
    clock_get_time(cs, &time);
    mach_port_deallocate(mach_task_self(), cs);
#else
    ::timespec time;
    ::clock_gettime(CLOCK_REALTIME, &time);
#endif
    ::time_t seconds = time.tv_sec;
    outputFormattedTime(o, &seconds);
    o << "." << std::setw(9) << std::setfill('0') << time.tv_nsec << " ";
}

void sleep(int secs) {
    ::sleep(secs);
}

void usleep(uint64_t usecs) {
    ::usleep(usecs);
}

}}
