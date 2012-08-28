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
 * \file PersistableMessageContext.cpp
 */

#include "qpid/asyncStore/PersistableMessageContext.h"

namespace qpid {
namespace asyncStore {

PersistableMessageContext::PersistableMessageContext(qpid::broker::AsyncStore* store) : m_store(store) {}

PersistableMessageContext::~PersistableMessageContext() {}

void
PersistableMessageContext::encode(qpid::framing::Buffer& /*buffer*/) const {}

uint32_t
PersistableMessageContext::encodedSize() const {
    return 0;
}

bool
PersistableMessageContext::isPersistent() const {
    return false;
}

void
PersistableMessageContext::decodeHeader(framing::Buffer& /*buffer*/) {}

void
PersistableMessageContext::decodeContent(framing::Buffer& /*buffer*/) {}

uint32_t
PersistableMessageContext::encodedHeaderSize() const {
    return 0;
}

boost::intrusive_ptr<qpid::broker::PersistableMessage> PersistableMessageContext::merge(const std::map<std::string, qpid::types::Variant>& /*annotations*/) const {
    boost::intrusive_ptr<qpid::broker::PersistableMessage> pmc;
    return pmc;
}

}} // namespace qpid::asyncStore
