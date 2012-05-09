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

#ifndef tests_storePerfTools_asyncPerf_MockTransactionContext_h_
#define tests_storePerfTools_asyncPerf_MockTransactionContext_h_

#include "qpid/asyncStore/AsyncOperation.h"

#include "qpid/broker/BrokerContext.h"
#include "qpid/broker/TransactionalStore.h" // qpid::broker::TransactionContext
#include "qpid/broker/TxnHandle.h"
#include "qpid/sys/Mutex.h"

#include <boost/shared_ptr.hpp>
#include <deque>

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class QueuedMessage;

typedef boost::shared_ptr<qpid::asyncStore::AsyncStoreImpl> AsyncStoreImplPtr;

class MockTransactionContext : public qpid::broker::TransactionContext
{
public:
    // NOTE: TransactionContext - Bad naming? This context is the async return handling context for class
    //       MockTransactionContext async ops. Other classes using this pattern simply use XXXContext for this class
    //       (e.g. QueueContext in MockPersistableQueue), but in this case it may be confusing.
    class TransactionContext : public qpid::broker::BrokerContext
    {
    public:
        TransactionContext(MockTransactionContext* tc,
                           const qpid::asyncStore::AsyncOperation::opCode op);
        virtual ~TransactionContext();
        const char* getOp() const;
        void destroy();
        MockTransactionContext* m_tc;
        const qpid::asyncStore::AsyncOperation::opCode m_op;
    };

    MockTransactionContext(AsyncStoreImplPtr store,
                           const std::string& xid = std::string());
    virtual ~MockTransactionContext();
    static void handleAsyncResult(const qpid::broker::AsyncResult* res,
                                  qpid::broker::BrokerContext* bc);

    qpid::broker::TxnHandle& getHandle();
    bool is2pc() const;
    const std::string& getXid() const;
    void addEnqueuedMsg(QueuedMessage* qm);

    void prepare();
    void abort();
    void commit();

protected:
    AsyncStoreImplPtr m_store;
    qpid::broker::TxnHandle m_txnHandle;
    bool m_prepared;
    std::deque<QueuedMessage*> m_enqueuedMsgs;
    qpid::sys::Mutex m_enqueuedMsgsMutex;

    void localPrepare();

    // --- Ascnc op completions (called through handleAsyncResult) ---
    void prepareComplete(const TransactionContext* tc);
    void abortComplete(const TransactionContext* tc);
    void commitComplete(const TransactionContext* tc);

};

}}} // namespace tests:storePerftools::asyncPerf

#endif // tests_storePerfTools_asyncPerf_MockTransactionContext_h_
