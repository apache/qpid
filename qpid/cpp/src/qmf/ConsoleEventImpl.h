#ifndef _QMF_CONSOLE_EVENT_IMPL_H_
#define _QMF_CONSOLE_EVENT_IMPL_H_
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
#include "qmf/ConsoleEvent.h"
#include "qmf/Agent.h"
#include "qmf/Data.h"
#include "qpid/types/Variant.h"
#include <list>

namespace qmf {
    class ConsoleEventImpl : public virtual qpid::RefCounted {
    public:
        //
        // Impl-only methods
        //
        ConsoleEventImpl(ConsoleEventCode e, AgentDelReason r = AGENT_DEL_AGED) :
            eventType(e), delReason(r), correlator(0), final(false) {}
        void setCorrelator(uint32_t c) { correlator = c; }
        void setAgent(const Agent& a) { agent = a; }
        void addData(const Data& d) { dataList.push_back(Data(d)); }
        void addSchemaId(const SchemaId& s) { newSchemaIds.push_back(SchemaId(s)); }
        void setFinal() { final = true; }
        void setArguments(const qpid::types::Variant::Map& a) { arguments = a; }
        void setSeverity(int s) { severity = s; }
        void setTimestamp(uint64_t t) { timestamp = t; }

        //
        // Methods from API handle
        //
        ConsoleEventCode getType() const { return eventType; }
        uint32_t getCorrelator() const { return correlator; }
        Agent getAgent() const { return agent; }
        AgentDelReason getAgentDelReason() const { return delReason; }
        uint32_t getSchemaIdCount() const { return newSchemaIds.size(); }
        SchemaId getSchemaId(uint32_t) const;
        uint32_t getDataCount() const { return dataList.size(); }
        Data getData(uint32_t i) const;
        bool isFinal() const { return final; }
        const qpid::types::Variant::Map& getArguments() const { return arguments; }
        int getSeverity() const { return severity; }
        uint64_t getTimestamp() const { return timestamp; }

    private:
        const ConsoleEventCode eventType;
        const AgentDelReason delReason;
        uint32_t correlator;
        Agent agent;
        bool final;
        std::list<Data> dataList;
        std::list<SchemaId> newSchemaIds;
        qpid::types::Variant::Map arguments;
        int severity;
        uint64_t timestamp;
    };

    struct ConsoleEventImplAccess
    {
        static ConsoleEventImpl& get(ConsoleEvent&);
        static const ConsoleEventImpl& get(const ConsoleEvent&);
    };
}

#endif
