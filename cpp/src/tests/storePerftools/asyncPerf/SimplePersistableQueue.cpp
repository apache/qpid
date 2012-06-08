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
 * \file SimplePersistableQueue.cpp
 */

#include "SimplePersistableQueue.h"

#include "MessageDeque.h"
#include "SimplePersistableMessage.h"
#include "SimpleTransactionContext.h"
#include "QueueAsyncContext.h"
#include "QueuedMessage.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"
#include "qpid/broker/AsyncResultHandle.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

SimplePersistableQueue::SimplePersistableQueue(const std::string& name,
                                               const qpid::framing::FieldTable& /*args*/,
                                               qpid::asyncStore::AsyncStoreImpl* store,
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

SimplePersistableQueue::~SimplePersistableQueue()
{
//    m_store->flush(*this);
    // TODO: Make destroying the store a test parameter
//    m_store->destroy(*this);
//    m_store = 0;
}

// static
void
SimplePersistableQueue::handleAsyncResult(const qpid::broker::AsyncResultHandle* const arh)
{
    if (arh) {
        boost::shared_ptr<QueueAsyncContext> qc = boost::dynamic_pointer_cast<QueueAsyncContext>(arh->getBrokerAsyncContext());
        if (arh->getErrNo()) {
            // TODO: Handle async failure here (other than by simply printing a message)
            std::cerr << "Queue name=\"" << qc->getQueue()->m_name << "\": Operation " << qc->getOpStr() << ": failure "
                      << arh->getErrNo() << " (" << arh->getErrMsg() << ")" << std::endl;
        } else {
//std::cout << "QQQ SimplePersistableQueue::handleAsyncResult() op=" << qc->getOpStr() << std::endl << std::flush;
            // Handle async success here
            switch(qc->getOpCode()) {
            case qpid::asyncStore::AsyncOperation::QUEUE_CREATE:
                qc->getQueue()->createComplete(qc);
                break;
            case qpid::asyncStore::AsyncOperation::QUEUE_FLUSH:
                qc->getQueue()->flushComplete(qc);
                break;
            case qpid::asyncStore::AsyncOperation::QUEUE_DESTROY:
                qc->getQueue()->destroyComplete(qc);
                break;
            case qpid::asyncStore::AsyncOperation::MSG_ENQUEUE:
                qc->getQueue()->enqueueComplete(qc);
                break;
            case qpid::asyncStore::AsyncOperation::MSG_DEQUEUE:
                qc->getQueue()->dequeueComplete(qc);
                break;
            default:
                std::ostringstream oss;
                oss << "tests::storePerftools::asyncPerf::SimplePersistableQueue::handleAsyncResult(): Unknown async queue operation: " << qc->getOpCode();
                throw qpid::Exception(oss.str());
            };
        }
    }
}

const qpid::broker::QueueHandle&
SimplePersistableQueue::getHandle() const
{
    return m_queueHandle;
}

qpid::broker::QueueHandle&
SimplePersistableQueue::getHandle()
{
    return m_queueHandle;
}

qpid::asyncStore::AsyncStoreImpl*
SimplePersistableQueue::getStore()
{
    return m_store;
}

void
SimplePersistableQueue::asyncCreate()
{
    if (m_store) {
        boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                       qpid::asyncStore::AsyncOperation::QUEUE_CREATE,
                                                                       &handleAsyncResult,
                                                                       &m_resultQueue));
        m_store->submitCreate(m_queueHandle,
                              this,
                              qac);
        ++m_asyncOpCounter;
    }
}

void
SimplePersistableQueue::asyncDestroy(const bool deleteQueue)
{
    m_destroyPending = true;
    if (m_store) {
        if (deleteQueue) {
            boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                           qpid::asyncStore::AsyncOperation::QUEUE_DESTROY,
                                                                           &handleAsyncResult,
                                                                           &m_resultQueue));
            m_store->submitDestroy(m_queueHandle,
                                   qac);
            ++m_asyncOpCounter;
        }
        m_asyncOpCounter.waitForZero(qpid::sys::Duration(10UL*1000*1000*1000));
    }
}

void
SimplePersistableQueue::deliver(boost::shared_ptr<SimplePersistableMessage> msg)
{
    QueuedMessage qm(this, msg);
    if(enqueue((SimpleTransactionContext*)0, qm)) {
        push(qm);
    }
}

bool
SimplePersistableQueue::dispatch()
{
    QueuedMessage qm;
    if (m_messages->consume(qm)) {
        return dequeue((SimpleTransactionContext*)0, qm);
    }
    return false;
}

bool
SimplePersistableQueue::enqueue(SimpleTransactionContext* ctxt,
                              QueuedMessage& qm)
{
    ScopedUse u(m_barrier);
    if (!u.m_acquired) {
        return false;
    }
    if (qm.payload()->isPersistent() && m_store) {
        qm.payload()->enqueueAsync(shared_from_this(), m_store);
        return asyncEnqueue(ctxt, qm);
    }
    return false;
}

bool
SimplePersistableQueue::dequeue(SimpleTransactionContext* ctxt,
                              QueuedMessage& qm)
{
    ScopedUse u(m_barrier);
    if (!u.m_acquired) {
        return false;
    }
    if (qm.payload()->isPersistent() && m_store) {
        qm.payload()->dequeueAsync(shared_from_this(), m_store);
        return asyncDequeue(ctxt, qm);
    }
    return false;
}

