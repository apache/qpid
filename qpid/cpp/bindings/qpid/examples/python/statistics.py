#!/usr/bin/env python
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

import time

TS = "ts"
TIME_SEC = 1000000000
MILLISECOND = 1000

class Statistic:
    def message(self, msg):
        return
    def report(self):
        return ""
    def header(self):
        return ""


class Throughput(Statistic):
    def __init__(self):
        self.messages = 0
        self.started = False

    def message(self, m):
        self.messages += 1
        if not self.started:
            self.start = time.time()
            self.started = True

    def header(self):
        return "tp(m/s)"

    def report(self):
        if self.started:
            elapsed = time.time() - self.start
            return str(int(self.messages/elapsed))
        else:
            return "0"


class ThroughputAndLatency(Throughput):
    def __init__(self):
        Throughput.__init__(self)
        self.total = 0.0
        self.min = float('inf')
        self.max = -float('inf')
        self.samples = 0

    def message(self, m):
        Throughput.message(self, m)
        if TS in m.properties:
            self.samples+=1
            latency = MILLISECOND * (time.time() - float(m.properties[TS])/TIME_SEC)
            if latency > 0:
                self.total += latency
                if latency < self.min:
                    self.min = latency
                if latency > self.max:
                    self.max = latency

    def header(self):
#        Throughput.header(self)
        return "%s\tl-min\tl-max\tl-avg" % Throughput.header(self)

    def report(self):
        output = Throughput.report(self)
        if (self.samples > 0):
            output += "\t%.2f\t%.2f\t%.2f" %(self.min, self.max, self.total/self.samples)
            return output


# Report batch and overall statistics
class ReporterBase:
    def __init__(self, batch, wantHeader):
        self.batchSize = batch
        self.batchCount = 0
        self.headerPrinted = not wantHeader
        self.overall = None
        self.batch = None

    def create(self):
        return

    # Count message in the statistics
    def message(self, m):
        if self.overall == None:
            self.overall = self.create()
        self.overall.message(m)
        if self.batchSize:
            if self.batch == None:
                self.batch = self.create()
            self.batch.message(m)
            self.batchCount+=1
            if self.batchCount == self.batchSize:
                self.header()
                print self.batch.report()
                self.create()
                self.batchCount = 0

    # Print overall report.
    def report(self):
        if self.overall == None:
            self.overall = self.create()
        self.header()
        print self.overall.report()

    def header(self):
        if not self.headerPrinted:
            if self.overall == None:
                self.overall = self.create()
            print self.overall.header()
            self.headerPrinted = True


class Reporter(ReporterBase):
    def __init__(self, batchSize, wantHeader, Stats):
        ReporterBase.__init__(self, batchSize, wantHeader)
        self.__stats = Stats

    def create(self):
        ClassName = self.__stats.__class__
        return ClassName()
