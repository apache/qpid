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

// --- Inner class Queue::MessageContext ---

MockPersistableMessage::MessageContext::MessageContext(MockPersistableMessagePtr msg,
                                                       const qpid::asyncStore::AsyncOperation::opCode op,
                                                       MockPersistableQueue* q) :
        m_msg(msg),
        m_op(op),
        m_q(q)
{}

MockPersistableMessage::MessageContext::~MessageContext()
{}

const char*
MockPersistableMessage::MessageContext::getOp() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

void
MockPersistableMessage::MessageContext::destroy()
{
    delete this;
}

// --- Class MockPersistableMessage ---


MockPersistableMessage::MockPersistableMessage(const char* msgData,
                                               const uint32_t msgSize,
                                               AsyncStoreImplPtr store,
                                               const bool persistent) :
        m_persistenceId(0ULL),
        m_msg(msgData, static_cast<size_t>(msgSize)),
        m_persistent(persistent),
        m_msgHandle(store->createMessageHandle(this))
{}

MockPersistableMessage::~MockPersistableMessage()
{}

// static
void
MockPersistableMessage::handleAsyncResult(const qpid::broker::AsyncResult* res,
                                          qpid::broker::BrokerContext* bc)
{
    if (bc) {
        MessageContext* mc = dynamic_cast<MessageContext*>(bc);
        if (mc->m_msg) {
            if (res->errNo) {
                // TODO: Handle async failure here
                std::cerr << "Message pid=0x" << std::hex << mc->m_msg->m_persistenceId << std::dec << ": Operation "
                          << mc->getOp() << ": failure " << res->errNo << " (" << res->errMsg << ")" << std::endl;
            } else {
                // Handle async success here
                switch(mc->m_op) {
                case qpid::asyncStore::AsyncOperation::MSG_DEQUEUE:
                    mc->m_msg->dequeueComplete(mc);
                    break;
                case qpid::asyncStore::AsyncOperation::MSG_ENQUEUE:
                    mc->m_msg->enqueueComplete(mc);
                    break;
                default:
                    std::ostringstream oss;
                    oss << "tests::storePerftools::asyncPerf::MockPersistableMessage::handleAsyncResult(): Unknown async queue operation: " << mc->m_op;
                    throw qpid::Exception(oss.str());
                };
            }
        }
    }
    if (bc) delete bc;
    if (res) delete res;
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
    return m_persistent;
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

// protected
void
MockPersistableMessage::enqueueComplete(const MessageContext* /*mc*/)
{
//std::cout << "~~~~~ Message pid=0x" << std::hex << m_persistenceId << std::dec << ": enqueueComplete() on queue \"" << mc->m_q->getName() << "\"" << std::endl;
}

// protected
void
MockPersistableMessage::dequeueComplete(const MessageContext* /*mc*/)
{
//std::cout << "~~~~~ Message pid=0x" << std::hex << m_persistenceId << std::dec << ": dequeueComplete() on queue \"" << mc->m_q->getName() << "\"" << std::endl;
}

}}} // namespace tests::storePerftools::asyncPerf