void
SimplePersistableQueue::encode(qpid::framing::Buffer& buffer) const
{
    buffer.putShortString(m_name);
}

uint32_t
SimplePersistableQueue::encodedSize() const
{
    return m_name.size() + 1;
}

uint64_t
SimplePersistableQueue::getPersistenceId() const
{
    return m_persistenceId;
}

void
SimplePersistableQueue::setPersistenceId(uint64_t persistenceId) const
{
    m_persistenceId = persistenceId;
}

void
SimplePersistableQueue::flush()
{
    //if(m_store) m_store->flush(*this);
}

const std::string&
SimplePersistableQueue::getName() const
{
    return m_name;
}

void
SimplePersistableQueue::setExternalQueueStore(qpid::broker::ExternalQueueStore* inst)
{
    if (externalQueueStore != inst && externalQueueStore)
        delete externalQueueStore;
    externalQueueStore = inst;
}

uint64_t
SimplePersistableQueue::getSize()
{
    return m_persistableData.size();
}

void
SimplePersistableQueue::write(char* target)
{
    ::memcpy(target, m_persistableData.data(), m_persistableData.size());
}

// --- Members & methods in msg handling path from qpid::Queue ---

// protected
SimplePersistableQueue::UsageBarrier::UsageBarrier(SimplePersistableQueue& q) :
        m_parent(q),
        m_count(0)
{}

// protected
bool
SimplePersistableQueue::UsageBarrier::acquire()
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
void SimplePersistableQueue::UsageBarrier::release()
{
    qpid::sys::Monitor::Monitor::ScopedLock l(m_monitor);
    if (--m_count == 0) {
        m_monitor.notifyAll();
    }
}

// protected
void SimplePersistableQueue::UsageBarrier::destroy()
{
    qpid::sys::Monitor::Monitor::ScopedLock l(m_monitor);
    m_parent.m_destroyed = true;
    while (m_count) {
        m_monitor.wait();
    }
}

// protected
SimplePersistableQueue::ScopedUse::ScopedUse(UsageBarrier& b) :
        m_barrier(b),
        m_acquired(m_barrier.acquire())
{}

// protected
SimplePersistableQueue::ScopedUse::~ScopedUse()
{
    if (m_acquired) {
        m_barrier.release();
    }
}

// private
void
SimplePersistableQueue::push(QueuedMessage& qm,
                           bool /*isRecovery*/)
{
    QueuedMessage removed;
    m_messages->push(qm, removed);
}

// --- End Members & methods in msg handling path from qpid::Queue ---

// private
bool
SimplePersistableQueue::asyncEnqueue(SimpleTransactionContext* txn,
                                   QueuedMessage& qm)
{
    qm.payload()->setPersistenceId(m_store->getNextRid());
//std::cout << "QQQ Queue=\"" << m_name << "\": asyncEnqueue() rid=0x" << std::hex << qm.payload()->getPersistenceId() << std::dec << std::endl << std::flush;
    boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                   qm.payload(),
                                                                   qpid::asyncStore::AsyncOperation::MSG_ENQUEUE,
                                                                   &handleAsyncResult,
                                                                   &m_resultQueue));
    m_store->submitEnqueue(qm.enqHandle(),
                           txn->getHandle(),
                           qac);
    ++m_asyncOpCounter;
    return true;
}

// private
bool
SimplePersistableQueue::asyncDequeue(SimpleTransactionContext* txn,
                                   QueuedMessage& qm)
{
//std::cout << "QQQ Queue=\"" << m_name << "\": asyncDequeue() rid=0x" << std::hex << qm.payload()->getPersistenceId() << std::dec << std::endl << std::flush;
    boost::shared_ptr<QueueAsyncContext> qac(new QueueAsyncContext(shared_from_this(),
                                                                   qm.payload(),
                                                                   qpid::asyncStore::AsyncOperation::MSG_DEQUEUE,
                                                                   &handleAsyncResult,
                                                                   &m_resultQueue));
    m_store->submitDequeue(qm.enqHandle(),
                           txn->getHandle(),
                           qac);
    ++m_asyncOpCounter;
    return true;
}

// private
void
SimplePersistableQueue::destroyCheck(const std::string& opDescr) const
{
    if (m_destroyPending || m_destroyed) {
        std::ostringstream oss;
        oss << opDescr << " on queue \"" << m_name << "\" after call to destroy";
        throw qpid::Exception(oss.str());
    }
}

// private
void
SimplePersistableQueue::createComplete(const boost::shared_ptr<QueueAsyncContext> qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": createComplete()" << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

// private
void
SimplePersistableQueue::flushComplete(const boost::shared_ptr<QueueAsyncContext> qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": flushComplete()" << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

// private
void
SimplePersistableQueue::destroyComplete(const boost::shared_ptr<QueueAsyncContext> qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": destroyComplete()" << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
    m_destroyed = true;
}

// private
void
SimplePersistableQueue::enqueueComplete(const boost::shared_ptr<QueueAsyncContext> qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": enqueueComplete() rid=0x" << std::hex << qc->getMessage()->getPersistenceId() << std::dec << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

// private
void
SimplePersistableQueue::dequeueComplete(const boost::shared_ptr<QueueAsyncContext> qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": dequeueComplete() rid=0x" << std::hex << qc->getMessage()->getPersistenceId() << std::dec << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

}}} // namespace tests::storePerftools::asyncPerf
