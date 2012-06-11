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

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>


namespace tests {
namespace storePerftools {
namespace asyncPerf {

class SimplePersistableMessage;
class SimplePersistableQueue;

class QueueAsyncContext: public qpid::broker::BrokerAsyncContext
{
public:
    QueueAsyncContext(boost::shared_ptr<SimplePersistableQueue> q,
                      const qpid::asyncStore::AsyncOperation::opCode op,
                      qpid::broker::AsyncResultCallback rcb,
                      qpid::broker::AsyncResultQueue* const arq);
    QueueAsyncContext(boost::shared_ptr<SimplePersistableQueue> q,
                      boost::intrusive_ptr<SimplePersistableMessage> msg,
                      const qpid::asyncStore::AsyncOperation::opCode op,
                      qpid::broker::AsyncResultCallback rcb,
                      qpid::broker::AsyncResultQueue* const arq);
    virtual ~QueueAsyncContext();
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    boost::shared_ptr<SimplePersistableQueue> getQueue() const;
    boost::intrusive_ptr<SimplePersistableMessage> getMessage() const;
    qpid::broker::AsyncResultQueue* getAsyncResultQueue() const;
    qpid::broker::AsyncResultCallback getAsyncResultCallback() const;
    void invokeCallback(const qpid::broker::AsyncResultHandle* const arh) const;
    void destroy();

private:
    boost::shared_ptr<SimplePersistableQueue> m_q;
    boost::intrusive_ptr<SimplePersistableMessage> m_msg;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
    qpid::broker::AsyncResultCallback m_rcb;
    qpid::broker::AsyncResultQueue* const m_arq;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_QueueContext_h_
