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

#include "SimplePersistableMessage.h"
#include "SimplePersistableQueue.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

QueuedMessage::QueuedMessage() :
        m_queue(0)
{}

QueuedMessage::QueuedMessage(SimplePersistableQueue* q,
                             boost::intrusive_ptr<SimplePersistableMessage> msg) :
        m_queue(q),
        m_msg(msg),
        m_enqHandle(q->getStore() ? q->getStore()->createEnqueueHandle(msg->getHandle(), q->getHandle()) : qpid::broker::EnqueueHandle(0))
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

boost::intrusive_ptr<SimplePersistableMessage>
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

void
QueuedMessage::prepareEnqueue(qpid::broker::TxnHandle& th)
{
    m_queue->enqueue(th, *this);
}

void
QueuedMessage::commitEnqueue()
{
    m_queue->process(m_msg);
}

void
QueuedMessage::abortEnqueue()
{
    m_queue->enqueueAborted(m_msg);
}

}}} // namespace tests::storePerfTools
