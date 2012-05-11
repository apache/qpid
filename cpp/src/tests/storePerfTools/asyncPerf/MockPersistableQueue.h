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
 * \file MockPersistableQueue.h
 */

#ifndef tests_storePerfTools_asyncPerf_MockPersistableQueue_h_
#define tests_storePerfTools_asyncPerf_MockPersistableQueue_h_

#include "qpid/asyncStore/AsyncOperation.h"
#include "qpid/broker/AsyncStore.h" // qpid::broker::DataSource
#include "qpid/broker/BrokerContext.h"
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/QueueHandle.h"
#include "qpid/sys/Condition.h"
#include "qpid/sys/Mutex.h"

#include <boost/shared_ptr.hpp>
#include <deque>

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;
}
namespace framing {
class FieldTable;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MockPersistableQueue;
class QueuedMessage;
class TestOptions;

typedef boost::shared_ptr<MockPersistableQueue> MockPersistableQueuePtr;
typedef boost::shared_ptr<QueuedMessage> QueuedMessagePtr;

class MockPersistableQueue : public qpid::broker::PersistableQueue, public qpid::broker::DataSource
{
public:
    class QueueContext : public qpid::broker::BrokerContext
    {
    public:
        QueueContext(MockPersistableQueuePtr q,
                     const qpid::asyncStore::AsyncOperation::opCode op);
        virtual ~QueueContext();
        const char* getOp() const;
        void destroy();
        MockPersistableQueuePtr m_q;
        const qpid::asyncStore::AsyncOperation::opCode m_op;
    };

    MockPersistableQueue(const std::string& name,
                         const qpid::framing::FieldTable& args,
                         qpid::asyncStore::AsyncStoreImpl* store,
                         const TestOptions& perfTestParams,
                         const char* msgData);
    virtual ~MockPersistableQueue();

    // --- Async functionality ---
    static void handleAsyncResult(const qpid::broker::AsyncResult* res,
                                  qpid::broker::BrokerContext* bc);
    qpid::broker::QueueHandle& getHandle();
    static void asyncStoreCreate(MockPersistableQueuePtr& qp);
    static void asyncStoreDestroy(MockPersistableQueuePtr& qp);

    // --- Performance test thread entry points ---
    void* runEnqueues();
    void* runDequeues();
    static void* startEnqueues(void* ptr);
    static void* startDequeues(void* ptr);

    // --- Interface qpid::broker::Persistable ---
    virtual void encode(qpid::framing::Buffer& buffer) const;
    virtual uint32_t encodedSize() const;
    virtual uint64_t getPersistenceId() const;
    virtual void setPersistenceId(uint64_t persistenceId) const;

    // --- Interface qpid::broker::PersistableQueue ---
    virtual void flush();
    virtual const std::string& getName() const;
    virtual void setExternalQueueStore(qpid::broker::ExternalQueueStore* inst);

    // --- Interface DataStore ---
    virtual uint64_t getSize();
    virtual void write(char* target);

protected:
    const std::string m_name;
    qpid::asyncStore::AsyncStoreImpl* m_store;
    mutable uint64_t m_persistenceId;
    std::string m_persistableData;
    qpid::broker::QueueHandle m_queueHandle;

    // Test params
    const TestOptions& m_perfTestOpts;
    const char* m_msgData;

    typedef std::deque<QueuedMessagePtr> MsgEnqList;
    typedef MsgEnqList::iterator MsgEnqListItr;
    MsgEnqList m_enqueuedMsgs;
    qpid::sys::Mutex m_enqueuedMsgsMutex;
    qpid::sys::Condition m_dequeueCondition;

    // --- Ascnc op completions (called through handleAsyncResult) ---
    void createComplete(const QueueContext* qc);
    void flushComplete(const QueueContext* qc);
    void destroyComplete(const QueueContext* qc);

    // --- Queue functionality ---
    void push(QueuedMessagePtr& msg);
    void pop(QueuedMessagePtr& msg);
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerfTools_asyncPerf_MockPersistableQueue_h_
