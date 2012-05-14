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
 * \file PerfTest.cpp
 */

#include "PerfTest.h"

#include "Journal.h"
#include "JournalParameters.h"

#include "tests/storePerftools/version.h"
#include "tests/storePerftools/common/ScopedTimer.h"
#include "tests/storePerftools/common/TestParameters.h"
#include "tests/storePerftools/common/Thread.h"

#ifdef JOURNAL2
#   include "qpid/asyncStore/jrnl2/AsyncJournal.h"
#   include "qpid/asyncStore/jrnl2/JournalDirectory.h"
#else
#   include "jrnl/jcntl.hpp"
#   include "jrnl/jdir.hpp"
#endif

#include <deque>
#include <getopt.h> // getopt_long(), required_argument, no_argument
#include <iomanip> // std::setw() std::setfill()
#include <sstream> // std::ostringstream
#include <stdint.h> // uint16_t, uint32_t

#ifdef ECLIPSE_CDT_ANNOYANCE // This prevents problems with Eclipse CODAN, which can't see this in getopt.h
    struct option;
    extern int getopt_long (int, char *const *, const char *, const struct option *, int *) __THROW;
#   define no_argument        0
#   define required_argument  1
#   define optional_argument  2
#endif

namespace tests {
namespace storePerftools {
namespace jrnlPerf {

PerfTest::PerfTest(const tests::storePerftools::common::TestParameters& tp,
                   const JournalParameters& jp) :
        Streamable(),
        m_testParams(tp),
        m_jrnlParams(jp),
        m_testResult(tp),
        m_msgData(new char[tp.m_msgSize])
{}

PerfTest::~PerfTest()
{
    delete[] m_msgData;
}

void
PerfTest::prepareJournals(std::vector<Journal*>& jrnlList)
{
#ifdef JOURNAL2
    if (qpid::asyncStore::jrnl2::JournalDirectory::s_exists(m_jrnlParams.m_jrnlDir)) {
        qpid::asyncStore::jrnl2::JournalDirectory::s_destroy(m_jrnlParams.m_jrnlDir);
    }
    qpid::asyncStore::jrnl2::JournalDirectory::s_create(m_jrnlParams.m_jrnlDir);
    qpid::asyncStore::jrnl2::AsyncJournal* jp;
#else
    if (mrg::journal::jdir::exists(m_jrnlParams.m_jrnlDir)) {
        mrg::journal::jdir::delete_dir(m_jrnlParams.m_jrnlDir);
    }
    mrg::journal::jdir::create_dir(m_jrnlParams.m_jrnlDir);
    mrg::journal::jcntl* jp;
#endif
    Journal* ptp;
    for (uint16_t j = 0; j < m_testParams.m_numQueues; j++) {
        std::ostringstream jname;
        jname << "jrnl_" << std::setw(4) << std::setfill('0') << j;
        std::ostringstream jdir;
        jdir << m_jrnlParams.m_jrnlDir << "/" << jname.str();
#ifdef JOURNAL2
        jp = new qpid::asyncStore::jrnl2::AsyncJournal(jname.str(), jdir.str(), m_jrnlParams.m_jrnlBaseFileName);
#else
        jp = new mrg::journal::jcntl(jname.str(), jdir.str(), m_jrnlParams.m_jrnlBaseFileName);
#endif
        ptp = new Journal(m_testParams.m_numMsgs, m_testParams.m_msgSize, m_msgData, jp);
#ifdef JOURNAL2
        jp->initialize(&m_jrnlParams, ptp);
#else
        jp->initialize(m_jrnlParams.m_numJrnlFiles, false, m_jrnlParams.m_numJrnlFiles,
                m_jrnlParams.m_jrnlFileSize_sblks, m_jrnlParams.m_writeBuffNumPgs,
                m_jrnlParams.m_writeBuffPgSize_sblks, ptp);
#endif

        jrnlList.push_back(ptp);
    }
}

void
PerfTest::destroyJournals(std::vector<Journal*>& jrnlList)
{
    while (jrnlList.size()) {
        delete jrnlList.back();
        jrnlList.pop_back();
    }
}

void
PerfTest::run()
{
    std::vector<Journal*> jrnlList;
    prepareJournals(jrnlList);

    std::deque<tests::storePerftools::common::Thread*> threads;
    tests::storePerftools::common::Thread* tp;
    { // --- Start of timed section ---
        tests::storePerftools::common::ScopedTimer st(m_testResult);

        for (uint16_t q = 0; q < m_testParams.m_numQueues; q++) {
            for (uint16_t t = 0; t < m_testParams.m_numEnqThreadsPerQueue; t++) {
                tp = new tests::storePerftools::common::Thread(jrnlList[q]->startEnqueues, reinterpret_cast<void*>(jrnlList[q]));
                threads.push_back(tp);
            }
            for (uint16_t dt = 0; dt < m_testParams.m_numDeqThreadsPerQueue; ++dt) {
                tp = new tests::storePerftools::common::Thread(jrnlList[q]->startDequeues, reinterpret_cast<void*>(jrnlList[q]));
                threads.push_back(tp);
            }
        }

        while (threads.size()) {
            threads.front()->join();
            delete threads.front();
            threads.pop_front();
        }
    } // --- End of timed section ---
    destroyJournals(jrnlList);
}

void
PerfTest::toStream(std::ostream& os) const
{
    os << m_testParams << std::endl;
    os << m_jrnlParams << std::endl;
    os << m_testResult << std::endl;
}

void
printArgs(std::ostream& os)
{
    os << " -h --help:                       This help message" << std::endl;
    os << std::endl;

    tests::storePerftools::common::TestParameters::printArgs(os);
    os << std::endl;

    JournalParameters::printArgs(os);
    os << std::endl;
}

bool
readArgs(int argc,
         char** argv,
         tests::storePerftools::common::TestParameters& tp,
         JournalParameters& jp)
{
    /// \todo TODO: At some point, find an easy way to aggregate these from JrnlPerfTestParameters and JrnlParameters themselves.
    static struct option long_options[] = {
        {"help", no_argument, 0, 'h'},
        {"version", no_argument, 0, 'v'},

        // Test params
        {"num_msgs", required_argument, 0, 'm'},
        {"msg_size", required_argument, 0, 'S'},
        {"num_queues", required_argument, 0, 'q'},
        {"num_enq_threads_per_queue", required_argument, 0, 'e'},
        {"num_deq_threads_per_queue", required_argument, 0, 'd'},

        // Journal params
        {"jrnl_dir", required_argument, 0, 'j'},
        {"jrnl_base_filename", required_argument, 0, 'b'},
        {"num_jfiles", required_argument, 0, 'f'},
        {"jfsize_sblks", required_argument, 0, 's'},
        {"wcache_num_pages", required_argument, 0, 'p'},
        {"wcache_pgsize_sblks", required_argument, 0, 'c'},

        {0, 0, 0, 0}
    };

    bool err = false;
    bool ver = false;
    int c = 0;
    while (true) {
        int option_index = 0;
        std::ostringstream oss;
        oss << "hv" << tests::storePerftools::common::TestParameters::shortArgs() << JournalParameters::shortArgs();
        c = getopt_long(argc, argv, oss.str().c_str(), long_options, &option_index);
        if (c == -1) break;
        if (c == 'v') {
            std::cout << tests::storePerftools::name() << " v." << tests::storePerftools::version() << std::endl;
            ver = true;
            break;
        }
        err = !(tp.parseArg(c, optarg) || jp.parseArg(c, optarg));
    }
    if (err) {
        std::cout << std::endl;
        printArgs();
    }
    return err || ver;
}

}}} // namespace tests::storePerftools::jrnlPerf

// -----------------------------------------------------------------

int
main(int argc, char** argv)
{
    tests::storePerftools::common::TestParameters tp;
    tests::storePerftools::jrnlPerf::JournalParameters jp;
    if (tests::storePerftools::jrnlPerf::readArgs(argc, argv, tp, jp)) return 1;
    tests::storePerftools::jrnlPerf::PerfTest jpt(tp, jp);
    jpt.run();
    std::cout << jpt << std::endl;
    return 0;
}
