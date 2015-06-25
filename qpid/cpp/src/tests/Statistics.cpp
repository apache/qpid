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
#include <iomanip>

namespace qpid {
namespace tests {

using namespace std;

Statistic::~Statistic() {}

Throughput::Throughput() : messages(0), started(false) {}

void Throughput::message(const messaging::Message&) {
    ++messages;
    if (!started) {
        start = sys::now();
        started = true;
    }
}

void Throughput::header(ostream& o) const {
    o << "tp(m/s)";
}

void Throughput::report(ostream& o) const {
    double elapsed(int64_t(sys::Duration(start, sys::now()))/double(sys::TIME_SEC));
    o << fixed << setprecision(0) << messages/elapsed;
}

ThroughputAndLatency::ThroughputAndLatency() :
    total(0),
    min(numeric_limits<double>::max()),
    max(numeric_limits<double>::min()),
    samples(0)
{}

const std::string TS = "ts";

void ThroughputAndLatency::message(const messaging::Message& m) {
    Throughput::message(m);
    types::Variant::Map::const_iterator i = m.getProperties().find(TS);
    if (i != m.getProperties().end()) {
        ++samples;
        int64_t start(i->second.asInt64());
        int64_t end(sys::Duration::FromEpoch());
        double latency = double(end - start)/sys::TIME_MSEC;
        if (latency > 0) {
            total += latency;
            if (latency < min) min = latency;
            if (latency > max) max = latency;
        }
    }
}

void ThroughputAndLatency::header(ostream& o) const {
    Throughput::header(o);
    o << '\t' << "l-min" << '\t' << "l-max" << '\t' << "l-avg";
}

void ThroughputAndLatency::report(ostream& o) const {
    Throughput::report(o);
    if (samples) {
        o << fixed << setprecision(2)
          << '\t' << min << '\t'  << max << '\t' << total/samples;
    }
}

ReporterBase::ReporterBase(ostream& o, int batch, bool wantHeader)
    : batchSize(batch), batchCount(0), headerPrinted(!wantHeader), out(o)
{}

ReporterBase::~ReporterBase() {}

/** Count message in the statistics */
void ReporterBase::message(const messaging::Message& m) {
    if (!overall.get()) overall = create();
    overall->message(m);
    if (batchSize) {
        if (!batch.get()) batch = create();
        batch->message(m);
        if (++batchCount == batchSize) {
            header();
            batch->report(out);
            out << endl;
            batch = create();
            batchCount = 0;
        }
    }
}

/** Print overall report. */
void ReporterBase::report() {
    if (!overall.get()) overall = create();
    header();
    overall->report(out);
    out << endl;
}

void ReporterBase::header() {
    if (!headerPrinted) {
        if (!overall.get()) overall = create();
        overall->header(out);
        out << endl;
        headerPrinted = true;
    }
}


}} // namespace qpid::tests
