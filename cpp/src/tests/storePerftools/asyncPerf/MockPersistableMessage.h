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
 * \file MockPersistableMessage.h
 */

#ifndef tests_storePerftools_asyncPerf_MockPersistableMessage_h_
#define tests_storePerftools_asyncPerf_MockPersistableMessage_h_

#include "qpid/asyncStore/AsyncOperation.h"
#include "qpid/broker/AsyncStore.h" // qpid::broker::DataSource
#include "qpid/broker/BrokerContext.h"
#include "qpid/broker/MessageHandle.h"
#include "qpid/broker/PersistableMessage.h"

#include <stdint.h> // uint32_t

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MockPersistableMessage;
class MockPersistableQueue;

typedef boost::shared_ptr<MockPersistableMessage> MockPersistableMessagePtr;

class MockPersistableMessage: public qpid::broker::PersistableMessage, qpid::broker::DataSource
{
public:
    class MessageContext : public qpid::broker::BrokerContext
    {
    public:
        MessageContext(MockPersistableMessagePtr msg,
                       const qpid::asyncStore::AsyncOperation::opCode op,
                       MockPersistableQueue* q);
        virtual ~MessageContext();
        const char* getOp() const;
        void destroy();
        MockPersistableMessagePtr m_msg;
        const qpid::asyncStore::AsyncOperation::opCode m_op;
        MockPersistableQueue* m_q;
    };

    MockPersistableMessage(const char* msgData,
                           const uint32_t msgSize,
                           qpid::asyncStore::AsyncStoreImpl* store,
                           const bool persistent = true);
    virtual ~MockPersistableMessage();
    static void handleAsyncResult(const qpid::broker::AsyncResult* res,
                                  qpid::broker::BrokerContext* bc);
    qpid::broker::MessageHandle& getHandle();

    // Interface Persistable
    virtual void setPersistenceId(uint64_t id) const;
    virtual uint64_t getPersistenceId() const;
    virtual void encode(qpid::framing::Buffer& buffer) const;
    virtual uint32_t encodedSize() const;

    // Interface PersistableMessage
    virtual void allDequeuesComplete();
    virtual uint32_t encodedHeaderSize() const;
    virtual bool isPersistent() const;

    // Interface DataStore
    virtual uint64_t getSize();
    virtual void write(char* target);

protected:
    mutable uint64_t m_persistenceId;
    const std::string m_msg;
    const bool m_persistent;
    qpid::broker::MessageHandle m_msgHandle;

    // --- Ascnc op completions (called through handleAsyncResult) ---
    void enqueueComplete(const MessageContext* mc);
    void dequeueComplete(const MessageContext* mc);

};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerfools_asyncPerf_MockPersistableMessage_h_
