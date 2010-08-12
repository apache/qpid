#ifndef QMF_CONSOLE_SESSION_H
#define QMF_CONSOLE_SESSION_H
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
#include "qmf/Agent.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Connection.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class ConsoleSessionImpl;
    class ConsoleEvent;

    class ConsoleSession : public qmf::Handle<ConsoleSessionImpl> {
    public:
        QMF_EXTERN ConsoleSession(ConsoleSessionImpl* impl = 0);
        QMF_EXTERN ConsoleSession(const ConsoleSession&);
        QMF_EXTERN ConsoleSession& operator=(const ConsoleSession&);
        QMF_EXTERN ~ConsoleSession();

        QMF_EXTERN ConsoleSession(qpid::messaging::Connection&, const std::string& options="");
        QMF_EXTERN void setDomain(const std::string&);
        QMF_EXTERN void setAgentFilter(const std::string&);
        QMF_EXTERN void open();
        QMF_EXTERN void close();
        QMF_EXTERN bool nextEvent(ConsoleEvent&, qpid::messaging::Duration timeout=qpid::messaging::Duration::FOREVER);
        QMF_EXTERN uint32_t getAgentCount() const;
        QMF_EXTERN Agent getAgent(uint32_t) const;
        QMF_EXTERN Agent getConnectedBrokerAgent() const;

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<ConsoleSession>;
#endif
    };

}

#endif
