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

#if !defined(QMF_USE_DEPRECATED_API) && !defined(qmf2_EXPORTS) && !defined(SWIG)
#  error "The API defined in this file has been DEPRECATED and will be removed in the future."
#  error "Define 'QMF_USE_DEPRECATED_API' to enable continued use of the API."
#endif

#include <qmf/ImportExport.h>
#include "qmf/Handle.h"
//#include "qmf/Subscription.h"
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

    class QMF_CLASS_EXTERN Agent : public qmf::Handle<AgentImpl> {
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

        /**
         * Create a subscription to this agent
         */
        //QMF_EXTERN Subscription subscribe(const Query&, const std::string& options = "");
        //QMF_EXTERN Subscription subscribe(const std::string&, const std::string& options = "");

        QMF_EXTERN ConsoleEvent callMethod(const std::string&, const qpid::types::Variant::Map&, const DataAddr&,
                                           qpid::messaging::Duration timeout=qpid::messaging::Duration::MINUTE);
        QMF_EXTERN uint32_t callMethodAsync(const std::string&, const qpid::types::Variant::Map&, const DataAddr&);

        /**
         * Query the agent for a list of schema classes that it exposes.  This operation comes in both
         * synchronous (blocking) and asynchronous flavors.
         *
         * This method will typically be used after receiving an AGENT_SCHEMA_UPDATE event from the console session.
         * It may also be used on a newly discovered agent to learn what schemata are exposed.
         *
         * querySchema returns a ConsoleEvent that contains a list of SchemaId objects exposed by the agent.
         * This list is cached locally and can be locally queried using getPackage[Count] and getSchemaId[Count].
         */
        QMF_EXTERN ConsoleEvent querySchema(qpid::messaging::Duration timeout=qpid::messaging::Duration::MINUTE);
        QMF_EXTERN uint32_t querySchemaAsync();

        /**
         * Get the list of schema packages exposed by the agent.
         *
         *   getPackageCount returns the number of packages exposed.
         *   getPackage returns the name of the package by index (0..package-count)
         *
         * Note that both of these calls are synchronous and non-blocking.  They only return locally cached data
         * and will not send any messages to the remote agent.  Use querySchema[Async] to get the latest schema
         * information from the remote agent.
         */
        QMF_EXTERN uint32_t getPackageCount() const;
        QMF_EXTERN const std::string& getPackage(uint32_t) const;

        /**
         * Get the list of schema identifiers for a particular package.
         *
         *   getSchemaIdCount returns the number of IDs in the indicates package.
         *   getSchemaId returns the SchemaId by index (0..schema-id-count)
         *
         * Note that both of these calls are synchronous and non-blocking.  They only return locally cached data
         * and will not send any messages to the remote agent.  Use querySchema[Async] to get the latest schema
         * information from the remote agent.
         */
        QMF_EXTERN uint32_t getSchemaIdCount(const std::string&) const;
        QMF_EXTERN SchemaId getSchemaId(const std::string&, uint32_t) const;

        /**
         * Get detailed schema information for a specified schema ID.
         *
         * This call will return cached information if it is available.  If not, it will send a query message to the
         * remote agent and block waiting for a response.  The timeout argument specifies the maximum time to wait
         * for a response from the agent.
         */
        QMF_EXTERN Schema getSchema(const SchemaId&, qpid::messaging::Duration timeout=qpid::messaging::Duration::MINUTE);


#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<Agent>;
        friend struct AgentImplAccess;
#endif
    };

}

#endif
