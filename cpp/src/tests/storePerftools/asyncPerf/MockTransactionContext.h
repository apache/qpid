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
 * \file MockTransactionContext.h
 */

#ifndef tests_storePerftools_asyncPerf_MockTransactionContext_h_
#define tests_storePerftools_asyncPerf_MockTransactionContext_h_

#include "qpid/broker/TransactionalStore.h" // qpid::broker::TransactionContext
#include "qpid/broker/TxnHandle.h"
#include "qpid/sys/Mutex.h"

#include <deque>

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;
}
namespace broker {
class AsyncResult;
class BrokerAsyncContext;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class QueuedMessage;
class TransactionAsyncContext;

class MockTransactionContext : public qpid::broker::TransactionContext
{
public:
    MockTransactionContext(const std::string& xid = std::string());
    MockTransactionContext(qpid::asyncStore::AsyncStoreImpl* store,
                           const std::string& xid = std::string());
    virtual ~MockTransactionContext();
    static void handleAsyncResult(const qpid::broker::AsyncResult* res,
                                  qpid::broker::BrokerAsyncContext* bc);

    const qpid::broker::TxnHandle& getHandle() const;
    qpid::broker::TxnHandle& getHandle();
    bool is2pc() const;
    const std::string& getXid() const;
    void addEnqueuedMsg(QueuedMessage* qm);

    void prepare();
    void abort();
    void commit();

protected:
    std::string m_xid;
    bool m_tpcFlag;
    qpid::asyncStore::AsyncStoreImpl* m_store;
    qpid::broker::TxnHandle m_txnHandle;
    bool m_prepared;
    std::deque<QueuedMessage*> m_enqueuedMsgs;
    qpid::sys::Mutex m_enqueuedMsgsMutex;

    void localPrepare();
    void setLocalXid();

    // --- Ascnc op completions (called through handleAsyncResult) ---
    void prepareComplete(const TransactionAsyncContext* tc);
    void abortComplete(const TransactionAsyncContext* tc);
    void commitComplete(const TransactionAsyncContext* tc);

};

}}} // namespace tests:storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_MockTransactionContext_h_
