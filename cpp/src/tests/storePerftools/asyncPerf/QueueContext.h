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

#include "MockPersistableQueue.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class QueueContext: public qpid::broker::BrokerContext
{
public:
    QueueContext(MockPersistableQueue::intrusive_ptr q,
                 const qpid::asyncStore::AsyncOperation::opCode op);
    virtual ~QueueContext();
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    MockPersistableQueue::intrusive_ptr getQueue() const;
    void destroy();
protected:
    MockPersistableQueue::intrusive_ptr m_q;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_QueueContext_h_
