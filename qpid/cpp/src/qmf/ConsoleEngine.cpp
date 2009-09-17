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
#include "qmf/MessageImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/Typecode.h"
#include "qmf/ObjectImpl.h"
#include "qmf/ObjectIdImpl.h"
#include "qmf/QueryImpl.h"
#include "qmf/ValueImpl.h"
#include "qmf/Protocol.h"
#include "qmf/SequenceManager.h"
#include <qpid/framing/Buffer.h>
#include <qpid/framing/Uuid.h>
#include <qpid/framing/FieldTable.h>
#include <qpid/framing/FieldValue.h>
#include <qpid/sys/Mutex.h>
#include <qpid/log/Statement.h>
#include <qpid/sys/Time.h>
#include <qpid/sys/SystemInfo.h>
#include <string.h>
#include <string>
#include <deque>
#include <map>
#include <vector>
#include <iostream>
#include <fstream>
#include <boost/shared_ptr.hpp>

using namespace std;
using namespace qmf;
using namespace qpid::framing;
using namespace qpid::sys;

namespace qmf {

    struct MethodResponseImpl {
        typedef boost::shared_ptr<MethodResponseImpl> Ptr;
        MethodResponse* envelope;
        uint32_t status;
        auto_ptr<Value> exception;
        auto_ptr<Value> arguments;

        MethodResponseImpl(Buffer& buf);
        ~MethodResponseImpl() { delete envelope; }
        uint32_t getStatus() const { return status; }
        const Value* getException() const { return exception.get(); }
        const Value* getArgs() const { return arguments.get(); }
    };

    struct QueryResponseImpl {
        typedef boost::shared_ptr<QueryResponseImpl> Ptr;
        QueryResponse *envelope;
        uint32_t status;
        auto_ptr<Value> exception;
        vector<ObjectImpl::Ptr> results;

        QueryResponseImpl() : envelope(new QueryResponse(this)), status(0) {}
        ~QueryResponseImpl() { delete envelope; }
        uint32_t getStatus() const { return status; }
        const Value* getException() const { return exception.get(); }
        uint32_t getObjectCount() const { return results.size(); }
        const Object* getObject(uint32_t idx) const;
    };

    struct ConsoleEventImpl {
        typedef boost::shared_ptr<ConsoleEventImpl> Ptr;
        ConsoleEvent::EventKind kind;
        boost::shared_ptr<AgentProxyImpl> agent;
        string name;
        boost::shared_ptr<SchemaClassKey> classKey;
        Object* object;
        void* context;
        Event* event;
        uint64_t timestamp;
        uint32_t methodHandle;
        MethodResponseImpl::Ptr methodResponse;

        ConsoleEventImpl(ConsoleEvent::EventKind k) :
            kind(k), object(0), context(0), event(0), timestamp(0), methodHandle(0) {}
        ~ConsoleEventImpl() {}
        ConsoleEvent copy();
    };

    struct BrokerEventImpl {
        typedef boost::shared_ptr<BrokerEventImpl> Ptr;
        BrokerEvent::EventKind kind;
        string name;
        string exchange;
        string bindingKey;
        void* context;
        QueryResponseImpl::Ptr queryResponse;

        BrokerEventImpl(BrokerEvent::EventKind k) : kind(k) {}
        ~BrokerEventImpl() {}
        BrokerEvent copy();
    };

    struct AgentProxyImpl {
        typedef boost::shared_ptr<AgentProxyImpl> Ptr;
        AgentProxy* envelope;
        ConsoleEngineImpl* console;
        BrokerProxyImpl* broker;
        uint32_t agentBank;
        string label;

        AgentProxyImpl(ConsoleEngineImpl* c, BrokerProxyImpl* b, uint32_t ab, const string& l) :
            envelope(new AgentProxy(this)), console(c), broker(b), agentBank(ab), label(l) {}
        ~AgentProxyImpl() {}
        const string& getLabel() const { return label; }
    };

    class BrokerProxyImpl {
    public:
        typedef boost::shared_ptr<BrokerProxyImpl> Ptr;

        BrokerProxyImpl(BrokerProxy* e, ConsoleEngine& _console);
        ~BrokerProxyImpl() {}

        void sessionOpened(SessionHandle& sh);
        void sessionClosed();
        void startProtocol();

        void sendBufferLH(Buffer& buf, const string& destination, const string& routingKey);
        void handleRcvMessage(Message& message);
        bool getXmtMessage(Message& item) const;
        void popXmt();

        bool getEvent(BrokerEvent& event) const;
        void popEvent();

        uint32_t agentCount() const;
        const AgentProxy* getAgent(uint32_t idx) const;
        void sendQuery(const Query& query, void* context, const AgentProxy* agent);
        void sendGetRequestLH(SequenceContext::Ptr queryContext, const Query& query, const AgentProxyImpl* agent);

        void addBinding(const string& exchange, const string& key);
        void staticRelease() { decOutstanding(); }

