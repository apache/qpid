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

#ifndef tests_storePerftools_asyncPerf_PerfTest_h_
#define tests_storePerftools_asyncPerf_PerfTest_h_

#include "MockPersistableQueue.h"
#include "TestResult.h"

#include "tests/storePerftools/common/Streamable.h"

#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/Thread.h"

#include <boost/shared_ptr.hpp>
#include <deque>

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;
class AsyncStoreOptions;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class TestOptions;

class PerfTest : public tests::storePerftools::common::Streamable
{
public:
    PerfTest(const TestOptions& to,
             const qpid::asyncStore::AsyncStoreOptions& aso);
    virtual ~PerfTest();
    void run();
    void toStream(std::ostream& os = std::cout) const;

protected:
    const TestOptions& m_testOpts;
    const qpid::asyncStore::AsyncStoreOptions& m_storeOpts;
    TestResult m_testResult;
    qpid::framing::FieldTable m_queueArgs;
    const char* m_msgData;
    boost::shared_ptr<qpid::sys::Poller> m_poller;
    qpid::sys::Thread m_pollingThread;
    qpid::asyncStore::AsyncStoreImpl* m_store;
    std::deque<MockPersistableQueue::intrusive_ptr> m_queueList;

    void prepareStore();
    void prepareQueues();
    void destroyQueues();

};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_PerfTest_h_
