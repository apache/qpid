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

#include "TestOptions.h"

#include "qpid/asyncStore/PersistableMessageContext.h"
#include "qpid/broker/SimpleMessage.h"
#include "qpid/broker/SimpleQueue.h"
#include "qpid/broker/SimpleTxnBuffer.h"
#include "qpid/broker/SimpleTxnPublish.h"

#include <stdint.h> // uint32_t

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MessageProducer::MessageProducer(const TestOptions& perfTestParams,
                                 const char* msgData,
                                 qpid::broker::AsyncStore* store,
                                 qpid::broker::AsyncResultQueue& arq,
                                 boost::shared_ptr<qpid::broker::SimpleQueue> queue) :
        m_perfTestParams(perfTestParams),
        m_msgData(msgData),
        m_store(store),
        m_resultQueue(arq),
        m_queue(queue),
        m_stopFlag(false)
{}

MessageProducer::~MessageProducer() {}

void
MessageProducer::stop() {
    m_stopFlag = true;
}

void*
MessageProducer::runProducers() {
    const bool useTxns = m_perfTestParams.m_enqTxnBlockSize > 0U;
    uint16_t recsInTxnCnt = 0U;
    qpid::broker::SimpleTxnBuffer* tb = 0;
    if (useTxns) {
        tb = new qpid::broker::SimpleTxnBuffer(m_resultQueue);
    }
    for (uint32_t numMsgs=0; numMsgs<m_perfTestParams.m_numMsgs && !m_stopFlag; ++numMsgs) {
        boost::intrusive_ptr<qpid::asyncStore::PersistableMessageContext> msgCtxt(new qpid::asyncStore::PersistableMessageContext(m_store));
        boost::intrusive_ptr<qpid::broker::AsyncCompletion> ingressCompl(new qpid::broker::AsyncCompletion);
        msgCtxt->setIngressCompletion(ingressCompl);
        boost::intrusive_ptr<qpid::broker::SimpleMessage> msg(new qpid::broker::SimpleMessage(m_msgData, m_perfTestParams.m_msgSize, msgCtxt));
        if (useTxns) {
            boost::shared_ptr<qpid::broker::SimpleTxnPublish> op(new qpid::broker::SimpleTxnPublish(msg));
            op->deliverTo(m_queue);
            tb->enlist(op);
            if (++recsInTxnCnt >= m_perfTestParams.m_enqTxnBlockSize) {
                tb->commitLocal(m_store);

                // TxnBuffer instance tb carries async state that precludes it being re-used for the next
                // transaction until the current commit cycle completes. So use another instance. This
                // instance should auto-delete when the async commit cycle completes.
                if ((numMsgs + 1) < m_perfTestParams.m_numMsgs) {
                    tb = new qpid::broker::SimpleTxnBuffer(m_resultQueue);
                }
                recsInTxnCnt = 0U;
            }
        } else {
            m_queue->deliver(msg);
        }
    }
    if (recsInTxnCnt && !m_stopFlag) {
        tb->commitLocal(m_store);
    }
    return reinterpret_cast<void*>(0);
}

//static
void*
MessageProducer::startProducers(void* ptr) {
    return reinterpret_cast<MessageProducer*>(ptr)->runProducers();
}

}}} // namespace tests::storePerftools::asyncPerf
