#ifndef _QMF_DATA_IMPL_H_
#define _QMF_DATA_IMPL_H_
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
#include "qmf/Data.h"
#include "qmf/SchemaId.h"
#include "qmf/Schema.h"
#include "qmf/DataAddr.h"
#include "qmf/Agent.h"
#include "qmf/AgentSubscription.h"
#include "qpid/types/Variant.h"

namespace qmf {
    class DataImpl : public virtual qpid::RefCounted {
    public:
        //
        // Public impl-only methods
        //
        DataImpl(const qpid::types::Variant::Map&, const Agent&);
        qpid::types::Variant::Map asMap() const;
        DataImpl() {}
        void addSubscription(boost::shared_ptr<AgentSubscription>);
        void delSubscription(uint64_t);
        qpid::types::Variant::Map publishSubscription(uint64_t);
        const Schema& getSchema() const { return schema; }

        //
        // Methods from API handle
        //
        DataImpl(const Schema& s) : schema(s) {}
        void setAddr(const DataAddr& a) { dataAddr = a; }
        void setProperty(const std::string& k, const qpid::types::Variant& v);
        void overwriteProperties(const qpid::types::Variant::Map& m);
        bool hasSchema() const { return schemaId.isValid() || schema.isValid(); }
        bool hasAddr() const { return dataAddr.isValid(); }
        const SchemaId& getSchemaId() const { if (schema.isValid()) return schema.getSchemaId(); else return schemaId; }
        const DataAddr& getAddr() const { return dataAddr; }
        const qpid::types::Variant& getProperty(const std::string& k) const;
        const qpid::types::Variant::Map& getProperties() const { return properties; }
        bool hasAgent() const { return agent.isValid(); }
        const Agent& getAgent() const { return agent; }

    private:
        struct Subscr {
            boost::shared_ptr<AgentSubscription> subscription;
            qpid::types::Variant::Map deltas;
        };
        std::map<uint64_t, boost::shared_ptr<Subscr> > subscriptions;

        SchemaId schemaId;
        Schema   schema;
        DataAddr dataAddr;
        qpid::types::Variant::Map properties;
        Agent agent;
    };

    struct DataImplAccess
    {
        static DataImpl& get(Data&);
        static const DataImpl& get(const Data&);
    };
}

#endif
