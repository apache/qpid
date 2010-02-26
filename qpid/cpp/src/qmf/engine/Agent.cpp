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

#include "qmf/engine/Agent.h"
#include "qmf/engine/SchemaImpl.h"
#include "qmf/engine/ObjectImpl.h"
#include "qmf/engine/QueryImpl.h"
#include "qmf/Protocol.h"
#include <qpid/sys/Mutex.h>
#include <qpid/log/Statement.h>
#include <qpid/sys/Time.h>
#include <qpid/sys/Thread.h>
#include <qpid/sys/Runnable.h>
#include <qpid/messaging/Session.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/MapView.h>
#include <string>
#include <deque>
#include <map>
#include <iostream>
#include <fstream>
#include <boost/shared_ptr.hpp>
#include <boost/noncopyable.hpp>

using namespace std;
using namespace qmf::engine;
using namespace qpid::sys;
using namespace qpid::messaging;

namespace qmf {
namespace engine {

    struct AgentEventImpl {
        typedef boost::shared_ptr<AgentEventImpl> Ptr;
        AgentEvent::EventKind kind;
        uint32_t    sequence;
        string      authUserId;
        string      authToken;
        string      name;
        Object*     object;
        string      objectKey;
        boost::shared_ptr<Query> query;
        boost::shared_ptr<Variant::Map> arguments;
        const SchemaClass* objectClass;

        AgentEventImpl(AgentEvent::EventKind k) :
            kind(k), sequence(0), object(0), objectClass(0) {}
        ~AgentEventImpl() {}
        AgentEvent copy();
    };

    /**
     * AgentQueryContext is used to track asynchronous requests (Query, Sync, or Method)
     * sent up to the application.
     */
    struct AgentQueryContext {
        typedef boost::shared_ptr<AgentQueryContext> Ptr;
        uint32_t sequence;
        string   consoleAddr;
        const SchemaMethod* schemaMethod;
        AgentQueryContext() : schemaMethod(0) {}
    };

    class AgentImpl : public boost::noncopyable, public qpid::sys::Runnable {
    public:
        AgentImpl(const char* vendor, const char* product, const char* name, const char* domain, bool internalStore);
        ~AgentImpl();

        void setNotifyCallback(Agent::notifyCb handler);
        void setNotifyCallback(Notifiable* handler);
        void setAttr(const char* key, const Variant& value);
        void setStoreDir(const char* path);
        void setTransferDir(const char* path);
        bool getEvent(AgentEvent& event) const;
        void popEvent();
        void setConnection(Connection& conn);
        void methodResponse(uint32_t sequence, uint32_t status, char* text, const Variant::Map& arguments);
        void queryResponse(uint32_t sequence, Object& object);
        void queryComplete(uint32_t sequence);
        void registerClass(SchemaClass* cls);
        const char* addObject(Object& obj, const char* key);
        void raiseEvent(Event& event);

        void run();
        void stop();

    private:
        mutable Mutex lock;
        Mutex     addLock;
        const string    vendor;
        const string    product;
        const string    name;
        const string    domain;
        string directAddr;
        Variant::Map attrMap;
        string    storeDir;
        string    transferDir;
        bool      internalStore;
        Agent::notifyCb  notifyHandler;
        Notifiable* notifiable;
        Uuid      systemId;
        uint16_t  bootSequence;
        uint32_t  nextContextNum;
        bool      running;
        deque<AgentEventImpl::Ptr> eventQueue;
        map<uint32_t, AgentQueryContext::Ptr> contextMap;
        Connection connection;
        Session session;
        Receiver directReceiver;
        Receiver topicReceiver;
        Sender sender;
        qpid::sys::Thread* thread;

        struct AgentClassKey {
            string name;
            uint8_t hash[16];
            AgentClassKey(const string& n, const uint8_t* h) : name(n) {
                memcpy(hash, h, 16);
            }
            string repr() {
                return name;
            }
        };

        struct AgentClassKeyComp {
            bool operator() (const AgentClassKey& lhs, const AgentClassKey& rhs) const
            {
                if (lhs.name != rhs.name)
                    return lhs.name < rhs.name;
                else
                    for (int i = 0; i < 16; i++)
                        if (lhs.hash[i] != rhs.hash[i])
                            return lhs.hash[i] < rhs.hash[i];
                return false;
            }
        };

        typedef map<AgentClassKey, SchemaClass*, AgentClassKeyComp> ClassMap;
        map<string, ClassMap> packages;

