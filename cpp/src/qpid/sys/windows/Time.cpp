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

#include "qpid/sys/Time.h"
#include <ostream>
#include <boost/date_time/posix_time/posix_time.hpp>
#include <boost/thread/thread_time.hpp>
#include <windows.h>

using namespace boost::posix_time;

namespace {

// High-res timing support. This will display times since program start,
// more or less. Keep track of the start value and the conversion factor to
// seconds.
bool timeInitialized = false;
LARGE_INTEGER start;
double freq = 1.0;

}

namespace qpid {
namespace sys {

AbsTime::AbsTime(const AbsTime& t, const Duration& d) {
    if (d == Duration::max()) {
        timepoint = ptime(max_date_time);
    }
    else {
        time_duration td = microseconds(d.nanosecs / 1000);
        timepoint = t.timepoint + td;
    }
}

AbsTime AbsTime::FarFuture() {
    AbsTime ff;
    ptime maxd(max_date_time);
    ff.timepoint = maxd;
    return ff;
}

AbsTime AbsTime::Epoch() {
    AbsTime time_epoch;
    time_epoch.timepoint = boost::posix_time::from_time_t(0);
    return time_epoch;
}

AbsTime AbsTime::now() {
    AbsTime time_now;
    time_now.timepoint = boost::get_system_time();
    return time_now;
}

Duration::Duration(const AbsTime& start, const AbsTime& finish) {
    time_duration d = finish.timepoint - start.timepoint;
    nanosecs = d.total_nanoseconds();
}

std::ostream& operator<<(std::ostream& o, const Duration& d) {
    return o << int64_t(d) << "ns";   
}

std::ostream& operator<<(std::ostream& o, const AbsTime& t) {
    std::string time_string = to_simple_string(t.timepoint);
    return o << time_string;
}


void sleep(int secs) {
    ::Sleep(secs * 1000);
}

void usleep(uint64_t usecs) {
    DWORD msecs = usecs / 1000;
    if (msecs == 0)
        msecs = 1;
    ::Sleep(msecs);
}

void outputFormattedNow(std::ostream& o) {
    ::time_t rawtime;
    ::tm timeinfo;
    char time_string[100];

    ::time( &rawtime );
    ::localtime_s(&timeinfo, &rawtime);
    ::strftime(time_string, 100,
               "%Y-%m-%d %H:%M:%S",
               &timeinfo);
    o << time_string << " ";
}

void outputHiresNow(std::ostream& o) {
    if (!timeInitialized) {
        start.QuadPart = 0;
        LARGE_INTEGER iFreq;
        iFreq.QuadPart = 1;
        QueryPerformanceCounter(&start);
        QueryPerformanceFrequency(&iFreq);
        freq = static_cast<double>(iFreq.QuadPart);
        timeInitialized = true;
    }
    LARGE_INTEGER iNow;
    iNow.QuadPart = 0;
    QueryPerformanceCounter(&iNow);
    iNow.QuadPart -= start.QuadPart;
    if (iNow.QuadPart < 0)
        iNow.QuadPart = 0;
    double now = static_cast<double>(iNow.QuadPart);
    now /= freq;                 // now is seconds after this
    o << std::fixed << std::setprecision(8) << std::setw(16) << std::setfill('0') << now << "s ";
}
}}