    private:
        friend class StaticContext;
        friend class QueryContext;
        mutable Mutex lock;
        BrokerProxy* envelope;
        ConsoleEngineImpl* console;
        string queueName;
        Uuid brokerId;
        SequenceManager seqMgr;
        uint32_t requestsOutstanding;
        bool topicBound;
        vector<AgentProxyImpl::Ptr> agentList;
        deque<MessageImpl::Ptr> xmtQueue;
        deque<BrokerEventImpl::Ptr> eventQueue;

#       define MA_BUFFER_SIZE 65536
        char outputBuffer[MA_BUFFER_SIZE];

        BrokerEventImpl::Ptr eventDeclareQueue(const string& queueName);
        BrokerEventImpl::Ptr eventBind(const string& exchange, const string& queue, const string& key);
        BrokerEventImpl::Ptr eventSetupComplete();
        BrokerEventImpl::Ptr eventStable();
        BrokerEventImpl::Ptr eventQueryComplete(void* context, QueryResponseImpl::Ptr response);

        void handleBrokerResponse(Buffer& inBuffer, uint32_t seq);
        void handlePackageIndication(Buffer& inBuffer, uint32_t seq);
        void handleCommandComplete(Buffer& inBuffer, uint32_t seq);
        void handleClassIndication(Buffer& inBuffer, uint32_t seq);
        void handleMethodResponse(Buffer& inBuffer, uint32_t seq);
        void handleHeartbeatIndication(Buffer& inBuffer, uint32_t seq);
        void handleEventIndication(Buffer& inBuffer, uint32_t seq);
        void handleSchemaResponse(Buffer& inBuffer, uint32_t seq);
        ObjectImpl::Ptr handleObjectIndication(Buffer& inBuffer, uint32_t seq, bool prop, bool stat);
        void incOutstandingLH();
        void decOutstanding();
    };

    struct StaticContext : public SequenceContext {
        StaticContext(BrokerProxyImpl& b) : broker(b) {}
        ~StaticContext() {}
        void reserve() {}
        void release() { broker.staticRelease(); }
        bool handleMessage(uint8_t opcode, uint32_t sequence, Buffer& buffer);
        BrokerProxyImpl& broker;
    };

    struct QueryContext : public SequenceContext {
        QueryContext(BrokerProxyImpl& b, void* u) :
            broker(b), userContext(u), requestsOutstanding(0), queryResponse(new QueryResponseImpl()) {}
        ~QueryContext() {}
        void reserve();
        void release();
        bool handleMessage(uint8_t opcode, uint32_t sequence, Buffer& buffer);

        mutable Mutex lock;
        BrokerProxyImpl& broker;
        void* userContext;
        uint32_t requestsOutstanding;
        QueryResponseImpl::Ptr queryResponse;
    };

    class ConsoleEngineImpl {
    public:
        ConsoleEngineImpl(ConsoleEngine* e, const ConsoleSettings& settings = ConsoleSettings());
        ~ConsoleEngineImpl();

        bool getEvent(ConsoleEvent& event) const;
        void popEvent();

        void addConnection(BrokerProxy& broker, void* context);
        void delConnection(BrokerProxy& broker);

        uint32_t packageCount() const;
        const string& getPackageName(uint32_t idx) const;

        uint32_t classCount(const char* packageName) const;
        const SchemaClassKey* getClass(const char* packageName, uint32_t idx) const;

        ClassKind getClassKind(const SchemaClassKey* key) const;
        const SchemaObjectClass* getObjectClass(const SchemaClassKey* key) const;
        const SchemaEventClass* getEventClass(const SchemaClassKey* key) const;

        void bindPackage(const char* packageName);
        void bindClass(const SchemaClassKey* key);
        void bindClass(const char* packageName, const char* className);

        /*
        void startSync(const Query& query, void* context, SyncQuery& sync);
        void touchSync(SyncQuery& sync);
        void endSync(SyncQuery& sync);
        */

    private:
        friend class BrokerProxyImpl;
        ConsoleEngine* envelope;
        const ConsoleSettings& settings;
        mutable Mutex lock;
        deque<ConsoleEventImpl::Ptr> eventQueue;
        vector<BrokerProxyImpl*> brokerList;
        vector<pair<string, string> > bindingList; // exchange/key (empty exchange => QMF_EXCHANGE)

        // Declare a compare class for the class maps that compares the dereferenced
        // class key pointers.  The default behavior would be to compare the pointer
        // addresses themselves.
        struct KeyCompare {
            bool operator()(const SchemaClassKeyImpl* left, const SchemaClassKeyImpl* right) const {
                return *left < *right;
            }
        };

        typedef map<const SchemaClassKeyImpl*, SchemaObjectClassImpl::Ptr, KeyCompare> ObjectClassList;
        typedef map<const SchemaClassKeyImpl*, SchemaEventClassImpl::Ptr, KeyCompare> EventClassList;
        typedef map<string, pair<ObjectClassList, EventClassList> > PackageList;

        PackageList packages;

        void learnPackage(const string& packageName);
        void learnClass(SchemaObjectClassImpl::Ptr cls);
        void learnClass(SchemaEventClassImpl::Ptr cls);
        bool haveClass(const SchemaClassKeyImpl& key) const;
        SchemaObjectClassImpl::Ptr getSchema(const SchemaClassKeyImpl& key) const;
    };
}

