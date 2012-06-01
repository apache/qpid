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
 * \file MockPersistableMessage.cpp
 */

#include "MockPersistableMessage.h"

#include "qpid/asyncStore/AsyncStoreImpl.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MockPersistableMessage::MockPersistableMessage(const char* msgData,
                                               const uint32_t msgSize,
                                               qpid::asyncStore::AsyncStoreImpl* store) :
        m_persistenceId(0ULL),
        m_msg(msgData, static_cast<size_t>(msgSize)),
        m_msgHandle(store ? store->createMessageHandle(this) : store->createMessageHandle(0))
{}

MockPersistableMessage::~MockPersistableMessage()
{}

const qpid::broker::MessageHandle&
MockPersistableMessage::getHandle() const
{
    return m_msgHandle;
}

qpid::broker::MessageHandle&
MockPersistableMessage::getHandle()
{
    return m_msgHandle;
}

void
MockPersistableMessage::setPersistenceId(uint64_t id) const
{
    m_persistenceId = id;
}

uint64_t
MockPersistableMessage::getPersistenceId() const
{
    return m_persistenceId;
}

void
MockPersistableMessage::encode(qpid::framing::Buffer& buffer) const
{
    buffer.putRawData(m_msg);
}

uint32_t
MockPersistableMessage::encodedSize() const
{
    return static_cast<uint32_t>(m_msg.size());
}

void
MockPersistableMessage::allDequeuesComplete()
{}

uint32_t
MockPersistableMessage::encodedHeaderSize() const
{
    return 0;
}

bool
MockPersistableMessage::isPersistent() const
{
    return m_msgHandle.isValid();
}

uint64_t
MockPersistableMessage::getSize()
{
    return m_msg.size();
}

void
MockPersistableMessage::write(char* target)
{
    ::memcpy(target, m_msg.data(), m_msg.size());
}

}}} // namespace tests::storePerftools::asyncPerf
