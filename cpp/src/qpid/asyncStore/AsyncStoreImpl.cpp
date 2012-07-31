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
 * \file AsyncStoreImpl.cpp
 */

#include "AsyncStoreImpl.h"

#include "AsyncOperation.h"
#include "ConfigHandleImpl.h"
#include "EnqueueHandleImpl.h"
#include "EventHandleImpl.h"
#include "MessageHandleImpl.h"
#include "QueueHandleImpl.h"
#include "TxnHandleImpl.h"

#include "qpid/broker/ConfigHandle.h"
#include "qpid/broker/EnqueueHandle.h"
#include "qpid/broker/EventHandle.h"
#include "qpid/broker/MessageHandle.h"
#include "qpid/broker/QueueAsyncContext.h"
#include "qpid/broker/QueueHandle.h"
#include "qpid/broker/TxnAsyncContext.h"
#include "qpid/broker/TxnHandle.h"

namespace qpid {
namespace asyncStore {

AsyncStoreImpl::AsyncStoreImpl(boost::shared_ptr<qpid::sys::Poller> poller,
                               const AsyncStoreOptions& opts) :
        m_poller(poller),
        m_opts(opts),
        m_runState(),
        m_operations(m_poller)
{}

AsyncStoreImpl::~AsyncStoreImpl()
{}

void
AsyncStoreImpl::initialize()
{}

uint64_t
AsyncStoreImpl::getNextRid()
{
    return m_ridCntr.next();
}

void
AsyncStoreImpl::initManagement(qpid::broker::Broker* /*broker*/)
{}

qpid::broker::TxnHandle
AsyncStoreImpl::createTxnHandle()
{
    return qpid::broker::TxnHandle(new TxnHandleImpl);
}

qpid::broker::TxnHandle
AsyncStoreImpl::createTxnHandle(qpid::broker::TxnBuffer* tb)
{
    return qpid::broker::TxnHandle(new TxnHandleImpl(tb));
}

qpid::broker::TxnHandle
AsyncStoreImpl::createTxnHandle(const std::string& xid,
                                const bool tpcFlag)
{
    return qpid::broker::TxnHandle(new TxnHandleImpl(xid, tpcFlag));
}

qpid::broker::TxnHandle
AsyncStoreImpl::createTxnHandle(const std::string& xid,
                                const bool tpcFlag,
                                qpid::broker::TxnBuffer* tb)
{
    return qpid::broker::TxnHandle(new TxnHandleImpl(xid, tpcFlag, tb));
}

void
AsyncStoreImpl::submitPrepare(qpid::broker::TxnHandle& txnHandle,
                              boost::shared_ptr<qpid::broker::TpcTxnAsyncContext> TxnCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpTxnPrepare(txnHandle, TxnCtxt));
    TxnCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitCommit(qpid::broker::TxnHandle& txnHandle,
                             boost::shared_ptr<qpid::broker::TxnAsyncContext> TxnCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpTxnCommit(txnHandle, TxnCtxt));
    TxnCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitAbort(qpid::broker::TxnHandle& txnHandle,
                            boost::shared_ptr<qpid::broker::TxnAsyncContext> TxnCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpTxnAbort(txnHandle, TxnCtxt));
    TxnCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

qpid::broker::ConfigHandle
AsyncStoreImpl::createConfigHandle()
{
    return qpid::broker::ConfigHandle(new ConfigHandleImpl());
}

qpid::broker::EnqueueHandle
AsyncStoreImpl::createEnqueueHandle(qpid::broker::MessageHandle& msgHandle,
                                    qpid::broker::QueueHandle& queueHandle)
{
    return qpid::broker::EnqueueHandle(new EnqueueHandleImpl(msgHandle,
                                                             queueHandle));
}

qpid::broker::EventHandle
AsyncStoreImpl::createEventHandle(qpid::broker::QueueHandle& queueHandle,
                                  const std::string& key)
{
    return qpid::broker::EventHandle(new EventHandleImpl(queueHandle,
                                                         key));
}

qpid::broker::MessageHandle
AsyncStoreImpl::createMessageHandle(const qpid::broker::DataSource* const dataSrc)

{
    return qpid::broker::MessageHandle(new MessageHandleImpl(dataSrc));
}

qpid::broker::QueueHandle
AsyncStoreImpl::createQueueHandle(const std::string& name,
                                  const qpid::types::Variant::Map& opts)
{
    return qpid::broker::QueueHandle(new QueueHandleImpl(name, opts));
}

void
AsyncStoreImpl::submitCreate(qpid::broker::ConfigHandle& cfgHandle,
                             const qpid::broker::DataSource* const dataSrc,
                             boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpConfigCreate(cfgHandle, dataSrc, brokerCtxt));
    brokerCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitDestroy(qpid::broker::ConfigHandle& cfgHandle,
                              boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpConfigDestroy(cfgHandle, brokerCtxt));
    brokerCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitCreate(qpid::broker::QueueHandle& queueHandle,
                             const qpid::broker::DataSource* const dataSrc,
                             boost::shared_ptr<qpid::broker::QueueAsyncContext> QueueCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpQueueCreate(queueHandle, dataSrc, QueueCtxt));
    QueueCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitDestroy(qpid::broker::QueueHandle& queueHandle,
                              boost::shared_ptr<qpid::broker::QueueAsyncContext> QueueCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpQueueDestroy(queueHandle, QueueCtxt));
    QueueCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitFlush(qpid::broker::QueueHandle& queueHandle,
                            boost::shared_ptr<qpid::broker::QueueAsyncContext> QueueCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpQueueFlush(queueHandle, QueueCtxt));
    QueueCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitCreate(qpid::broker::EventHandle& eventHandle,
                             const qpid::broker::DataSource* const dataSrc,
                             qpid::broker::TxnHandle& txnHandle,
                             boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpEventCreate(eventHandle, dataSrc, txnHandle, brokerCtxt));
    brokerCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitDestroy(qpid::broker::EventHandle& eventHandle,
                              qpid::broker::TxnHandle& txnHandle,
                              boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpEventDestroy(eventHandle, txnHandle, brokerCtxt));
    brokerCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitEnqueue(qpid::broker::EnqueueHandle& enqHandle,
                              qpid::broker::TxnHandle& txnHandle,
                              boost::shared_ptr<qpid::broker::QueueAsyncContext> QueueCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpMsgEnqueue(enqHandle, txnHandle, QueueCtxt));
    QueueCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

void
AsyncStoreImpl::submitDequeue(qpid::broker::EnqueueHandle& enqHandle,
                              qpid::broker::TxnHandle& txnHandle,
                              boost::shared_ptr<qpid::broker::QueueAsyncContext> QueueCtxt)
{
    boost::shared_ptr<const AsyncOperation> op(new AsyncOpMsgDequeue(enqHandle, txnHandle, QueueCtxt));
    QueueCtxt->setOpStr(op->getOpStr());
    m_operations.submit(op);
}

int
AsyncStoreImpl::loadContent(qpid::broker::MessageHandle& /*msgHandle*/,
                            qpid::broker::QueueHandle& /*queueHandle*/,
                            char* /*data*/,
                            uint64_t /*offset*/,
                            const uint64_t /*length*/)
{
    return 0;
}

}} // namespace qpid::asyncStore
