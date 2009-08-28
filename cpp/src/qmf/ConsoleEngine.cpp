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
#include <qpid/framing/Buffer.h>
#include <qpid/framing/Uuid.h>
#include <qpid/framing/FieldTable.h>
#include <qpid/framing/FieldValue.h>
#include <qpid/sys/Mutex.h>
#include <qpid/log/Statement.h>
#include <qpid/sys/Time.h>
#include <string.h>
#include <string>
#include <deque>
#include <map>
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
        ~MethodResponseImpl() {}
        uint32_t getStatus() const { return status; }
        const Value* getException() const { return exception.get(); }
        const Value* getArgs() const { return arguments.get(); }
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

        BrokerEventImpl(BrokerEvent::EventKind k) : kind(k) {}
        ~BrokerEventImpl() {}
        BrokerEvent copy();
    };

    struct BrokerProxyImpl {
        typedef boost::shared_ptr<BrokerProxyImpl> Ptr;
        mutable Mutex lock;
        BrokerProxy* envelope;
        ConsoleEngineImpl* console;
        string queueName;
        deque<MessageImpl::Ptr> xmtQueue;
        deque<BrokerEventImpl::Ptr> eventQueue;

        static const char* QMF_EXCHANGE;
        static const char* DIR_EXCHANGE;
        static const char* BROKER_KEY;

        BrokerProxyImpl(BrokerProxy* e, ConsoleEngine& _console);
        ~BrokerProxyImpl() {}

        void sessionOpened(SessionHandle& sh);
        void sessionClosed();
        void startProtocol();

        void handleRcvMessage(Message& message);
        bool getXmtMessage(Message& item) const;
        void popXmt();

        bool getEvent(BrokerEvent& event) const;
        void popEvent();

        BrokerEventImpl::Ptr eventDeclareQueue(const string& queueName);
        BrokerEventImpl::Ptr eventBind(const string& exchange, const string& queue, const string& key);
        BrokerEventImpl::Ptr eventSetupComplete();
    };

    struct AgentProxyImpl {
        typedef boost::shared_ptr<AgentProxyImpl> Ptr;
        AgentProxy* envelope;
        ConsoleEngineImpl* console;

        AgentProxyImpl(AgentProxy* e, ConsoleEngine& _console) :
            envelope(e), console(_console.impl) {}
        ~AgentProxyImpl() {}
    };

    class ConsoleEngineImpl {
    public:
        ConsoleEngineImpl(ConsoleEngine* e, const ConsoleSettings& settings = ConsoleSettings());
        ~ConsoleEngineImpl();

        void handleRcvMessage(BrokerProxy& broker, Message& message);
        bool getXmtMessage(Message& item) const;
        void popXmt();

        bool getEvent(ConsoleEvent& event) const;
        void popEvent();

        void addConnection(BrokerProxy& broker, void* context);
        void delConnection(BrokerProxy& broker);

        uint32_t packageCount() const;
        const string& getPackageName(uint32_t idx) const;

        uint32_t classCount(const char* packageName) const;
        const SchemaClassKey* getClass(const char* packageName, uint32_t idx) const;

        ClassKind getClassKind(const SchemaClassKey& key) const;
        const SchemaObjectClass* getObjectClass(const SchemaClassKey& key) const;
        const SchemaEventClass* getEventClass(const SchemaClassKey& key) const;

        void bindPackage(const char* packageName);
        void bindClass(const SchemaClassKey& key);
        void bindClass(const char* packageName, const char* className);

        uint32_t agentCount() const;
        const AgentProxy* getAgent(uint32_t idx) const;

        void sendQuery(const Query& query, void* context);

        /*
        void startSync(const Query& query, void* context, SyncQuery& sync);
        void touchSync(SyncQuery& sync);
        void endSync(SyncQuery& sync);
        */

    private:
        ConsoleEngine* envelope;
        const ConsoleSettings& settings;
        mutable Mutex lock;
        deque<ConsoleEventImpl::Ptr> eventQueue;
    };
}

const char* BrokerProxyImpl::QMF_EXCHANGE = "qpid.management";
const char* BrokerProxyImpl::DIR_EXCHANGE = "amq.direct";
const char* BrokerProxyImpl::BROKER_KEY   = "broker";


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

    return item;
}

BrokerProxyImpl::BrokerProxyImpl(BrokerProxy* e, ConsoleEngine& _console) :
    envelope(e), console(_console.impl), queueName("qmfc-")
{
    // TODO: Give the queue name a unique suffix
}

