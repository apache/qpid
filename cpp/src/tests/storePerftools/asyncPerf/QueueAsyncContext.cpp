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

namespace tests {
namespace storePerftools {
namespace asyncPerf {

QueueAsyncContext::QueueAsyncContext(MockPersistableQueue::intrusive_ptr q,
                           const qpid::asyncStore::AsyncOperation::opCode op) :
        qpid::broker::BrokerAsyncContext(),
        m_q(q),
        m_op(op)
{
    assert(m_q.get() != 0);
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

MockPersistableQueue::intrusive_ptr
QueueAsyncContext::getQueue() const
{
    return m_q;
}

void
QueueAsyncContext::destroy()
{
    delete this;
}

}}} // namespace tests::storePerftools::asyncPerf
