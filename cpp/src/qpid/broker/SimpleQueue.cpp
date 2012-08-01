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
 * \file SimpleQueue.cpp
 */

#include "SimpleQueue.h"

#include "AsyncResultHandle.h"
#include "QueueAsyncContext.h"
#include "SimpleConsumer.h"
#include "SimpleDeliveryRecord.h"
#include "SimpleMessage.h"
#include "SimpleMessageDeque.h"
#include "SimpleQueuedMessage.h"
#include "SimpleTxnBuffer.h"

#include <string.h> // memcpy()

namespace qpid  {
namespace broker {

//static
TxnHandle SimpleQueue::s_nullTxnHandle; // used for non-txn operations


SimpleQueue::SimpleQueue(const std::string& name,
                         const qpid::framing::FieldTable& /*args*/,
                         AsyncStore* store,
                         AsyncResultQueue& arq) :
        PersistableQueue(),
        m_name(name),
        m_store(store),
        m_resultQueue(arq),
        m_asyncOpCounter(0UL),
        m_persistenceId(0ULL),
        m_persistableData(m_name), // TODO: Currently queue durable data consists only of the queue name. Update this.
        m_destroyPending(false),
        m_destroyed(false),
        m_barrier(*this),
        m_messages(new SimpleMessageDeque())
{
    if (m_store != 0) {
        const qpid::types::Variant::Map qo;
        m_queueHandle = m_store->createQueueHandle(m_name, qo);
    }
}

SimpleQueue::~SimpleQueue() {}

const QueueHandle&
SimpleQueue::getHandle() const {
    return m_queueHandle;
}

QueueHandle&
SimpleQueue::getHandle() {
    return m_queueHandle;
}

AsyncStore*
SimpleQueue::getStore() {
    return m_store;
}

void
SimpleQueue::asyncCreate() {
    if (m_store) {
        boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                       &handleAsyncCreateResult,
                                                                       &m_resultQueue));
        m_store->submitCreate(m_queueHandle, this, qac);
        ++m_asyncOpCounter;
    }
}

//static
void
SimpleQueue::handleAsyncCreateResult(const AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<QueueAsyncContext> qc = boost::dynamic_pointer_cast<QueueAsyncContext>(arh->getBrokerAsyncContext());
        boost::shared_ptr<SimpleQueue> sq = boost::dynamic_pointer_cast<SimpleQueue>(qc->getQueue());
        if (arh->getErrNo()) {
            // TODO: Handle async failure here (other than by simply printing a message)
            std::cerr << "Queue name=\"" << sq->m_name << "\": Operation " << qc->getOpStr() << ": failure "
                      << arh->getErrNo() << " (" << arh->getErrMsg() << ")" << std::endl;
        } else {
            sq->createComplete(qc);
        }
    }
}

void
SimpleQueue::asyncDestroy(const bool deleteQueue)
{
    m_destroyPending = true;
    if (m_store) {
        if (deleteQueue) {
            boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                           &handleAsyncDestroyResult,
                                                                           &m_resultQueue));
            m_store->submitDestroy(m_queueHandle, qac);
            ++m_asyncOpCounter;
        }
        m_asyncOpCounter.waitForZero(qpid::sys::Duration(10UL*1000*1000*1000));
    }
}

//static
void
SimpleQueue::handleAsyncDestroyResult(const AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<QueueAsyncContext> qc =
                boost::dynamic_pointer_cast<QueueAsyncContext>(arh->getBrokerAsyncContext());
        boost::shared_ptr<SimpleQueue> sq = boost::dynamic_pointer_cast<SimpleQueue>(qc->getQueue());
        if (arh->getErrNo()) {
            // TODO: Handle async failure here (other than by simply printing a message)
            std::cerr << "Queue name=\"" << sq->m_name << "\": Operation " << qc->getOpStr() << ": failure "
                      << arh->getErrNo() << " (" << arh->getErrMsg() << ")" << std::endl;
        } else {
            sq->destroyComplete(qc);
        }
    }
}

void
SimpleQueue::deliver(boost::intrusive_ptr<SimpleMessage> msg) {
    boost::shared_ptr<SimpleQueuedMessage> qm(boost::shared_ptr<SimpleQueuedMessage>(new SimpleQueuedMessage(this, msg)));
    enqueue(qm);
    push(qm);
}

