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

#include "MockPersistableMessage.h"
#include "MockTransactionContext.h"
#include "QueuedMessage.h"
#include "TestOptions.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"
#include "qpid/broker/EnqueueHandle.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

// --- Inner class MockPersistableQueue::QueueContext ---

MockPersistableQueue::QueueContext::QueueContext(MockPersistableQueuePtr q,
                                                 const qpid::asyncStore::AsyncOperation::opCode op) :
        m_q(q),
        m_op(op)
{}

MockPersistableQueue::QueueContext::~QueueContext()
{}

const char*
MockPersistableQueue::QueueContext::getOp() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

void
MockPersistableQueue::QueueContext::destroy()
{
    delete this;
}

// --- Class MockPersistableQueue ---

MockPersistableQueue::MockPersistableQueue(const std::string& name,
                                           const qpid::framing::FieldTable& /*args*/,
                                           qpid::asyncStore::AsyncStoreImpl* store,
                                           const TestOptions& to,
                                           const char* msgData) :
        qpid::broker::PersistableQueue(),
        m_name(name),
        m_store(store),
        m_persistenceId(0ULL),
        m_persistableData(m_name), // TODO: Currently queue durable data consists only of the queue name. Update this.
        m_perfTestOpts(to),
        m_msgData(msgData)
{
    const qpid::types::Variant::Map qo;
    m_queueHandle = m_store->createQueueHandle(m_name, qo);
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
                                        qpid::broker::BrokerContext* bc)
{
    if (bc && res) {
        QueueContext* qc = dynamic_cast<QueueContext*>(bc);
        if (qc->m_q) {
            if (res->errNo) {
                // TODO: Handle async failure here
                std::cerr << "Queue name=\"" << qc->m_q->m_name << "\": Operation " << qc->getOp() << ": failure "
                          << res->errNo << " (" << res->errMsg << ")" << std::endl;
            } else {
                // Handle async success here
                switch(qc->m_op) {
                case qpid::asyncStore::AsyncOperation::QUEUE_CREATE:
                    qc->m_q->createComplete(qc);
                    break;
                case qpid::asyncStore::AsyncOperation::QUEUE_FLUSH:
                    qc->m_q->flushComplete(qc);
                    break;
                case qpid::asyncStore::AsyncOperation::QUEUE_DESTROY:
                    qc->m_q->destroyComplete(qc);
                    break;
                default:
                    std::ostringstream oss;
                    oss << "tests::storePerftools::asyncPerf::MockPersistableQueue::handleAsyncResult(): Unknown async queue operation: " << qc->m_op;
                    throw qpid::Exception(oss.str());
                };
            }
        }
    }
    if (bc) delete bc;
    if (res) delete res;
}

qpid::broker::QueueHandle&
MockPersistableQueue::getHandle()
{
    return m_queueHandle;
}

// static
void
MockPersistableQueue::asyncStoreCreate(MockPersistableQueuePtr& qp)
{
    qp->m_store->submitCreate(qp->m_queueHandle,
                              dynamic_cast<const qpid::broker::DataSource*>(qp.get()),
                              &handleAsyncResult,
                              new QueueContext(qp, qpid::asyncStore::AsyncOperation::QUEUE_CREATE));
}

// static
void
MockPersistableQueue::asyncStoreDestroy(MockPersistableQueuePtr& qp)
{
    qp->m_store->submitDestroy(qp->m_queueHandle,
                               &handleAsyncResult,
                               new QueueContext(qp, qpid::asyncStore::AsyncOperation::QUEUE_DESTROY));
}

void*
MockPersistableQueue::runEnqueues()
{
    uint32_t numMsgs = 0;
    uint16_t txnCnt = 0;
    const bool useTxn = m_perfTestOpts.m_enqTxnBlockSize > 0;
    MockTransactionContextPtr txn;
    while (numMsgs < m_perfTestOpts.m_numMsgs) {
        if (useTxn && txnCnt == 0) {
            txn.reset(new MockTransactionContext(m_store)); // equivalent to begin()
        }
        MockPersistableMessagePtr msg(new MockPersistableMessage(m_msgData, m_perfTestOpts.m_msgSize, m_store, true));
        msg->setPersistenceId(m_store->getNextRid());
        qpid::broker::EnqueueHandle enqHandle = m_store->createEnqueueHandle(msg->getHandle(), m_queueHandle);
        MockPersistableMessage::MessageContext* msgCtxt = new MockPersistableMessage::MessageContext(msg,
                                                                                                     qpid::asyncStore::AsyncOperation::MSG_ENQUEUE,
                                                                                                     this);
        if (useTxn) {
            m_store->submitEnqueue(enqHandle,
                                   txn->getHandle(),
                                   &MockPersistableMessage::handleAsyncResult,
                                   dynamic_cast<qpid::broker::BrokerContext*>(msgCtxt));
        } else {
            m_store->submitEnqueue(enqHandle,
                                   &MockPersistableMessage::handleAsyncResult,
                                   dynamic_cast<qpid::broker::BrokerContext*>(msgCtxt));
        }
        QueuedMessagePtr qm(new QueuedMessage(msg, enqHandle, txn));
        push(qm);
        if (useTxn && ++txnCnt >= m_perfTestOpts.m_enqTxnBlockSize) {
            txn->commit();
            txnCnt = 0;
        }
        ++numMsgs;
    }
    if (txnCnt > 0) {
        txn->commit();
        txnCnt = 0;
    }
    return 0;
}