        AgentEventImpl::Ptr eventQuery(uint32_t num, const string& userId, const string& package, const string& cls,
                                       const string& key);
        AgentEventImpl::Ptr eventMethod(uint32_t num, const string& userId, const string& method,
                                        const string& key, boost::shared_ptr<Variant::Map> argMap,
                                        const SchemaClass* cls);
        void notify();
        void handleRcvMessageLH(const Message& message);
        void handleAgentLocateLH(const Message& message);
        void handleQueryRequestLH(const Message& message);
        void handleSubscribeRequest(const Message& message);
        void handleSubscribeCancel(const Message& message);
        void handleSubscribeRefresh(const Message& message);
        void handleMethodRequest(const Message& message);

        void sendPackageIndicationLH(const string& packageName);
        void sendClassIndicationLH(ClassKind kind, const string& packageName, const AgentClassKey& key);
        void sendCommandCompleteLH(const string& exchange, const string& key, uint32_t seq,
                                   uint32_t code = 0, const string& text = "OK");
        void sendMethodErrorLH(uint32_t sequence, const string& key, uint32_t code, const string& text="");
        void handlePackageRequest(Message& msg);
        void handleClassQuery(Message& msg);
        void handleSchemaRequest(Message& msg, uint32_t sequence,
                                 const string& replyToExchange, const string& replyToKey);
        void handleGetQuery(Message& msg, uint32_t sequence, const string& replyTo, const string& userId);
        void handleMethodRequest(Message& msg, uint32_t sequence, const string& replyTo, const string& userId);
    };
}
}

#define STRING_REF(s) {if (!s.empty()) item.s = const_cast<char*>(s.c_str());}

AgentEvent AgentEventImpl::copy()
{
    AgentEvent item;

    ::memset(&item, 0, sizeof(AgentEvent));
    item.kind      = kind;
    item.sequence  = sequence;
    item.object    = object;
    item.query     = query.get();
    item.arguments = arguments.get();
    item.objectClass = objectClass;

    STRING_REF(objectKey);
    STRING_REF(authUserId);
    STRING_REF(authToken);
    STRING_REF(name);

    return item;
}

AgentImpl::AgentImpl(const char* _v, const char* _p, const char* _n, const char* _d, bool _i) :
    vendor(_v), product(_p), name(_n), domain(_d ? _d : "default"), internalStore(_i),
    notifyHandler(0), notifiable(0),
    bootSequence(1), nextContextNum(1), running(true), thread(0)
{
    directAddr = "qmf." + domain + ".direct/" + vendor + ":" + product + ":" + name;
    if (_d == 0) {
        directAddr += " { create:always }";
    }
    attrMap["vendor"] = vendor;
    attrMap["product"] = product;
    attrMap["name"] = name;
}


AgentImpl::~AgentImpl()
{
}

void AgentImpl::setNotifyCallback(Agent::notifyCb handler)
{
    Mutex::ScopedLock _lock(lock);
    notifyHandler = handler;
}

void AgentImpl::setNotifyCallback(Notifiable* handler)
{
    Mutex::ScopedLock _lock(lock);
    notifiable = handler;
}

void AgentImpl::setAttr(const char* key, const Variant& value)
{
    Mutex::ScopedLock _lock(lock);
    attrMap[key] = value;
}

void AgentImpl::setStoreDir(const char* path)
{
    Mutex::ScopedLock _lock(lock);
    if (path)
        storeDir = path;
    else
        storeDir.clear();
}

void AgentImpl::setTransferDir(const char* path)
{
    Mutex::ScopedLock _lock(lock);
    if (path)
        transferDir = path;
    else
        transferDir.clear();
}


bool AgentImpl::getEvent(AgentEvent& event) const
{
    Mutex::ScopedLock _lock(lock);
    if (eventQueue.empty())
        return false;
    event = eventQueue.front()->copy();
    return true;
}

void AgentImpl::popEvent()
{
    Mutex::ScopedLock _lock(lock);
    if (!eventQueue.empty())
        eventQueue.pop_front();
}

void AgentImpl::setConnection(Connection& conn)
{
    Mutex::ScopedLock _lock(lock);

    //
    // Don't permit the overwriting of an existing connection
    // TODO: return an error or throw an exception if an overwrite is attempted.
    //
    if (connection == 0)
        return;
    connection = conn;

    //
    // Start the Agent thread now that we have a connection to work with.
    //
    thread = new qpid::sys::Thread(*this);
}

void AgentImpl::methodResponse(uint32_t sequence, uint32_t status, char* text, const Variant::Map& /*argMap*/)
{
    Mutex::ScopedLock _lock(lock);
    map<uint32_t, AgentQueryContext::Ptr>::iterator iter = contextMap.find(sequence);
    if (iter == contextMap.end())
        return;
    AgentQueryContext::Ptr context = iter->second;
    contextMap.erase(iter);

    // TODO: Encode method response
    QPID_LOG(trace, "SENT MethodResponse seq=" << context->sequence << " status=" << status << " text=" << text);
}

