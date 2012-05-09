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
 * \file PerfTest.h
 */

#ifndef tests_storePerfTools_jrnlPerf_PerfTest_h_
#define tests_storePerfTools_jrnlPerf_PerfTest_h_

#include "TestResult.h"
#include "tests/storePerfTools/common/Streamable.h"

#include <vector>

namespace tests {
namespace storePerftools {
namespace common {
class TestParameters;
}
namespace jrnlPerf {

class Journal;
class JournalParameters;

/**
 * \brief Main test class; Create an instance and execute run()
 *
 * Main test class which aggregates the components of a test.
 */
class PerfTest : public tests::storePerftools::common::Streamable
{
public:
    /**
     * \brief Constructor
     *
     * \param tp Test parameters for the test
     * \param jp Journal parameters for all queues (journals) in the test
     */
    PerfTest(const tests::storePerftools::common::TestParameters& tp,
             const JournalParameters& jp);

    /**
     * \brief Virtual destructor
     */
    virtual ~PerfTest();

    /**
     * \brief Runs the test and prints out the results.
     *
     * Runs the test  as set by the test parameters and journal parameters.
     */
    void run();

    /**
     * \brief Stream the test setup and results to an output stream
     *
     * Convenience feature which streams the test setup and results to an output stream.
     *
     * \param os Output stream to which the test setup and results are to be streamed.
     */
    void toStream(std::ostream& os = std::cout) const;

protected:
    const tests::storePerftools::common::TestParameters& m_testParams; ///< Ref to a struct containing test params
    const JournalParameters& m_jrnlParams;      ///< Ref to a struct containing the journal parameters
    TestResult m_testResult;                    ///< Journal performance object
    const char* m_msgData;                      ///< Pointer to msg data, which is the same for all messages

    /**
     * \brief Creates journals and JrnlInstance classes for all journals (queues) to be tested
     *
     * Creates a new journal instance and JrnlInstance instance for each queue. The journals are initialized
     * which creates a new set of journal files on the local storage media (which is determined by path in
     * JrnlParameters._jrnlDir). This activity is not timed, and is not a part of the performance test per se.
     *
     * \param jrnlList List which will be filled with pointers to the newly prepared journals
     */
    void prepareJournals(std::vector<Journal*>& jrnlList);

    /**
     * \brief Destroy the journal instances in list jrnlList
     *
     * \param jrnlList List of pointers to journals to be destroyed
     */
    void destroyJournals(std::vector<Journal*>& jrnlList);

};

/**
 * \brief Print out the program arguments
 *
 * Print out the arguments to the performance program if requested by help or a parameter error.
 *
 * \param os Stream to which the arguments should be streamed.
 */
void printArgs(std::ostream& os = std::cout);

/**
 * \brief Process the command-line arguments
 *
 * Process the command-line arguments and populate the JrnlPerfTestParameters and JrnlParameters structs. Only the
 * arguments supplied are on the command-line are changed in these structs, the others remain unchanged. It is
 * important therefore to make sure that defaults are pre-loaded (the default behavior of the default constructors
 * for these structs).
 *
 * \param argc Number of command-line arguments.  Process directly from main().
 * \param argv Pointer to array of command-line argument pointers. Process directly from main().
 * \param tp Reference to test parameter object. Only params on the command-line are changed.
 * \param jp Reference to journal parameter object. Only params on the command-line are changed.
 */
bool readArgs(int argc,
              char** argv,
              tests::storePerftools::common::TestParameters& tp,
              JournalParameters& jp);

}}} // namespace tests::storePerftools::jrnlPerf

#endif // tests_storePerfTools_jrnlPerf_PerfTest_h_