namespace {
    const char* QMF_EXCHANGE     = "qpid.management";
    const char* DIR_EXCHANGE     = "amq.direct";
    const char* BROKER_KEY       = "broker";
    const char* BROKER_PACKAGE   = "org.apache.qpid.broker";
    const char* AGENT_CLASS      = "agent";
    const char* BROKER_AGENT_KEY = "agent.1.0";
}

const Object* QueryResponseImpl::getObject(uint32_t idx) const
{
    vector<ObjectImpl::Ptr>::const_iterator iter = results.begin();

    while (idx > 0) {
        if (iter == results.end())
            return 0;
        iter++;
        idx--;
    }

    return (*iter)->envelope;
}

#define STRING_REF(s) {if (!s.empty()) item.s = const_cast<char*>(s.c_str());}

ConsoleEvent ConsoleEventImpl::copy()
{
    ConsoleEvent item;

    ::memset(&item, 0, sizeof(ConsoleEvent));
    item.kind           = kind;
    item.agent          = agent.get() ? agent->envelope : 0;
    item.classKey       = classKey.get();
    item.object         = object;
    item.context        = context;
    item.event          = event;
    item.timestamp      = timestamp;
    item.methodHandle   = methodHandle;
    item.methodResponse = methodResponse.get() ? methodResponse->envelope : 0;

    STRING_REF(name);

    return item;
}

BrokerEvent BrokerEventImpl::copy()
{
    BrokerEvent item;

    ::memset(&item, 0, sizeof(BrokerEvent));
    item.kind = kind;

    STRING_REF(name);
    STRING_REF(exchange);
    STRING_REF(bindingKey);
    item.context = context;
    item.queryResponse = queryResponse.get() ? queryResponse->envelope : 0;

    return item;
}

BrokerProxyImpl::BrokerProxyImpl(BrokerProxy* e, ConsoleEngine& _console) :
    envelope(e), console(_console.impl)
{
    stringstream qn;
    qpid::TcpAddress addr;

    SystemInfo::getLocalHostname(addr);
    qn << "qmfc-" << SystemInfo::getProcessName() << "-" << addr << "-" << SystemInfo::getProcessId();
    queueName = qn.str();

    seqMgr.setUnsolicitedContext(SequenceContext::Ptr(new StaticContext(*this)));
}

void BrokerProxyImpl::sessionOpened(SessionHandle& /*sh*/)
{
    Mutex::ScopedLock _lock(lock);
    agentList.clear();
    eventQueue.clear();
    xmtQueue.clear();
    eventQueue.push_back(eventDeclareQueue(queueName));
    eventQueue.push_back(eventBind(DIR_EXCHANGE, queueName, queueName));
    eventQueue.push_back(eventSetupComplete());

    // TODO: Store session handle
}

void BrokerProxyImpl::sessionClosed()
{
    Mutex::ScopedLock _lock(lock);
    agentList.clear();
    eventQueue.clear();
    xmtQueue.clear();
}

void BrokerProxyImpl::startProtocol()
{
    Mutex::ScopedLock _lock(lock);
    char rawbuffer[512];
    Buffer buffer(rawbuffer, 512);

    agentList.push_back(AgentProxyImpl::Ptr(new AgentProxyImpl(console, this, 0, "Agent embedded in broker")));

    requestsOutstanding = 1;
    topicBound = false;
    uint32_t sequence(seqMgr.reserve());
    Protocol::encodeHeader(buffer, Protocol::OP_BROKER_REQUEST, sequence);
    sendBufferLH(buffer, QMF_EXCHANGE, BROKER_KEY);
    QPID_LOG(trace, "SENT BrokerRequest seq=" << sequence);
}

void BrokerProxyImpl::sendBufferLH(Buffer& buf, const string& destination, const string& routingKey)
{
    uint32_t length = buf.getPosition();
    MessageImpl::Ptr message(new MessageImpl);

    buf.reset();
    buf.getRawData(message->body, length);
    message->destination   = destination;
    message->routingKey    = routingKey;
    message->replyExchange = DIR_EXCHANGE;
    message->replyKey      = queueName;

    xmtQueue.push_back(message);
}

void BrokerProxyImpl::handleRcvMessage(Message& message)
{
    Buffer inBuffer(message.body, message.length);
    uint8_t opcode;
    uint32_t sequence;

    while (Protocol::checkHeader(inBuffer, &opcode, &sequence))
        seqMgr.dispatch(opcode, sequence, inBuffer);
}

bool BrokerProxyImpl::getXmtMessage(Message& item) const
{
    Mutex::ScopedLock _lock(lock);
    if (xmtQueue.empty())
        return false;
    item =  xmtQueue.front()->copy();
    return true;
}

void BrokerProxyImpl::popXmt()
{
    Mutex::ScopedLock _lock(lock);
    if (!xmtQueue.empty())
        xmtQueue.pop_front();
}

bool BrokerProxyImpl::getEvent(BrokerEvent& event) const
{
    Mutex::ScopedLock _lock(lock);
    if (eventQueue.empty())
        return false;
    event = eventQueue.front()->copy();
    return true;
}

void BrokerProxyImpl::popEvent()
{
    Mutex::ScopedLock _lock(lock);
    if (!eventQueue.empty())
        eventQueue.pop_front();
}

