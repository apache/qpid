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
 * \file QueuedMessage.h
 */

#ifndef tests_storePerftools_asyncPerf_QueuedMessage_h_
#define tests_storePerftools_asyncPerf_QueuedMessage_h_

#include "qpid/broker/EnqueueHandle.h"
#include <boost/shared_ptr.hpp>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MockPersistableMessage;
class MockTransactionContext;

typedef boost::shared_ptr<MockPersistableMessage> MockPersistableMessagePtr;
typedef boost::shared_ptr<MockTransactionContext> MockTransactionContextPtr;

class QueuedMessage
{
public:
    QueuedMessage(MockPersistableMessagePtr msg,
                  qpid::broker::EnqueueHandle& enqHandle,
                  MockTransactionContextPtr txn);
    virtual ~QueuedMessage();
    MockPersistableMessagePtr getMessage() const;
    qpid::broker::EnqueueHandle getEnqueueHandle() const;
    MockTransactionContextPtr getTransactionContext() const;
    bool isTransactional() const;
    void clearTransaction();

protected:
    MockPersistableMessagePtr m_msg;
    qpid::broker::EnqueueHandle m_enqHandle;
    MockTransactionContextPtr m_txn;
};

}}} // namespace tests::storePerfTools

#endif // tests_storePerftools_asyncPerf_QueuedMessage_h_