bool
SimpleQueue::dispatch(SimpleConsumer& sc) {
    boost::shared_ptr<SimpleQueuedMessage> qm;
    if (m_messages->consume(qm)) {
        boost::shared_ptr<SimpleDeliveryRecord> dr(new SimpleDeliveryRecord(qm, sc, false));
        sc.record(dr);
        return true;
    }
    return false;
}

bool
SimpleQueue::enqueue(boost::shared_ptr<SimpleQueuedMessage> qm) {
    return enqueue(0, qm);
}

bool
SimpleQueue::enqueue(SimpleTxnBuffer* tb,
                     boost::shared_ptr<SimpleQueuedMessage> qm) {
    ScopedUse u(m_barrier);
    if (!u.m_acquired) {
        return false;
    }
    if (qm->payload()->isPersistent() && m_store) {
        qm->payload()->enqueueAsync(shared_from_this(), m_store);
        return asyncEnqueue(tb, qm);
    }
    return false;
}

bool
SimpleQueue::dequeue(boost::shared_ptr<SimpleQueuedMessage> qm) {
    return dequeue(0, qm);
}

bool
SimpleQueue::dequeue(SimpleTxnBuffer* tb,
                     boost::shared_ptr<SimpleQueuedMessage> qm) {
    ScopedUse u(m_barrier);
    if (!u.m_acquired) {
        return false;
    }
    if (qm->payload()->isPersistent() && m_store) {
        qm->payload()->dequeueAsync(shared_from_this(), m_store);
        return asyncDequeue(tb, qm);
    }
    return true;
}

void
SimpleQueue::process(boost::intrusive_ptr<SimpleMessage> msg) {
    push(boost::shared_ptr<SimpleQueuedMessage>(new SimpleQueuedMessage(this, msg)));
}

void
SimpleQueue::enqueueAborted(boost::intrusive_ptr<SimpleMessage>) {}

void
SimpleQueue::encode(qpid::framing::Buffer& buffer) const {
    buffer.putShortString(m_name);
}

uint32_t
SimpleQueue::encodedSize() const {
    return m_name.size() + 1;
}

uint64_t
SimpleQueue::getPersistenceId() const {
    return m_persistenceId;
}

void
SimpleQueue::setPersistenceId(uint64_t persistenceId) const {
    m_persistenceId = persistenceId;
}

void
SimpleQueue::flush() {
    //if(m_store) m_store->flush(*this);
}

const std::string&
SimpleQueue::getName() const {
    return m_name;
}

void
SimpleQueue::setExternalQueueStore(ExternalQueueStore* inst) {
    if (externalQueueStore != inst && externalQueueStore)
        delete externalQueueStore;
    externalQueueStore = inst;
}

uint64_t
SimpleQueue::getSize() {
    return m_persistableData.size();
}

void
SimpleQueue::write(char* target) {
    ::memcpy(target, m_persistableData.data(), m_persistableData.size());
}

// --- Members & methods in msg handling path from qpid::Queue ---

// protected
SimpleQueue::UsageBarrier::UsageBarrier(SimpleQueue& q) :
        m_parent(q),
        m_count(0)
{}

// protected
bool
SimpleQueue::UsageBarrier::acquire() {
    qpid::sys::Monitor::ScopedLock l(m_monitor);
    if (m_parent.m_destroyed) {
        return false;
    } else {
        ++m_count;
        return true;
    }
}

// protected
void SimpleQueue::UsageBarrier::release() {
    qpid::sys::Monitor::Monitor::ScopedLock l(m_monitor);
    if (--m_count == 0) {
        m_monitor.notifyAll();
    }
}

// protected
void SimpleQueue::UsageBarrier::destroy() {
    qpid::sys::Monitor::Monitor::ScopedLock l(m_monitor);
    m_parent.m_destroyed = true;
    while (m_count) {
        m_monitor.wait();
    }
}

// protected
SimpleQueue::ScopedUse::ScopedUse(UsageBarrier& b) :
        m_barrier(b),
        m_acquired(m_barrier.acquire())
{}

// protected
SimpleQueue::ScopedUse::~ScopedUse() {
    if (m_acquired) {
        m_barrier.release();
    }
}

// private
void
SimpleQueue::push(boost::shared_ptr<SimpleQueuedMessage> qm,
                  bool /*isRecovery*/) {
    m_messages->push(qm);
}

// --- End Members & methods in msg handling path from qpid::Queue ---

