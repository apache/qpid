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

namespace qpid {
namespace sys {

/**
 * Record latency between events in the lifecycle of an object.
 * For testing/debugging purposes: use the macros to declare
 * and #define QPID_LATENCY_TRACKER to enable in a build.
 */
template <class T> class LatencyTracker
{
  public:
    static void start(const char* name, const void* p) {  instance.doStart(name, p); }
    static void stage(const char* name, const void* p) { instance.doStage(name, p); }
    static void end(const char* name, const void* p) { instance.doEnd(name, p); }

  private:
    
    LatencyTracker() : object(), times(), totals(), count(), names(), index(), maxIndex() { }
    ~LatencyTracker() { print(); }

    void doStart(const char* n, const void* p) { if (!object) { name(n); object=p; times[0] = now(); index = 1; } }
    void doStage(const char* n, const void* p) { if (p == object) { name(n); times[index++] = now(); } }
    void doEnd(const char* n, const void* p) { if (p == object) { name(n); times[index++] = now(); record(); object = 0; } }

    void name(const char* n) {
        if (names[index] == 0) names[index] = n;
        assert(names[index] == n);
    }
 
    void record() {
        if (maxIndex == 0) maxIndex = index;
        assert(maxIndex == index);
        for (int i = 0; i < index-1; ++i) 
            totals[i] += Duration(times[i], times[i+1]);
        ++count;
    }
    
    void print() {
        printf("\nLatency from %s (%lu samples, %d stages) :\n", names[0], count, maxIndex-1);
        for (int i = 0; i < maxIndex-1; ++i) 
            printf("to %s:\t%luus\n", names[i+1], (totals[i]/count)/TIME_USEC);
    }

    static const int SIZE = 1024;
    const void* object;
    AbsTime times[SIZE];
    unsigned long totals[SIZE];
    unsigned long count;
    const char* names[SIZE];
    int index, maxIndex;

    static LatencyTracker instance;
};

template <class T> struct LatencyEndOnExit {
    const char* name;
    const void* ptr;
    LatencyEndOnExit(const char* n, const void* p) : name(n), ptr(p) {}
    ~LatencyEndOnExit() { LatencyTracker<T>::end(name, ptr); }
};

template <class T> LatencyTracker<T> LatencyTracker<T>::instance;

#if defined(QPID_LATENCY_TRACKER)
#define LATENCY_START(TAG, NAME, PTR) ::qpid::sys::LatencyTracker<TAG>::start(NAME, PTR)
#define LATENCY_STAGE(TAG, NAME, PTR) ::qpid::sys::LatencyTracker<TAG>::stage(NAME, PTR)
#define LATENCY_END(TAG, NAME, PTR) ::qpid::sys::LatencyTracker<TAG>::end(NAME, PTR)
#define LATENCY_END_ON_EXIT(TAG, NAME, PTR) ::qpid::sys::LatencyEndOnExit<TAG>(NAME, PTR)
#else
#define LATENCY_START(TAG, NAME, PTR) void(0)
#define LATENCY_STAGE(TAG, NAME, PTR) void(0)
#define LATENCY_END(TAG, NAME, PTR) void(0)
#define LATENCY_END_ON_EXIT(TAG, NAME, PTR) void(0) 
#endif

}} // namespace qpid::sys

#endif  /*!QPID_SYS_LATENCYTRACKER_H*/
