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
 * \file QueueContext.cpp
 */

#include "QueueAsyncContext.h"
#include "SimplePersistableMessage.h"

#include <cassert>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

QueueAsyncContext::QueueAsyncContext(boost::shared_ptr<SimplePersistableQueue> q,
                                     qpid::broker::TxnHandle& th,
                                     const qpid::asyncStore::AsyncOperation::opCode op,
                                     qpid::broker::AsyncResultCallback rcb,
                                     qpid::broker::AsyncResultQueue* const arq) :
        m_q(q),
        m_th(th),
        m_op(op),
        m_rcb(rcb),
        m_arq(arq)
{
    assert(m_q.get() != 0);
}

QueueAsyncContext::QueueAsyncContext(boost::shared_ptr<SimplePersistableQueue> q,
                                     boost::intrusive_ptr<SimplePersistableMessage> msg,
                                     qpid::broker::TxnHandle& th,
                                     const qpid::asyncStore::AsyncOperation::opCode op,
                                     qpid::broker::AsyncResultCallback rcb,
                                     qpid::broker::AsyncResultQueue* const arq) :
        m_q(q),
        m_msg(msg),
        m_th(th),
        m_op(op),
        m_rcb(rcb),
        m_arq(arq)
{
    assert(m_q.get() != 0);
    assert(m_msg.get() != 0);
}

QueueAsyncContext::~QueueAsyncContext()
{}

qpid::asyncStore::AsyncOperation::opCode
QueueAsyncContext::getOpCode() const
{
    return m_op;
}

const char*
QueueAsyncContext::getOpStr() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

boost::shared_ptr<SimplePersistableQueue>
QueueAsyncContext::getQueue() const
{
    return m_q;
}

boost::intrusive_ptr<SimplePersistableMessage>
QueueAsyncContext::getMessage() const
{
    return m_msg;
}

qpid::broker::TxnHandle
QueueAsyncContext::getTxnHandle() const
{
    return m_th;
}

qpid::broker::AsyncResultQueue*
QueueAsyncContext::getAsyncResultQueue() const
{
    return m_arq;
}

qpid::broker::AsyncResultCallback
QueueAsyncContext::getAsyncResultCallback() const
{
    return m_rcb;
}

void
QueueAsyncContext::invokeCallback(const qpid::broker::AsyncResultHandle* const arh) const
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

}}} // namespace tests::storePerftools::asyncPerf
