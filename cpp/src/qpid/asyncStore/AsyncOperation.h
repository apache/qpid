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
#include "qpid/broker/IdHandle.h"

namespace qpid {
namespace asyncStore {

class AsyncOperation {
public:
    typedef enum {NONE=0,
                  TXN_PREPARE,
                  TXN_COMMIT,
                  TXN_ABORT,
                  CONFIG_CREATE,
                  CONFIG_DESTROY,
                  QUEUE_CREATE,
                  QUEUE_FLUSH,
                  QUEUE_DESTROY,
                  EVENT_CREATE,
                  EVENT_DESTROY,
                  MSG_ENQUEUE,
                  MSG_DEQUEUE
    } opCode;

    AsyncOperation();
    AsyncOperation(const opCode op,
                   const qpid::broker::IdHandle* th,
                   const qpid::broker::ResultCallback resCb,
                   qpid::broker::BrokerAsyncContext* brokerCtxt);
    AsyncOperation(const opCode op,
                   const qpid::broker::IdHandle* th,
                   const qpid::broker::DataSource* dataSrc,
                   const qpid::broker::ResultCallback resCb,
                   qpid::broker::BrokerAsyncContext* brokerCtxt);
    AsyncOperation(const opCode op,
                   const qpid::broker::IdHandle* th,
                   const qpid::broker::TxnHandle* txnHandle,
                   const qpid::broker::ResultCallback resCb,
                   qpid::broker::BrokerAsyncContext* brokerCtxt);
    AsyncOperation(const opCode op,
                   const qpid::broker::IdHandle* th,
                   const qpid::broker::DataSource* dataSrc,
                   const qpid::broker::TxnHandle* txnHandle,
                   const qpid::broker::ResultCallback resCb,
                   qpid::broker::BrokerAsyncContext* brokerCtxt);
    virtual ~AsyncOperation();
    const char* getOpStr() const;
    static const char* getOpStr(const opCode op);

    opCode m_op;
    const qpid::broker::IdHandle* m_targetHandle;
    const qpid::broker::DataSource* m_dataSrc;
    const qpid::broker::TxnHandle* m_txnHandle;
    const qpid::broker::ResultCallback m_resCb;
    qpid::broker::BrokerAsyncContext* m_brokerCtxt;
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_AsyncOperation_h_
