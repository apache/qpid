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

#include "TestOptions.h"

#include "qpid/broker/SimpleDeliveryRecord.h"
#include "qpid/broker/SimpleQueue.h"
#include "qpid/broker/SimpleTxnAccept.h"
#include "qpid/broker/SimpleTxnBuffer.h"

#include <stdint.h> // uint32_t

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MessageConsumer::MessageConsumer(const TestOptions& perfTestParams,
                                 qpid::broker::AsyncStore* store,
                                 qpid::broker::AsyncResultQueue& arq,
                                 boost::shared_ptr<qpid::broker::SimpleQueue> queue) :
        m_perfTestParams(perfTestParams),
        m_store(store),
        m_resultQueue(arq),
        m_queue(queue),
        m_stopFlag(false)
{}

MessageConsumer::~MessageConsumer() {}

void
MessageConsumer::record(boost::shared_ptr<qpid::broker::SimpleDeliveryRecord> dr) {
    m_unacked.push_back(dr);
}

void
MessageConsumer::commitComplete() {}

void
MessageConsumer::stop() {
    m_stopFlag = true;
}

void*
MessageConsumer::runConsumers() {
    const bool useTxns = m_perfTestParams.m_deqTxnBlockSize > 0U;
    uint16_t opsInTxnCnt = 0U;
    qpid::broker::SimpleTxnBuffer* tb = 0;
    if (useTxns) {
        tb = new qpid::broker::SimpleTxnBuffer(m_resultQueue);
    }

    uint32_t msgsPerConsumer = m_perfTestParams.m_numEnqThreadsPerQueue * m_perfTestParams.m_numMsgs /
                               m_perfTestParams.m_numDeqThreadsPerQueue;
    uint32_t numMsgs = 0UL;
    while (numMsgs < msgsPerConsumer && !m_stopFlag) {
        if (m_queue->dispatch(*this)) {
            ++numMsgs;
            if (useTxns) {
                // --- Transactional dequeue ---
                boost::shared_ptr<qpid::broker::SimpleTxnAccept> ta(new qpid::broker::SimpleTxnAccept(m_unacked));
                m_unacked.clear();
                tb->enlist(ta);
                if (++opsInTxnCnt >= m_perfTestParams.m_deqTxnBlockSize) {
                    tb->commitLocal(m_store);
                    if (numMsgs < msgsPerConsumer) {
                        tb = new qpid::broker::SimpleTxnBuffer(m_resultQueue);
                    }
                    opsInTxnCnt = 0U;
                }
            } else {
                // --- Non-transactional dequeue ---
                for (std::deque<boost::shared_ptr<qpid::broker::SimpleDeliveryRecord> >::iterator i = m_unacked.begin(); i != m_unacked.end(); ++i) {
                    (*i)->accept();
                }
                m_unacked.clear();
            }
        } else {
            ::usleep(1000); // TODO - replace this poller with condition variable
        }
    }

    if (opsInTxnCnt && !m_stopFlag) {
        tb->commitLocal(m_store);
    }

    return reinterpret_cast<void*>(0);
}

//static
void*
MessageConsumer::startConsumers(void* ptr) {
    return reinterpret_cast<MessageConsumer*>(ptr)->runConsumers();
}

}}} // namespace tests::storePerftools::asyncPerf
