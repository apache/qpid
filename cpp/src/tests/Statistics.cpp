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
#include "Statistics.h"
#include <qpid/messaging/Message.h>
#include <ostream>

namespace qpid {
namespace tests {

Statistic::~Statistic() {}

Throughput::Throughput() : messages(0), started(false) {}

void Throughput::message(const messaging::Message&) {
    ++messages;
    if (!started) {
        start = sys::now();
        started = true;
    }
}

void Throughput::header(std::ostream& o) const {
    o << "msg/sec";
}

void Throughput::report(std::ostream& o) const {
    double elapsed(int64_t(sys::Duration(start, sys::now()))/double(sys::TIME_SEC));
    o << messages/elapsed;
}

ThroughputAndLatency::ThroughputAndLatency() :
    total(0),
    min(std::numeric_limits<double>::max()),
    max(std::numeric_limits<double>::min())
{}

void ThroughputAndLatency::message(const messaging::Message& m) {
    Throughput::message(m);
    types::Variant::Map::const_iterator i = m.getProperties().find("ts");
    if (i != m.getProperties().end()) {
        int64_t start(i->second.asInt64());
        int64_t end(sys::Duration(sys::EPOCH, sys::now()));
        double latency = double(end - start)/sys::TIME_MSEC;
        if (latency > 0) {
            total += latency;
            if (latency < min) min = latency;
            if (latency > max) max = latency;
        }
    }
}

void ThroughputAndLatency::header(std::ostream& o) const {
    Throughput::header(o);
    o << "  latency(ms)min max avg";
}

void ThroughputAndLatency::report(std::ostream& o) const {
    Throughput::report(o);
    o << "  ";
    if (messages)
        o << min << " "  << max << " " << total/messages;
    else
        o << "Can't compute latency for 0 messages.";
}

ReporterBase::ReporterBase(std::ostream& o, int batch)
    : wantBatch(batch), batchCount(0), headerPrinted(false), out(o) {}

ReporterBase::~ReporterBase() {}

/** Count message in the statistics */
void ReporterBase::message(const messaging::Message& m) {
    if (!overall.get()) overall = create();
    overall->message(m);
    if (wantBatch) {
        if (!batch.get()) batch = create();
        batch->message(m);
        if (++batchCount == wantBatch) {
            header();
            batch->report(out);
            out << std::endl;
            batch = create();
            batchCount = 0;
        }
    }
}

/** Print overall report. */
void ReporterBase::report() {
    header();
    overall->report(out);
    out << std::endl;
}

void ReporterBase::header() {
    if (!headerPrinted) {
        if (!overall.get()) overall = create();
        overall->header(out);
        out << std::endl;
        headerPrinted = true;
    }
}


}} // namespace qpid::tests
