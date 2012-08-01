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
 * \file SimpleQueuedMessage.cpp
 */

#include "SimpleQueuedMessage.h"

#include "SimpleMessage.h"
#include "SimpleQueue.h"

namespace qpid {
namespace broker {

SimpleQueuedMessage::SimpleQueuedMessage() :
        m_queue(0)
{}

SimpleQueuedMessage::SimpleQueuedMessage(SimpleQueue* q,
                                         boost::intrusive_ptr<SimpleMessage> msg) :
        boost::enable_shared_from_this<SimpleQueuedMessage>(),
        m_queue(q),
        m_msg(msg)
{
    if (m_queue->getStore()) {
        m_enqHandle = q->getStore()->createEnqueueHandle(msg->getHandle(), q->getHandle());
    }
}

SimpleQueuedMessage::SimpleQueuedMessage(const SimpleQueuedMessage& qm) :
        boost::enable_shared_from_this<SimpleQueuedMessage>(),
        m_queue(qm.m_queue),
        m_msg(qm.m_msg),
        m_enqHandle(qm.m_enqHandle)
{}

SimpleQueuedMessage::SimpleQueuedMessage(SimpleQueuedMessage* const qm) :
        boost::enable_shared_from_this<SimpleQueuedMessage>(),
        m_queue(qm->m_queue),
        m_msg(qm->m_msg),
        m_enqHandle(qm->m_enqHandle)
{}

SimpleQueuedMessage::~SimpleQueuedMessage() {}

SimpleQueue*
SimpleQueuedMessage::getQueue() const {
    return m_queue;
}

boost::intrusive_ptr<SimpleMessage>
SimpleQueuedMessage::payload() const {
    return m_msg;
}

const EnqueueHandle&
SimpleQueuedMessage::enqHandle() const {
    return m_enqHandle;
}

EnqueueHandle&
SimpleQueuedMessage::enqHandle() {
    return m_enqHandle;
}

void
SimpleQueuedMessage::prepareEnqueue(SimpleTxnBuffer* tb) {
    m_queue->enqueue(tb, shared_from_this());
}

void
SimpleQueuedMessage::commitEnqueue() {
    m_queue->process(m_msg);
}

void
SimpleQueuedMessage::abortEnqueue() {
    m_queue->enqueueAborted(m_msg);
}

}} // namespace qpid::broker
