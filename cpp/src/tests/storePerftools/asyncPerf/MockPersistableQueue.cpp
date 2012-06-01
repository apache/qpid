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
 * \file MockPersistableQueue.cpp
 */

#include "MockPersistableQueue.h"

#include "MessageDeque.h"
#include "MockPersistableMessage.h"
#include "MockTransactionContext.h"
#include "QueueAsyncContext.h"
#include "QueuedMessage.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MockPersistableQueue::MockPersistableQueue(const std::string& name,
                                           const qpid::framing::FieldTable& /*args*/,
                                           qpid::asyncStore::AsyncStoreImpl* store) :
        qpid::broker::PersistableQueue(),
        m_name(name),
        m_store(store),
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

MockPersistableQueue::~MockPersistableQueue()
{
//    m_store->flush(*this);
    // TODO: Make destroying the store a test parameter
//    m_store->destroy(*this);
//    m_store = 0;
}

// static
void
MockPersistableQueue::handleAsyncResult(const qpid::broker::AsyncResult* res,
                                        qpid::broker::BrokerAsyncContext* bc)
{
    if (bc && res) {
        QueueAsyncContext* qc = dynamic_cast<QueueAsyncContext*>(bc);
        if (res->errNo) {
            // TODO: Handle async failure here (other than by simply printing a message)
            std::cerr << "Queue name=\"" << qc->getQueue()->m_name << "\": Operation " << qc->getOpStr() << ": failure "
                      << res->errNo << " (" << res->errMsg << ")" << std::endl;
        } else {
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
                oss << "tests::storePerftools::asyncPerf::MockPersistableQueue::handleAsyncResult(): Unknown async queue operation: " << qc->getOpCode();
                throw qpid::Exception(oss.str());
            };
        }
    }
    if (bc) delete bc;
    if (res) delete res;
}

const qpid::broker::QueueHandle&
MockPersistableQueue::getHandle() const
{
    return m_queueHandle;
}

qpid::broker::QueueHandle&
MockPersistableQueue::getHandle()
{
    return m_queueHandle;
}

qpid::asyncStore::AsyncStoreImpl*
MockPersistableQueue::getStore()
{
    return m_store;
}

void
MockPersistableQueue::asyncCreate()
{
    if (m_store) {
        m_store->submitCreate(m_queueHandle,
                              this,
                              &handleAsyncResult,
                              new QueueAsyncContext(shared_from_this(),
                                                    qpid::asyncStore::AsyncOperation::QUEUE_CREATE));
        ++m_asyncOpCounter;
    }
}

void
MockPersistableQueue::asyncDestroy(const bool deleteQueue)
{
    m_destroyPending = true;
    if (m_store) {
        if (deleteQueue) {
            m_store->submitDestroy(m_queueHandle,
                                   &handleAsyncResult,
                                   new QueueAsyncContext(shared_from_this(),
                                                         qpid::asyncStore::AsyncOperation::QUEUE_DESTROY));
            ++m_asyncOpCounter;
        }
        m_asyncOpCounter.waitForZero(qpid::sys::Duration(10UL*1000*1000*1000));
    }
}

void
MockPersistableQueue::deliver(boost::shared_ptr<MockPersistableMessage> msg)
{
    QueuedMessage qm(this, msg);
    if(enqueue((MockTransactionContext*)0, qm)) {
        push(qm);
    }
}

bool
MockPersistableQueue::dispatch()
{
    QueuedMessage qm;
    if (m_messages->consume(qm)) {
        return dequeue((MockTransactionContext*)0, qm);
    }
    return false;
}

