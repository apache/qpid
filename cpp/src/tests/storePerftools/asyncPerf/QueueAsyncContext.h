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
 * \file QueueContext.h
 */

#ifndef tests_storePerftools_asyncPerf_QueueContext_h_
#define tests_storePerftools_asyncPerf_QueueContext_h_

#include "qpid/asyncStore/AsyncOperation.h"
#include "qpid/broker/AsyncStore.h"
#include "qpid/broker/TxnHandle.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {
typedef void (*AsyncResultCallback)(const AsyncResultHandle* const);
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class SimpleMessage;
class SimpleQueue;

class QueueAsyncContext: public qpid::broker::BrokerAsyncContext
{
public:
    QueueAsyncContext(boost::shared_ptr<SimpleQueue> q,
                      qpid::broker::TxnHandle& th,
                      const qpid::asyncStore::AsyncOperation::opCode op,
                      qpid::broker::AsyncResultCallback rcb,
                      qpid::broker::AsyncResultQueue* const arq);
    QueueAsyncContext(boost::shared_ptr<SimpleQueue> q,
                      boost::intrusive_ptr<SimpleMessage> msg,
                      qpid::broker::TxnHandle& th,
                      const qpid::asyncStore::AsyncOperation::opCode op,
                      qpid::broker::AsyncResultCallback rcb,
                      qpid::broker::AsyncResultQueue* const arq);
    virtual ~QueueAsyncContext();
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    boost::shared_ptr<SimpleQueue> getQueue() const;
    boost::intrusive_ptr<SimpleMessage> getMessage() const;
    qpid::broker::TxnHandle getTxnHandle() const;
    qpid::broker::AsyncResultQueue* getAsyncResultQueue() const;
    qpid::broker::AsyncResultCallback getAsyncResultCallback() const;
    void invokeCallback(const qpid::broker::AsyncResultHandle* const arh) const;
    void destroy();

private:
    boost::shared_ptr<SimpleQueue> m_q;
    boost::intrusive_ptr<SimpleMessage> m_msg;
    qpid::broker::TxnHandle m_th;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
    qpid::broker::AsyncResultCallback m_rcb;
    qpid::broker::AsyncResultQueue* const m_arq;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_QueueContext_h_
