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
 * \file QueuedMessage.cpp
 */

#include "QueuedMessage.h"

#include "MockTransactionContext.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

QueuedMessage::QueuedMessage(MockPersistableMessagePtr msg,
                             qpid::broker::EnqueueHandle& enqHandle,
                             MockTransactionContextPtr txn) :
        m_msg(msg),
        m_enqHandle(enqHandle),
        m_txn(txn)
{
    if (txn) {
        txn->addEnqueuedMsg(this);
    }
}

QueuedMessage::~QueuedMessage()
{}

MockPersistableMessagePtr
QueuedMessage::getMessage() const
{
    return m_msg;
}

qpid::broker::EnqueueHandle
QueuedMessage::getEnqueueHandle() const
{
    return m_enqHandle;
}

MockTransactionContextPtr
QueuedMessage::getTransactionContext() const
{
    return m_txn;
}

bool
QueuedMessage::isTransactional() const
{
    return m_txn.get() != 0;
}

void
QueuedMessage::clearTransaction()
{
    m_txn.reset(static_cast<MockTransactionContext*>(0));
}

}}} // namespace tests::storePerfTools