uint32_t BrokerProxyImpl::agentCount() const
{
    Mutex::ScopedLock _lock(lock);
    return agentList.size();
}

const AgentProxy* BrokerProxyImpl::getAgent(uint32_t idx) const
{
    Mutex::ScopedLock _lock(lock);
    for (vector<AgentProxyImpl::Ptr>::const_iterator iter = agentList.begin();
         iter != agentList.end(); iter++)
        if (idx-- == 0)
            return (*iter)->envelope;
    return 0;
}

void BrokerProxyImpl::sendQuery(const Query& query, void* context, const AgentProxy* agent)
{
    SequenceContext::Ptr queryContext(new QueryContext(*this, context));
    Mutex::ScopedLock _lock(lock);
    if (agent != 0) {
        sendGetRequestLH(queryContext, query, agent->impl);
    } else {
        // TODO (optimization) only send queries to agents that have the requested class+package
        for (vector<AgentProxyImpl::Ptr>::const_iterator iter = agentList.begin();
             iter != agentList.end(); iter++) {
            sendGetRequestLH(queryContext, query, (*iter).get());
        }
    }
}

void BrokerProxyImpl::sendGetRequestLH(SequenceContext::Ptr queryContext, const Query& query, const AgentProxyImpl* agent)
{
    stringstream key;
    Buffer outBuffer(outputBuffer, MA_BUFFER_SIZE);
    uint32_t sequence(seqMgr.reserve(queryContext));

    Protocol::encodeHeader(outBuffer, Protocol::OP_GET_QUERY, sequence);
    query.impl->encode(outBuffer);
    key << "agent.1." << agent->agentBank;
    sendBufferLH(outBuffer, QMF_EXCHANGE, key.str());
    QPID_LOG(trace, "SENT GetQuery seq=" << sequence << " key=" << key.str());
}

void BrokerProxyImpl::addBinding(const string& exchange, const string& key)
{
    eventQueue.push_back(eventBind(exchange, queueName, key));
}

BrokerEventImpl::Ptr BrokerProxyImpl::eventDeclareQueue(const string& queueName)
{
    BrokerEventImpl::Ptr event(new BrokerEventImpl(BrokerEvent::DECLARE_QUEUE));
    event->name = queueName;
    return event;
}

BrokerEventImpl::Ptr BrokerProxyImpl::eventBind(const string& exchange, const string& queue, const string& key)
{
    BrokerEventImpl::Ptr event(new BrokerEventImpl(BrokerEvent::BIND));
    event->name       = queue;
    event->exchange   = exchange;
    event->bindingKey = key;

    return event;
}

BrokerEventImpl::Ptr BrokerProxyImpl::eventSetupComplete()
{
    BrokerEventImpl::Ptr event(new BrokerEventImpl(BrokerEvent::SETUP_COMPLETE));
    return event;
}

BrokerEventImpl::Ptr BrokerProxyImpl::eventStable()
{
    BrokerEventImpl::Ptr event(new BrokerEventImpl(BrokerEvent::STABLE));
    return event;
}

BrokerEventImpl::Ptr BrokerProxyImpl::eventQueryComplete(void* context, QueryResponseImpl::Ptr response)
{
    BrokerEventImpl::Ptr event(new BrokerEventImpl(BrokerEvent::QUERY_COMPLETE));
    event->context = context;
    event->queryResponse = response;
    return event;
}

void BrokerProxyImpl::handleBrokerResponse(Buffer& inBuffer, uint32_t seq)
{
    brokerId.decode(inBuffer);
    QPID_LOG(trace, "RCVD BrokerResponse seq=" << seq << " brokerId=" << brokerId);
    Mutex::ScopedLock _lock(lock);
    Buffer outBuffer(outputBuffer, MA_BUFFER_SIZE);
    uint32_t sequence(seqMgr.reserve());
    incOutstandingLH();
    Protocol::encodeHeader(outBuffer, Protocol::OP_PACKAGE_REQUEST, sequence);
    sendBufferLH(outBuffer, QMF_EXCHANGE, BROKER_KEY);
    QPID_LOG(trace, "SENT PackageRequest seq=" << sequence);
}

void BrokerProxyImpl::handlePackageIndication(Buffer& inBuffer, uint32_t seq)
{
    string package;

    inBuffer.getShortString(package);
    QPID_LOG(trace, "RCVD PackageIndication seq=" << seq << " package=" << package);
    console->learnPackage(package);

    Mutex::ScopedLock _lock(lock);
    Buffer outBuffer(outputBuffer, MA_BUFFER_SIZE);
    uint32_t sequence(seqMgr.reserve());
    incOutstandingLH();
    Protocol::encodeHeader(outBuffer, Protocol::OP_CLASS_QUERY, sequence);
    outBuffer.putShortString(package);
    sendBufferLH(outBuffer, QMF_EXCHANGE, BROKER_KEY);
    QPID_LOG(trace, "SENT ClassQuery seq=" << sequence << " package=" << package);
}

void BrokerProxyImpl::handleCommandComplete(Buffer& inBuffer, uint32_t seq)
{
    string text;
    uint32_t code = inBuffer.getLong();
    inBuffer.getShortString(text);
    QPID_LOG(trace, "RCVD CommandComplete seq=" << seq << " code=" << code << " text=" << text);
}