// private
bool
SimpleQueue::asyncEnqueue(SimpleTxnBuffer* tb,
                          boost::shared_ptr<SimpleQueuedMessage> qm) {
    assert(qm.get());
    boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                   qm->payload(),
                                                                   tb,
                                                                   &handleAsyncEnqueueResult,
                                                                   &m_resultQueue));
    if (tb) {
        tb->incrOpCnt();
        m_store->submitEnqueue(qm->enqHandle(), tb->getTxnHandle(), qac);
    } else {
        m_store->submitEnqueue(qm->enqHandle(), s_nullTxnHandle, qac);
    }
    ++m_asyncOpCounter;
    return true;
}

// private static
void
SimpleQueue::handleAsyncEnqueueResult(const AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<QueueAsyncContext> qc =
                boost::dynamic_pointer_cast<QueueAsyncContext>(arh->getBrokerAsyncContext());
        boost::shared_ptr<SimpleQueue> sq = boost::dynamic_pointer_cast<SimpleQueue>(qc->getQueue());
        if (arh->getErrNo()) {
            // TODO: Handle async failure here (other than by simply printing a message)
            std::cerr << "Queue name=\"" << sq->m_name << "\": Operation " << qc->getOpStr() << ": failure "
                      << arh->getErrNo() << " (" << arh->getErrMsg() << ")" << std::endl;
        } else {
            sq->enqueueComplete(qc);
        }
    }
}

// private
bool
SimpleQueue::asyncDequeue(SimpleTxnBuffer* tb,
                          boost::shared_ptr<SimpleQueuedMessage> qm) {
    assert(qm.get());
    boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                   qm->payload(),
                                                                   tb,
                                                                   &handleAsyncDequeueResult,
                                                                   &m_resultQueue));
    if (tb) {
        tb->incrOpCnt();
        m_store->submitDequeue(qm->enqHandle(), tb->getTxnHandle(), qac);
    } else {
        m_store->submitDequeue(qm->enqHandle(), s_nullTxnHandle, qac);
    }
    ++m_asyncOpCounter;
    return true;
}

// private static
void
SimpleQueue::handleAsyncDequeueResult(const AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<QueueAsyncContext> qc = boost::dynamic_pointer_cast<QueueAsyncContext>(arh->getBrokerAsyncContext());
        boost::shared_ptr<SimpleQueue> sq = boost::dynamic_pointer_cast<SimpleQueue>(qc->getQueue());
        if (arh->getErrNo()) {
            // TODO: Handle async failure here (other than by simply printing a message)
            std::cerr << "Queue name=\"" << sq->m_name << "\": Operation " << qc->getOpStr() << ": failure "
                      << arh->getErrNo() << " (" << arh->getErrMsg() << ")" << std::endl;
        } else {
            sq->dequeueComplete(qc);
        }
    }
}

// private
void
SimpleQueue::destroyCheck(const std::string& opDescr) const {
    if (m_destroyPending || m_destroyed) {
        std::ostringstream oss;
        oss << opDescr << " on queue \"" << m_name << "\" after call to destroy";
        throw qpid::Exception(oss.str());
    }
}

// private
void
SimpleQueue::createComplete(const boost::shared_ptr<QueueAsyncContext> qc) {
    if (qc.get()) {
        assert(qc->getQueue().get() == this);
    }
    --m_asyncOpCounter;
}

// private
void
SimpleQueue::flushComplete(const boost::shared_ptr<QueueAsyncContext> qc) {
    if (qc.get()) {
        assert(qc->getQueue().get() == this);
    }
    --m_asyncOpCounter;
}

// private
void
SimpleQueue::destroyComplete(const boost::shared_ptr<QueueAsyncContext> qc) {
    if (qc.get()) {
        assert(qc->getQueue().get() == this);
    }
    --m_asyncOpCounter;
    m_destroyed = true;
}

// private
void
SimpleQueue::enqueueComplete(const boost::shared_ptr<QueueAsyncContext> qc) {
    if (qc.get()) {
        assert(qc->getQueue().get() == this);
        if (qc->getTxnBuffer()) { // transactional enqueue
            qc->getTxnBuffer()->decrOpCnt();
        }
    }
    --m_asyncOpCounter;
}

// private
void
SimpleQueue::dequeueComplete(const boost::shared_ptr<QueueAsyncContext> qc) {
    if (qc.get()) {
        assert(qc->getQueue().get() == this);
        if (qc->getTxnBuffer()) { // transactional enqueue
            qc->getTxnBuffer()->decrOpCnt();
        }
    }
    --m_asyncOpCounter;
}

}} // namespace qpid::broker
