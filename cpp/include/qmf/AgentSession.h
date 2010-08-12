#ifndef QMF_AGENT_SESSION_H
#define QMF_AGENT_SESSION_H
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
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Connection.h"
#include "qpid/types/Variant.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class AgentSessionImpl;
    class AgentEvent;
    class Schema;
    class Data;
    class DataAddr;

    class AgentSession : public qmf::Handle<AgentSessionImpl> {
    public:
        QMF_EXTERN AgentSession(AgentSessionImpl* impl = 0);
        QMF_EXTERN AgentSession(const AgentSession&);
        QMF_EXTERN AgentSession& operator=(const AgentSession&);
        QMF_EXTERN ~AgentSession();

        /**
         *
         * The options string is of the form "{key:value,key:value}".  The following keys are supported:
         *
         *    interval:N                 - Heartbeat interval in seconds [default: 60]
         *    external:{True,False}      - Use external data storage (queries are pass-through) [default: False]
         *    allow-queries:{True,False} - If True:  automatically allow all queries [default]
         *                                 If False: generate an AUTH_QUERY event to allow per-query authorization
         *    allow-methods:{True,False} - If True:  automatically allow all methods [default]
         *                                 If False: generate an AUTH_METHOD event to allow per-method authorization
         */
        QMF_EXTERN AgentSession(qpid::messaging::Connection&, const std::string& options="");
        QMF_EXTERN void setDomain(const std::string&);
        QMF_EXTERN void setVendor(const std::string&);
        QMF_EXTERN void setProduct(const std::string&);
        QMF_EXTERN void setInstance(const std::string&);
        QMF_EXTERN void setAttribute(const std::string&, const qpid::types::Variant&);
        QMF_EXTERN const std::string& getName() const;
        QMF_EXTERN void open();
        QMF_EXTERN void close();
        QMF_EXTERN bool nextEvent(AgentEvent&, qpid::messaging::Duration timeout=qpid::messaging::Duration::FOREVER);

        QMF_EXTERN void registerSchema(Schema&);
        QMF_EXTERN DataAddr addData(Data&, const std::string& name="", bool persistent=false);
        QMF_EXTERN void delData(const DataAddr&);

        QMF_EXTERN void authAccept(AgentEvent&);
        QMF_EXTERN void authReject(AgentEvent&, const std::string& diag="");
        QMF_EXTERN void raiseException(AgentEvent&, const std::string&);
        QMF_EXTERN void raiseException(AgentEvent&, const Data&);
        QMF_EXTERN void response(AgentEvent&, const Data&);
        QMF_EXTERN void complete(AgentEvent&);
        QMF_EXTERN void methodSuccess(AgentEvent&);
        QMF_EXTERN void raiseEvent(const Data&);

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<AgentSession>;
#endif
    };

}

#endif
