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

#include "LatencyMetric.h"
#include "Time.h"
#include <iostream>

namespace qpid {
namespace sys {

void LatencyMetricTimestamp::initialize(const LatencyMetricTimestamp& ts) {
    const_cast<int64_t&>(ts.latency_metric_timestamp) = Duration(now());
}

void LatencyMetricTimestamp::clear(const LatencyMetricTimestamp& ts) {
    const_cast<int64_t&>(ts.latency_metric_timestamp) = 0;
}

LatencyMetric::LatencyMetric(const char* msg, int64_t skip_) : 
    message(msg), count(0), total(0), skipped(0), skip(skip_)
{}

LatencyMetric::~LatencyMetric() { report(); }
    
void LatencyMetric::record(const LatencyMetricTimestamp& start) {
    if (!start.latency_metric_timestamp) return; // Ignore 0 timestamps.
    if (skip) {
        if (++skipped < skip) return;
        else skipped = 0;
    }
    ++count;
    int64_t now_ = Duration(now());
    total += now_ - start.latency_metric_timestamp;
    // Set start time for next leg of the journey 
    const_cast<int64_t&>(start.latency_metric_timestamp) = now_; 
}

void LatencyMetric::report() {
    using namespace std;
    if (count) {
        cout << "LATENCY: " << message << ": "
             << total / (count * TIME_USEC) << " microseconds" << endl;
    }
    else {
        cout << "LATENCY: " << message << ": no data." << endl;
    }
    count = 0;
    total = 0; 
}


}} // namespace qpid::sys

#endif 
