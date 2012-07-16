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

#include "SimpleMessage.h"
#include "SimpleQueue.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

QueuedMessage::QueuedMessage() :
        m_queue(0)
{}

QueuedMessage::QueuedMessage(SimpleQueue* q,
                             boost::intrusive_ptr<SimpleMessage> msg) :
        boost::enable_shared_from_this<QueuedMessage>(),
        m_queue(q),
        m_msg(msg)
{}

QueuedMessage::QueuedMessage(const QueuedMessage& qm) :
        boost::enable_shared_from_this<QueuedMessage>(),
        m_queue(qm.m_queue),
        m_msg(qm.m_msg)
{}

QueuedMessage::QueuedMessage(QueuedMessage* const qm) :
        boost::enable_shared_from_this<QueuedMessage>(),
        m_queue(qm->m_queue),
        m_msg(qm->m_msg)
{}

QueuedMessage::~QueuedMessage()
{}

SimpleQueue*
QueuedMessage::getQueue() const
{
    return m_queue;
}

boost::intrusive_ptr<SimpleMessage>
QueuedMessage::payload() const
{
    return m_msg;
}

void
QueuedMessage::prepareEnqueue(qpid::broker::TxnHandle& th)
{
    m_queue->enqueue(th, shared_from_this());
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
