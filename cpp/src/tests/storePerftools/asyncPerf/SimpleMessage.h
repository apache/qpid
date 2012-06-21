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
 * \file SimpleMessage.h
 */

#ifndef tests_storePerftools_asyncPerf_SimpleMessage_h_
#define tests_storePerftools_asyncPerf_SimpleMessage_h_

#include "qpid/broker/AsyncStore.h" // qpid::broker::DataSource
#include "qpid/broker/MessageHandle.h"
#include "qpid/broker/PersistableMessage.h"

#include <set>

namespace qpid {
namespace asyncStore {
class AsyncStoreImpl;
}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class SimpleQueue;

class SimpleMessage: public qpid::broker::PersistableMessage,
                     public qpid::broker::DataSource
{
public:
    SimpleMessage(const char* msgData,
                  const uint32_t msgSize,
                  qpid::asyncStore::AsyncStoreImpl* store);
    virtual ~SimpleMessage();
    const qpid::broker::MessageHandle& getHandle() const;
    qpid::broker::MessageHandle& getHandle();
    uint64_t contentSize() const;

    // --- Interface Persistable ---
    virtual void setPersistenceId(uint64_t id) const;
    virtual uint64_t getPersistenceId() const;
    virtual void encode(qpid::framing::Buffer& buffer) const;
    virtual uint32_t encodedSize() const;

    // --- Interface PersistableMessage ---
    virtual void allDequeuesComplete();
    virtual uint32_t encodedHeaderSize() const;
    virtual bool isPersistent() const;

    // --- Interface DataSource ---
    virtual uint64_t getSize(); // <- same as encodedSize()?
    virtual void write(char* target);

private:
    mutable uint64_t m_persistenceId;
    const std::string m_msg;
    qpid::broker::MessageHandle m_msgHandle;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_SimpleMessage_h_
