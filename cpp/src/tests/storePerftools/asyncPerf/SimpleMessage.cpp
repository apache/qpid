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
 * \file SimpleMessage.cpp
 */

#include "SimpleMessage.h"

#include <string.h> // memcpy()

namespace tests {
namespace storePerftools {
namespace asyncPerf {

SimpleMessage::SimpleMessage(const char* msgData,
                             const uint32_t msgSize) :
        m_persistenceId(0ULL),
        m_msg(msgData, static_cast<size_t>(msgSize)),
        m_store(0),
        m_msgHandle(qpid::broker::MessageHandle())
{}

SimpleMessage::SimpleMessage(const char* msgData,
                             const uint32_t msgSize,
                             qpid::broker::AsyncStore* store) :
        m_persistenceId(0ULL),
        m_msg(msgData, static_cast<size_t>(msgSize)),
        m_store(store),
        m_msgHandle(store ? store->createMessageHandle(this) : qpid::broker::MessageHandle())
{}

SimpleMessage::~SimpleMessage()
{}

const qpid::broker::MessageHandle&
SimpleMessage::getHandle() const
{
    return m_msgHandle;
}

qpid::broker::MessageHandle&
SimpleMessage::getHandle()
{
    return m_msgHandle;
}

uint64_t
SimpleMessage::contentSize() const
{
    return  static_cast<uint64_t>(m_msg.size());
}

void
SimpleMessage::setPersistenceId(uint64_t id) const
{
    m_persistenceId = id;
}

uint64_t
SimpleMessage::getPersistenceId() const
{
    return m_persistenceId;
}

void
SimpleMessage::encode(qpid::framing::Buffer& buffer) const
{
    buffer.putRawData(m_msg);
}

uint32_t
SimpleMessage::encodedSize() const
{
    return static_cast<uint32_t>(m_msg.size());
}

void
SimpleMessage::allDequeuesComplete()
{}

uint32_t
SimpleMessage::encodedHeaderSize() const
{
    return 0;
}

bool
SimpleMessage::isPersistent() const
{
    return m_store != 0;
}

uint64_t
SimpleMessage::getSize()
{
    return m_msg.size();
}

void
SimpleMessage::write(char* target)
{
    ::memcpy(target, m_msg.data(), m_msg.size());
}

}}} // namespace tests::storePerftools::asyncPerf
