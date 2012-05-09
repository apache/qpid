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
 * \file TestOptions.cpp
 */

#include "TestOptions.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

// static declarations
uint16_t TestOptions::s_defaultEnqTxnBlkSize = 0;
uint16_t TestOptions::s_defaultDeqTxnBlkSize = 0;

TestOptions::TestOptions(const std::string& name) :
        tests::storePerftools::common::TestOptions(name),
        m_enqTxnBlockSize(s_defaultEnqTxnBlkSize),
        m_deqTxnBlockSize(s_defaultDeqTxnBlkSize)
{
    doAddOptions();
}

TestOptions::TestOptions(const uint32_t numMsgs,
                         const uint32_t msgSize,
                         const uint16_t numQueues,
                         const uint16_t numEnqThreadsPerQueue,
                         const uint16_t numDeqThreadsPerQueue,
                         const uint16_t enqTxnBlockSize,
                         const uint16_t deqTxnBlockSize,
                         const std::string& name) :
        tests::storePerftools::common::TestOptions(numMsgs, msgSize, numQueues, numEnqThreadsPerQueue, numDeqThreadsPerQueue, name),
        m_enqTxnBlockSize(enqTxnBlockSize),
        m_deqTxnBlockSize(deqTxnBlockSize)
{
    doAddOptions();
}

TestOptions::~TestOptions()
{}

void
TestOptions::printVals(std::ostream& os) const
{
    tests::storePerftools::common::TestOptions::printVals(os);
    os << "          Num enqueus per transaction [-t, --enq-txn-size]: " << m_enqTxnBlockSize << std::endl;
    os << "         Num dequeues per transaction [-d, --deq-txn-size]: " << m_deqTxnBlockSize << std::endl;
}

void
TestOptions::doAddOptions()
{
    addOptions()
            ("enq-txn-size,t", qpid::optValue(m_enqTxnBlockSize, "N"),
                    "Num enqueus per transaction (0 = no transactions)")
            ("deq-txn-size,d", qpid::optValue(m_deqTxnBlockSize, "N"),
                    "Num dequeues per transaction (0 = no transactions)")
    ;
}

}}} // namespace tests::storePerftools::asyncPerf
