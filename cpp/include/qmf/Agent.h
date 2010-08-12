#ifndef QMF_AGENT_H
#define QMF_AGENT_H
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

#include <qmf/ImportExport.h>
#include "qmf/Handle.h"
#include "qmf/exceptions.h"
#include "qpid/messaging/Duration.h"
#include "qpid/types/Variant.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class AgentImpl;
    class ConsoleEvent;
    class Query;
    class DataAddr;
    class SchemaId;
    class Schema;

    class Agent : public qmf::Handle<AgentImpl> {
    public:
        QMF_EXTERN Agent(AgentImpl* impl = 0);
        QMF_EXTERN Agent(const Agent&);
        QMF_EXTERN Agent& operator=(const Agent&);
        QMF_EXTERN ~Agent();

        QMF_EXTERN std::string getName() const;
        QMF_EXTERN uint32_t getEpoch() const;
        QMF_EXTERN std::string getVendor() const;
        QMF_EXTERN std::string getProduct() const;
        QMF_EXTERN std::string getInstance() const;
        QMF_EXTERN const qpid::types::Variant& getAttribute(const std::string&) const;
        QMF_EXTERN const qpid::types::Variant::Map& getAttributes() const;

        QMF_EXTERN ConsoleEvent query(const Query&, qpid::messaging::Duration timeout=qpid::messaging::Duration::MINUTE);
        QMF_EXTERN ConsoleEvent query(const std::string&, qpid::messaging::Duration timeout=qpid::messaging::Duration::MINUTE);
        QMF_EXTERN uint32_t queryAsync(const Query&);
        QMF_EXTERN uint32_t queryAsync(const std::string&);

        QMF_EXTERN ConsoleEvent callMethod(const std::string&, const qpid::types::Variant::Map&, const DataAddr&,
                                           qpid::messaging::Duration timeout=qpid::messaging::Duration::MINUTE);
        QMF_EXTERN uint32_t callMethodAsync(const std::string&, const qpid::types::Variant::Map&, const DataAddr&);

        QMF_EXTERN uint32_t getPackageCount() const;
        QMF_EXTERN const std::string& getPackage(uint32_t) const;
        QMF_EXTERN uint32_t getSchemaIdCount(const std::string&) const;
        QMF_EXTERN SchemaId getSchemaId(const std::string&, uint32_t) const;
        QMF_EXTERN Schema getSchema(const SchemaId&, qpid::messaging::Duration timeout=qpid::messaging::Duration::MINUTE);


#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<Agent>;
        friend struct AgentImplAccess;
#endif
    };

}

#endif
