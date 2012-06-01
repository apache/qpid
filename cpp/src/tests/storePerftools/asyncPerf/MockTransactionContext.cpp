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

#include "TransactionAsyncContext.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

#include <uuid/uuid.h>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MockTransactionContext::MockTransactionContext(const std::string& xid) :
        qpid::broker::TransactionContext(),
        m_xid(xid),
        m_tpcFlag(!xid.empty()),
        m_store(0),
        m_txnHandle(0),
        m_prepared(false),
        m_enqueuedMsgs()
{
    if (!m_tpcFlag) {
        setLocalXid();
    }
//std::cout << "*TXN* begin: xid=" << getXid() << "; 2PC=" << (is2pc()?"T":"F") << std::endl;
}

MockTransactionContext::MockTransactionContext(qpid::asyncStore::AsyncStoreImpl* store,
                                               const std::string& xid) :
        m_store(store),
        m_prepared(false),
        m_enqueuedMsgs()
{
//std::cout << "*TXN* begin: xid=" << getXid() << "; 2PC=" << (is2pc()?"T":"F") << std::endl;
    if (m_store != 0) {
        m_txnHandle = store->createTxnHandle(xid);
    }
}

MockTransactionContext::~MockTransactionContext()
{}

// static
void
MockTransactionContext::handleAsyncResult(const qpid::broker::AsyncResult* res,
                                          qpid::broker::BrokerAsyncContext* bc)
{
    if (bc && res) {
        TransactionAsyncContext* tac = dynamic_cast<TransactionAsyncContext*>(bc);
        if (res->errNo) {
            // TODO: Handle async failure here
            std::cerr << "Transaction xid=\"" << tac->getTransactionContext()->getXid() << "\": Operation " << tac->getOpStr() << ": failure "
                      << res->errNo << " (" << res->errMsg << ")" << std::endl;
        } else {
            // Handle async success here
            switch(tac->getOpCode()) {
            case qpid::asyncStore::AsyncOperation::TXN_PREPARE:
                tac->getTransactionContext()->prepareComplete(tac);
                break;
            case qpid::asyncStore::AsyncOperation::TXN_COMMIT:
                tac->getTransactionContext()->commitComplete(tac);
                break;
            case qpid::asyncStore::AsyncOperation::TXN_ABORT:
                tac->getTransactionContext()->abortComplete(tac);
                break;
            default:
                std::ostringstream oss;
                oss << "tests::storePerftools::asyncPerf::MockTransactionContext::handleAsyncResult(): Unknown async operation: " << tac->getOpCode();
                throw qpid::Exception(oss.str());
            };
        }
    }
    if (bc) delete bc;
    if (res) delete res;
}

const qpid::broker::TxnHandle&
MockTransactionContext::getHandle() const
{
    return m_txnHandle;
}

qpid::broker::TxnHandle&
MockTransactionContext::getHandle()
{
    return m_txnHandle;
}

bool
MockTransactionContext::is2pc() const
{
    return m_tpcFlag;
}

const std::string&
MockTransactionContext::getXid() const
{
    return m_xid;
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
    if (m_tpcFlag) {
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
    if (m_store != 0) {
//        m_store->submitAbort(m_txnHandle,
//                             &handleAsyncResult,
//                             dynamic_cast<qpid::broker::BrokerAsyncContext*>(new TransactionAsyncContext(this, qpid::asyncStore::AsyncOperation::TXN_ABORT)));
    }
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
    if (m_store != 0) {
//        m_store->submitCommit(m_txnHandle,
//                              &handleAsyncResult,
//                              dynamic_cast<qpid::broker::BrokerAsyncContext*>(new TransactionAsyncContext(this, qpid::asyncStore::AsyncOperation::TXN_COMMIT)));
    }
//std::cout << "*TXN* commit: xid=" << m_txnHandle.getXid() << "; 2PC=" << (m_txnHandle.is2pc()?"T":"F") << std::endl;
}


// protected
void
MockTransactionContext::localPrepare()
{
    if (m_store != 0) {
//        m_store->submitPrepare(m_txnHandle,
//                               &handleAsyncResult,
//                               dynamic_cast<qpid::broker::BrokerAsyncContext*>(new TransactionAsyncContext(this, qpid::asyncStore::AsyncOperation::TXN_PREPARE)));
    }
//std::cout << "*TXN* localPrepare: xid=" << m_txnHandle.getXid() << "; 2PC=" << (m_txnHandle.is2pc()?"T":"F") << std::endl;
}

// protected
void
MockTransactionContext::setLocalXid()
{
    uuid_t uuid;
    // TODO: Valgrind warning: Possible race condition in uuid_generate_random() - is it thread-safe, and if not, does it matter?
    //       If this race condition affects the randomness of the UUID, then there could be a problem here.
    ::uuid_generate_random(uuid);
    char uuidStr[37]; // 36-char uuid + trailing '\0'
    ::uuid_unparse(uuid, uuidStr);
    m_xid.assign(uuidStr);
}

// protected
void
MockTransactionContext::prepareComplete(const TransactionAsyncContext* /*tc*/)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_enqueuedMsgsMutex);
//    while (!m_enqueuedMsgs.empty()) {
//        m_enqueuedMsgs.front()->clearTransaction();
//        m_enqueuedMsgs.pop_front();
//    }
//std::cout << "~~~~~ Transaction xid=\"" << tc->m_tc->getXid() << "\": prepareComplete()" << std::endl << std::flush;
//    assert(tc->getTransactionContext().get() == this);
}


// protected
void
MockTransactionContext::abortComplete(const TransactionAsyncContext* tc)
{
//std::cout << "~~~~~ Transaction xid=\"" << tc->m_tc->getXid() << "\": abortComplete()" << std::endl << std::flush;
    assert(tc->getTransactionContext().get() == this);
}


// protected
void
MockTransactionContext::commitComplete(const TransactionAsyncContext* tc)
{
//std::cout << "~~~~~ Transaction xid=\"" << tc->m_tc->getXid() << "\": commitComplete()" << std::endl << std::flush;
    assert(tc->getTransactionContext().get() == this);
}

}}} // namespace tests::storePerftools::asyncPerf