void BrokerProxyImpl::sessionOpened(SessionHandle& /*sh*/)
{
    Mutex::ScopedLock _lock(lock);
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
    eventQueue.clear();
    xmtQueue.clear();
}

void BrokerProxyImpl::startProtocol()
{
    cout << "BrokerProxyImpl::startProtocol" << endl;
}

void BrokerProxyImpl::handleRcvMessage(Message& /*message*/)
{
    // TODO: Dispatch the messages types
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

ConsoleEngineImpl::ConsoleEngineImpl(ConsoleEngine* e, const ConsoleSettings& s) :
    envelope(e), settings(s)
{
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

void ConsoleEngineImpl::addConnection(BrokerProxy& /*broker*/, void* /*context*/)
{
}

void ConsoleEngineImpl::delConnection(BrokerProxy& /*broker*/)
{
}

uint32_t ConsoleEngineImpl::packageCount() const
{
    return 0;
}

const string& ConsoleEngineImpl::getPackageName(uint32_t /*idx*/) const
{
    static string temp;
    return temp;
}

uint32_t ConsoleEngineImpl::classCount(const char* /*packageName*/) const
{
    return 0;
}

const SchemaClassKey* ConsoleEngineImpl::getClass(const char* /*packageName*/, uint32_t /*idx*/) const
{
    return 0;
}

ClassKind ConsoleEngineImpl::getClassKind(const SchemaClassKey& /*key*/) const
{
    return CLASS_OBJECT;
}

const SchemaObjectClass* ConsoleEngineImpl::getObjectClass(const SchemaClassKey& /*key*/) const
{
    return 0;
}

const SchemaEventClass* ConsoleEngineImpl::getEventClass(const SchemaClassKey& /*key*/) const
{
    return 0;
}

void ConsoleEngineImpl::bindPackage(const char* /*packageName*/)
{
}

void ConsoleEngineImpl::bindClass(const SchemaClassKey& /*key*/)
{
}

void ConsoleEngineImpl::bindClass(const char* /*packageName*/, const char* /*className*/)
{
}

uint32_t ConsoleEngineImpl::agentCount() const
{
    return 0;
}

const AgentProxy* ConsoleEngineImpl::getAgent(uint32_t /*idx*/) const
{
    return 0;
}

void ConsoleEngineImpl::sendQuery(const Query& /*query*/, void* /*context*/)
{
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



//==================================================================
// Wrappers
//==================================================================

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

AgentProxy::AgentProxy(ConsoleEngine& console) : impl(new AgentProxyImpl(this, console)) {}
AgentProxy::~AgentProxy() { delete impl; }

MethodResponse::MethodResponse(MethodResponseImpl* i) : impl(i) {}
MethodResponse::~MethodResponse() { delete impl; } // TODO: correct to delete here?
uint32_t MethodResponse::getStatus() const { return impl->getStatus(); }
const Value* MethodResponse::getException() const { return impl->getException(); }
const Value* MethodResponse::getArgs() const { return impl->getArgs(); }

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
ClassKind ConsoleEngine::getClassKind(const SchemaClassKey& key) const { return impl->getClassKind(key); }
const SchemaObjectClass* ConsoleEngine::getObjectClass(const SchemaClassKey& key) const { return impl->getObjectClass(key); }
const SchemaEventClass* ConsoleEngine::getEventClass(const SchemaClassKey& key) const { return impl->getEventClass(key); }
void ConsoleEngine::bindPackage(const char* packageName) { impl->bindPackage(packageName); }
void ConsoleEngine::bindClass(const SchemaClassKey& key) { impl->bindClass(key); }
void ConsoleEngine::bindClass(const char* packageName, const char* className) { impl->bindClass(packageName, className); }
uint32_t ConsoleEngine::agentCount() const { return impl->agentCount(); }
const AgentProxy* ConsoleEngine::getAgent(uint32_t idx) const { return impl->getAgent(idx); }
void ConsoleEngine::sendQuery(const Query& query, void* context) { impl->sendQuery(query, context); }
//void ConsoleEngine::startSync(const Query& query, void* context, SyncQuery& sync) { impl->startSync(query, context, sync); }
//void ConsoleEngine::touchSync(SyncQuery& sync) { impl->touchSync(sync); }
//void ConsoleEngine::endSync(SyncQuery& sync) { impl->endSync(sync); }


