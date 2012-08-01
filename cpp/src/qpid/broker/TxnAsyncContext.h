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
 * \file TxnAsyncContext.h
 */

#ifndef qpid_broker_TxnAsyncContext_h_
#define qpid_broker_TxnAsyncContext_h_

#include "AsyncStore.h" // qpid::broker::BrokerAsyncContext

#include "qpid/asyncStore/AsyncOperation.h"

namespace qpid {
namespace broker {

class AsyncResultHandle;
class AsyncResultQueue;

typedef void (*AsyncResultCallback)(const AsyncResultHandle* const);

class TxnAsyncContext: public BrokerAsyncContext {
public:
    TxnAsyncContext(SimpleTxnBuffer* const tb,
                    AsyncResultCallback rcb,
                    AsyncResultQueue* const arq);
    virtual ~TxnAsyncContext();
    SimpleTxnBuffer* getTxnBuffer() const;

    // --- Interface BrokerAsyncContext ---
    AsyncResultQueue* getAsyncResultQueue() const;
    void invokeCallback(const AsyncResultHandle* const) const;

private:
    SimpleTxnBuffer* const m_tb;
    AsyncResultCallback m_rcb;
    AsyncResultQueue* const m_arq;
};

class TpcTxnAsyncContext : public TxnAsyncContext {
public:
    TpcTxnAsyncContext(SimpleTxnBuffer* const tb,
                       AsyncResultCallback rcb,
                       AsyncResultQueue* const arq) :
        TxnAsyncContext(tb, rcb, arq)
    {}
    virtual ~TpcTxnAsyncContext() {}
};

}} // namespace qpid::broker

#endif // qpid_broker_TxnAsyncContext_h_