void BrokerProxyImpl::handleClassIndication(Buffer& inBuffer, uint32_t seq)
{
    uint8_t kind = inBuffer.getOctet();
    SchemaClassKeyImpl classKey(inBuffer);

    QPID_LOG(trace, "RCVD ClassIndication seq=" << seq << " kind=" << (int) kind << " key=" << classKey.str());

    if (!console->haveClass(classKey)) {
        Mutex::ScopedLock _lock(lock);
        incOutstandingLH();
        Buffer outBuffer(outputBuffer, MA_BUFFER_SIZE);
        uint32_t sequence(seqMgr.reserve());
        Protocol::encodeHeader(outBuffer, Protocol::OP_SCHEMA_REQUEST, sequence);
        classKey.encode(outBuffer);
        sendBufferLH(outBuffer, QMF_EXCHANGE, BROKER_KEY);
        QPID_LOG(trace, "SENT SchemaRequest seq=" << sequence <<" key=" << classKey.str());
    }
}

void BrokerProxyImpl::handleMethodResponse(Buffer& /*inBuffer*/, uint32_t /*seq*/)
{
    // TODO
}

void BrokerProxyImpl::handleHeartbeatIndication(Buffer& /*inBuffer*/, uint32_t /*seq*/)
{
    // TODO
}

void BrokerProxyImpl::handleEventIndication(Buffer& /*inBuffer*/, uint32_t /*seq*/)
{
    // TODO
}

void BrokerProxyImpl::handleSchemaResponse(Buffer& inBuffer, uint32_t seq)
{
    SchemaObjectClassImpl::Ptr oClassPtr;
    SchemaEventClassImpl::Ptr eClassPtr;
    uint8_t kind = inBuffer.getOctet();
    const SchemaClassKeyImpl* key;
    if (kind == CLASS_OBJECT) {
        oClassPtr.reset(new SchemaObjectClassImpl(inBuffer));
        console->learnClass(oClassPtr);
        key = oClassPtr->getClassKey()->impl;
        QPID_LOG(trace, "RCVD SchemaResponse seq=" << seq << " kind=object key=" << key->str());

        //
        // If we have just learned about the org.apache.qpid.broker:agent class, send a get
        // request for the current list of agents so we can have it on-hand before we declare
        // this session "stable".
        //
        if (key->getClassName() == AGENT_CLASS && key->getPackageName() == BROKER_PACKAGE) {
            Mutex::ScopedLock _lock(lock);
            incOutstandingLH();
            Buffer outBuffer(outputBuffer, MA_BUFFER_SIZE);
            uint32_t sequence(seqMgr.reserve());
            Protocol::encodeHeader(outBuffer, Protocol::OP_GET_QUERY, sequence);
            FieldTable ft;
            ft.setString("_class", AGENT_CLASS);
            ft.setString("_package", BROKER_PACKAGE);
            ft.encode(outBuffer);
            sendBufferLH(outBuffer, QMF_EXCHANGE, BROKER_AGENT_KEY);
            QPID_LOG(trace, "SENT GetQuery seq=" << sequence << " key=" << BROKER_AGENT_KEY);
        }
    } else if (kind == CLASS_EVENT) {
        eClassPtr.reset(new SchemaEventClassImpl(inBuffer));
        console->learnClass(eClassPtr);
        key = eClassPtr->getClassKey()->impl;
        QPID_LOG(trace, "RCVD SchemaResponse seq=" << seq << " kind=event key=" << key->str());
    }
    else {
        QPID_LOG(error, "BrokerProxyImpl::handleSchemaResponse received unknown class kind: " << (int) kind);
    }
}

ObjectImpl::Ptr BrokerProxyImpl::handleObjectIndication(Buffer& inBuffer, uint32_t seq, bool prop, bool stat)
{
    SchemaClassKeyImpl classKey(inBuffer);
    QPID_LOG(trace, "RCVD ObjectIndication seq=" << seq << " key=" << classKey.str());

    SchemaObjectClassImpl::Ptr schema = console->getSchema(classKey);
    if (schema.get() == 0) {
        QPID_LOG(trace, "No Schema Found for ObjectIndication. seq=" << seq << " key=" << classKey.str());
        return ObjectImpl::Ptr();
    }

    return ObjectImpl::Ptr(new ObjectImpl(schema->envelope, inBuffer, prop, stat, true));
}

void BrokerProxyImpl::incOutstandingLH()
{
    requestsOutstanding++;
}

void BrokerProxyImpl::decOutstanding()
{
    Mutex::ScopedLock _lock(lock);
    requestsOutstanding--;
    if (requestsOutstanding == 0 && !topicBound) {
        topicBound = true;
        for (vector<pair<string, string> >::const_iterator iter = console->bindingList.begin();
             iter != console->bindingList.end(); iter++) {
            string exchange(iter->first.empty() ? QMF_EXCHANGE : iter->first);
            string key(iter->second);
            eventQueue.push_back(eventBind(exchange, queueName, key));
        }
        eventQueue.push_back(eventStable());
    }
}

