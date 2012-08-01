/*
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
 */

/**
 * \file TestResult.cpp
 */

#include "TestResult.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

TestResult::TestResult(const TestOptions& to) :
        tests::storePerftools::common::TestResult(),
        m_testOpts(to)
{}

TestResult::~TestResult() {}

void
TestResult::toStream(std::ostream& os) const {
    double msgsRate;
    os << "TEST RESULTS:" << std::endl;
    os << "         Msgs per thread: " << m_testOpts.m_numMsgs << std::endl;
    os << "                Msg size: " << m_testOpts.m_msgSize << std::endl;
    os << "              No. queues: " << m_testOpts.m_numQueues << std::endl;
    os << "   No. enq threads/queue: " << m_testOpts.m_numEnqThreadsPerQueue << std::endl;
    os << "   No. deq threads/queue: " << m_testOpts.m_numDeqThreadsPerQueue << std::endl;
    os << "              Time taken: " << m_elapsed << " sec" << std::endl;
    uint32_t msgsPerQueue = m_testOpts.m_numMsgs * m_testOpts.m_numEnqThreadsPerQueue;
    if (m_testOpts.m_numQueues > 1) {
        msgsRate = double(msgsPerQueue) / m_elapsed;
        os << "      No. msgs per queue: " << msgsPerQueue << std::endl;
        os << "Per queue msg throughput: " << (msgsRate / 1e3) << " kMsgs/sec" << std::endl;
        os << "                          " << (msgsRate * m_testOpts.m_msgSize / 1e6) << " MB/sec" << std::endl;
    }
    uint32_t totalMsgs = msgsPerQueue * m_testOpts.m_numQueues;
    msgsRate = double(totalMsgs) / m_elapsed;
    os << "          Total no. msgs: " << totalMsgs << std::endl;
    os << "   Broker msg throughput: " << (msgsRate / 1e3) << " kMsgs/sec" << std::endl;
    os << "                          " << (msgsRate * m_testOpts.m_msgSize / 1e6) << " MB/sec" << std::endl;
}

}}} // namespace tests::storePerftools::asyncPerf
