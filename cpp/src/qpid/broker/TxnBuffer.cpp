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

namespace qpid {
namespace broker {

TxnBuffer::TxnBuffer(AsyncResultQueue& arq) :
        m_store(0),
        m_resultQueue(arq),
        m_state(NONE)
{}

TxnBuffer::~TxnBuffer()
{}

void
TxnBuffer::enlist(boost::shared_ptr<TxnOp> op)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    m_ops.push_back(op);
}

bool
TxnBuffer::prepare(TxnHandle& th)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        if (!(*i)->prepare(th)) {
            return false;
        }
    }
    return true;
}

void
TxnBuffer::commit()
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        (*i)->commit();
    }
    m_ops.clear();
}

void
TxnBuffer::rollback()
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        (*i)->rollback();
    }
    m_ops.clear();
}

bool
TxnBuffer::commitLocal(AsyncTransaction* const store)
{
    if (store) {
        try {
            m_store = store;
            asyncLocalCommit();
        } catch (std::exception& e) {
            QPID_LOG(error, "TxnBuffer::commitLocal: Commit failed: " << e.what());
        } catch (...) {
            QPID_LOG(error, "TxnBuffer::commitLocal: Commit failed (unknown exception)");
        }
    }
    return false;
}

// static
void
TxnBuffer::handleAsyncResult(const AsyncResultHandle* const arh)
{
    if (arh) {
        boost::shared_ptr<TxnAsyncContext> tac = boost::dynamic_pointer_cast<TxnAsyncContext>(arh->getBrokerAsyncContext());
        if (arh->getErrNo()) {
            QPID_LOG(error, "TxnBuffer::handleAsyncResult: Transactional operation " << tac->getOpStr() << " failed: err=" << arh->getErrNo()
                    << " (" << arh->getErrMsg() << ")");
            tac->getTxnBuffer()->asyncLocalAbort();
        } else {
            if (tac->getOpCode() == qpid::asyncStore::AsyncOperation::TXN_ABORT) {
                tac->getTxnBuffer()->asyncLocalAbort();
            } else {
                tac->getTxnBuffer()->asyncLocalCommit();
            }
        }
    }
}

void
TxnBuffer::asyncLocalCommit()
{
    assert(m_store != 0);
    switch(m_state) {
    case NONE:
        m_state = PREPARE;
        m_txnHandle = m_store->createTxnHandle(this);
        prepare(m_txnHandle);
        break;
    case PREPARE:
        m_state = COMMIT;
        {
            boost::shared_ptr<TxnAsyncContext> tac(new TxnAsyncContext(this,
                                                                       m_txnHandle,
                                                                       qpid::asyncStore::AsyncOperation::TXN_COMMIT,
                                                                       &handleAsyncResult,
                                                                       &m_resultQueue));
            m_store->submitCommit(m_txnHandle, tac);
        }
        break;
    case COMMIT:
        commit();
        m_state = COMPLETE;
        delete this; // TODO: ugly! Find a better way to handle the life cycle of this class
        break;
    case COMPLETE:
    default: ;
    }
}

void
TxnBuffer::asyncLocalAbort()
{
    assert(m_store != 0);
    switch (m_state) {
    case NONE:
    case PREPARE:
    case COMMIT:
        m_state = ROLLBACK;
        {
            boost::shared_ptr<TxnAsyncContext> tac(new TxnAsyncContext(this,
                                                                       m_txnHandle,
                                                                       qpid::asyncStore::AsyncOperation::TXN_ABORT,
                                                                       &handleAsyncResult,
                                                                       &m_resultQueue));
            m_store->submitCommit(m_txnHandle, tac);
        }
        break;
    case ROLLBACK:
        rollback();
        m_state = COMPLETE;
        delete this; // TODO: ugly! Find a better way to handle the life cycle of this class
    default: ;
    }
}

}} // namespace qpid::broker