MethodResponseImpl::MethodResponseImpl(Buffer& buf) : envelope(new MethodResponse(this))
{
    string text;

    status = buf.getLong();
    buf.getMediumString(text);
    exception.reset(new Value(TYPE_LSTR));
    exception->setString(text.c_str());

    // TODO: Parse schema-specific output arguments.
    arguments.reset(new Value(TYPE_MAP));
}

bool StaticContext::handleMessage(uint8_t opcode, uint32_t sequence, Buffer& buffer)
{
    bool completeContext = false;
    if      (opcode == Protocol::OP_BROKER_RESPONSE) {
        broker.handleBrokerResponse(buffer, sequence);
        completeContext = true;
    }
    else if (opcode == Protocol::OP_COMMAND_COMPLETE) {
        broker.handleCommandComplete(buffer, sequence);
        completeContext = true;
    }
    else if (opcode == Protocol::OP_SCHEMA_RESPONSE) {
        broker.handleSchemaResponse(buffer, sequence);
        completeContext = true;
    }
    else if (opcode == Protocol::OP_PACKAGE_INDICATION)
        broker.handlePackageIndication(buffer, sequence);
    else if (opcode == Protocol::OP_CLASS_INDICATION)
        broker.handleClassIndication(buffer, sequence);
    else if (opcode == Protocol::OP_HEARTBEAT_INDICATION)
        broker.handleHeartbeatIndication(buffer, sequence);
    else if (opcode == Protocol::OP_EVENT_INDICATION)
        broker.handleEventIndication(buffer, sequence);
    else if (opcode == Protocol::OP_PROPERTY_INDICATION)
        broker.handleObjectIndication(buffer, sequence, true,  false);
    else if (opcode == Protocol::OP_STATISTIC_INDICATION)
        broker.handleObjectIndication(buffer, sequence, false, true);
    else if (opcode == Protocol::OP_OBJECT_INDICATION)
        broker.handleObjectIndication(buffer, sequence, true,  true);
    else {
        QPID_LOG(trace, "StaticContext::handleMessage invalid opcode: " << opcode);
        completeContext = true;
    }

    return completeContext;
}

void QueryContext::reserve()
{
    Mutex::ScopedLock _lock(lock);
    requestsOutstanding++;
}

void QueryContext::release()
{
    Mutex::ScopedLock _lock(lock);
    if (--requestsOutstanding == 0) {
        broker.eventQueue.push_back(broker.eventQueryComplete(userContext, queryResponse));
    }
}

bool QueryContext::handleMessage(uint8_t opcode, uint32_t sequence, Buffer& buffer)
{
    bool completeContext = false;
    ObjectImpl::Ptr object;

    if      (opcode == Protocol::OP_COMMAND_COMPLETE) {
        broker.handleCommandComplete(buffer, sequence);
        completeContext = true;
    }
    else if (opcode == Protocol::OP_OBJECT_INDICATION) {
        object = broker.handleObjectIndication(buffer, sequence, true,  true);
        if (object.get() != 0)
            queryResponse->results.push_back(object);
    }
    else {
        QPID_LOG(trace, "QueryContext::handleMessage invalid opcode: " << opcode);
        completeContext = true;
    }

    return completeContext;
}

ConsoleEngineImpl::ConsoleEngineImpl(ConsoleEngine* e, const ConsoleSettings& s) :
    envelope(e), settings(s)
{
    bindingList.push_back(pair<string, string>(string(), "schema.#"));
    if (settings.rcvObjects && settings.rcvEvents && settings.rcvHeartbeats && !settings.userBindings) {
        bindingList.push_back(pair<string, string>(string(), "console.#"));
    } else {
        if (settings.rcvObjects && !settings.userBindings)
            bindingList.push_back(pair<string, string>(string(), "console.obj.#"));
        else
            bindingList.push_back(pair<string, string>(string(), "console.obj.*.*.org.apache.qpid.broker.agent"));
        if (settings.rcvEvents)
            bindingList.push_back(pair<string, string>(string(), "console.event.#"));
        if (settings.rcvHeartbeats)
            bindingList.push_back(pair<string, string>(string(), "console.heartbeat.#"));
    }
}

ConsoleEngineImpl::~ConsoleEngineImpl()
{
    // This function intentionally left blank.
}

bool ConsoleEngineImpl::getEvent(ConsoleEvent& event) const
{
    Mutex::ScopedLock _lock(lock);
    if (eventQueue.empty())
        return false;
    event = eventQueue.front()->copy();
    return true;
}

void ConsoleEngineImpl::popEvent()
{
    Mutex::ScopedLock _lock(lock);
    if (!eventQueue.empty())
        eventQueue.pop_front();
}

void ConsoleEngineImpl::addConnection(BrokerProxy& broker, void* /*context*/)
{
    Mutex::ScopedLock _lock(lock);
    brokerList.push_back(broker.impl);
}

void ConsoleEngineImpl::delConnection(BrokerProxy& broker)
{
    Mutex::ScopedLock _lock(lock);
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        if (*iter == broker.impl) {
            brokerList.erase(iter);
            break;
        }
}

