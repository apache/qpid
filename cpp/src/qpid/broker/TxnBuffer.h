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
 * \file TxnBuffer.h
 */

#ifndef qpid_broker_TxnBuffer_h_
#define qpid_broker_TxnBuffer_h_

#include "TxnHandle.h"

//#include <boost/enable_shared_from_this.hpp>
#include <boost/shared_ptr.hpp>
#include <vector>

namespace qpid {
namespace broker {

class AsyncResultHandle;
class AsyncResultQueue;
class AsyncTransactionalStore;
class TxnOp;

class TxnBuffer /*: public boost::enable_shared_from_this<TxnBuffer>*/ {
public:
    TxnBuffer(AsyncResultQueue& arq);
    virtual ~TxnBuffer();

    void enlist(boost::shared_ptr<TxnOp> op);
    bool prepare(TxnHandle& th);
    void commit();
    void rollback();
    bool commitLocal(AsyncTransactionalStore* const store);

    // --- Async operations ---
    static void handleAsyncResult(const AsyncResultHandle* const arh);
    void asyncLocalCommit();
    void asyncLocalAbort();

private:
    std::vector<boost::shared_ptr<TxnOp> > m_ops;
    TxnHandle m_txnHandle;
    AsyncTransactionalStore* m_store;
    AsyncResultQueue& m_resultQueue;

    typedef enum {NONE = 0, PREPARE, COMMIT, ROLLBACK, COMPLETE} e_txnState;
    e_txnState m_state;
};

}} // namespace qpid::broker

#endif // qpid_broker_TxnBuffer_h_
