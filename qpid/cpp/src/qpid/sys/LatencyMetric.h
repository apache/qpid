#ifndef QPID_SYS_LATENCYMETRIC_H
#define QPID_SYS_LATENCYMETRIC_H

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

#ifdef QPID_LATENCY_METRIC

#include "qpid/sys/IntegerTypes.h"

namespace qpid {
namespace sys {

/** Use this base class to add a timestamp for latency to an object */
struct LatencyMetricTimestamp {
    LatencyMetricTimestamp() : latency_metric_timestamp(0) {}
    static void initialize(const LatencyMetricTimestamp&);
    static void clear(const LatencyMetricTimestamp&);
    int64_t latency_metric_timestamp;
};
    
/**
 * Record average latencies, report on destruction.
 *
 * For debugging only, use via macros below so it can be compiled out
 * of production code.
 */
class LatencyMetric {
  public:
    /** msg should be a string literal. */
    LatencyMetric(const char* msg, int64_t skip_=0);
    ~LatencyMetric();
    
    void record(const LatencyMetricTimestamp& start);

  private:
    void report();
    const char* message;
    int64_t count, total, skipped, skip;
};

}} // namespace qpid::sys

#define QPID_LATENCY_INIT(x) ::qpid::sys::LatencyMetricTimestamp::initialize(x)
#define QPID_LATENCY_CLEAR(x) ::qpid::sys::LatencyMetricTimestamp::clear(x)
#define QPID_LATENCY_RECORD(msg, x) do {                                 \
        static ::qpid::sys::LatencyMetric metric__(msg); metric__.record(x); \
    } while (false)
#define QPID_LATENCY_RECORD_SKIP(msg, x, skip) do {                          \
        static ::qpid::sys::LatencyMetric metric__(msg, skip); metric__.record(x); \
    } while (false)


#else  /* defined QPID_LATENCY_METRIC */

namespace qpid { namespace sys {
class LatencyMetricTimestamp {};
}}

#define QPID_LATENCY_INIT(x) (void)x
#define QPID_LATENCY_CLEAR(x) (void)x
#define QPID_LATENCY_RECORD(msg, x) (void)x
#define QPID_LATENCY_RECORD_SKIP(msg, x, skip) (void)x

#endif /* defined QPID_LATENCY_METRIC */

#endif  /*!QPID_SYS_LATENCYMETRIC_H*/
