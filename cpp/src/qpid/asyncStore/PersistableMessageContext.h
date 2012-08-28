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
 * \file PersistableMessageContext.h
 */

#ifndef qpid_asyncStore_PersistableMessageContext_h_
#define qpid_asyncStore_PersistableMessageContext_h_

#include "qpid/broker/MessageHandle.h"
#include "qpid/broker/PersistableMessage.h"

namespace qpid {
namespace asyncStore {

class PersistableMessageContext: public qpid::broker::PersistableMessage {
private:
    qpid::broker::MessageHandle m_msgHandle;
    qpid::broker::AsyncStore* m_store;
public:
    PersistableMessageContext(qpid::broker::AsyncStore* store);
    virtual ~PersistableMessageContext();

    // --- Interface Persistable ---
    void encode(qpid::framing::Buffer& buffer) const;
    uint32_t encodedSize() const;

    // --- Class PersistableMessage ---
    bool isPersistent() const;
    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer);
    uint32_t encodedHeaderSize() const;
    boost::intrusive_ptr<PersistableMessage> merge(const std::map<std::string, qpid::types::Variant>& annotations) const;
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_PersistableMessageContext_h_
