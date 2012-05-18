#ifndef _QMF_CONSOLE_SESSION_IMPL_H_
#define _QMF_CONSOLE_SESSION_IMPL_H_
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
#include "qmf/ConsoleSession.h"
#include "qmf/AgentImpl.h"
#include "qmf/SchemaId.h"
#include "qmf/Schema.h"
#include "qmf/ConsoleEventImpl.h"
#include "qmf/EventNotifierImpl.h"
#include "qmf/SchemaCache.h"
#include "qmf/Query.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Condition.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Address.h"
#include "qpid/management/Buffer.h"
#include "qpid/types/Variant.h"

#include <boost/shared_ptr.hpp>
#include <map>
#include <queue>

namespace qmf {
    class ConsoleSessionImpl : public virtual qpid::RefCounted, public qpid::sys::Runnable {
    public:
        ~ConsoleSessionImpl();

        //
        // Methods from API handle
        //
        ConsoleSessionImpl(qpid::messaging::Connection& c, const std::string& o);
        void setDomain(const std::string& d) { domain = d; }
        void setAgentFilter(const std::string& f);
        void open();
        void closeAsync();
        void close();
        bool nextEvent(ConsoleEvent& e, qpid::messaging::Duration t);
        int pendingEvents() const;

        void setEventNotifier(EventNotifierImpl* notifier);
        EventNotifierImpl* getEventNotifier() const;

        uint32_t getAgentCount() const;
        Agent getAgent(uint32_t i) const;
        Agent getConnectedBrokerAgent() const { return connectedBrokerAgent; }
        Subscription subscribe(const Query&, const std::string& agentFilter, const std::string& options);
        Subscription subscribe(const std::string&, const std::string& agentFilter, const std::string& options);

    protected:
        mutable qpid::sys::Mutex lock;
        qpid::sys::Condition cond;
        qpid::messaging::Connection connection;
        qpid::messaging::Session session;
        qpid::messaging::Sender directSender;
        qpid::messaging::Sender topicSender;
        std::string domain;
        uint32_t maxAgentAgeMinutes;
        bool listenOnDirect;
        bool strictSecurity;
        uint32_t maxThreadWaitTime;
        Query agentQuery;
        bool opened;
        std::queue<ConsoleEvent> eventQueue;
        EventNotifierImpl* eventNotifier;
        qpid::sys::Thread* thread;
        bool threadCanceled;
        uint64_t lastVisit;
        uint64_t lastAgePass;
        std::map<std::string, Agent> agents;
        Agent connectedBrokerAgent;
        bool connectedBrokerInAgentList;
        qpid::messaging::Address replyAddress;
        std::string directBase;
        std::string topicBase;
        boost::shared_ptr<SchemaCache> schemaCache;
        qpid::sys::Mutex corrlock;
        uint32_t nextCorrelator;

        void enqueueEvent(const ConsoleEvent&);
        void enqueueEventLH(const ConsoleEvent&);
        void dispatch(qpid::messaging::Message);
        void sendBrokerLocate();
        void sendAgentLocate();
        void handleAgentUpdate(const std::string&, const qpid::types::Variant::Map&, const qpid::messaging::Message&);
        void handleV1SchemaResponse(qpid::management::Buffer&, uint32_t, const qpid::messaging::Message&);
        void periodicProcessing(uint64_t);
        void alertEventNotifierLH(bool readable);
        void run();
        uint32_t correlator() { qpid::sys::Mutex::ScopedLock l(corrlock); return nextCorrelator++; }

        friend class AgentImpl;
    };

    struct ConsoleSessionImplAccess {
        static ConsoleSessionImpl& get(ConsoleSession& session);
        static const ConsoleSessionImpl& get(const ConsoleSession& session);
    };
}

#endif
