#ifndef TESTS_STATISTICS_H
#define TESTS_STATISTICS_H

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


#include <qpid/sys/Time.h>
#include <limits>
#include <iosfwd>
#include <memory>

namespace qpid {

namespace messaging {
class Message;
}

namespace tests {

class Statistic {
  public:
    virtual ~Statistic();
    virtual void message(const messaging::Message&) = 0;
    virtual void report(std::ostream&) const = 0;
    virtual void header(std::ostream&) const = 0;
};

class Throughput : public Statistic {
  public:
    Throughput();
    virtual void message(const messaging::Message&);
    virtual void report(std::ostream&) const;
    virtual void header(std::ostream&) const;

  protected:
    int messages;

  private:
    bool started;
    sys::AbsTime start;
};

class ThroughputAndLatency : public Throughput {
  public:
    ThroughputAndLatency();
    virtual void message(const messaging::Message&);
    virtual void report(std::ostream&) const;
    virtual void header(std::ostream&) const;

  private:
    double total, min, max;     // Milliseconds
    int samples;
};

/** Report batch and overall statistics */
class ReporterBase {
  public:
    virtual ~ReporterBase();

    /** Count message in the statistics */
    void message(const messaging::Message& m);

    /** Print overall report. */
    void report();

  protected:
    ReporterBase(std::ostream& o, int batchSize, bool wantHeader);
    virtual std::auto_ptr<Statistic> create() = 0;

  private:
    void header();
    void report(const Statistic& s);
    std::auto_ptr<Statistic> overall;
    std::auto_ptr<Statistic> batch;
    int batchSize, batchCount;
    bool stopped, headerPrinted;
    std::ostream& out;
};

template <class Stats> class Reporter : public ReporterBase {
  public:
    Reporter(std::ostream& o, int batchSize, bool wantHeader)
        : ReporterBase(o, batchSize, wantHeader) {}

    virtual std::auto_ptr<Statistic> create() {
        return std::auto_ptr<Statistic>(new Stats);
    }
};

}} // namespace qpid::tests

#endif  /*!TESTS_STATISTICS_H*/
