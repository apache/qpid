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
 * \file AsyncOperation.cpp
 */

#include "qpid/asyncStore/AsyncOperation.h"

#include "qpid/broker/AsyncResultHandle.h"
#include "qpid/broker/AsyncResultHandleImpl.h"
#include "qpid/broker/QueueAsyncContext.h"
#include "qpid/broker/TxnAsyncContext.h"

namespace qpid {
namespace asyncStore {

AsyncOperation::AsyncOperation(boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                               qpid::broker::AsyncStore* store) :
        m_brokerCtxt(brokerCtxt),
        m_store(store)
{}

AsyncOperation::~AsyncOperation() {}

boost::shared_ptr<qpid::broker::BrokerAsyncContext> AsyncOperation::getBrokerContext() const {
    return m_brokerCtxt;
}

void
AsyncOperation::submitResult() const {
    return submitResult(0, "");
}

void
AsyncOperation::submitResult(const int errNo,
                             const std::string& errMsg) const {
    if (m_brokerCtxt.get()) {
        qpid::broker::AsyncResultQueue* const arq = m_brokerCtxt->getAsyncResultQueue();
        if (arq) {
            qpid::broker::AsyncResultHandleImpl* arhi = new qpid::broker::AsyncResultHandleImpl(errNo, errMsg, m_brokerCtxt);
            boost::shared_ptr<qpid::broker::AsyncResultHandle> arh(new qpid::broker::AsyncResultHandle(arhi));
            arq->submit(arh);
        }
    }
}


// --- class AsyncOpTxnPrepare ---

AsyncOpTxnPrepare::AsyncOpTxnPrepare(qpid::broker::TxnHandle& txnHandle,
                                     boost::shared_ptr<qpid::broker::TpcTxnAsyncContext> txnCtxt,
                                     qpid::broker::AsyncStore* store) :
        AsyncOperation(boost::dynamic_pointer_cast<qpid::broker::BrokerAsyncContext>(txnCtxt), store),
        m_txnHandle(txnHandle)
{}

AsyncOpTxnPrepare::~AsyncOpTxnPrepare() {}

void
AsyncOpTxnPrepare::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpTxnPrepare::getOpStr() const {
    return "TXN_PREPARE";
}



// --- class AsyncOpTxnCommit ---

AsyncOpTxnCommit::AsyncOpTxnCommit(qpid::broker::TxnHandle& txnHandle,
                                   boost::shared_ptr<qpid::broker::TxnAsyncContext> txnCtxt,
                                   qpid::broker::AsyncStore* store) :
        AsyncOperation(boost::dynamic_pointer_cast<qpid::broker::BrokerAsyncContext>(txnCtxt), store),
        m_txnHandle(txnHandle)
{}

AsyncOpTxnCommit::~AsyncOpTxnCommit() {}

void
AsyncOpTxnCommit::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpTxnCommit::getOpStr() const {
    return "TXN_COMMIT";
}


// --- class AsyncOpTxnAbort ---

AsyncOpTxnAbort::AsyncOpTxnAbort(qpid::broker::TxnHandle& txnHandle,
                                 boost::shared_ptr<qpid::broker::TxnAsyncContext> txnCtxt,
                                 qpid::broker::AsyncStore* store) :
        AsyncOperation(boost::dynamic_pointer_cast<qpid::broker::BrokerAsyncContext>(txnCtxt), store),
        m_txnHandle(txnHandle)
{}

AsyncOpTxnAbort::~AsyncOpTxnAbort() {}

void
AsyncOpTxnAbort::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpTxnAbort::getOpStr() const {
    return "TXN_ABORT";
}


// --- class AsyncOpConfigCreate ---

AsyncOpConfigCreate::AsyncOpConfigCreate(qpid::broker::ConfigHandle& cfgHandle,
                                         const qpid::broker::DataSource* const data,
                                         boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                                         qpid::broker::AsyncStore* store) :
        AsyncOperation(brokerCtxt, store),
        m_cfgHandle(cfgHandle),
        m_data(data)
{}

AsyncOpConfigCreate::~AsyncOpConfigCreate() {}

void
AsyncOpConfigCreate::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpConfigCreate::getOpStr() const {
    return "CONFIG_CREATE";
}


// --- class AsyncOpConfigDestroy ---

AsyncOpConfigDestroy::AsyncOpConfigDestroy(qpid::broker::ConfigHandle& cfgHandle,
                                           boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                                           qpid::broker::AsyncStore* store) :
        AsyncOperation(brokerCtxt, store),
        m_cfgHandle(cfgHandle)
{}

AsyncOpConfigDestroy::~AsyncOpConfigDestroy() {}

void
AsyncOpConfigDestroy::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpConfigDestroy::getOpStr() const {
    return "CONFIG_DESTROY";
}


// --- class AsyncOpQueueCreate ---

AsyncOpQueueCreate::AsyncOpQueueCreate(qpid::broker::QueueHandle& queueHandle,
                                       const qpid::broker::DataSource* const data,
                                       boost::shared_ptr<qpid::broker::QueueAsyncContext> queueCtxt,
                                       qpid::broker::AsyncStore* store) :
        AsyncOperation(boost::dynamic_pointer_cast<qpid::broker::BrokerAsyncContext>(queueCtxt), store),
        m_queueHandle(queueHandle),
        m_data(data)
{}

AsyncOpQueueCreate::~AsyncOpQueueCreate() {}

void
AsyncOpQueueCreate::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpQueueCreate::getOpStr() const {
    return "QUEUE_CREATE";
}


// --- class AsyncOpQueueFlush ---

AsyncOpQueueFlush::AsyncOpQueueFlush(qpid::broker::QueueHandle& queueHandle,
                                     boost::shared_ptr<qpid::broker::QueueAsyncContext> queueCtxt,
                                     qpid::broker::AsyncStore* store) :
        AsyncOperation(boost::dynamic_pointer_cast<qpid::broker::BrokerAsyncContext>(queueCtxt), store),
        m_queueHandle(queueHandle)
{}

AsyncOpQueueFlush::~AsyncOpQueueFlush() {}

void
AsyncOpQueueFlush::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpQueueFlush::getOpStr() const {
    return "QUEUE_FLUSH";
}


// --- class AsyncOpQueueDestroy ---

AsyncOpQueueDestroy::AsyncOpQueueDestroy(qpid::broker::QueueHandle& queueHandle,
                                         boost::shared_ptr<qpid::broker::QueueAsyncContext> queueCtxt,
                                         qpid::broker::AsyncStore* store) :
        AsyncOperation(boost::dynamic_pointer_cast<qpid::broker::BrokerAsyncContext>(queueCtxt), store),
        m_queueHandle(queueHandle)
{}

AsyncOpQueueDestroy::~AsyncOpQueueDestroy() {}

void
AsyncOpQueueDestroy::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpQueueDestroy::getOpStr() const {
    return "QUEUE_DESTROY";
}


// --- class AsyncOpEventCreate ---

AsyncOpEventCreate::AsyncOpEventCreate(qpid::broker::EventHandle& evtHandle,
                   const qpid::broker::DataSource* const data,
                   qpid::broker::TxnHandle& txnHandle,
                   boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                   qpid::broker::AsyncStore* store) :
        AsyncOperation(brokerCtxt, store),
        m_evtHandle(evtHandle),
        m_data(data),
        m_txnHandle(txnHandle)
{}

AsyncOpEventCreate::~AsyncOpEventCreate() {}

void
AsyncOpEventCreate::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpEventCreate::getOpStr() const {
    return "EVENT_CREATE";
}


// --- class AsyncOpEventDestroy ---

AsyncOpEventDestroy::AsyncOpEventDestroy(qpid::broker::EventHandle& evtHandle,
                    qpid::broker::TxnHandle& txnHandle,
                    boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                    qpid::broker::AsyncStore* store) :
        AsyncOperation(brokerCtxt, store),
        m_evtHandle(evtHandle),
        m_txnHandle(txnHandle)
{}

AsyncOpEventDestroy::~AsyncOpEventDestroy() {}

void
AsyncOpEventDestroy::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char*
AsyncOpEventDestroy::getOpStr() const {
    return "EVENT_DESTROY";
}


// --- class AsyncOpMsgEnqueue ---

AsyncOpMsgEnqueue::AsyncOpMsgEnqueue(qpid::broker::EnqueueHandle& enqHandle,
                  qpid::broker::TxnHandle& txnHandle,
                  boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                  qpid::broker::AsyncStore* store) :
        AsyncOperation(brokerCtxt, store),
        m_enqHandle(enqHandle),
        m_txnHandle(txnHandle)
{}

AsyncOpMsgEnqueue::~AsyncOpMsgEnqueue() {}

void AsyncOpMsgEnqueue::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char* AsyncOpMsgEnqueue::getOpStr() const {
    return "MSG_ENQUEUE";
}


// --- class AsyncOpMsgDequeue ---

AsyncOpMsgDequeue::AsyncOpMsgDequeue(qpid::broker::EnqueueHandle& enqHandle,
                  qpid::broker::TxnHandle& txnHandle,
                  boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                  qpid::broker::AsyncStore* store) :
        AsyncOperation(brokerCtxt, store),
        m_enqHandle(enqHandle),
        m_txnHandle(txnHandle)
{}

AsyncOpMsgDequeue::~AsyncOpMsgDequeue() {}

void AsyncOpMsgDequeue::executeOp() const {
    // TODO: Implement store operation here
    submitResult();
}

const char* AsyncOpMsgDequeue::getOpStr() const {
    return "MSG_DEQUEUE";
}

}} // namespace qpid::asyncStore
