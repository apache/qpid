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
 * \file MessageConsumer.h
 */

#ifndef tests_storePerftools_asyncPerf_MessageConsumer_h_
#define tests_storePerftools_asyncPerf_MessageConsumer_h_

#include "qpid/broker/SimpleConsumer.h"

#include "boost/shared_ptr.hpp"
#include <deque>

namespace qpid {
namespace broker {
class AsyncResultQueue;
class AsyncStore;
class SimpleDeliveryRecord;
class SimpleQueue;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class TestOptions;

class MessageConsumer: public qpid::broker::SimpleConsumer
{
public:
    MessageConsumer(const TestOptions& perfTestParams,
                    qpid::broker::AsyncStore* store,
                    qpid::broker::AsyncResultQueue& arq,
                    boost::shared_ptr<qpid::broker::SimpleQueue> queue);
    virtual ~MessageConsumer();
    void record(boost::shared_ptr<qpid::broker::SimpleDeliveryRecord> dr);
    void commitComplete();
    void stop();

    void* runConsumers();
    static void* startConsumers(void* ptr);
private:
    const TestOptions& m_perfTestParams;
    qpid::broker::AsyncStore* m_store;
    qpid::broker::AsyncResultQueue& m_resultQueue;
    boost::shared_ptr<qpid::broker::SimpleQueue> m_queue;
    std::deque<boost::shared_ptr<qpid::broker::SimpleDeliveryRecord> > m_unacked;
    bool m_stopFlag;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_MessageConsumer_h_
