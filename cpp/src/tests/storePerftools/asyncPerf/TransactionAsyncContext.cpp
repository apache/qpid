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

#include "TransactionAsyncContext.h"

#include <cassert>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

TransactionAsyncContext::TransactionAsyncContext(boost::shared_ptr<SimpleTransactionContext> tc,
                                                 const qpid::asyncStore::AsyncOperation::opCode op):
        m_tc(tc),
        m_op(op)
{
    assert(m_tc.get() != 0);
}

TransactionAsyncContext::~TransactionAsyncContext()
{}

qpid::asyncStore::AsyncOperation::opCode
TransactionAsyncContext::getOpCode() const
{
    return m_op;
}

const char*
TransactionAsyncContext::getOpStr() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

boost::shared_ptr<SimpleTransactionContext>
TransactionAsyncContext::getTransactionContext() const
{
    return m_tc;
}

void
TransactionAsyncContext::destroy()
{
    delete this;
}

}}} // namespace tests::storePerftools::asyncPerf
