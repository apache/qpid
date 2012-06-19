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
#include "AsyncStore.h"
#include "TxnAsyncContext.h"
#include "TxnOp.h"

#include "qpid/Exception.h"

#include <boost/shared_ptr.hpp>

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
//std::cout << "TTT TxnBuffer::enlist" << std::endl << std::flush;
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    m_ops.push_back(op);
}

bool
TxnBuffer::prepare(TxnHandle& th)
{
//std::cout << "TTT TxnBuffer::prepare" << std::endl << std::flush;
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
//std::cout << "TTT TxnBuffer::commit" << std::endl << std::flush;
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        (*i)->commit();
    }
    m_ops.clear();
}

void
TxnBuffer::rollback()
{
//std::cout << "TTT TxnBuffer::rollback" << std::endl << std::flush;
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_opsMutex);
    for(std::vector<boost::shared_ptr<TxnOp> >::iterator i = m_ops.begin(); i != m_ops.end(); ++i) {
        (*i)->rollback();
    }
    m_ops.clear();
}

bool
TxnBuffer::commitLocal(AsyncTransactionalStore* const store)
{
//std::cout << "TTT TxnBuffer::commitLocal" << std::endl << std::flush;
    if (store) {
        try {
            m_store = store;
            asyncLocalCommit();
        } catch (std::exception& e) {
            std::cerr << "Commit failed: " << e.what() << std::endl;
        } catch (...) {
            std::cerr << "Commit failed (unknown exception)" << std::endl;
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
            tac->getTxnBuffer()->asyncLocalAbort();
            std::cerr << "Transaction xid=\"" << tac->getTransactionContext().getXid() << "\": Operation " << tac->getOpStr() << ": failure "
                      << arh->getErrNo() << " (" << arh->getErrMsg() << ")" << std::endl;
            tac->getTxnBuffer()->asyncLocalAbort();
        } else {
//std::cout << "TTT TxnBuffer::handleAsyncResult() op=" << tac->getOpStr() << std::endl << std::flush;
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
//std::cout << "TTT TxnBuffer::asyncLocalCommit: NONE->PREPARE" << std::endl << std::flush;
        m_state = PREPARE;
        m_txnHandle = m_store->createTxnHandle(this);
        prepare(m_txnHandle);
        break;
    case PREPARE:
//std::cout << "TTT TxnBuffer::asyncLocalCommit: PREPARE->COMMIT" << std::endl << std::flush;
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
//std::cout << "TTT TxnBuffer:asyncLocalCommit: COMMIT->COMPLETE" << std::endl << std::flush;
        commit();
        m_state = COMPLETE;
        //delete this; // TODO: ugly! Find a better way to handle the life cycle of this class
        break;
    default: ;
//std::cout << "TTT TxnBuffer:asyncLocalCommit: Unexpected state " << m_state << std::endl << std::flush;
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
//std::cout << "TTT TxnBuffer::asyncRollback: xxx->ROLLBACK" << std::endl << std::flush;
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
//std::cout << "TTT TxnBuffer:asyncRollback: ROLLBACK->COMPLETE" << std::endl << std::flush;
        rollback();
        m_state = COMPLETE;
        //delete this; // TODO: ugly! Find a better way to handle the life cycle of this class
    default: ;
//std::cout << "TTT TxnBuffer:asyncRollback: Unexpected state " << m_state << std::endl << std::flush;
    }
}

// for debugging
/*
void
TxnBuffer::printState(std::ostream& os)
{
    os << "state=";
    switch(m_state) {
    case NONE: os << "NONE"; break;
    case PREPARE: os << "PREPARE"; break;
    case COMMIT: os << "COMMIT"; break;
    case ROLLBACK: os << "ROLLBACK"; break;
    case COMPLETE: os << "COMPLETE"; break;
    default: os << m_state << "(unknown)";
    }
    os << "; " << m_ops.size() << "; store=" << m_store;
}
*/

}} // namespace qpid::broker
