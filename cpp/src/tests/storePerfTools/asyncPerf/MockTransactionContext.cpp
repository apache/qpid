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
 * \file MockTransactionContext.cpp
 */

#include "MockTransactionContext.h"

#include "QueuedMessage.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

// --- Inner class MockTransactionContext::QueueContext ---

MockTransactionContext::TransactionContext::TransactionContext(MockTransactionContext* tc,
                                                               const qpid::asyncStore::AsyncOperation::opCode op) :
        m_tc(tc),
        m_op(op)
{}

MockTransactionContext::TransactionContext::~TransactionContext()
{}

const char*
MockTransactionContext::TransactionContext::getOp() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

void
MockTransactionContext::TransactionContext::destroy()
{
    delete this;
}

// --- Class MockTransactionContext ---


MockTransactionContext::MockTransactionContext(AsyncStoreImplPtr store,
                                               const std::string& xid) :
        m_store(store),
        m_txnHandle(store->createTxnHandle(xid)),
        m_prepared(false),
        m_enqueuedMsgs()
{
//std::cout << "*TXN* begin: xid=" << getXid() << "; 2PC=" << (is2pc()?"T":"F") << std::endl;
}

MockTransactionContext::~MockTransactionContext()
{}

// static
void
MockTransactionContext::handleAsyncResult(const qpid::broker::AsyncResult* res,
                                          qpid::broker::BrokerContext* bc)
{
    if (bc && res) {
        TransactionContext* tc = dynamic_cast<TransactionContext*>(bc);
        if (tc->m_tc) {
            if (res->errNo) {
                // TODO: Handle async failure here
                std::cerr << "Transaction xid=\"" << tc->m_tc->getXid() << "\": Operation " << tc->getOp() << ": failure "
                          << res->errNo << " (" << res->errMsg << ")" << std::endl;
            } else {
                // Handle async success here
                switch(tc->m_op) {
                case qpid::asyncStore::AsyncOperation::TXN_PREPARE:
                    tc->m_tc->prepareComplete(tc);
                    break;
                case qpid::asyncStore::AsyncOperation::TXN_COMMIT:
                    tc->m_tc->commitComplete(tc);
                    break;
                case qpid::asyncStore::AsyncOperation::TXN_ABORT:
                    tc->m_tc->abortComplete(tc);
                    break;
                default:
                    std::ostringstream oss;
                    oss << "tests::storePerftools::asyncPerf::MockTransactionContext::handleAsyncResult(): Unknown async operation: " << tc->m_op;
                    throw qpid::Exception(oss.str());
                };
            }
        }
    }
    if (bc) delete bc;
    if (res) delete res;
}

qpid::broker::TxnHandle&
MockTransactionContext::getHandle()
{
    return m_txnHandle;
}

bool
MockTransactionContext::is2pc() const
{
    return m_txnHandle.is2pc();
}

const std::string&
MockTransactionContext::getXid() const
{
    return m_txnHandle.getXid();
}

void
MockTransactionContext::addEnqueuedMsg(QueuedMessage* qm)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_enqueuedMsgsMutex);
    m_enqueuedMsgs.push_back(qm);
}

void
MockTransactionContext::prepare()
{
    if (m_txnHandle.is2pc()) {
        localPrepare();
        m_prepared = true;
    }
    std::ostringstream oss;
    oss << "MockTransactionContext::prepare(): xid=\"" << getXid()
        << "\": Transaction Error: called prepare() on local transaction";
    throw qpid::Exception(oss.str());
}

void
MockTransactionContext::abort()
{
    // TODO: Check the following XA transaction semantics:
    // Assuming 2PC aborts can occur without a prepare. Do local prepare if not already prepared.
    if (!m_prepared) {
        localPrepare();
    }
    m_store->submitAbort(m_txnHandle,
                         &handleAsyncResult,
                         dynamic_cast<qpid::broker::BrokerContext*>(new TransactionContext(this, qpid::asyncStore::AsyncOperation::TXN_ABORT)));
//std::cout << "*TXN* abort: xid=" << m_txnHandle.getXid() << "; 2PC=" << (m_txnHandle.is2pc()?"T":"F") << std::endl;
}

void
MockTransactionContext::commit()
{
    if (is2pc()) {
        if (!m_prepared) {
            std::ostringstream oss;
            oss << "MockTransactionContext::abort(): xid=\"" << getXid()
                    << "\": Transaction Error: called commit() without prepare() on 2PC transaction";
            throw qpid::Exception(oss.str());
        }
    } else {
        localPrepare();
    }
    m_store->submitCommit(m_txnHandle,
                          &handleAsyncResult,
                          dynamic_cast<qpid::broker::BrokerContext*>(new TransactionContext(this, qpid::asyncStore::AsyncOperation::TXN_COMMIT)));
//std::cout << "*TXN* commit: xid=" << m_txnHandle.getXid() << "; 2PC=" << (m_txnHandle.is2pc()?"T":"F") << std::endl;
}


// protected
void
MockTransactionContext::localPrepare()
{
    m_store->submitPrepare(m_txnHandle,
                           &handleAsyncResult,
                           dynamic_cast<qpid::broker::BrokerContext*>(new TransactionContext(this, qpid::asyncStore::AsyncOperation::TXN_PREPARE)));
//std::cout << "*TXN* localPrepare: xid=" << m_txnHandle.getXid() << "; 2PC=" << (m_txnHandle.is2pc()?"T":"F") << std::endl;
}

// protected
void
MockTransactionContext::prepareComplete(const TransactionContext* /*tc*/)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_enqueuedMsgsMutex);
    while (!m_enqueuedMsgs.empty()) {
        m_enqueuedMsgs.front()->clearTransaction();
        m_enqueuedMsgs.pop_front();
    }
//std::cout << "~~~~~ Transaction xid=\"" << getXid() << "\": prepareComplete()" << std::endl;
}


// protected
void
MockTransactionContext::abortComplete(const TransactionContext* /*tc*/)
{
//std::cout << "~~~~~ Transaction xid=\"" << getXid() << "\": abortComplete()" << std::endl;
}


// protected
void
MockTransactionContext::commitComplete(const TransactionContext* /*tc*/)
{
//std::cout << "~~~~~ Transaction xid=\"" << getXid() << "\": commitComplete()" << std::endl;
}

}}} // namespace tests::storePerftools::asyncPerf