uint32_t ConsoleEngineImpl::packageCount() const
{
    Mutex::ScopedLock _lock(lock);
    return packages.size();
}

const string& ConsoleEngineImpl::getPackageName(uint32_t idx) const
{
    const static string empty;

    Mutex::ScopedLock _lock(lock);
    if (idx >= packages.size())
        return empty;

    PackageList::const_iterator iter = packages.begin();
    for (uint32_t i = 0; i < idx; i++) iter++;
    return iter->first;
}

uint32_t ConsoleEngineImpl::classCount(const char* packageName) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(packageName);
    if (pIter == packages.end())
        return 0;

    const ObjectClassList& oList = pIter->second.first;
    const EventClassList& eList = pIter->second.second;

    return oList.size() + eList.size();
}

const SchemaClassKey* ConsoleEngineImpl::getClass(const char* packageName, uint32_t idx) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(packageName);
    if (pIter == packages.end())
        return 0;

    const ObjectClassList& oList = pIter->second.first;
    const EventClassList& eList = pIter->second.second;
    uint32_t count = 0;
    
    for (ObjectClassList::const_iterator oIter = oList.begin();
         oIter != oList.end(); oIter++) {
        if (count == idx)
            return oIter->second->getClassKey();
        count++;
    }

    for (EventClassList::const_iterator eIter = eList.begin();
         eIter != eList.end(); eIter++) {
        if (count == idx)
            return eIter->second->getClassKey();
        count++;
    }

    return 0;
}

ClassKind ConsoleEngineImpl::getClassKind(const SchemaClassKey* key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return CLASS_OBJECT;

    const EventClassList& eList = pIter->second.second;
    if (eList.find(key->impl) != eList.end())
        return CLASS_EVENT;
    return CLASS_OBJECT;
}

const SchemaObjectClass* ConsoleEngineImpl::getObjectClass(const SchemaClassKey* key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return 0;

    const ObjectClassList& oList = pIter->second.first;
    ObjectClassList::const_iterator iter = oList.find(key->impl);
    if (iter == oList.end())
        return 0;
    return iter->second->envelope;
}

const SchemaEventClass* ConsoleEngineImpl::getEventClass(const SchemaClassKey* key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return 0;

    const EventClassList& eList = pIter->second.second;
    EventClassList::const_iterator iter = eList.find(key->impl);
    if (iter == eList.end())
        return 0;
    return iter->second->envelope;
}

void ConsoleEngineImpl::bindPackage(const char* packageName)
{
    stringstream key;
    key << "console.obj.*.*." << packageName << ".#";
    Mutex::ScopedLock _lock(lock);
    bindingList.push_back(pair<string, string>(string(), key.str()));
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        (*iter)->addBinding(QMF_EXCHANGE, key.str());
}

void ConsoleEngineImpl::bindClass(const SchemaClassKey* classKey)
{
    stringstream key;
    key << "console.obj.*.*." << classKey->getPackageName() << "." << classKey->getClassName() << ".#";
    Mutex::ScopedLock _lock(lock);
    bindingList.push_back(pair<string, string>(string(), key.str()));
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        (*iter)->addBinding(QMF_EXCHANGE, key.str());
}

void ConsoleEngineImpl::bindClass(const char* packageName, const char* className)
{
    stringstream key;
    key << "console.obj.*.*." << packageName << "." << className << ".#";
    Mutex::ScopedLock _lock(lock);
    bindingList.push_back(pair<string, string>(string(), key.str()));
    for (vector<BrokerProxyImpl*>::iterator iter = brokerList.begin();
         iter != brokerList.end(); iter++)
        (*iter)->addBinding(QMF_EXCHANGE, key.str());
}

/*
void ConsoleEngineImpl::startSync(const Query& query, void* context, SyncQuery& sync)
{
}

void ConsoleEngineImpl::touchSync(SyncQuery& sync)
{
}

void ConsoleEngineImpl::endSync(SyncQuery& sync)
{
}
*/

void ConsoleEngineImpl::learnPackage(const string& packageName)
{
    Mutex::ScopedLock _lock(lock);
    if (packages.find(packageName) == packages.end())
        packages.insert(pair<string, pair<ObjectClassList, EventClassList> >
                        (packageName, pair<ObjectClassList, EventClassList>(ObjectClassList(), EventClassList())));
}

void ConsoleEngineImpl::learnClass(SchemaObjectClassImpl::Ptr cls)
{
    Mutex::ScopedLock _lock(lock);
    const SchemaClassKey* key = cls->getClassKey();
    PackageList::iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return;

    ObjectClassList& list = pIter->second.first;
    if (list.find(key->impl) == list.end())
        list[key->impl] = cls;
}

void ConsoleEngineImpl::learnClass(SchemaEventClassImpl::Ptr cls)
{
    Mutex::ScopedLock _lock(lock);
    const SchemaClassKey* key = cls->getClassKey();
    PackageList::iterator pIter = packages.find(key->getPackageName());
    if (pIter == packages.end())
        return;

    EventClassList& list = pIter->second.second;
    if (list.find(key->impl) == list.end())
        list[key->impl] = cls;
}

