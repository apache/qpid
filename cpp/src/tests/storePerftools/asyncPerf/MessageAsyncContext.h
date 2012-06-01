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
 * \file MessageContext.h
 */

#ifndef tests_storePerfTools_asyncPerf_MessageContext_h_
#define tests_storePerfTools_asyncPerf_MessageContext_h_

#include "qpid/asyncStore/AsyncOperation.h"
#include "qpid/broker/AsyncStore.h" // qpid::broker::BrokerAsyncContext

#include <boost/shared_ptr.hpp>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MockPersistableMessage;
class MockPersistableQueue;

class MessageAsyncContext : public qpid::broker::BrokerAsyncContext
{
public:
    MessageAsyncContext(boost::shared_ptr<MockPersistableMessage> msg,
                        const qpid::asyncStore::AsyncOperation::opCode op,
                        boost::shared_ptr<MockPersistableQueue> q);
    virtual ~MessageAsyncContext();
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    boost::shared_ptr<MockPersistableMessage> getMessage() const;
    boost::shared_ptr<MockPersistableQueue> getQueue() const;
    void destroy();

protected:
    boost::shared_ptr<MockPersistableMessage> m_msg;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
    boost::shared_ptr<MockPersistableQueue> m_q;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerfTools_asyncPerf_MessageContext_h_
