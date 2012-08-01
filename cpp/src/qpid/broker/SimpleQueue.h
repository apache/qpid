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

#ifndef qpid_broker_SimpleQueue_h_
#define qpid_broker_SimpleQueue_h_

#include "qpid/asyncStore/AtomicCounter.h" // AsyncOpCounter
#include "qpid/broker/AsyncStore.h" // DataSource
#include "qpid/broker/PersistableQueue.h"
#include "qpid/broker/QueueHandle.h"
#include "qpid/sys/Monitor.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>

namespace qpid {

namespace framing {
class FieldTable;
}

namespace broker {

class AsyncResultQueue;
class QueueAsyncContext;
class SimpleConsumer;
class SimpleMessages;
class SimpleQueuedMessage;
class SimpleMessage;
class SimpleTxnBuffer;

class SimpleQueue : public boost::enable_shared_from_this<SimpleQueue>,
                    public PersistableQueue,
                    public DataSource
{
public:
    SimpleQueue(const std::string& name,
                const qpid::framing::FieldTable& args,
                AsyncStore* store,
                AsyncResultQueue& arq);
    virtual ~SimpleQueue();

    const QueueHandle& getHandle() const;
    QueueHandle& getHandle();
    AsyncStore* getStore();

    void asyncCreate();
    static void handleAsyncCreateResult(const AsyncResultHandle* const arh);
    void asyncDestroy(const bool deleteQueue);
    static void handleAsyncDestroyResult(const AsyncResultHandle* const arh);

    // --- Methods in msg handling path from qpid::Queue ---
    void deliver(boost::intrusive_ptr<SimpleMessage> msg);
    bool dispatch(SimpleConsumer& sc);
    bool enqueue(boost::shared_ptr<SimpleQueuedMessage> qm);
    bool enqueue(SimpleTxnBuffer* tb,
                 boost::shared_ptr<SimpleQueuedMessage> qm);
    bool dequeue(boost::shared_ptr<SimpleQueuedMessage> qm);
    bool dequeue(SimpleTxnBuffer* tb,
                 boost::shared_ptr<SimpleQueuedMessage> qm);
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
    virtual void setExternalQueueStore(ExternalQueueStore* inst);

    // --- Interface qpid::broker::DataStore ---
    virtual uint64_t getSize();
    virtual void write(char* target);

private:
    static TxnHandle s_nullTxnHandle; // used for non-txn operations

    const std::string m_name;
    AsyncStore* m_store;
    AsyncResultQueue& m_resultQueue;
    qpid::asyncStore::AsyncOpCounter m_asyncOpCounter; // TODO: change this to non-async store counter!
    mutable uint64_t m_persistenceId;
    std::string m_persistableData;
    QueueHandle m_queueHandle;
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
    std::auto_ptr<SimpleMessages> m_messages;
    void push(boost::shared_ptr<SimpleQueuedMessage> qm,
              bool isRecovery = false);

    // -- Async ops ---
    bool asyncEnqueue(SimpleTxnBuffer* tb,
                      boost::shared_ptr<SimpleQueuedMessage> qm);
    static void handleAsyncEnqueueResult(const AsyncResultHandle* const arh);
    bool asyncDequeue(SimpleTxnBuffer* tb,
                      boost::shared_ptr<SimpleQueuedMessage> qm);
    static void handleAsyncDequeueResult(const AsyncResultHandle* const arh);

    // --- Async op counter ---
    void destroyCheck(const std::string& opDescr) const;

    // --- Async op completions (called through handleAsyncResult) ---
    void createComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void flushComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void destroyComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void enqueueComplete(const boost::shared_ptr<QueueAsyncContext> qc);
    void dequeueComplete(const boost::shared_ptr<QueueAsyncContext> qc);
};

}} // namespace qpid::broker

#endif // qpid_broker_SimpleQueue_h_
