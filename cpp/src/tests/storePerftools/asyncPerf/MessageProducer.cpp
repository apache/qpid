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

#include "SimplePersistableMessage.h"
#include "SimplePersistableQueue.h"
#include "TestOptions.h"

#include <stdint.h> // uint32_t

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class SimpleTransactionContext;

MessageProducer::MessageProducer(const TestOptions& perfTestParams,
                                 const char* msgData,
                                 qpid::asyncStore::AsyncStoreImpl* store,
                                 boost::shared_ptr<SimplePersistableQueue> queue) :
        m_perfTestParams(perfTestParams),
        m_msgData(msgData),
        m_store(store),
        m_queue(queue)
{}

MessageProducer::~MessageProducer()
{}

void*
MessageProducer::runProducers()
{
    for (uint32_t numMsgs=0; numMsgs<m_perfTestParams.m_numMsgs; ++numMsgs) {
        boost::shared_ptr<SimplePersistableMessage> msg(new SimplePersistableMessage(m_msgData, m_perfTestParams.m_msgSize, m_store));
        m_queue->deliver(msg);
    }
    return 0;
}

//static
void*
MessageProducer::startProducers(void* ptr)
{
    return reinterpret_cast<MessageProducer*>(ptr)->runProducers();
}

}}} // namespace tests::storePerftools::asyncPerf
