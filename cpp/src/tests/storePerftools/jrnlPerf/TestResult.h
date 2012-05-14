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
 * \file TestResult.h
 */

#ifndef tests_storePerftools_jrnlPerf_TestResult_h_
#define tests_storePerftools_jrnlPerf_TestResult_h_

#include "tests/storePerftools/common/TestParameters.h"
#include "tests/storePerftools/common/TestResult.h"

namespace tests {
namespace storePerftools {
namespace jrnlPerf {

class TestOptions;

/**
 * \brief Results class that accepts an elapsed time to calculate the rate of message throughput in the journal.
 *
 * This class (being subclassed from ScopedTimable) is passed to a ScopedTimer object on construction, and the
 * inherited _elapsed member will be written with the calculated elapsed time (in seconds) on destruction of the
 * ScopedTimer object. This time (initially set to 0.0) is used to calculate message and message byte throughput.
 * The message number and size information comes from the JrnlPerfTestParameters object passed to the constructor.
 *
 * Results are available through the use of toStream(), toString() or the << operators.
 *
 * Output is in the following format:
 * <pre>
 * TEST RESULTS:
 *     Msgs per thread: 10000
 *            Msg size: 2048
 *          No. queues: 2
 *   No. threads/queue: 2
 *          Time taken: 1.6626 sec
 *      Total no. msgs: 40000
 *      Msg throughput: 24.0587 kMsgs/sec
 *                      49.2723 MB/sec
 * </pre>
 */
class TestResult : public tests::storePerftools::common::TestResult
{
public:
    /**
     * \brief Constructor
     *
     * Constructor. Will start the time interval measurement.
     *
     * \param tp Test parameter details used to calculate the performance results.
     */
    TestResult(const tests::storePerftools::common::TestParameters& tp);

    /**
     * \brief Virtual destructor
     */
    virtual ~TestResult();

    /**
     * \brief Stream the performance test results to an output stream
     *
     * Convenience feature which streams a multi-line performance result an output stream.
     *
     * \param os Output stream to which the results are to be streamed
     */
    void toStream(std::ostream& os = std::cout) const;

protected:
    tests::storePerftools::common::TestParameters m_testParams; ///< Test parameters used for performance calculations

};

}}} // namespace tests::storePerftools::jrnlPerf

#endif // tests_storePerftools_jrnlPerf_TestResult_h_
