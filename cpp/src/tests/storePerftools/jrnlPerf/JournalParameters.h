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
 * \file JournalParameters.h
 */

#ifndef tests_storePerftools_jrnlPerf_JournalParameters_h_
#define tests_storePerftools_jrnlPerf_JournalParameters_h_

#include "tests/storePerftools/common/Parameters.h"

#ifdef JOURNAL2
#   include "qpid/asyncStore/jrnl2/JournalParameters.h"
#endif

#include <stdint.h> // uint16_6, uint32_t

namespace tests {
namespace storePerftools {
namespace jrnlPerf {

/**
 * \brief Stuct for aggregating the common journal parameters
 *
 * This struct is used to aggregate and keep together all the common journal parameters. These affect the journal
 * geometry and buffers. The test parameters are aggregated in class JrnlPerfTestParameters.
 */
class JournalParameters :
#ifdef JOURNAL2
    public qpid::asyncStore::jrnl2::JournalParameters,
#endif
    public tests::storePerftools::common::Parameters
{
public:
#ifndef JOURNAL2
    // static default store params
    static std::string s_defaultJrnlDir;            ///< Default journal directory
    static std::string s_defaultJrnlBaseFileName;   ///< Default journal base file name
    static uint16_t s_defaultNumJrnlFiles;          ///< Default number of journal data files
    static uint32_t s_defaultJrnlFileSize_sblks;    ///< Default journal data file size in softblocks
    static uint16_t s_defaultWriteBuffNumPgs;       ///< Default number of write buffer pages
    static uint32_t s_defaultWriteBuffPgSize_sblks; ///< Default size of each write buffer page in softblocks

    std::string m_jrnlDir;                          ///< Journal directory
    std::string m_jrnlBaseFileName;                 ///< Journal base file name
    uint16_t m_numJrnlFiles;                        ///< Number of journal data files
    uint32_t m_jrnlFileSize_sblks;                  ///< Journal data file size in softblocks
    uint16_t m_writeBuffNumPgs;                     ///< Number of write buffer pages
    uint32_t m_writeBuffPgSize_sblks;               ///< Size of each write buffer page in softblocks
#endif

    /**
     * \brief Default constructor
     *
     * Default constructor. Uses the default values for all parameters.
     */
    JournalParameters();

    /**
     * \brief Constructor
     *
     * Convenience constructor.
     *
     * \param jrnlDir Journal directory
     * \param jrnlBaseFileName Journal base file name
     * \param numJrnlFiles Number of journal data files
     * \param jrnlFileSize_sblks Journal data file size in softblocks
     * \param writeBuffNumPgs Number of write buffer pages
     * \param writeBuffPgSize_sblks Size of each write buffer page in softblocks
     */
    JournalParameters(const std::string& jrnlDir,
                      const std::string& jrnlBaseFileName,
                      const uint16_t numJrnlFiles,
                      const uint32_t jrnlFileSize_sblks,
                      const uint16_t writeBuffNumPgs,
                      const uint32_t writeBuffPgSize_sblks);

    /**
     * \brief Copy constructor
     *
     * \param jp Reference to JrnlParameters instance to be copied
     */
#ifdef JOURNAL2
    JournalParameters(const qpid::asyncStore::jrnl2::JournalParameters& jp);
#endif
    JournalParameters(const JournalParameters& jp);

    /**
     * \brief Virtual destructor
     */
    virtual ~JournalParameters();

    virtual bool parseArg(const int arg,
                          const char* optarg);

    static void printArgs(std::ostream& os);

    static std::string shortArgs();

    /***
     * \brief Stream the journal parameters to an output stream
     *
     * Convenience feature which streams a multi-line representation of all the journal parameters, one per line to
     * an output stream.
     *
     * \param os Output stream to which the class data is to be streamed
     */
    void toStream(std::ostream& os = std::cout) const;

};

}}} // namespace tests::storePerftools::jrnlPerf

#endif // tests_storePerftools_jrnlPerf_JournalParameters_h_