void AgentImpl::queryResponse(uint32_t sequence, Object&)
{
    Mutex::ScopedLock _lock(lock);
    map<uint32_t, AgentQueryContext::Ptr>::iterator iter = contextMap.find(sequence);
    if (iter == contextMap.end())
        return;
    AgentQueryContext::Ptr context = iter->second;

    // TODO: accumulate data records and send response messages when we have "enough"

    QPID_LOG(trace, "SENT ContentIndication seq=" << context->sequence);
}

void AgentImpl::queryComplete(uint32_t sequence)
{
    Mutex::ScopedLock _lock(lock);
    map<uint32_t, AgentQueryContext::Ptr>::iterator iter = contextMap.find(sequence);
    if (iter == contextMap.end())
        return;

    // TODO: send a response message if there are any unsent data records

    AgentQueryContext::Ptr context = iter->second;
    contextMap.erase(iter);
    //sendCommandCompleteLH(context->exchange, context->key, context->sequence, 0, "OK");
}

void AgentImpl::registerClass(SchemaClass* cls)
{
    Mutex::ScopedLock _lock(lock);

    map<string, ClassMap>::iterator iter = packages.find(cls->getClassKey()->getPackageName());
    if (iter == packages.end()) {
        packages[cls->getClassKey()->getPackageName()] = ClassMap();
        iter = packages.find(cls->getClassKey()->getPackageName());
        // TODO: Indicate this package if connected
    }

    AgentClassKey key(cls->getClassKey()->getClassName(), cls->getClassKey()->getHashData());
    iter->second[key] = cls;

    // TODO: Indicate this schema if connected.
}

const char* AgentImpl::addObject(Object&, const char*)
{
    Mutex::ScopedLock _lock(lock);
    return 0;
}

void AgentImpl::raiseEvent(Event&)
{
    Mutex::ScopedLock _lock(lock);
}

void AgentImpl::run()
{
    qpid::sys::Duration duration = qpid::sys::TIME_MSEC * 500;

    session = connection.newSession();
    directReceiver = session.createReceiver(directAddr);
    directReceiver.setCapacity(10);

    Mutex::ScopedLock _lock(lock);
    while (running) {
        Receiver rcvr;
        bool available;
        {
            Mutex::ScopedUnlock _unlock(lock);
            available = session.nextReceiver(rcvr, duration);
        }

        if (available) {
            Message msg(rcvr.get());
            handleRcvMessageLH(msg);
        }
    }

    directReceiver.close();
    session.close();
}

void AgentImpl::stop()
{
    Mutex::ScopedLock _lock(lock);
    running = false;
}

void AgentImpl::handleRcvMessageLH(const Message& message)
{
    Variant::Map headers(message.getHeaders());
    cout << "AgentImpl::handleRcvMessageLH headers=" << headers << endl;

    if (message.getContentType() != Protocol::AMQP_CONTENT_MAP)
        return;

    Variant::Map::const_iterator iter = headers.find(Protocol::APP_OPCODE);
    if (iter == headers.end())
        return;
    string opcode = iter->second.asString();

    if (opcode == Protocol::OP_AGENT_LOCATE_REQUEST)        handleAgentLocateLH(message);
    if (opcode == Protocol::OP_QUERY_REQUEST)               handleQueryRequestLH(message);
    if (opcode == Protocol::OP_SUBSCRIBE_REQUEST)           handleSubscribeRequest(message);
    if (opcode == Protocol::OP_SUBSCRIBE_CANCEL_INDICATION) handleSubscribeCancel(message);
    if (opcode == Protocol::OP_SUBSCRIBE_REFRESH_REQUEST)   handleSubscribeRefresh(message);
    if (opcode == Protocol::OP_METHOD_REQUEST)              handleMethodRequest(message);
}

void AgentImpl::handleAgentLocateLH(const Message& message)
{
    const MapView predicate(message);

    //if (predicateMatches(predicate, attrMap)) {
    //    sendResponse(message, Protocol::OP_AGENT_LOCATE_RESPONSE, attrMap);
    //}
}

void AgentImpl::handleQueryRequestLH(const Message& message)
{
    const MapView map(message);
}

void AgentImpl::handleSubscribeRequest(const Message& message)
{
    const MapView map(message);
}

void AgentImpl::handleSubscribeCancel(const Message& message)
{
    const MapView map(message);
}

void AgentImpl::handleSubscribeRefresh(const Message& message)
{
    const MapView map(message);
}

