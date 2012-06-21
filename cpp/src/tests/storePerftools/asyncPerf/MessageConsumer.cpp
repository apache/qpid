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

#include "SimpleQueue.h"
#include "TestOptions.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"
#include "qpid/broker/TxnBuffer.h"

#include <stdint.h> // uint32_t

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MessageConsumer::MessageConsumer(const TestOptions& perfTestParams,
                                 qpid::asyncStore::AsyncStoreImpl* store,
                                 qpid::broker::AsyncResultQueue& arq,
                                 boost::shared_ptr<SimpleQueue> queue) :
        m_perfTestParams(perfTestParams),
        m_store(store),
        m_resultQueue(arq),
        m_queue(queue)
{}

MessageConsumer::~MessageConsumer()
{}

void*
MessageConsumer::runConsumers()
{
    const bool useTxns = m_perfTestParams.m_deqTxnBlockSize > 0U;
    uint16_t txnCnt = 0U;
    qpid::broker::TxnBuffer* tb = 0;
    if (useTxns) {
        tb = new qpid::broker::TxnBuffer(m_resultQueue);
    }

    uint32_t numMsgs = 0;
    while (numMsgs < m_perfTestParams.m_numMsgs) {
        if (m_queue->dispatch()) {
            ++numMsgs;
        } else {
            ::usleep(1000); // TODO - replace this poller with condition variable
        }
    }

    if (txnCnt) {
        if (m_perfTestParams.m_durable) {
            tb->commitLocal(m_store);
        } else {
            tb->commit();
        }
    }

    return reinterpret_cast<void*>(0);
}

//static
void*
MessageConsumer::startConsumers(void* ptr)
{
    return reinterpret_cast<MessageConsumer*>(ptr)->runConsumers();
}

}}} // namespace tests::storePerftools::asyncPerf
