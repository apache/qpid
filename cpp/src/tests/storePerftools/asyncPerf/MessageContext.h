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

#include "MockPersistableMessage.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MessageContext : public qpid::broker::BrokerContext
{
public:
    MessageContext(MockPersistableMessage::shared_ptr msg,
                   const qpid::asyncStore::AsyncOperation::opCode op,
                   MockPersistableQueue* q);
    virtual ~MessageContext();
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    MockPersistableMessage::shared_ptr getMessage() const;
    MockPersistableQueue* getQueue() const;
    void destroy();
protected:
    MockPersistableMessage::shared_ptr m_msg;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
    MockPersistableQueue* m_q;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerfTools_asyncPerf_MessageContext_h_
