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
#include "qmf/DataAddr.h"
#include "qmf/Agent.h"
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

        //
        // Methods from API handle
        //
        DataImpl(const SchemaId& s) : schemaId(s) {}
        void setSchema(const SchemaId& s) { schemaId = s; }
        void setAddr(const DataAddr& a) { dataAddr = a; }
        void setProperty(const std::string& k, const qpid::types::Variant& v) { properties[k] = v; }
        void overwriteProperties(const qpid::types::Variant::Map& m);
        bool hasSchema() const { return schemaId.isValid(); }
        bool hasAddr() const { return dataAddr.isValid(); }
        const SchemaId& getSchemaId() const { return schemaId; }
        const DataAddr& getAddr() const { return dataAddr; }
        const qpid::types::Variant& getProperty(const std::string& k) const;
        const qpid::types::Variant::Map& getProperties() const { return properties; }
        bool hasAgent() const { return agent.isValid(); }
        const Agent& getAgent() const { return agent; }

    private:
        SchemaId schemaId;
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
