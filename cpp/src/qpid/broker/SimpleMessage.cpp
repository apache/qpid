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

#include "qpid/broker/SimpleMessage.h"

#include <string.h> // memcpy()

namespace qpid  {
namespace broker {

SimpleMessage::SimpleMessage() {}

SimpleMessage::SimpleMessage(const char* msgData,
                             const uint32_t msgSize,
                             boost::intrusive_ptr<PersistableMessage> persistentContext) :
        m_msg(msgData, static_cast<size_t>(msgSize)),
        m_persistentContext(persistentContext)
{}

SimpleMessage::~SimpleMessage() {}

uint64_t
SimpleMessage::contentSize() const {
    return  static_cast<uint64_t>(m_msg.size());
}

bool
SimpleMessage::isPersistent() const {
    return m_persistentContext.get() != 0;
}

boost::intrusive_ptr<PersistableMessage>
SimpleMessage::getPersistentContext() const {
    return m_persistentContext;
}


uint64_t
SimpleMessage::getSize() {
    return m_msg.size();
}

void
SimpleMessage::write(char* target) {
    ::memcpy(target, m_msg.data(), m_msg.size());
}

}} // namespace qpid::broker
