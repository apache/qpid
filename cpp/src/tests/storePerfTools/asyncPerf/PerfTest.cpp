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

#include "MockPersistableQueue.h"

#include "tests/storePerfTools/version.h"
#include "tests/storePerfTools/common/ScopedTimer.h"
#include "tests/storePerfTools/common/Thread.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

#include <iomanip>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

PerfTest::PerfTest(const TestOptions& to,
                   const qpid::asyncStore::AsyncStoreOptions& aso) :
        m_testOpts(to),
        m_storeOpts(aso),
        m_testResult(to),
        m_msgData(new char[to.m_msgSize]),
        m_poller(new qpid::sys::Poller),
        m_pollingThread(m_poller.get())
{
    std::memset((void*)m_msgData, 0, (size_t)to.m_msgSize);
}

PerfTest::~PerfTest()
{
    m_poller->shutdown();
    m_pollingThread.join();
    delete[] m_msgData;
}

AsyncStoreImplPtr
PerfTest::prepareStore()
{
    AsyncStoreImplPtr store(new qpid::asyncStore::AsyncStoreImpl(m_poller, m_storeOpts));
    store->initialize();
    return store;
}

void
PerfTest::prepareQueues(std::deque<MockPersistableQueuePtr>& jrnlList,
                        AsyncStoreImplPtr store)
{
    for (uint16_t i = 0; i < m_testOpts.m_numQueues; ++i) {
        std::ostringstream qname;
        qname << "queue_" << std::setw(4) << std::setfill('0') << i;
        MockPersistableQueuePtr mpq(new MockPersistableQueue(qname.str(), m_queueArgs, store, m_testOpts, m_msgData));
        jrnlList.push_back(mpq);
    }
}

void
PerfTest::destroyQueues(std::deque<MockPersistableQueuePtr>& jrnlList)
{
    jrnlList.clear();
}

void
PerfTest::run()
{
    typedef boost::shared_ptr<tests::storePerftools::common::Thread> ThreadPtr; // TODO - replace with qpid threads

    AsyncStoreImplPtr store = prepareStore();

    std::deque<MockPersistableQueuePtr> queueList;
    prepareQueues(queueList, store);

    std::deque<ThreadPtr> threads;
    { // --- Start of timed section ---
        tests::storePerftools::common::ScopedTimer st(m_testResult);

        for (uint16_t q = 0; q < m_testOpts.m_numQueues; q++) {
            for (uint16_t t = 0; t < m_testOpts.m_numEnqThreadsPerQueue; t++) { // TODO - replace with qpid threads
                ThreadPtr tp(new tests::storePerftools::common::Thread(queueList[q]->startEnqueues,
                                                                       reinterpret_cast<void*>(queueList[q].get())));
                threads.push_back(tp);
            }
            for (uint16_t dt = 0; dt < m_testOpts.m_numDeqThreadsPerQueue; ++dt) { // TODO - replace with qpid threads
                ThreadPtr tp(new tests::storePerftools::common::Thread(queueList[q]->startDequeues,
                                                                       reinterpret_cast<void*>(queueList[q].get())));
                threads.push_back(tp);
            }
        }
        while (threads.size()) {
            threads.front()->join();
            threads.pop_front();
        }
    } // --- End of timed section ---

    destroyQueues(queueList);
// DEBUG MEASURE - REMOVE WHEN FIXED
//::sleep(2);
}

void
PerfTest::toStream(std::ostream& os) const
{
    m_testOpts.printVals(os);
    os << std::endl;
    m_storeOpts.printVals(os);
    os << std::endl;
    os << m_testResult << std::endl;
}

}}} // namespace tests::storePerftools::asyncPerf

// -----------------------------------------------------------------

int
main(int argc, char** argv)
{
    qpid::CommonOptions co;
    qpid::asyncStore::AsyncStoreOptions aso;
    tests::storePerftools::asyncPerf::TestOptions to;
    qpid::Options opts;
    opts.add(co).add(aso).add(to);
    try {
        opts.parse(argc, argv);
        aso.validate();
        to.validate();
    }
    catch (std::exception& e) {
        std::cerr << e.what() << std::endl;
        return 1;
    }

    // Handle options that just print information then exit.
    if (co.version) {
        std::cout << tests::storePerftools::name() << " v." << tests::storePerftools::version() << std::endl;
        return 0;
    }
    if (co.help) {
        std::cout << tests::storePerftools::name() << ": asyncPerf" << std::endl;
        std::cout << "Performance test for the async store through the qpid async store interface." << std::endl;
        std::cout << "Usage: asyncPerf [options]" << std::endl;
        std::cout << opts << std::endl;
        return 0;
    }

    // Create and start test
    tests::storePerftools::asyncPerf::PerfTest apt(to, aso);
    apt.run();

    // Print test result
    std::cout << apt << std::endl;
    ::sleep(1);
    return 0;
}
