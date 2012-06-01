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

#include "MockPersistableMessage.h"
#include "MockPersistableQueue.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

QueuedMessage::QueuedMessage() :
        m_queue(0)
{}

QueuedMessage::QueuedMessage(MockPersistableQueue* q,
                             boost::shared_ptr<MockPersistableMessage> msg) :
        m_queue(q),
        m_msg(msg),
        m_enqHandle(q->getStore()->createEnqueueHandle(msg->getHandle(), q->getHandle()))
{}

QueuedMessage::QueuedMessage(const QueuedMessage& qm) :
        m_queue(qm.m_queue),
        m_msg(qm.m_msg),
        m_enqHandle(qm.m_enqHandle)
{}

QueuedMessage::~QueuedMessage()
{}

QueuedMessage&
QueuedMessage::operator=(const QueuedMessage& rhs)
{
    m_queue = rhs.m_queue;
    m_msg = rhs.m_msg;
    m_enqHandle = rhs.m_enqHandle;
    return *this;
}

boost::shared_ptr<MockPersistableMessage>
QueuedMessage::payload() const
{
    return m_msg;
}

const qpid::broker::EnqueueHandle&
QueuedMessage::enqHandle() const
{
    return m_enqHandle;
}

qpid::broker::EnqueueHandle&
QueuedMessage::enqHandle()
{
    return m_enqHandle;
}

/*
QueuedMessage::QueuedMessage(boost::shared_ptr<MockPersistableMessage> msg,
                             qpid::broker::EnqueueHandle& enqHandle,
                             boost::shared_ptr<MockTransactionContext> txn) :
        m_msg(msg),
        m_enqHandle(enqHandle),
        m_txn(txn)
{
    if (txn.get()) {
        txn->addEnqueuedMsg(this);
    }
}

QueuedMessage::~QueuedMessage()
{}

boost::shared_ptr<MockPersistableMessage>
QueuedMessage::getMessage() const
{
    return m_msg;
}

qpid::broker::EnqueueHandle
QueuedMessage::getEnqueueHandle() const
{
    return m_enqHandle;
}

boost::shared_ptr<MockTransactionContext>
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
*/

}}} // namespace tests::storePerfTools
