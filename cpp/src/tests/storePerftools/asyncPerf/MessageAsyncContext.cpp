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
 * \file MessageContext.cpp
 */

#include "MessageAsyncContext.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

MessageContext::MessageContext(MockPersistableMessage::shared_ptr msg,
                                                       const qpid::asyncStore::AsyncOperation::opCode op,
                                                       MockPersistableQueue* q) :
        m_msg(msg),
        m_op(op),
        m_q(q)
{
    assert(m_msg.get() != 0);
    assert(m_q != 0);
}

MessageContext::~MessageContext()
{}

qpid::asyncStore::AsyncOperation::opCode
MessageContext::getOpCode() const
{
    return m_op;
}

const char*
MessageContext::getOpStr() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

MockPersistableMessage::shared_ptr
MessageContext::getMessage() const
{
    return m_msg;
}

MockPersistableQueue*
MessageContext::getQueue() const
{
    return m_q;
}

void
MessageContext::destroy()
{
    delete this;
}

}}} // namespace tests::storePerftools::asyncPerf
