#ifndef _QmfBrokerProxyImpl_
#define _QmfBrokerProxyImpl_

/*
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
 */

#include "qmf/ConsoleEngine.h"
#include "qmf/ObjectImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/ValueImpl.h"
#include "qmf/QueryImpl.h"
#include "qmf/SequenceManager.h"
#include "qmf/MessageImpl.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/Mutex.h"
#include "boost/shared_ptr.hpp"
#include <memory>
#include <string>
#include <deque>
#include <map>
#include <vector>

namespace qmf {

    struct MethodResponseImpl {
        typedef boost::shared_ptr<MethodResponseImpl> Ptr;
        MethodResponse* envelope;
        uint32_t status;
        SchemaMethodImpl* schema;
        boost::shared_ptr<Value> exception;
        boost::shared_ptr<Value> arguments;

        MethodResponseImpl(const MethodResponseImpl& from);
        MethodResponseImpl(qpid::framing::Buffer& buf, SchemaMethodImpl* schema);
        MethodResponseImpl(uint32_t status, const std::string& text);
        ~MethodResponseImpl() { delete envelope; }
        uint32_t getStatus() const { return status; }
        const Value* getException() const { return exception.get(); }
        const Value* getArgs() const { return arguments.get(); }
    };

    struct QueryResponseImpl {
        typedef boost::shared_ptr<QueryResponseImpl> Ptr;
        QueryResponse *envelope;
        uint32_t status;
        std::auto_ptr<Value> exception;
        std::vector<ObjectImpl::Ptr> results;

        QueryResponseImpl() : envelope(new QueryResponse(this)), status(0) {}
        ~QueryResponseImpl() { delete envelope; }
        uint32_t getStatus() const { return status; }
        const Value* getException() const { return exception.get(); }
        uint32_t getObjectCount() const { return results.size(); }
        const Object* getObject(uint32_t idx) const;
    };

    struct BrokerEventImpl {
        typedef boost::shared_ptr<BrokerEventImpl> Ptr;
        BrokerEvent::EventKind kind;
        std::string name;
        std::string exchange;
        std::string bindingKey;
        void* context;
        QueryResponseImpl::Ptr queryResponse;
        MethodResponseImpl::Ptr methodResponse;

        BrokerEventImpl(BrokerEvent::EventKind k) : kind(k), context(0) {}
        ~BrokerEventImpl() {}
        BrokerEvent copy();
    };

    struct AgentProxyImpl {
        typedef boost::shared_ptr<AgentProxyImpl> Ptr;
        AgentProxy* envelope;
        ConsoleEngineImpl* console;
        BrokerProxyImpl* broker;
        uint32_t agentBank;
        std::string label;

        AgentProxyImpl(ConsoleEngineImpl* c, BrokerProxyImpl* b, uint32_t ab, const std::string& l) :
            envelope(new AgentProxy(this)), console(c), broker(b), agentBank(ab), label(l) {}
        ~AgentProxyImpl() {}
        const std::string& getLabel() const { return label; }
    };

    class BrokerProxyImpl {
    public:
        typedef boost::shared_ptr<BrokerProxyImpl> Ptr;

        BrokerProxyImpl(BrokerProxy* e, ConsoleEngine& _console);
        ~BrokerProxyImpl() {}

        void sessionOpened(SessionHandle& sh);
        void sessionClosed();
        void startProtocol();

        void sendBufferLH(qpid::framing::Buffer& buf, const std::string& destination, const std::string& routingKey);
        void handleRcvMessage(Message& message);
        bool getXmtMessage(Message& item) const;
        void popXmt();

        bool getEvent(BrokerEvent& event) const;
        void popEvent();

        uint32_t agentCount() const;
        const AgentProxy* getAgent(uint32_t idx) const;
        void sendQuery(const Query& query, void* context, const AgentProxy* agent);
        void sendGetRequestLH(SequenceContext::Ptr queryContext, const Query& query, const AgentProxyImpl* agent);
        std::string encodeMethodArguments(const SchemaMethod* schema, const Value* args, qpid::framing::Buffer& buffer);
        void sendMethodRequest(ObjectIdImpl* oid, const SchemaObjectClass* cls, const std::string& method, const Value* args, void* context);

