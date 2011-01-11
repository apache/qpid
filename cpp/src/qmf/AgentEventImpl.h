#ifndef _QMF_AGENT_EVENT_IMPL_H_
#define _QMF_AGENT_EVENT_IMPL_H_
/*
 *
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
 *
 */

#include "qpid/RefCounted.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/messaging/Address.h"
#include "qmf/AgentEvent.h"
#include "qmf/Query.h"
#include "qmf/DataAddr.h"
#include "qmf/Data.h"
#include "qmf/Schema.h"
#include <queue>

namespace qmf {
    class AgentEventImpl : public virtual qpid::RefCounted {
    public:
        //
        // Impl-only methods
        //
        AgentEventImpl(AgentEventCode e) : eventType(e) {}
        void setUserId(const std::string& u) { userId = u; }
        void setQuery(const Query& q) { query = q; }
        void setDataAddr(const DataAddr& d) { dataAddr = d; }
        void setMethodName(const std::string& m) { methodName = m; }
        void setArguments(const qpid::types::Variant::Map& a) { arguments = a; }
        void setArgumentSubtypes(const qpid::types::Variant::Map& a) { argumentSubtypes = a; }
        void setReplyTo(const qpid::messaging::Address& r) { replyTo = r; }
        void setSchema(const Schema& s) { schema = s; }
        const qpid::messaging::Address& getReplyTo() { return replyTo; }
        void setCorrelationId(const std::string& c) { correlationId = c; }
        const std::string& getCorrelationId() { return correlationId; }
        const qpid::types::Variant::Map& getReturnArguments() const { return outArguments; }
        const qpid::types::Variant::Map& getReturnArgumentSubtypes() const { return outArgumentSubtypes; }
        uint32_t enqueueData(const Data&);
        Data dequeueData();

        //
        // Methods from API handle
        //
        AgentEventCode getType() const { return eventType; }
        const std::string& getUserId() const { return userId; }
        Query getQuery() const { return query; }
        bool hasDataAddr() const { return dataAddr.isValid(); }
        DataAddr getDataAddr() const { return dataAddr; }
        const std::string& getMethodName() const { return methodName; }
        qpid::types::Variant::Map& getArguments() { return arguments; }
        qpid::types::Variant::Map& getArgumentSubtypes() { return argumentSubtypes; }
        void addReturnArgument(const std::string&, const qpid::types::Variant&, const std::string&);

    private:
        const AgentEventCode eventType;
        std::string userId;
        qpid::messaging::Address replyTo;
        std::string correlationId;
        Query query;
        DataAddr dataAddr;
        Schema schema;
        std::string methodName;
        qpid::types::Variant::Map arguments;
        qpid::types::Variant::Map argumentSubtypes;
        qpid::types::Variant::Map outArguments;
        qpid::types::Variant::Map outArgumentSubtypes;

        qpid::sys::Mutex lock;
        std::queue<Data> dataQueue;
    };

    struct AgentEventImplAccess
    {
        static AgentEventImpl& get(AgentEvent&);
        static const AgentEventImpl& get(const AgentEvent&);
    };
}

#endif
