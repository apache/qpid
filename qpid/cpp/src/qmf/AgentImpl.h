#ifndef _QMF_AGENT_IMPL_H_
#define _QMF_AGENT_IMPL_H_
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
#include "qmf/Agent.h"
#include "qmf/ConsoleEventImpl.h"
#include "qmf/ConsoleSessionImpl.h"
#include "qmf/QueryImpl.h"
#include "qmf/SchemaCache.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Sender.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Condition.h"
#include <boost/shared_ptr.hpp>
#include <map>
#include <set>

namespace qmf {
    class AgentImpl : public virtual qpid::RefCounted {
    public:
        //
        // Impl-only methods
        //
        AgentImpl(const std::string& n, uint32_t e, ConsoleSessionImpl& s);
        void setAttribute(const std::string& k, const qpid::types::Variant& v);
        void setAttribute(const std::string& k, const std::string& v) { attributes[k] = v; }
        void touch() { touched = true; }
        uint32_t age() { untouchedCount = touched ? 0 : untouchedCount + 1; touched = false; return untouchedCount; }
        uint32_t getCapability() const { return capability; }
        void handleException(const qpid::types::Variant::Map&, const qpid::messaging::Message&);
        void handleMethodResponse(const qpid::types::Variant::Map&, const qpid::messaging::Message&);
        void handleDataIndication(const qpid::types::Variant::List&, const qpid::messaging::Message&);
        void handleQueryResponse(const qpid::types::Variant::List&, const qpid::messaging::Message&);

        //
        // Methods from API handle
        //
        const std::string& getName() const { return name; }
        uint32_t getEpoch() const { return epoch; }
        void setEpoch(uint32_t e) { epoch = e; }
        std::string getVendor() const { return getAttribute("_vendor").asString(); }
        std::string getProduct() const { return getAttribute("_product").asString(); }
        std::string getInstance() const { return getAttribute("_instance").asString(); }
        const qpid::types::Variant& getAttribute(const std::string& k) const;
        const qpid::types::Variant::Map& getAttributes() const { return attributes; }

        ConsoleEvent querySchema(qpid::messaging::Duration t) { return query(Query(QUERY_SCHEMA_ID), t); }
        uint32_t querySchemaAsync() { return queryAsync(Query(QUERY_SCHEMA_ID)); }

        ConsoleEvent query(const Query& q, qpid::messaging::Duration t);
        ConsoleEvent query(const std::string& q, qpid::messaging::Duration t);
        uint32_t queryAsync(const Query& q);
        uint32_t queryAsync(const std::string& q);

        ConsoleEvent callMethod(const std::string& m, const qpid::types::Variant::Map& a, const DataAddr&, qpid::messaging::Duration t);
        uint32_t callMethodAsync(const std::string& m, const qpid::types::Variant::Map& a, const DataAddr&);

        uint32_t getPackageCount() const;
        const std::string& getPackage(uint32_t i) const;
        uint32_t getSchemaIdCount(const std::string& p) const;
        SchemaId getSchemaId(const std::string& p, uint32_t i) const;
        Schema getSchema(const SchemaId& s, qpid::messaging::Duration t);

    private:
        struct SyncContext {
            qpid::sys::Mutex lock;
            qpid::sys::Condition cond;
            ConsoleEvent response;
        };

        mutable qpid::sys::Mutex lock;
        std::string name;
        std::string directSubject;
        uint32_t epoch;
        ConsoleSessionImpl& session;
        bool touched;
        uint32_t untouchedCount;
        uint32_t capability;
        qpid::messaging::Sender sender;
        qpid::types::Variant::Map attributes;
        std::map<uint32_t, boost::shared_ptr<SyncContext> > contextMap;
        boost::shared_ptr<SchemaCache> schemaCache;
        mutable std::set<std::string> packageSet;
        std::set<SchemaId, SchemaIdCompare> schemaIdSet;

        Query stringToQuery(const std::string&);
        void sendQuery(const Query&, uint32_t);
        void sendSchemaIdQuery(uint32_t);
        void sendMethod(const std::string&, const qpid::types::Variant::Map&, const DataAddr&, uint32_t);
        void sendSchemaRequest(const SchemaId&);
        void learnSchemaId(const SchemaId&);
    };

    struct AgentImplAccess
    {
        static AgentImpl& get(Agent&);
        static const AgentImpl& get(const Agent&);
    };
}

#endif