void AgentImpl::handleMethodRequest(const Message& message)
{
    const MapView map(message);
}

AgentEventImpl::Ptr AgentImpl::eventQuery(uint32_t num, const string& userId, const string&, const string&, const string& key)
{
    AgentEventImpl::Ptr event(new AgentEventImpl(AgentEvent::GET_QUERY));
    event->sequence = num;
    event->authUserId = userId;
    event->objectKey = key;
    return event;
}

AgentEventImpl::Ptr AgentImpl::eventMethod(uint32_t num, const string& userId, const string& method,
                                           const string& key, boost::shared_ptr<Variant::Map> argMap,
                                           const SchemaClass* cls)
{
    AgentEventImpl::Ptr event(new AgentEventImpl(AgentEvent::METHOD_CALL));
    event->sequence = num;
    event->authUserId = userId;
    event->name = method;
    event->objectKey = key;
    event->arguments = argMap;
    event->objectClass = cls;
    return event;
}

void AgentImpl::notify()
{
    if (notifyHandler != 0)
        notifyHandler();
    if (notifiable != 0)
        notifiable->notify();
}

void AgentImpl::sendPackageIndicationLH(const string& packageName)
{
    // TODO
    QPID_LOG(trace, "SENT PackageIndication:  package_name=" << packageName);
}

void AgentImpl::sendClassIndicationLH(ClassKind /*kind*/, const string& packageName, const AgentClassKey& key)
{
    // TODO
    QPID_LOG(trace, "SENT ClassIndication:  package_name=" << packageName << " class_name=" << key.name);
}

void AgentImpl::sendCommandCompleteLH(const string&, const string&, uint32_t sequence, uint32_t code, const string& text)
{
    // TODO
    QPID_LOG(trace, "SENT CommandComplete: seq=" << sequence << " code=" << code << " text=" << text);
}

void AgentImpl::sendMethodErrorLH(uint32_t /*sequence*/, const string& /*key*/, uint32_t code, const string& text)
{
    // TODO
    QPID_LOG(trace, "SENT MethodResponse: errorCode=" << code << " text=" << text);
}

void AgentImpl::handlePackageRequest(Message&)
{
    Mutex::ScopedLock _lock(lock);
}

void AgentImpl::handleClassQuery(Message&)
{
    Mutex::ScopedLock _lock(lock);
}

void AgentImpl::handleSchemaRequest(Message&, uint32_t, const string&, const string&)
{
    Mutex::ScopedLock _lock(lock);
}

void AgentImpl::handleGetQuery(Message&, uint32_t, const string&, const string&)
{
    Mutex::ScopedLock _lock(lock);
}

void AgentImpl::handleMethodRequest(Message& /*msg*/, uint32_t sequence, const string& /*replyTo*/, const string& /*userId*/)
{
    Mutex::ScopedLock _lock(lock);
    QPID_LOG(trace, "RCVD MethodRequest seq=" << sequence << " method=");

    AgentQueryContext::Ptr context(new AgentQueryContext);
    uint32_t contextNum = nextContextNum++;
    contextMap[contextNum] = context;
}

//==================================================================
// Wrappers
//==================================================================

Agent::Agent(const char* v, const char* p, const char* n, const char* d, bool i) { impl = new AgentImpl(v, p, n, d, i); }
Agent::~Agent() { delete impl; }
void Agent::setNotifyCallback(notifyCb handler) { impl->setNotifyCallback(handler); }
void Agent::setNotifyCallback(Notifiable* handler) { impl->setNotifyCallback(handler); }
void Agent::setAttr(const char* key, const Variant& value) { impl->setAttr(key, value); }
void Agent::setStoreDir(const char* path) { impl->setStoreDir(path); }
void Agent::setTransferDir(const char* path) { impl->setTransferDir(path); }
bool Agent::getEvent(AgentEvent& event) const { return impl->getEvent(event); }
void Agent::popEvent() { impl->popEvent(); }
void Agent::setConnection(Connection& conn) { impl->setConnection(conn); }
void Agent::methodResponse(uint32_t sequence, uint32_t status, char* text, const Variant::Map& arguments) { impl->methodResponse(sequence, status, text, arguments); }
void Agent::queryResponse(uint32_t sequence, Object& object) { impl->queryResponse(sequence, object); }
void Agent::queryComplete(uint32_t sequence) { impl->queryComplete(sequence); }
void Agent::registerClass(SchemaClass* cls) { impl->registerClass(cls); }
const char* Agent::addObject(Object& obj, const char* key) { return impl->addObject(obj, key); }
void Agent::raiseEvent(Event& event) { impl->raiseEvent(event); }

