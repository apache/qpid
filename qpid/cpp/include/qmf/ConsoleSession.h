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

#if !defined(QMF_USE_DEPRECATED_API) && !defined(qmf2_EXPORTS) && !defined(SWIG)
#  error "The API defined in this file has been DEPRECATED and will be removed in the future."
#  error "Define 'QMF_USE_DEPRECATED_API' to enable continued use of the API."
#endif

#include <qmf/ImportExport.h>
#include "qmf/Handle.h"
#include "qmf/Agent.h"
#include "qmf/Subscription.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Connection.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class ConsoleSessionImpl;
    class ConsoleEvent;

    class QMF_CLASS_EXTERN ConsoleSession : public qmf::Handle<ConsoleSessionImpl> {
    public:
        QMF_EXTERN ConsoleSession(ConsoleSessionImpl* impl = 0);
        QMF_EXTERN ConsoleSession(const ConsoleSession&);
        QMF_EXTERN ConsoleSession& operator=(const ConsoleSession&);
        QMF_EXTERN ~ConsoleSession();

        /**
         * ConsoleSession
         *   A session that runs over an AMQP connection for QMF console operation.
         *
         * @param connection - An opened qpid::messaging::Connection
         * @param options - An optional string containing options
         *
         * The options string is of the form "{key:value,key:value}".  The following keys are supported:
         *
         *    domain:NAME                - QMF Domain to join [default: "default"]
         *    max-agent-age:N            - Maximum time, in minutes, that we will tolerate not hearing from
         *                                 an agent before deleting it [default: 5]
         *    listen-on-direct:{True,False} - If True:  Listen on legacy direct-exchange address for backward compatibility [default]
         *                                    If False: Listen only on the routable direct address
         *    strict-security:{True,False}  - If True:  Cooperate with the broker to enforce strict access control to the network
         *                                  - If False: Operate more flexibly with regard to use of messaging facilities [default]
         *    max-thread-wait-time:N     - Time (in seconds) the session thread will wait for messages from the network between
         *                                 periodic background processing passes.
         *                                 Must not be greater than 60.  Larger numbers will cause fewer wake-ups but will
         *                                 increase the time it takes to shut down the process. [default: 5]
         */
        QMF_EXTERN ConsoleSession(qpid::messaging::Connection& conn, const std::string& options="");

        /**
         * setDomain - Change the QMF domain that this console will operate in.  If this is not called,
         * the domain will be "default".  Agents in a domain can be seen only by consoles in the same domain.
         * This must be called prior to opening the console session.
         */
        QMF_EXTERN void setDomain(const std::string& domain);
        QMF_EXTERN void setAgentFilter(const std::string& filter);

        /**
         * Open the console session.  After opening the session, the domain cannot be changed.
         */
        QMF_EXTERN void open();

        /**
         * Close the session.  Once closed, the session no longer communicates on the messaging network.
         */
        QMF_EXTERN void close();

        /**
         * Get the next event from the console session.  Events represent actions that must be acted upon by the
         * console application.  This method blocks for up to the timeout if there are no events to be handled.
         * This method will typically be the focus of the console application's main execution loop.
         * If the timeout is set to Duration::IMMEDIATE, the call will not block.
         */
        QMF_EXTERN bool nextEvent(ConsoleEvent& outEvent, qpid::messaging::Duration timeout=qpid::messaging::Duration::FOREVER);

        /**
         * Return the number of events pending for nextEvent.  This method will never block.
         */
        QMF_EXTERN int pendingEvents() const;

        /**
         * getAgentCount, getAgent - Retrieve the set of agents that match the console session's agent filter.
         */
        QMF_EXTERN uint32_t getAgentCount() const;
        QMF_EXTERN Agent getAgent(uint32_t agentIndex) const;

        /**
         * Get the agent for the connected broker (i.e. the agent embedded in the broker to which we have a connection).
         */
        QMF_EXTERN Agent getConnectedBrokerAgent() const;

        /**
         * Create a subscription that involves a subset of the known agents.  The set of known agents is defined by
         * the session's agent-filter (see setAgentFilter).  The agentFilter argument to the subscribe method is used
         * to further refine the set of agents.  If agentFilter is the empty string (i.e. match-all) the subscription
         * will involve all known agents.  If agentFilter is non-empty, it will be applied only to the set of known
         * agents.  A subscription cannot be created that involves an agent not known by the session.
         */
        QMF_EXTERN Subscription subscribe(const Query& query,       const std::string& agentFilter = "", const std::string& options = "");
        QMF_EXTERN Subscription subscribe(const std::string& query, const std::string& agentFilter = "", const std::string& options = "");

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<ConsoleSession>;
        friend struct ConsoleSessionImplAccess;
#endif
    };

}

#endif
