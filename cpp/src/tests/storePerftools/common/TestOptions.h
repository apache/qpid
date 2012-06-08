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

#ifndef tests_storePerftools_common_TestOptions_h_
#define tests_storePerftools_common_TestOptions_h_

#include "qpid/Options.h"

namespace tests {
namespace storePerftools {
namespace common {

class TestOptions : public qpid::Options
{
public:
    TestOptions(const std::string& name="Test Options");
    TestOptions(const uint32_t numMsgs,
                const uint32_t msgSize,
                const uint16_t numQueues,
                const uint16_t numEnqThreadsPerQueue,
                const uint16_t numDeqThreadsPerQueue,
                const std::string& name="Test Options");
    virtual ~TestOptions();
    void printVals(std::ostream& os) const;
    void validate();

    uint32_t m_numMsgs;                             ///< Number of messages to be sent
    uint32_t m_msgSize;                             ///< Message size in bytes
    uint16_t m_numQueues;                           ///< Number of queues to test simultaneously
    uint16_t m_numEnqThreadsPerQueue;               ///< Number of enqueue threads per queue
    uint16_t m_numDeqThreadsPerQueue;               ///< Number of dequeue threads per queue

private:
    static uint32_t s_defaultNumMsgs;               ///< Default number of messages to be sent
    static uint32_t s_defaultMsgSize;               ///< Default message size in bytes
    static uint16_t s_defaultNumQueues;             ///< Default number of queues to test simultaneously
    static uint16_t s_defaultEnqThreadsPerQueue;    ///< Default number of enqueue threads per queue
    static uint16_t s_defaultDeqThreadsPerQueue;    ///< Default number of dequeue threads per queue

    void doAddOptions();

};

}}} // namespace tests::storePerftools::common

#endif // tests_storePerftools_common_TestOptions_h_