void*
MockPersistableQueue::runDequeues()
{
    uint32_t numMsgs = 0;
    const uint32_t numMsgsToDequeue = m_perfTestOpts.m_numMsgs * m_perfTestOpts.m_numEnqThreadsPerQueue / m_perfTestOpts.m_numDeqThreadsPerQueue;
    uint16_t txnCnt = 0;
    const bool useTxn = m_perfTestOpts.m_deqTxnBlockSize > 0;
    MockTransactionContextPtr txn;
    QueuedMessagePtr qm;
    while (numMsgs < numMsgsToDequeue) {
        if (useTxn && txnCnt == 0) {
            txn.reset(new MockTransactionContext(m_store)); // equivalent to begin()
        }
        pop(qm);
        if (qm.get()) {
            qpid::broker::EnqueueHandle enqHandle = qm->getEnqueueHandle();
            qpid::broker::BrokerContext* bc = new MockPersistableMessage::MessageContext(qm->getMessage(),
                                                                                         qpid::asyncStore::AsyncOperation::MSG_DEQUEUE,
                                                                                         this);
            if (useTxn) {
                m_store->submitDequeue(enqHandle,
                                       txn->getHandle(),
                                       &MockPersistableMessage::handleAsyncResult,
                                       bc);
            } else {
                m_store->submitDequeue(enqHandle,
                                       &MockPersistableMessage::handleAsyncResult,
                                       bc);
            }
            ++numMsgs;
            qm.reset(static_cast<QueuedMessage*>(0));
            if (useTxn && ++txnCnt >= m_perfTestOpts.m_deqTxnBlockSize) {
                txn->commit();
                txnCnt = 0;
            }
        }
    }
    if (txnCnt > 0) {
        txn->commit();
        txnCnt = 0;
    }
    return 0;
}

//static
void*
MockPersistableQueue::startEnqueues(void* ptr)
{
    return reinterpret_cast<MockPersistableQueue*>(ptr)->runEnqueues();
}

//static
void*
MockPersistableQueue::startDequeues(void* ptr)
{
    return reinterpret_cast<MockPersistableQueue*>(ptr)->runDequeues();
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

// protected
void
MockPersistableQueue::createComplete(const QueueContext* qc)
{
//std::cout << "~~~~~ Queue name=\"" << qc->m_q->getName() << "\": createComplete()" << std::endl << std::flush;
    assert(qc->m_q.get() == this);
}

// protected
void
MockPersistableQueue::flushComplete(const QueueContext* qc)
{
//std::cout << "~~~~~ Queue name=\"" << qc->m_q->getName() << "\": flushComplete()" << std::endl << std::flush;
    assert(qc->m_q.get() == this);
}

// protected
void
MockPersistableQueue::destroyComplete(const QueueContext* qc)
{
//std::cout << "~~~~~ Queue name=\"" << qc->m_q->getName() << "\": destroyComplete()" << std::endl << std::flush;
    assert(qc->m_q.get() == this);
}

// protected
void
MockPersistableQueue::push(QueuedMessagePtr& qm)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_enqueuedMsgsMutex);
    m_enqueuedMsgs.push_back(qm);
    m_dequeueCondition.notify();
}

// protected
void
MockPersistableQueue::pop(QueuedMessagePtr& qm)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_enqueuedMsgsMutex);
    while (m_enqueuedMsgs.empty()) {
        m_dequeueCondition.wait(m_enqueuedMsgsMutex);
    }
    qm = m_enqueuedMsgs.front();
    if (qm->isTransactional()) {
        // The next msg is still in an open transaction, skip and find next non-open-txn msg
        MsgEnqListItr i = m_enqueuedMsgs.begin();
        while (++i != m_enqueuedMsgs.end()) {
            if (!(*i)->isTransactional()) {
                qm = *i;
                m_enqueuedMsgs.erase(i);
            }
        }
    } else {
        // The next msg is not in an open txn
        m_enqueuedMsgs.pop_front();
    }
}

}}} // namespace tests::storePerftools::asyncPerf
