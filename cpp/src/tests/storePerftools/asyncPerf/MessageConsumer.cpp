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
 * \file MessageConsumer.cpp
 */

#include "MessageConsumer.h"

#include "MockPersistableQueue.h"
#include "TestOptions.h"

#include <stdint.h> // uint32_t

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MockTransactionContext;

MessageConsumer::MessageConsumer(const TestOptions& perfTestParams,
                                 boost::shared_ptr<MockPersistableQueue> queue) :
        m_perfTestParams(perfTestParams),
        m_queue(queue)
{}

MessageConsumer::~MessageConsumer()
{}

void*
MessageConsumer::runConsumers()
{
/*
    uint32_t numMsgs = 0;
    while (numMsgs < m_perfTestParams.m_numMsgs) {
        if (m_queue->dispatch()) {
            ++numMsgs;
        } else {
            ::usleep(1000); // TODO - replace this poller with condition variable
        }
    }
*/
    return 0;
}

//static
void*
MessageConsumer::startConsumers(void* ptr)
{
    return reinterpret_cast<MessageConsumer*>(ptr)->runConsumers();
}

}}} // namespace tests::storePerftools::asyncPerf
