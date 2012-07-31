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
 * \file TxnBuffer.cpp
 */

#include "TxnBuffer.h"

#include "AsyncResultHandle.h"
#include "TxnAsyncContext.h"
#include "TxnOp.h"

#include "qpid/log/Statement.h"

#include <uuid/uuid.h>

namespace qpid {
namespace broker {

qpid::sys::Mutex TxnBuffer::s_uuidMutex;

TxnBuffer::TxnBuffer(AsyncResultQueue& arq) :
        m_store(0),
        m_resultQueue(arq),
        m_tpcFlag(false),
        m_submitOpCnt(0),
        m_completeOpCnt(0),
        m_state(NONE)
{
    createLocalXid();
}

TxnBuffer::TxnBuffer(AsyncResultQueue& arq, std::string& xid) :
        m_store(0),
        m_resultQueue(arq),
        m_xid(xid),
        m_tpcFlag(!xid.empty()),
        m_submitOpCnt(0),
        m_completeOpCnt(0),
        m_state(NONE)
{
    if (m_xid.empty()) {
        createLocalXid();
    }
}

TxnBuffer::~TxnBuffer() {}

TxnHandle&
TxnBuffer::getTxnHandle() {
    return m_txnHandle;
}

const std::string&
TxnBuffer::getXid() const {
    return m_xid;
}

bool
TxnBuffer::is2pc() const {
    return m_tpcFlag;
}

void
TxnBuffer::incrOpCnt() {
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_submitOpCntMutex);
    ++m_submitOpCnt;
}

void
TxnBuffer::decrOpCnt() {
    const uint32_t numOps = getNumOps();
    qpid::sys::ScopedLock<qpid::sys::Mutex> l2(m_completeOpCntMutex);
    qpid::sys::ScopedLock<qpid::sys::Mutex> l3(m_submitOpCntMutex);
    if (m_completeOpCnt == m_submitOpCnt) {
        throw qpid::Exception("Transaction async operation count underflow");
    }
    ++m_completeOpCnt;
    if (numOps == m_submitOpCnt && numOps == m_completeOpCnt) {
        asyncLocalCommit();
    }
}

void
TxnBuffer::enlist(boost::shared_ptr<TxnOp> op) {
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    m_ops.push_back(op);
}

bool
TxnBuffer::prepare() {
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        if (!(*i)->prepare(this)) {
            return false;
        }
    }
    return true;
}

void
TxnBuffer::commit() {
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        (*i)->commit();
    }
    m_ops.clear();
}

void
TxnBuffer::rollback() {
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        (*i)->rollback();
    }
    m_ops.clear();
}

bool
TxnBuffer::commitLocal(AsyncTransactionalStore* const store) {
    try {
        m_store = store;
        asyncLocalCommit();
    } catch (std::exception& e) {
        QPID_LOG(error, "TxnBuffer::commitLocal: Commit failed: " << e.what());
    } catch (...) {
        QPID_LOG(error, "TxnBuffer::commitLocal: Commit failed (unknown exception)");
    }
    return false;
}

void
TxnBuffer::asyncLocalCommit() {
    switch(m_state) {
    case NONE:
        m_state = PREPARE;
        if (m_store) {
            m_txnHandle = m_store->createTxnHandle(this);
        }
        prepare(/*shared_from_this()*/);
        if (m_store) {
            break;
        }
    case PREPARE:
        m_state = COMMIT;
        if (m_store) {
            boost::shared_ptr<TxnAsyncContext> tac(new TxnAsyncContext(this,
                                                                       &handleAsyncCommitResult,
                                                                       &m_resultQueue));
            m_store->testOp();
            m_store->submitCommit(m_txnHandle, tac);
            break;
        }
    case COMMIT:
        commit();
        m_state = COMPLETE;
        delete this;
        break;
    case COMPLETE:
    default: ;
    }
}

//static
void
TxnBuffer::handleAsyncCommitResult(const AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<TxnAsyncContext> tac = boost::dynamic_pointer_cast<TxnAsyncContext>(arh->getBrokerAsyncContext());
        if (arh->getErrNo()) {
            QPID_LOG(error, "TxnBuffer::handleAsyncCommitResult: Transactional operation " << tac->getOpStr() << " failed: err=" << arh->getErrNo()
                    << " (" << arh->getErrMsg() << ")");
            tac->getTxnBuffer()->asyncLocalAbort();
        } else {
            tac->getTxnBuffer()->asyncLocalCommit();
        }
    }
}

void
TxnBuffer::asyncLocalAbort() {
    assert(m_store != 0);
    switch (m_state) {
    case NONE:
    case PREPARE:
    case COMMIT:
        m_state = ROLLBACK;
        {
            boost::shared_ptr<TxnAsyncContext> tac(new TxnAsyncContext(this,
                                                                       &handleAsyncAbortResult,
                                                                       &m_resultQueue));
            m_store->submitCommit(m_txnHandle, tac);
        }
        break;
    case ROLLBACK:
        rollback();
        m_state = COMPLETE;
        delete this;
    default: ;
    }
}

//static
void
TxnBuffer::handleAsyncAbortResult(const AsyncResultHandle* const arh) {
    if (arh) {
        boost::shared_ptr<TxnAsyncContext> tac = boost::dynamic_pointer_cast<TxnAsyncContext>(arh->getBrokerAsyncContext());
        if (arh->getErrNo()) {
            QPID_LOG(error, "TxnBuffer::handleAsyncAbortResult: Transactional operation " << tac->getOpStr()
                     << " failed: err=" << arh->getErrNo() << " (" << arh->getErrMsg() << ")");
        }
        tac->getTxnBuffer()->asyncLocalAbort();
    }
}

// private
uint32_t
TxnBuffer::getNumOps() const {
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    return m_ops.size();
}

// private
void
TxnBuffer::createLocalXid()
{
    uuid_t uuid;
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(s_uuidMutex);
        ::uuid_generate_random(uuid); // Not thread-safe
    }
    char uuidStr[37]; // 36-char uuid + trailing '\0'
    ::uuid_unparse(uuid, uuidStr);
    m_xid.assign(uuidStr);
    QPID_LOG(debug, "Local XID created: \"" << m_xid << "\"");
}

}} // namespace qpid::broker