        void addBinding(const std::string& exchange, const std::string& key);
        void staticRelease() { decOutstanding(); }

    private:
        friend class StaticContext;
        friend class QueryContext;
        friend class MethodContext;
        mutable qpid::sys::Mutex lock;
        BrokerProxy* envelope;
        ConsoleEngineImpl* console;
        std::string queueName;
        qpid::framing::Uuid brokerId;
        SequenceManager seqMgr;
        uint32_t requestsOutstanding;
        bool topicBound;
        std::vector<AgentProxyImpl::Ptr> agentList;
        std::deque<MessageImpl::Ptr> xmtQueue;
        std::deque<BrokerEventImpl::Ptr> eventQueue;

#       define MA_BUFFER_SIZE 65536
        char outputBuffer[MA_BUFFER_SIZE];

        BrokerEventImpl::Ptr eventDeclareQueue(const std::string& queueName);
        BrokerEventImpl::Ptr eventBind(const std::string& exchange, const std::string& queue, const std::string& key);
        BrokerEventImpl::Ptr eventSetupComplete();
        BrokerEventImpl::Ptr eventStable();
        BrokerEventImpl::Ptr eventQueryComplete(void* context, QueryResponseImpl::Ptr response);
        BrokerEventImpl::Ptr eventMethodResponse(void* context, MethodResponseImpl::Ptr response);

        void handleBrokerResponse(qpid::framing::Buffer& inBuffer, uint32_t seq);
        void handlePackageIndication(qpid::framing::Buffer& inBuffer, uint32_t seq);
        void handleCommandComplete(qpid::framing::Buffer& inBuffer, uint32_t seq);
        void handleClassIndication(qpid::framing::Buffer& inBuffer, uint32_t seq);
        MethodResponseImpl::Ptr handleMethodResponse(qpid::framing::Buffer& inBuffer, uint32_t seq, SchemaMethodImpl* schema);
        void handleHeartbeatIndication(qpid::framing::Buffer& inBuffer, uint32_t seq);
        void handleEventIndication(qpid::framing::Buffer& inBuffer, uint32_t seq);
        void handleSchemaResponse(qpid::framing::Buffer& inBuffer, uint32_t seq);
        ObjectImpl::Ptr handleObjectIndication(qpid::framing::Buffer& inBuffer, uint32_t seq, bool prop, bool stat);
        void incOutstandingLH();
        void decOutstanding();
    };

    //
    // StaticContext is used to handle:
    //
    //  1) Responses to console-level requests (for schema info, etc.)
    //  2) Unsolicited messages from agents (events, published updates, etc.)
    //
    struct StaticContext : public SequenceContext {
        StaticContext(BrokerProxyImpl& b) : broker(b) {}
        virtual ~StaticContext() {}
        void reserve() {}
        void release() { broker.staticRelease(); }
        bool handleMessage(uint8_t opcode, uint32_t sequence, qpid::framing::Buffer& buffer);
        BrokerProxyImpl& broker;
    };

    //
    // QueryContext is used to track and handle responses associated with a single Get Query
    //
    struct QueryContext : public SequenceContext {
        QueryContext(BrokerProxyImpl& b, void* u) :
            broker(b), userContext(u), requestsOutstanding(0), queryResponse(new QueryResponseImpl()) {}
        virtual ~QueryContext() {}
        void reserve();
        void release();
        bool handleMessage(uint8_t opcode, uint32_t sequence, qpid::framing::Buffer& buffer);

        mutable qpid::sys::Mutex lock;
        BrokerProxyImpl& broker;
        void* userContext;
        uint32_t requestsOutstanding;
        QueryResponseImpl::Ptr queryResponse;
    };

    struct MethodContext : public SequenceContext {
        MethodContext(BrokerProxyImpl& b, void* u, SchemaMethodImpl* s) : broker(b), userContext(u), schema(s) {}
        virtual ~MethodContext() {}
        void reserve() {}
        void release();
        bool handleMessage(uint8_t opcode, uint32_t sequence, qpid::framing::Buffer& buffer);

        BrokerProxyImpl& broker;
        void* userContext;
        SchemaMethodImpl* schema;
        MethodResponseImpl::Ptr methodResponse;
    };



}

#endif

