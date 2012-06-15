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
 * \file TransactionAsyncContext.cpp
 */

#include "TxnAsyncContext.h"

#include <cassert>

namespace qpid {
namespace broker {

TxnAsyncContext::TxnAsyncContext(TxnBuffer* const tb,
                                 TxnHandle& th,
                                 const qpid::asyncStore::AsyncOperation::opCode op,
                                 qpid::broker::AsyncResultCallback rcb,
                                 qpid::broker::AsyncResultQueue* const arq):
        m_tb(tb),
        m_th(th),
        m_op(op),
        m_rcb(rcb),
        m_arq(arq)
{
    assert(m_th.isValid());
}

TxnAsyncContext::~TxnAsyncContext()
{}

TxnBuffer*
TxnAsyncContext::getTxnBuffer() const
{
    return m_tb;
}

qpid::asyncStore::AsyncOperation::opCode
TxnAsyncContext::getOpCode() const
{
    return m_op;
}

const char*
TxnAsyncContext::getOpStr() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

TxnHandle
TxnAsyncContext::getTransactionContext() const
{
    return m_th;
}

AsyncResultQueue*
TxnAsyncContext::getAsyncResultQueue() const
{
    return m_arq;
}

void
TxnAsyncContext::invokeCallback(const AsyncResultHandle* const arh) const
{
    if (m_rcb) {
        m_rcb(arh);
    }
}

}} // namespace qpid::broker
