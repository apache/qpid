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
 * \file QueueAsyncContext.cpp
 */

#include "QueueAsyncContext.h"

#include "qpid/broker/PersistableMessage.h"

#include <cassert>

namespace qpid {
namespace broker {

QueueAsyncContext::QueueAsyncContext(boost::shared_ptr<PersistableQueue> q,
                                     TxnHandle& th,
                                     AsyncResultCallback rcb,
                                     AsyncResultQueue* const arq) :
        m_q(q),
        m_th(th),
        m_rcb(rcb),
        m_arq(arq)
{
    assert(m_q.get() != 0);
}

QueueAsyncContext::QueueAsyncContext(boost::shared_ptr<PersistableQueue> q,
                                     boost::intrusive_ptr<PersistableMessage> msg,
                                     TxnHandle& th,
                                     AsyncResultCallback rcb,
                                     AsyncResultQueue* const arq) :
        m_q(q),
        m_msg(msg),
        m_th(th),
        m_rcb(rcb),
        m_arq(arq)
{
    assert(m_q.get() != 0);
    assert(m_msg.get() != 0);
}

QueueAsyncContext::~QueueAsyncContext()
{}

boost::shared_ptr<PersistableQueue>
QueueAsyncContext::getQueue() const
{
    return m_q;
}

boost::intrusive_ptr<PersistableMessage>
QueueAsyncContext::getMessage() const
{
    return m_msg;
}

TxnHandle
QueueAsyncContext::getTxnHandle() const
{
    return m_th;
}

AsyncResultQueue*
QueueAsyncContext::getAsyncResultQueue() const
{
    return m_arq;
}

AsyncResultCallback
QueueAsyncContext::getAsyncResultCallback() const
{
    return m_rcb;
}

void
QueueAsyncContext::invokeCallback(const AsyncResultHandle* const arh) const
{
    if (m_rcb) {
        m_rcb(arh);
    }
}

void
QueueAsyncContext::destroy()
{
    delete this;
}

}} // namespace qpid::broker
