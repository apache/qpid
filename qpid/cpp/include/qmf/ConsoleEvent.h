#ifndef QMF_CONSOLE_EVENT_H
#define QMF_CONSOLE_EVENT_H
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
#include "qmf/Agent.h"
#include "qmf/Data.h"
#include "qmf/SchemaId.h"
#include "qpid/types/Variant.h"

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class ConsoleEventImpl;

    enum ConsoleEventCode {
    CONSOLE_AGENT_ADD             = 1,
    CONSOLE_AGENT_DEL             = 2,
    CONSOLE_AGENT_RESTART         = 3,
    CONSOLE_AGENT_SCHEMA_UPDATE   = 4,
    CONSOLE_AGENT_SCHEMA_RESPONSE = 5,
    CONSOLE_EVENT                 = 6,
    CONSOLE_QUERY_RESPONSE        = 7,
    CONSOLE_METHOD_RESPONSE       = 8,
    CONSOLE_EXCEPTION             = 9,
    CONSOLE_SUBSCRIBE_ADD         = 10,
    CONSOLE_SUBSCRIBE_UPDATE      = 11,
    CONSOLE_SUBSCRIBE_DEL         = 12,
    CONSOLE_THREAD_FAILED         = 13
    };

    enum AgentDelReason {
    AGENT_DEL_AGED   = 1,
    AGENT_DEL_FILTER = 2 
    };

    class QMF_CLASS_EXTERN ConsoleEvent : public qmf::Handle<ConsoleEventImpl> {
    public:
        QMF_EXTERN ConsoleEvent(ConsoleEventImpl* impl = 0);
        QMF_EXTERN ConsoleEvent(const ConsoleEvent&);
        QMF_EXTERN ConsoleEvent& operator=(const ConsoleEvent&);
        QMF_EXTERN ~ConsoleEvent();

        QMF_EXTERN ConsoleEventCode getType() const;
        QMF_EXTERN uint32_t getCorrelator() const;
        QMF_EXTERN Agent getAgent() const;
        QMF_EXTERN AgentDelReason getAgentDelReason() const;
        QMF_EXTERN uint32_t getSchemaIdCount() const;
        QMF_EXTERN SchemaId getSchemaId(uint32_t) const;
        QMF_EXTERN uint32_t getDataCount() const;
        QMF_EXTERN Data getData(uint32_t) const;
        QMF_EXTERN bool isFinal() const;
        QMF_EXTERN const qpid::types::Variant::Map& getArguments() const;
        QMF_EXTERN int getSeverity() const;
        QMF_EXTERN uint64_t getTimestamp() const;

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<ConsoleEvent>;
        friend struct ConsoleEventImplAccess;
#endif
    };

}

#endif
