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
 * \file SimplePersistableQueue.h
 */

#ifndef tests_storePerftools_asyncPerf_SimplePersistableQueue_h_
#define tests_storePerftools_asyncPerf_SimplePersistableQueue_h_

#include "qpid/asyncStore/AtomicCounter.h" // AsyncOpCounter
#include "qpid/broker/AsyncStore.h" // qpid::broker::DataSource
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/QueueHandle.h"
#include "qpid/sys/Monitor.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;
}
namespace broker {
class AsyncResultQueue;
class TxnHandle;
}
namespace framing {
class FieldTable;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class Messages;
class SimplePersistableMessage;
class QueueAsyncContext;
class QueuedMessage;

class SimplePersistableQueue : public boost::enable_shared_from_this<SimplePersistableQueue>,
                               public qpid::broker::PersistableQueue,
                               public qpid::broker::DataSource
{
public:
    SimplePersistableQueue(const std::string& name,
                         const qpid::framing::FieldTable& args,
                         qpid::asyncStore::AsyncStoreImpl* store,
                         qpid::broker::AsyncResultQueue& arq);
    virtual ~SimplePersistableQueue();

    static void handleAsyncResult(const qpid::broker::AsyncResultHandle* const res);
    const qpid::broker::QueueHandle& getHandle() const;
    qpid::broker::QueueHandle& getHandle();
    qpid::asyncStore::AsyncStoreImpl* getStore();

    void asyncCreate();
    void asyncDestroy(const bool deleteQueue);

    // --- Methods in msg handling path from qpid::Queue ---
    void deliver(boost::intrusive_ptr<SimplePersistableMessage> msg);
    bool dispatch(); // similar to qpid::broker::Queue::distpatch(Consumer&) but without Consumer param
    bool enqueue(qpid::broker::TxnHandle& th,
                 QueuedMessage& qm);
    bool dequeue(qpid::broker::TxnHandle& th,
                 QueuedMessage& qm);
    void process(boost::intrusive_ptr<SimplePersistableMessage> msg);
    void enqueueAborted(boost::intrusive_ptr<SimplePersistableMessage> msg);

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

private:
    static qpid::broker::TxnHandle s_nullTxnHandle; // used for non-txn operations

    const std::string m_name;
    qpid::asyncStore::AsyncStoreImpl* m_store;
    qpid::broker::AsyncResultQueue& m_resultQueue;
    qpid::asyncStore::AsyncOpCounter m_asyncOpCounter;
    mutable uint64_t m_persistenceId;
    std::string m_persistableData;
    qpid::broker::QueueHandle m_queueHandle;
    bool m_destroyPending;
    bool m_destroyed;

    // --- Members & methods in msg handling path copied from qpid::Queue ---
    struct UsageBarrier
    {
        SimplePersistableQueue& m_parent;
        uint32_t m_count;
        qpid::sys::Monitor m_monitor;
        UsageBarrier(SimplePersistableQueue& q);
        bool acquire();
        void release();
        void destroy();
    };
    struct ScopedUse
    {
        UsageBarrier& m_barrier;
        const bool m_acquired;
        ScopedUse(UsageBarrier& b);
        ~ScopedUse();
    };
    UsageBarrier m_barrier;
    std::auto_ptr<Messages> m_messages;
    void push(QueuedMessage& qm,
              bool isRecovery = false);

    // -- Async ops ---
    bool asyncEnqueue(qpid::broker::TxnHandle& th,
                      QueuedMessage& qm);
    bool asyncDequeue(qpid::broker::TxnHandle& th,
                      QueuedMessage& qm);

    // --- Async op counter ---
    void destroyCheck(const std::string& opDescr) const;

    // --- Async op completions (called through handleAsyncResult) ---
    void createComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void flushComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void destroyComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void enqueueComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void dequeueComplete(const boost::shared_ptr<QueueAsyncContext> qc);
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_SimplePersistableQueue_h_