bool
MockPersistableQueue::enqueue(MockTransactionContext* ctxt,
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
MockPersistableQueue::dequeue(MockTransactionContext* ctxt,
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
MockPersistableQueue::encode(qpid::framing::Buffer& buffer) const
{
    buffer.putShortString(m_name);
}

uint32_t
MockPersistableQueue::encodedSize() const
{
    return m_name.size() + 1;
}

uint64_t
MockPersistableQueue::getPersistenceId() const
{
    return m_persistenceId;
}

void
MockPersistableQueue::setPersistenceId(uint64_t persistenceId) const
{
    m_persistenceId = persistenceId;
}

void
MockPersistableQueue::flush()
{
    //if(m_store) m_store->flush(*this);
}

const std::string&
MockPersistableQueue::getName() const
{
    return m_name;
}

void
MockPersistableQueue::setExternalQueueStore(qpid::broker::ExternalQueueStore* inst)
{
    if (externalQueueStore != inst && externalQueueStore)
        delete externalQueueStore;
    externalQueueStore = inst;
}

uint64_t
MockPersistableQueue::getSize()
{
    return m_persistableData.size();
}

void
MockPersistableQueue::write(char* target)
{
    ::memcpy(target, m_persistableData.data(), m_persistableData.size());
}

// --- Members & methods in msg handling path from qpid::Queue ---

// protected
MockPersistableQueue::UsageBarrier::UsageBarrier(MockPersistableQueue& q) :
        m_parent(q),
        m_count(0)
{}

// protected
bool
MockPersistableQueue::UsageBarrier::acquire()
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
void MockPersistableQueue::UsageBarrier::release()
{
    qpid::sys::Monitor::Monitor::ScopedLock l(m_monitor);
    if (--m_count == 0) {
        m_monitor.notifyAll();
    }
}

// protected
void MockPersistableQueue::UsageBarrier::destroy()
{
    qpid::sys::Monitor::Monitor::ScopedLock l(m_monitor);
    m_parent.m_destroyed = true;
    while (m_count) {
        m_monitor.wait();
    }
}

// protected
MockPersistableQueue::ScopedUse::ScopedUse(UsageBarrier& b) :
        m_barrier(b),
        m_acquired(m_barrier.acquire())
{}

// protected
MockPersistableQueue::ScopedUse::~ScopedUse()
{
    if (m_acquired) {
        m_barrier.release();
    }
}

// protected
void
MockPersistableQueue::push(QueuedMessage& qm,
                           bool /*isRecovery*/)
{
    QueuedMessage removed;
    m_messages->push(qm, removed);
}

// --- End Members & methods in msg handling path from qpid::Queue ---

// protected
bool
MockPersistableQueue::asyncEnqueue(MockTransactionContext* txn,
                                   QueuedMessage& qm)
{
    qm.payload()->setPersistenceId(m_store->getNextRid());
//std::cout << "QQQ Queue=\"" << m_name << "\": asyncEnqueue() rid=0x" << std::hex << qm.payload()->getPersistenceId() << std::dec << std::endl << std::flush;
    m_store->submitEnqueue(/*enqHandle*/qm.enqHandle(),
                           txn->getHandle(),
                           &handleAsyncResult,
                           new QueueAsyncContext(shared_from_this(),
                                                 qm.payload(),
                                                 qpid::asyncStore::AsyncOperation::MSG_ENQUEUE));
    ++m_asyncOpCounter;
    return true;
}

// protected
bool
MockPersistableQueue::asyncDequeue(MockTransactionContext* txn,
                                   QueuedMessage& qm)
{
//std::cout << "QQQ Queue=\"" << m_name << "\": asyncDequeue() rid=0x" << std::hex << qm.payload()->getPersistenceId() << std::dec << std::endl << std::flush;
    qpid::broker::EnqueueHandle enqHandle = qm.enqHandle();
    m_store->submitDequeue(enqHandle,
                           txn->getHandle(),
                           &handleAsyncResult,
                           new QueueAsyncContext(shared_from_this(),
                                                 qm.payload(),
                                                 qpid::asyncStore::AsyncOperation::MSG_DEQUEUE));
    ++m_asyncOpCounter;
    return true;
}

// protected
void
MockPersistableQueue::destroyCheck(const std::string& opDescr) const
{
    if (m_destroyPending || m_destroyed) {
        std::ostringstream oss;
        oss << opDescr << " on queue \"" << m_name << "\" after call to destroy";
        throw qpid::Exception(oss.str());
    }
}

// protected
void
MockPersistableQueue::createComplete(const QueueAsyncContext* qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": createComplete()" << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

// protected
void
MockPersistableQueue::flushComplete(const QueueAsyncContext* qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": flushComplete()" << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

// protected
void
MockPersistableQueue::destroyComplete(const QueueAsyncContext* qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": destroyComplete()" << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
    m_destroyed = true;
}

void
MockPersistableQueue::enqueueComplete(const QueueAsyncContext* qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": enqueueComplete() rid=0x" << std::hex << qc->getMessage()->getPersistenceId() << std::dec << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

void
MockPersistableQueue::dequeueComplete(const QueueAsyncContext* qc)
{
//std::cout << "QQQ Queue name=\"" << qc->getQueue()->getName() << "\": dequeueComplete() rid=0x" << std::hex << qc->getMessage()->getPersistenceId() << std::dec << std::endl << std::flush;
    assert(qc->getQueue().get() == this);
    --m_asyncOpCounter;
}

}}} // namespace tests::storePerftools::asyncPerf
