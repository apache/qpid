#ifndef __QMF_AGENT_SESSION_IMPL_H
#define __QMF_AGENT_SESSION_IMPL_H

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
#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"
#include "qmf/AgentSession.h"
#include "qmf/AgentEventImpl.h"
#include "qmf/EventNotifierImpl.h"
#include "qpid/messaging/Connection.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Condition.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/management/Buffer.h"
#include "qpid/RefCounted.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/AgentSession.h"
#include "qmf/exceptions.h"
#include "qmf/AgentSession.h"
#include "qmf/SchemaIdImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/DataAddrImpl.h"
#include "qmf/DataImpl.h"
#include "qmf/QueryImpl.h"
#include "qmf/agentCapability.h"
#include "qmf/constants.h"

#include <queue>
#include <map>
#include <iostream>
#include <memory>

using namespace std;
using namespace qpid::messaging;
using namespace qmf;
using qpid::types::Variant;
using namespace boost;

typedef qmf::PrivateImplRef<AgentSession> PI;

namespace qmf {
    class AgentSessionImpl : public virtual qpid::RefCounted, public qpid::sys::Runnable {
    public:
        ~AgentSessionImpl();

        //
        // Methods from API handle
        //
        AgentSessionImpl(Connection& c, const string& o);
        void setDomain(const string& d) { checkOpen(); domain = d; }
        void setVendor(const string& v) { checkOpen(); attributes["_vendor"] = v; }
        void setProduct(const string& p) { checkOpen(); attributes["_product"] = p; }
        void setInstance(const string& i) { checkOpen(); attributes["_instance"] = i; }
        void setAttribute(const string& k, const qpid::types::Variant& v) { checkOpen(); attributes[k] = v; }
        const string& getName() const { return agentName; }
        void open();
        void closeAsync();
        void close();
        bool nextEvent(AgentEvent& e, Duration t);
        int pendingEvents() const;

        void setEventNotifier(EventNotifierImpl* eventNotifier);
        EventNotifierImpl* getEventNotifier() const;

        void registerSchema(Schema& s);
        DataAddr addData(Data& d, const string& n, bool persist);
        void delData(const DataAddr&);

        void authAccept(AgentEvent& e);
        void authReject(AgentEvent& e, const string& m);
        void raiseException(AgentEvent& e, const string& s);
        void raiseException(AgentEvent& e, const Data& d);
        void response(AgentEvent& e, const Data& d);
        void complete(AgentEvent& e);
        void methodSuccess(AgentEvent& e);
        void raiseEvent(const Data& d);
        void raiseEvent(const Data& d, int s);

    private:
        typedef map<DataAddr, Data, DataAddrCompare> DataIndex;
        typedef map<SchemaId, Schema, SchemaIdCompare> SchemaMap;

        mutable qpid::sys::Mutex lock;
        qpid::sys::Condition cond;
        Connection connection;
        Session session;
        Sender directSender;
        Sender topicSender;
        string domain;
        Variant::Map attributes;
        Variant::Map options;
        string agentName;
        bool opened;
        queue<AgentEvent> eventQueue;
        EventNotifierImpl* eventNotifier;
        qpid::sys::Thread* thread;
        bool threadCanceled;
        uint32_t bootSequence;
        uint32_t interval;
        uint64_t lastHeartbeat;
        uint64_t lastVisit;
        bool forceHeartbeat;
        bool externalStorage;
        bool autoAllowQueries;
        bool autoAllowMethods;
        uint32_t maxSubscriptions;
        uint32_t minSubInterval;
        uint32_t subLifetime;
        bool publicEvents;
        bool listenOnDirect;
        bool strictSecurity;
        uint32_t maxThreadWaitTime;
        uint64_t schemaUpdateTime;
        string directBase;
        string topicBase;

        SchemaMap schemata;
        DataIndex globalIndex;
        map<SchemaId, DataIndex, SchemaIdCompareNoHash> schemaIndex;

        void checkOpen();
        void setAgentName();
        void enqueueEvent(const AgentEvent&);
        void alertEventNotifierLH(bool readable);
        void handleLocateRequest(const Variant::List& content, const Message& msg);
        void handleMethodRequest(const Variant::Map& content, const Message& msg);
        void handleQueryRequest(const Variant::Map& content, const Message& msg);
        void handleSchemaRequest(AgentEvent&);
        void handleV1SchemaRequest(qpid::management::Buffer&, uint32_t, const Message&);
        void dispatch(Message);
        void sendHeartbeat();
        void send(Message, const Address&);
        void flushResponses(AgentEvent&, bool);
        void periodicProcessing(uint64_t);
        void run();
    };

    struct AgentSessionImplAccess {
        static AgentSessionImpl& get(AgentSession& session);
        static const AgentSessionImpl& get(const AgentSession& session);
    };
}


#endif

