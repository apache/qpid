#ifndef QPID_SYS_LATENCYTRACKER_H
#define QPID_SYS_LATENCYTRACKER_H

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
#include <string>
#include <limits>
#include <map>

namespace qpid {
namespace sys {

/**@file Tools for measuring latency. NOT SUITABLE FOR PROUDCTION BUILDS.
 * Uses should be compiled only if QPID_LATENCY_TRACKER is defined.
 * See the convenience macros at the end of this file.
 */

/** Used by LatencyCounter and LatencyTracker below */
class LatencyStatistic {
  public:
    LatencyStatistic(std::string name_) : name(name_), count(0), total(0), min(std::numeric_limits<int64_t>::max()), max(0) {}
    ~LatencyStatistic() { print(); }

    void record(Duration d) {
        total += d;
        ++count;
        if (d > max) max=d;
        if (d < min) min=d;
    }
    
    void print() {
        if (count) {
            double meanMsec =  (double(total)/count)/TIME_MSEC;
            printf("\n==== Latency metric %s: samples=%lu mean=%fms (%f-%f)\n", name.c_str(), count, meanMsec, double(min)/TIME_MSEC, double(max)/TIME_MSEC);
        }
        else 
            printf("\n==== Latency metric %s: no samples.\n", name.c_str());
    }

  private:
    std::string name;
    unsigned long count;
    int64_t total, min, max;
};
    
/** Measure delay between seeing the same value at start and finish. */
template <class T> class LatencyTracker {
  public:
    LatencyTracker(std::string name) : measuring(false), stat(name) {}

    void start(T value) {
        sys::Mutex::ScopedLock l(lock);
        if (!measuring) {
            measureAt = value;
            measuring = true;
            startTime = AbsTime::now();
        }
    }
    
    void finish(T value) {
        sys::Mutex::ScopedLock l(lock);
        if(measuring && measureAt == value) {
            stat.record(Duration(startTime, AbsTime::now()));
            measuring = false;
        }
    }

  private:
    sys::Mutex lock;
    bool measuring;
    T measureAt;
    AbsTime startTime;
    LatencyStatistic stat;
};


/** Measures delay between the nth call to start and the nth call to finish.
 * E.g. to measure latency between sending & receiving an ordered stream of messages.
 */
class LatencyCounter {
  public:
    LatencyCounter(std::string name) : measuring(false), startCount(0), finishCount(0), stat(name) {}

    void start() {
        sys::Mutex::ScopedLock l(lock);
        if (!measuring) {
            measureAt = startCount;
            measuring = true;
            startTime = AbsTime::now();
        }
        ++startCount;
    }

    void finish() {
        sys::Mutex::ScopedLock l(lock);
        if (measuring && measureAt == finishCount) {
            stat.record(Duration(startTime, AbsTime::now()));
            measuring = false;
        }
        ++finishCount;
    }

  private:
    sys::Mutex lock;
    bool measuring;
    uint64_t startCount, finishCount, measureAt;
    AbsTime startTime;
    LatencyStatistic stat;
};

/** Measures time spent in a scope. */
class LatencyScope {
  public:
    LatencyScope(LatencyStatistic& s) : stat(s), startTime(AbsTime::now()) {}

    ~LatencyScope() { 
        sys::Mutex::ScopedLock l(lock);
        stat.record(Duration(startTime, AbsTime::now()));
    }

  private:
    sys::Mutex lock;
    LatencyStatistic& stat;
    AbsTime startTime;
};

    
/** Macros to wrap latency tracking so disabled unless QPID_LATENCY_TRACKER is defined */

#if defined(QPID_LATENCY_TRACKER)
#define LATENCY_TRACK(X) X
#else
#define LATENCY_TRACK(X)
#endif
}} // namespace qpid::sys

#endif  /*!QPID_SYS_LATENCYTRACKER_H*/
