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

#include "DeliveryRecord.h"
#include "MessageConsumer.h"
#include "MessageDeque.h"
#include "QueuedMessage.h"
#include "SimpleMessage.h"

#include "qpid/broker/AsyncResultHandle.h"
#include "qpid/broker/QueueAsyncContext.h"
#include "qpid/broker/TxnHandle.h"

#include <string.h> // memcpy()

namespace tests {
namespace storePerftools {
namespace asyncPerf {

//static
qpid::broker::TxnHandle SimpleQueue::s_nullTxnHandle; // used for non-txn operations


SimpleQueue::SimpleQueue(const std::string& name,
                         const qpid::framing::FieldTable& /*args*/,
                         qpid::broker::AsyncStore* store,
                         qpid::broker::AsyncResultQueue& arq) :
        qpid::broker::PersistableQueue(),
        m_name(name),
        m_store(store),
        m_resultQueue(arq),
        m_asyncOpCounter(0UL),
        m_persistenceId(0ULL),
        m_persistableData(m_name), // TODO: Currently queue durable data consists only of the queue name. Update this.
        m_destroyPending(false),
        m_destroyed(false),
        m_barrier(*this),
        m_messages(new MessageDeque())
{
    if (m_store != 0) {
        const qpid::types::Variant::Map qo;
        m_queueHandle = m_store->createQueueHandle(m_name, qo);
    }
}

SimpleQueue::~SimpleQueue()
{}

const qpid::broker::QueueHandle&
SimpleQueue::getHandle() const
{
    return m_queueHandle;
}

qpid::broker::QueueHandle&
SimpleQueue::getHandle()
{
    return m_queueHandle;
}

qpid::broker::AsyncStore*
SimpleQueue::getStore()
{
    return m_store;
}

void
SimpleQueue::asyncCreate()
{
    if (m_store) {
        boost::shared_ptr<qpid::broker::QueueAsyncContext> qac(new qpid::broker::QueueAsyncContext(shared_from_this(),
                                                                                                   s_nullTxnHandle,
                                                                                                   &handleAsyncCreateResult,
                                                                                                   &m_resultQueue));
        m_store->submitCreate(m_queueHandle, this, qac);
        ++m_asyncOpCounter;
    }
}

//static
void
SimpleQueue::handleAsyncCreateResult(const qpid::broker::AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<qpid::broker::QueueAsyncContext> qc =
                boost::dynamic_pointer_cast<qpid::broker::QueueAsyncContext>(arh->getBrokerAsyncContext());
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
            boost::shared_ptr<qpid::broker::QueueAsyncContext> qac(new qpid::broker::QueueAsyncContext(shared_from_this(),
                                                                                                       s_nullTxnHandle,
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
SimpleQueue::handleAsyncDestroyResult(const qpid::broker::AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<qpid::broker::QueueAsyncContext> qc =
                boost::dynamic_pointer_cast<qpid::broker::QueueAsyncContext>(arh->getBrokerAsyncContext());
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
SimpleQueue::deliver(boost::intrusive_ptr<SimpleMessage> msg)
{
    boost::shared_ptr<QueuedMessage> qm(boost::shared_ptr<QueuedMessage>(new QueuedMessage(this, msg)));
    enqueue(s_nullTxnHandle, qm);
    push(qm);
}

bool
SimpleQueue::dispatch(MessageConsumer& mc)
{
    boost::shared_ptr<QueuedMessage> qm;
    if (m_messages->consume(qm)) {
        boost::shared_ptr<DeliveryRecord> dr(new DeliveryRecord(qm, mc, false));
        mc.record(dr);
        return true;
    }
    return false;
}

bool
SimpleQueue::enqueue(boost::shared_ptr<QueuedMessage> qm)
{
    return enqueue(s_nullTxnHandle, qm);
}

bool
SimpleQueue::enqueue(qpid::broker::TxnHandle& th,
                     boost::shared_ptr<QueuedMessage> qm)
{
    ScopedUse u(m_barrier);
    if (!u.m_acquired) {
        return false;
    }
    if (qm->payload()->isPersistent() && m_store) {
        qm->payload()->enqueueAsync(shared_from_this(), m_store);
        return asyncEnqueue(th, qm);
    }
    return false;
}

bool
SimpleQueue::dequeue(boost::shared_ptr<QueuedMessage> qm)
{
    return dequeue(s_nullTxnHandle, qm);
}

bool
SimpleQueue::dequeue(qpid::broker::TxnHandle& th,
                     boost::shared_ptr<QueuedMessage> qm)
{
    ScopedUse u(m_barrier);
    if (!u.m_acquired) {
        return false;
    }
    if (qm->payload()->isPersistent() && m_store) {
        qm->payload()->dequeueAsync(shared_from_this(), m_store);
        return asyncDequeue(th, qm);
    }
    return true;
}

void
SimpleQueue::process(boost::intrusive_ptr<SimpleMessage> msg)
{
    push(boost::shared_ptr<QueuedMessage>(new QueuedMessage(this, msg)));
}

void
SimpleQueue::enqueueAborted(boost::intrusive_ptr<SimpleMessage> /*msg*/)
{}

void
SimpleQueue::encode(qpid::framing::Buffer& buffer) const
{
    buffer.putShortString(m_name);
}

uint32_t
SimpleQueue::encodedSize() const
{
    return m_name.size() + 1;
}

uint64_t
SimpleQueue::getPersistenceId() const
{
    return m_persistenceId;
}

void
SimpleQueue::setPersistenceId(uint64_t persistenceId) const
{
    m_persistenceId = persistenceId;
}

void
SimpleQueue::flush()
{
    //if(m_store) m_store->flush(*this);
}

const std::string&
SimpleQueue::getName() const
{
    return m_name;
}

void
SimpleQueue::setExternalQueueStore(qpid::broker::ExternalQueueStore* inst)
{
    if (externalQueueStore != inst && externalQueueStore)
        delete externalQueueStore;
    externalQueueStore = inst;
}

uint64_t
SimpleQueue::getSize()
{
    return m_persistableData.size();
}

void
SimpleQueue::write(char* target)
{
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
SimpleQueue::UsageBarrier::acquire()
{
    qpid::sys::Monitor::ScopedLock l(m_monitor);
    if (m_parent.m_destroyed) {
        return false;
    } else {
        ++m_count;
        return true;
    }
}

// protected
void SimpleQueue::UsageBarrier::release()
{
    qpid::sys::Monitor::Monitor::ScopedLock l(m_monitor);
    if (--m_count == 0) {
        m_monitor.notifyAll();
    }
}

// protected
void SimpleQueue::UsageBarrier::destroy()
{
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
SimpleQueue::ScopedUse::~ScopedUse()
{
    if (m_acquired) {
        m_barrier.release();
    }
}

// private
void
SimpleQueue::push(boost::shared_ptr<QueuedMessage> qm,
                  bool /*isRecovery*/)
{
    m_messages->push(qm);
}

// --- End Members & methods in msg handling path from qpid::Queue ---

// private
bool
SimpleQueue::asyncEnqueue(qpid::broker::TxnHandle& th,
                          boost::shared_ptr<QueuedMessage> qm)
{
    assert(qm.get());
//    qm.payload()->setPersistenceId(m_store->getNextRid()); // TODO: rid is set by store itself - find way to do this
    boost::shared_ptr<qpid::broker::QueueAsyncContext> qac(new qpid::broker::QueueAsyncContext(shared_from_this(),
                                                                                               qm->payload(),
                                                                                               th,
                                                                                               &handleAsyncEnqueueResult,
                                                                                               &m_resultQueue));
    // TODO : This must be done from inside store, not here (the txn handle is opaque outside the store)
    if (th.isValid()) {
        th.incrOpCnt();
    }
    m_store->submitEnqueue(qm->enqHandle(), th, qac);
    ++m_asyncOpCounter;
    return true;
}

// private static
void
SimpleQueue::handleAsyncEnqueueResult(const qpid::broker::AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<qpid::broker::QueueAsyncContext> qc =
                boost::dynamic_pointer_cast<qpid::broker::QueueAsyncContext>(arh->getBrokerAsyncContext());
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
SimpleQueue::asyncDequeue(qpid::broker::TxnHandle& th,
                          boost::shared_ptr<QueuedMessage> qm)
{
    assert(qm.get());
    boost::shared_ptr<qpid::broker::QueueAsyncContext> qac(new qpid::broker::QueueAsyncContext(shared_from_this(),
                                                                                               qm->payload(),
                                                                                               th,
                                                                                               &handleAsyncDequeueResult,
                                                                                               &m_resultQueue));
    // TODO : This must be done from inside store, not here (the txn handle is opaque outside the store)
    if (th.isValid()) {
        th.incrOpCnt();
    }
    m_store->submitDequeue(qm->enqHandle(),
                           th,
                           qac);
    ++m_asyncOpCounter;
    return true;
}
// private static
void
SimpleQueue::handleAsyncDequeueResult(const qpid::broker::AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<qpid::broker::QueueAsyncContext> qc =
                boost::dynamic_pointer_cast<qpid::broker::QueueAsyncContext>(arh->getBrokerAsyncContext());
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
SimpleQueue::destroyCheck(const std::string& opDescr) const
{
    if (m_destroyPending || m_destroyed) {
        std::ostringstream oss;
        oss << opDescr << " on queue \"" << m_name << "\" after call to destroy";
        throw qpid::Exception(oss.str());
    }
}

// private
void
SimpleQueue::createComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc)
{
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

// private
void
SimpleQueue::flushComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc)
{
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

// private
void
SimpleQueue::destroyComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc)
{
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
    m_destroyed = true;
}

// private
void
SimpleQueue::enqueueComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc)
{
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;

    // TODO : This must be done from inside store, not here (the txn handle is opaque outside the store)
    qpid::broker::TxnHandle th = qc->getTxnHandle();
    if (th.isValid()) { // transactional enqueue
        th.decrOpCnt();
    }
}

// private
void
SimpleQueue::dequeueComplete(const boost::shared_ptr<qpid::broker::QueueAsyncContext> qc)
{
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;

    // TODO : This must be done from inside store, not here (the txn handle is opaque outside the store)
    qpid::broker::TxnHandle th = qc->getTxnHandle();
    if (th.isValid()) { // transactional enqueue
        th.decrOpCnt();
    }
}

}}} // namespace tests::storePerftools::asyncPerf