bool ConsoleEngineImpl::haveClass(const SchemaClassKeyImpl& key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key.getPackageName());
    if (pIter == packages.end())
        return false;

    const ObjectClassList& oList = pIter->second.first;
    const EventClassList& eList = pIter->second.second;

    return oList.find(&key) != oList.end() || eList.find(&key) != eList.end();
}

SchemaObjectClassImpl::Ptr ConsoleEngineImpl::getSchema(const SchemaClassKeyImpl& key) const
{
    Mutex::ScopedLock _lock(lock);
    PackageList::const_iterator pIter = packages.find(key.getPackageName());
    if (pIter == packages.end())
        return SchemaObjectClassImpl::Ptr();

    const ObjectClassList& oList = pIter->second.first;
    ObjectClassList::const_iterator iter = oList.find(&key);
    if (iter == oList.end())
        return SchemaObjectClassImpl::Ptr();

    return iter->second;
}

//==================================================================
// Wrappers
//==================================================================

AgentProxy::AgentProxy(AgentProxyImpl* i) : impl(i) {}
AgentProxy::~AgentProxy() { delete impl; }
const char* AgentProxy::getLabel() const { return impl->getLabel().c_str(); }

BrokerProxy::BrokerProxy(ConsoleEngine& console) : impl(new BrokerProxyImpl(this, console)) {}
BrokerProxy::~BrokerProxy() { delete impl; }
void BrokerProxy::sessionOpened(SessionHandle& sh) { impl->sessionOpened(sh); }
void BrokerProxy::sessionClosed() { impl->sessionClosed(); }
void BrokerProxy::startProtocol() { impl->startProtocol(); }
void BrokerProxy::handleRcvMessage(Message& message) { impl->handleRcvMessage(message); }
bool BrokerProxy::getXmtMessage(Message& item) const { return impl->getXmtMessage(item); }
void BrokerProxy::popXmt() { impl->popXmt(); }
bool BrokerProxy::getEvent(BrokerEvent& event) const { return impl->getEvent(event); }
void BrokerProxy::popEvent() { impl->popEvent(); }
uint32_t BrokerProxy::agentCount() const { return impl->agentCount(); }
const AgentProxy* BrokerProxy::getAgent(uint32_t idx) const { return impl->getAgent(idx); }
void BrokerProxy::sendQuery(const Query& query, void* context, const AgentProxy* agent) { impl->sendQuery(query, context, agent); }

MethodResponse::MethodResponse(MethodResponseImpl* i) : impl(i) {}
MethodResponse::~MethodResponse() {}
uint32_t MethodResponse::getStatus() const { return impl->getStatus(); }
const Value* MethodResponse::getException() const { return impl->getException(); }
const Value* MethodResponse::getArgs() const { return impl->getArgs(); }

QueryResponse::QueryResponse(QueryResponseImpl* i) : impl(i) {}
QueryResponse::~QueryResponse() {}
uint32_t QueryResponse::getStatus() const { return impl->getStatus(); }
const Value* QueryResponse::getException() const { return impl->getException(); }
uint32_t QueryResponse::getObjectCount() const { return impl->getObjectCount(); }
const Object* QueryResponse::getObject(uint32_t idx) const { return impl->getObject(idx); }

ConsoleEngine::ConsoleEngine(const ConsoleSettings& settings) : impl(new ConsoleEngineImpl(this, settings)) {}
ConsoleEngine::~ConsoleEngine() { delete impl; }
bool ConsoleEngine::getEvent(ConsoleEvent& event) const { return impl->getEvent(event); }
void ConsoleEngine::popEvent() { impl->popEvent(); }
void ConsoleEngine::addConnection(BrokerProxy& broker, void* context) { impl->addConnection(broker, context); }
void ConsoleEngine::delConnection(BrokerProxy& broker) { impl->delConnection(broker); }
uint32_t ConsoleEngine::packageCount() const { return impl->packageCount(); }
const char* ConsoleEngine::getPackageName(uint32_t idx) const { return impl->getPackageName(idx).c_str(); }
uint32_t ConsoleEngine::classCount(const char* packageName) const { return impl->classCount(packageName); }
const SchemaClassKey* ConsoleEngine::getClass(const char* packageName, uint32_t idx) const { return impl->getClass(packageName, idx); }
ClassKind ConsoleEngine::getClassKind(const SchemaClassKey* key) const { return impl->getClassKind(key); }
const SchemaObjectClass* ConsoleEngine::getObjectClass(const SchemaClassKey* key) const { return impl->getObjectClass(key); }
const SchemaEventClass* ConsoleEngine::getEventClass(const SchemaClassKey* key) const { return impl->getEventClass(key); }
void ConsoleEngine::bindPackage(const char* packageName) { impl->bindPackage(packageName); }
void ConsoleEngine::bindClass(const SchemaClassKey* key) { impl->bindClass(key); }
void ConsoleEngine::bindClass(const char* packageName, const char* className) { impl->bindClass(packageName, className); }
//void ConsoleEngine::startSync(const Query& query, void* context, SyncQuery& sync) { impl->startSync(query, context, sync); }
//void ConsoleEngine::touchSync(SyncQuery& sync) { impl->touchSync(sync); }
//void ConsoleEngine::endSync(SyncQuery& sync) { impl->endSync(sync); }


