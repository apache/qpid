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
 * \file TestParameters.h
 */

#ifndef tests_storePerftools_common_TestParameters_h_
#define tests_storePerftools_common_TestParameters_h_

#include "Parameters.h"

#include <stdint.h>  // uint16_t, uint32_t

namespace tests {
namespace storePerftools {
namespace common {

class TestOptions;

/**
 * \brief Struct for aggregating the test parameters
 *
 * This struct is used to aggregate and keep together all the test parameters. These affect the test itself, the
 * journal geometry is aggregated in class JrnlParameters.
 */
class TestParameters : public Parameters
{
public:
    static uint32_t s_defaultNumMsgs;               ///< Default number of messages to be sent
    static uint32_t s_defaultMsgSize;               ///< Default message size in bytes
    static uint16_t s_defaultNumQueues;             ///< Default number of queues to test simultaneously
    static uint16_t s_defaultEnqThreadsPerQueue;    ///< Default number of enqueue threads per queue
    static uint16_t s_defaultDeqThreadsPerQueue;    ///< Default number of dequeue threads per queue

    uint32_t m_numMsgs;                             ///< Number of messages to be sent
    uint32_t m_msgSize;                             ///< Message size in bytes
    uint16_t m_numQueues;                           ///< Number of queues to test simultaneously
    uint16_t m_numEnqThreadsPerQueue;               ///< Number of enqueue threads per queue
    uint16_t m_numDeqThreadsPerQueue;               ///< Number of dequeue threads per queue

    /**
     * \brief Defaault constructor
     *
     * Default constructor. Uses the default values for all parameters.
     */
    TestParameters();

    /**
     * \brief Constructor
     *
     * Convenience constructor.
     *
     * \param numMsgs Number of messages to be sent
     * \param msgSize Message size in bytes
     * \param numQueues Number of queues to test simultaneously
     * \param numEnqThreadsPerQueue Number of enqueue threads per queue
     * \param numDeqThreadsPerQueue Number of dequeue threads per queue
     */
    TestParameters(const uint32_t numMsgs,
                   const uint32_t msgSize,
                   const uint16_t numQueues,
                   const uint16_t numEnqThreadsPerQueue,
                   const uint16_t numDeqThreadsPerQueue);

    /**
     * \brief Copy constructor
     *
     * \param tp Reference to JrnlPerfTestParameters instance to be copied
     */
    TestParameters(const TestParameters& tp);

    /**
     * \brief Virtual destructor
     */
    virtual ~TestParameters();

    virtual bool parseArg(const int arg,
                          const char* optarg);

    static void printArgs(std::ostream& os);

    static std::string shortArgs();

    /***
     * \brief Stream the test parameters to an output stream
     *
     * Convenience feature which streams a multi-line representation of all the test parameters, one per line to an
     * output stream.
     *
     * \param os Output stream to which the class data is to be streamed
     */
    void toStream(std::ostream& os = std::cout) const;

};

}}} // namespace tests::storePerftools::common

#endif // tests_storePerftools_common_TestParameters_h_
