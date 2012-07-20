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
 * \file SimpleQueue.h
 */

#ifndef tests_storePerftools_asyncPerf_SimpleQueue_h_
#define tests_storePerftools_asyncPerf_SimpleQueue_h_

#include "qpid/asyncStore/AtomicCounter.h" // AsyncOpCounter
#include "qpid/broker/AsyncStore.h" // qpid::broker::DataSource
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/QueueHandle.h"
#include "qpid/sys/Monitor.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>

namespace qpid {
namespace broker {
class AsyncResultQueue;
class QueueAsyncContext;
}
namespace framing {
class FieldTable;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MessageConsumer;
class Messages;
class PersistableQueuedMessage;
class QueuedMessage;
class SimpleMessage;

class SimpleQueue : public boost::enable_shared_from_this<SimpleQueue>,
                    public qpid::broker::PersistableQueue,
                    public qpid::broker::DataSource
{
public:
    SimpleQueue(const std::string& name,
                const qpid::framing::FieldTable& args,
                qpid::broker::AsyncStore* store,
                qpid::broker::AsyncResultQueue& arq);
    virtual ~SimpleQueue();

    const qpid::broker::QueueHandle& getHandle() const;
    qpid::broker::QueueHandle& getHandle();
    qpid::broker::AsyncStore* getStore();

    void asyncCreate();
    static void handleAsyncCreateResult(const qpid::broker::AsyncResultHandle* const arh);
    void asyncDestroy(const bool deleteQueue);
    static void handleAsyncDestroyResult(const qpid::broker::AsyncResultHandle* const arh);

    // --- Methods in msg handling path from qpid::Queue ---
    void deliver(boost::intrusive_ptr<SimpleMessage> msg);
    bool dispatch(MessageConsumer& mc);
    bool enqueue(boost::shared_ptr<QueuedMessage> qm);
    bool enqueue(qpid::broker::TxnHandle& th,
                 boost::shared_ptr<QueuedMessage> qm);
    bool dequeue(boost::shared_ptr<QueuedMessage> qm);
    bool dequeue(qpid::broker::TxnHandle& th,
                 boost::shared_ptr<QueuedMessage> qm);
    void process(boost::intrusive_ptr<SimpleMessage> msg);
    void enqueueAborted(boost::intrusive_ptr<SimpleMessage> msg);

    // --- Interface qpid::broker::Persistable ---
    virtual void encode(qpid::framing::Buffer& buffer) const;
    virtual uint32_t encodedSize() const;
    virtual uint64_t getPersistenceId() const;
    virtual void setPersistenceId(uint64_t persistenceId) const;

    // --- Interface qpid::broker::PersistableQueue ---
    virtual void flush();
    virtual const std::string& getName() const;
    virtual void setExternalQueueStore(qpid::broker::ExternalQueueStore* inst);

    // --- Interface qpid::broker::DataStore ---
    virtual uint64_t getSize();
    virtual void write(char* target);

private:
    static qpid::broker::TxnHandle s_nullTxnHandle; // used for non-txn operations

    const std::string m_name;
    qpid::broker::AsyncStore* m_store;
    qpid::broker::AsyncResultQueue& m_resultQueue;
    qpid::asyncStore::AsyncOpCounter m_asyncOpCounter; // TODO: change this to non-async store counter!
    mutable uint64_t m_persistenceId;
    std::string m_persistableData;
    qpid::broker::QueueHandle m_queueHandle;
    bool m_destroyPending;
    bool m_destroyed;

    // --- Members & methods in msg handling path copied from qpid::Queue ---
    struct UsageBarrier {
        SimpleQueue& m_parent;
        uint32_t m_count;
        qpid::sys::Monitor m_monitor;
        UsageBarrier(SimpleQueue& q);
        bool acquire();
        void release();
        void destroy();
    };
    struct ScopedUse {
        UsageBarrier& m_barrier;
        const bool m_acquired;
        ScopedUse(UsageBarrier& b);
        ~ScopedUse();
    };
    UsageBarrier m_barrier;
    std::auto_ptr<Messages> m_messages;
    void push(boost::shared_ptr<QueuedMessage> qm,
              bool isRecovery = false);

    // -- Async ops ---
    bool asyncEnqueue(qpid::broker::TxnHandle& th,
                      boost::shared_ptr<PersistableQueuedMessage> pqm);
    static void handleAsyncEnqueueResult(const qpid::broker::AsyncResultHandle* const arh);
    bool asyncDequeue(qpid::broker::TxnHandle& th,
                      boost::shared_ptr<PersistableQueuedMessage> pqm);
    static void handleAsyncDequeueResult(const qpid::broker::AsyncResultHandle* const arh);

    // --- Async op counter ---
    void destroyCheck(const std::string& opDescr) const;

    // --- Async op completions (called through handleAsyncResult) ---
    void createComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc);
    void flushComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc);
    void destroyComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc);
    void enqueueComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc);
    void dequeueComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc);
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_SimpleQueue_h_
