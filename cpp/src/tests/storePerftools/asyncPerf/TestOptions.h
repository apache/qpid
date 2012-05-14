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
 * \file TestOptions.h
 */

#ifndef tests_storePerftools_asyncPerf_TestOptions_h_
#define tests_storePerftools_asyncPerf_TestOptions_h_

#include "tests/storePerftools/common/TestOptions.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class TestOptions : public tests::storePerftools::common::TestOptions
{
public:
    TestOptions(const std::string& name="Test Options");
    TestOptions(const uint32_t numMsgs,
                const uint32_t msgSize,
                const uint16_t numQueues,
                const uint16_t numEnqThreadsPerQueue,
                const uint16_t numDeqThreadsPerQueue,
                const uint16_t enqTxnBlockSize,
                const uint16_t deqTxnBlockSize,
                const std::string& name="Test Options");
    virtual ~TestOptions();
    void printVals(std::ostream& os) const;

    uint16_t m_enqTxnBlockSize;                     ///< Transaction block size for enqueues
    uint16_t m_deqTxnBlockSize;                     ///< Transaction block size for dequeues

protected:
    static uint16_t s_defaultEnqTxnBlkSize;         ///< Default transaction block size for enqueues
    static uint16_t s_defaultDeqTxnBlkSize;         ///< Default transaction block size for dequeues

    void doAddOptions();
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_TestOptions_h_
