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
 * \file TransactionAsyncContext.h
 */

#ifndef tests_storePerftools_asyncPerf_TransactionAsyncContext_h_
#define tests_storePerftools_asyncPerf_TransactionAsyncContext_h_

#include "qpid/asyncStore/AsyncOperation.h"
#include "qpid/broker/AsyncStore.h" // qpid::broker::BrokerAsyncContext

#include <boost/shared_ptr.hpp>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MockTransactionContext;

class TransactionAsyncContext: public qpid::broker::BrokerAsyncContext
{
public:
    TransactionAsyncContext(boost::shared_ptr<MockTransactionContext> tc,
                            const qpid::asyncStore::AsyncOperation::opCode op);
    virtual ~TransactionAsyncContext();
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    boost::shared_ptr<MockTransactionContext> getTransactionContext() const;
    void destroy();

private:
    boost::shared_ptr<MockTransactionContext> m_tc;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_TransactionAsyncContext_h_
