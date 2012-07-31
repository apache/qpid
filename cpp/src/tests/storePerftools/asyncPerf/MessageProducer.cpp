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
 * \file MessageProducer.cpp
 */

#include "MessageProducer.h"

#include "SimpleMessage.h"
#include "SimpleQueue.h"
#include "TestOptions.h"
#include "TxnPublish.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"
#include "qpid/broker/TxnBuffer.h"

#include <stdint.h> // uint32_t

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MessageProducer::MessageProducer(const TestOptions& perfTestParams,
                                 const char* msgData,
                                 qpid::asyncStore::AsyncStoreImpl* store,
                                 qpid::broker::AsyncResultQueue& arq,
                                 boost::shared_ptr<SimpleQueue> queue) :
        m_perfTestParams(perfTestParams),
        m_msgData(msgData),
        m_store(store),
        m_resultQueue(arq),
        m_queue(queue)
{}

MessageProducer::~MessageProducer() {}

void*
MessageProducer::runProducers() {
    const bool useTxns = m_perfTestParams.m_enqTxnBlockSize > 0U;
    uint16_t recsInTxnCnt = 0U;
    qpid::broker::TxnBuffer* tb = 0;
    if (useTxns) {
        tb = new qpid::broker::TxnBuffer(m_resultQueue);
    }
    for (uint32_t numMsgs=0; numMsgs<m_perfTestParams.m_numMsgs; ++numMsgs) {
        boost::intrusive_ptr<SimpleMessage> msg(new SimpleMessage(m_msgData, m_perfTestParams.m_msgSize, m_store));
        if (useTxns) {
            boost::shared_ptr<TxnPublish> op(new TxnPublish(msg));
            op->deliverTo(m_queue);
            tb->enlist(op);
            if (++recsInTxnCnt >= m_perfTestParams.m_enqTxnBlockSize) {
                tb->commitLocal(m_store);

                // TxnBuffer instance tb carries async state that precludes it being re-used for the next
                // transaction until the current commit cycle completes. So use another instance. This
                // instance should auto-delete when the async commit cycle completes.
                if ((numMsgs + 1) < m_perfTestParams.m_numMsgs) {
                    tb = new qpid::broker::TxnBuffer(m_resultQueue);
                }
                recsInTxnCnt = 0U;
            }
        } else {
            m_queue->deliver(msg);
        }
    }
    if (recsInTxnCnt) {
        tb->commitLocal(m_store);
    }
    return reinterpret_cast<void*>(0);
}

//static
void*
MessageProducer::startProducers(void* ptr)
{
    return reinterpret_cast<MessageProducer*>(ptr)->runProducers();
}

}}} // namespace tests::storePerftools::asyncPerf
