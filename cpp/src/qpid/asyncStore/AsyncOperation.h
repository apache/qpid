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
 * \file AsyncOperation.h
 */

#ifndef qpid_asyncStore_AsyncOperation_h_
#define qpid_asyncStore_AsyncOperation_h_

#include "qpid/broker/AsyncStore.h"

#include <boost/shared_ptr.hpp>

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;

class AsyncOperation {
public:
    AsyncOperation(boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                   qpid::broker::AsyncStore* store);
    virtual ~AsyncOperation();
    virtual void executeOp() const = 0;
    boost::shared_ptr<qpid::broker::BrokerAsyncContext> getBrokerContext() const;
    virtual const char* getOpStr() const = 0;
protected:
    void submitResult() const;
    void submitResult(const int errNo,
                      const std::string& errMsg) const;
private:
    boost::shared_ptr<qpid::broker::BrokerAsyncContext> const m_brokerCtxt;
protected:
    qpid::broker::AsyncStore* m_store;
};


class AsyncOpTxnPrepare: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpTxnPrepare(qpid::broker::TxnHandle& txnHandle,
                      boost::shared_ptr<qpid::broker::TpcTxnAsyncContext> txnCtxt,
                      qpid::broker::AsyncStore* store);
    virtual ~AsyncOpTxnPrepare();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::TxnHandle& m_txnHandle;
};


class AsyncOpTxnCommit: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpTxnCommit(qpid::broker::TxnHandle& txnHandle,
                     boost::shared_ptr<qpid::broker::TxnAsyncContext> txnCtxt,
                     qpid::broker::AsyncStore* store);
    virtual ~AsyncOpTxnCommit();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::TxnHandle& m_txnHandle;
};


class AsyncOpTxnAbort: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpTxnAbort(qpid::broker::TxnHandle& txnHandle,
                    boost::shared_ptr<qpid::broker::TxnAsyncContext> txnCtxt,
                    qpid::broker::AsyncStore* store);
    virtual ~AsyncOpTxnAbort();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::TxnHandle& m_txnHandle;
};


class AsyncOpRecover: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpRecover(qpid::broker::RecoveryHandle& rcvrHandle,
                   boost::shared_ptr<qpid::broker::RecoveryAsyncContext> rcvrCtxt,
                   qpid::broker::AsyncStore* store);
    virtual ~AsyncOpRecover();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::RecoveryHandle& m_rcvrHandle;
};

class AsyncOpConfigCreate: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpConfigCreate(qpid::broker::ConfigHandle& cfgHandle,
                        const qpid::broker::DataSource* const data,
                        boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                        qpid::broker::AsyncStore* store);
    virtual ~AsyncOpConfigCreate();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::ConfigHandle& m_cfgHandle;
    const qpid::broker::DataSource* const m_data;
};


class AsyncOpConfigDestroy: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpConfigDestroy(qpid::broker::ConfigHandle& cfgHandle,
                         boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                         qpid::broker::AsyncStore* store);
    virtual ~AsyncOpConfigDestroy();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::ConfigHandle& m_cfgHandle;
};


class AsyncOpQueueCreate: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpQueueCreate(qpid::broker::QueueHandle& queueHandle,
                       const qpid::broker::DataSource* const data,
                       boost::shared_ptr<qpid::broker::QueueAsyncContext> queueCtxt,
                       qpid::broker::AsyncStore* store);
    virtual ~AsyncOpQueueCreate();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::QueueHandle& m_queueHandle;
    const qpid::broker::DataSource* const m_data;
};


class AsyncOpQueueFlush: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpQueueFlush(qpid::broker::QueueHandle& queueHandle,
                      boost::shared_ptr<qpid::broker::QueueAsyncContext> queueCtxt,
                      qpid::broker::AsyncStore* store);
    virtual ~AsyncOpQueueFlush();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::QueueHandle& m_queueHandle;
};


class AsyncOpQueueDestroy: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpQueueDestroy(qpid::broker::QueueHandle& queueHandle,
                      boost::shared_ptr<qpid::broker::QueueAsyncContext> queueCtxt,
                      qpid::broker::AsyncStore* store);
    virtual ~AsyncOpQueueDestroy();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::QueueHandle& m_queueHandle;
};


class AsyncOpEventCreate: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpEventCreate(qpid::broker::EventHandle& evtHandle,
                       const qpid::broker::DataSource* const data,
                       qpid::broker::TxnHandle& txnHandle,
                       boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                       qpid::broker::AsyncStore* store);
    virtual ~AsyncOpEventCreate();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::EventHandle& m_evtHandle;
    const qpid::broker::DataSource* const m_data;
    qpid::broker::TxnHandle& m_txnHandle;
};


class AsyncOpEventDestroy: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpEventDestroy(qpid::broker::EventHandle& evtHandle,
                        qpid::broker::TxnHandle& txnHandle,
                        boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                        qpid::broker::AsyncStore* store);
    virtual ~AsyncOpEventDestroy();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::EventHandle& m_evtHandle;
    qpid::broker::TxnHandle& m_txnHandle;
};


class AsyncOpMsgEnqueue: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpMsgEnqueue(qpid::broker::EnqueueHandle& enqHandle,
                      qpid::broker::TxnHandle& txnHandle,
                      boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                      qpid::broker::AsyncStore* store);
    virtual ~AsyncOpMsgEnqueue();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::EnqueueHandle& m_enqHandle;
    qpid::broker::TxnHandle& m_txnHandle;
};


class AsyncOpMsgDequeue: public qpid::asyncStore::AsyncOperation {
public:
    AsyncOpMsgDequeue(qpid::broker::EnqueueHandle& enqHandle,
                      qpid::broker::TxnHandle& txnHandle,
                      boost::shared_ptr<qpid::broker::BrokerAsyncContext> brokerCtxt,
                      qpid::broker::AsyncStore* store);
    virtual ~AsyncOpMsgDequeue();
    virtual void executeOp() const;
    virtual const char* getOpStr() const;
private:
    qpid::broker::EnqueueHandle& m_enqHandle;
    qpid::broker::TxnHandle& m_txnHandle;
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_AsyncOperation_h_
