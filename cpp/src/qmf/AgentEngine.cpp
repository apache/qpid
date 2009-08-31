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

#include "qmf/AgentEngine.h"
#include "qmf/MessageImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/Typecode.h"
#include "qmf/ObjectImpl.h"
#include "qmf/ObjectIdImpl.h"
#include "qmf/QueryImpl.h"
#include "qmf/ValueImpl.h"
#include "qmf/Protocol.h"
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

    struct AgentEventImpl {
        typedef boost::shared_ptr<AgentEventImpl> Ptr;
        AgentEvent::EventKind kind;
        uint32_t    sequence;
        string      authUserId;
        string      authToken;
        string      name;
        Object*     object;
        boost::shared_ptr<ObjectId> objectId;
        Query       query;
        boost::shared_ptr<Value> arguments;
        string      exchange;
        string      bindingKey;
        SchemaObjectClass* objectClass;

        AgentEventImpl(AgentEvent::EventKind k) :
            kind(k), sequence(0), object(0), objectClass(0) {}
        ~AgentEventImpl() {}
        AgentEvent copy();
    };

    struct AgentQueryContext {
        typedef boost::shared_ptr<AgentQueryContext> Ptr;
        uint32_t sequence;
        string   exchange;
        string   key;
        SchemaMethodImpl* schemaMethod;
        AgentQueryContext() : schemaMethod(0) {}
    };

    class AgentEngineImpl {
    public:
        AgentEngineImpl(char* label, bool internalStore);
        ~AgentEngineImpl();

        void setStoreDir(const char* path);
        void setTransferDir(const char* path);
        void handleRcvMessage(Message& message);
        bool getXmtMessage(Message& item) const;
        void popXmt();
        bool getEvent(AgentEvent& event) const;
        void popEvent();
        void newSession();
        void startProtocol();
        void heartbeat();
        void methodResponse(uint32_t sequence, uint32_t status, char* text, const Value& arguments);
        void queryResponse(uint32_t sequence, Object& object, bool prop, bool stat);
        void queryComplete(uint32_t sequence);
        void registerClass(SchemaObjectClass* cls);
        void registerClass(SchemaEventClass* cls);
        const ObjectId* addObject(Object& obj, uint64_t persistId);
        const ObjectId* allocObjectId(uint64_t persistId);
        const ObjectId* allocObjectId(uint32_t persistIdLo, uint32_t persistIdHi);
        void raiseEvent(Event& event);

    private:
        mutable Mutex lock;
        Mutex     addLock;
        string    label;
        string    queueName;
        string    storeDir;
        string    transferDir;
        bool      internalStore;
        uint64_t  nextTransientId;
        Uuid      systemId;
        uint32_t  requestedBrokerBank;
        uint32_t  requestedAgentBank;
        uint32_t  assignedBrokerBank;
        uint32_t  assignedAgentBank;
        AgentAttachment attachment;
        uint16_t  bootSequence;
        uint64_t  nextObjectId;
        uint32_t  nextContextNum;
        deque<AgentEventImpl::Ptr> eventQueue;
        deque<MessageImpl::Ptr> xmtQueue;
        map<uint32_t, AgentQueryContext::Ptr> contextMap;

        static const char* QMF_EXCHANGE;
        static const char* DIR_EXCHANGE;
        static const char* BROKER_KEY;
        static const uint32_t MERR_UNKNOWN_METHOD = 2;
        static const uint32_t MERR_UNKNOWN_PACKAGE = 8;
        static const uint32_t MERR_UNKNOWN_CLASS = 9;
        static const uint32_t MERR_INTERNAL_ERROR = 10;
#       define MA_BUFFER_SIZE 65536
        char outputBuffer[MA_BUFFER_SIZE];

        struct AgentClassKey {
            string name;
            uint8_t hash[16];
            AgentClassKey(const string& n, const uint8_t* h) : name(n) {
                memcpy(hash, h, 16);
            }
            AgentClassKey(Buffer& buffer) {
                buffer.getShortString(name);
                buffer.getBin128(hash);
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

        typedef map<AgentClassKey, SchemaObjectClassImpl*, AgentClassKeyComp> ObjectClassMap;
        typedef map<AgentClassKey, SchemaEventClassImpl*, AgentClassKeyComp>  EventClassMap;

        struct ClassMaps {
            ObjectClassMap objectClasses;
            EventClassMap  eventClasses;
        };

        map<string, ClassMaps> packages;

        AgentEventImpl::Ptr eventDeclareQueue(const string& queueName);
        AgentEventImpl::Ptr eventBind(const string& exchange, const string& queue, const string& key);
        AgentEventImpl::Ptr eventSetupComplete();
        AgentEventImpl::Ptr eventQuery(uint32_t num, const string& userId, const string& package, const string& cls,
                                       boost::shared_ptr<ObjectId> oid);
        AgentEventImpl::Ptr eventMethod(uint32_t num, const string& userId, const string& method,
                                        boost::shared_ptr<ObjectId> oid, boost::shared_ptr<Value> argMap,
                                        SchemaObjectClass* objectClass);
        void sendBufferLH(Buffer& buf, const string& destination, const string& routingKey);

        void sendPackageIndicationLH(const string& packageName);
        void sendClassIndicationLH(ClassKind kind, const string& packageName, const AgentClassKey& key);
        void sendCommandCompleteLH(const string& exchange, const string& key, uint32_t seq,
                                   uint32_t code = 0, const string& text = "OK");
        void sendMethodErrorLH(uint32_t sequence, const string& key, uint32_t code, const string& text="");
        void handleAttachResponse(Buffer& inBuffer);
        void handlePackageRequest(Buffer& inBuffer);
        void handleClassQuery(Buffer& inBuffer);
        void handleSchemaRequest(Buffer& inBuffer, uint32_t sequence,
                                 const string& replyToExchange, const string& replyToKey);
        void handleGetQuery(Buffer& inBuffer, uint32_t sequence, const string& replyTo, const string& userId);
        void handleMethodRequest(Buffer& inBuffer, uint32_t sequence, const string& replyTo, const string& userId);
        void handleConsoleAddedIndication();
    };
}

const char* AgentEngineImpl::QMF_EXCHANGE = "qpid.management";
const char* AgentEngineImpl::DIR_EXCHANGE = "amq.direct";
const char* AgentEngineImpl::BROKER_KEY   = "broker";

#define STRING_REF(s) {if (!s.empty()) item.s = const_cast<char*>(s.c_str());}

AgentEvent AgentEventImpl::copy()
{
    AgentEvent item;

    ::memset(&item, 0, sizeof(AgentEvent));
    item.kind      = kind;
    item.sequence  = sequence;
    item.object    = object;
    item.objectId  = objectId.get();
    item.query     = &query;
    item.arguments = arguments.get();
    item.objectClass = objectClass;

    STRING_REF(authUserId);
    STRING_REF(authToken);
    STRING_REF(name);
    STRING_REF(exchange);
    STRING_REF(bindingKey);

    return item;
}

AgentEngineImpl::AgentEngineImpl(char* _label, bool i) :
    label(_label), queueName("qmfa-"), internalStore(i), nextTransientId(1),
    requestedBrokerBank(0), requestedAgentBank(0),
    assignedBrokerBank(0), assignedAgentBank(0),
    bootSequence(1), nextObjectId(1), nextContextNum(1)
{
    queueName += label;
}

AgentEngineImpl::~AgentEngineImpl()
{
}

void AgentEngineImpl::setStoreDir(const char* path)
{
    Mutex::ScopedLock _lock(lock);
    if (path)
        storeDir = path;
    else
        storeDir.clear();
}

void AgentEngineImpl::setTransferDir(const char* path)
{
    Mutex::ScopedLock _lock(lock);
    if (path)
        transferDir = path;
    else
        transferDir.clear();
}

void AgentEngineImpl::handleRcvMessage(Message& message)
{
    Buffer   inBuffer(message.body, message.length);
    uint8_t  opcode;
    uint32_t sequence;
    string   replyToExchange(message.replyExchange ? message.replyExchange : "");
    string   replyToKey(message.replyKey ? message.replyKey : "");
    string   userId(message.userId ? message.userId : "");

    while (Protocol::checkHeader(inBuffer, &opcode, &sequence)) {
        if      (opcode == Protocol::OP_ATTACH_RESPONSE) handleAttachResponse(inBuffer);
        else if (opcode == Protocol::OP_SCHEMA_REQUEST) handleSchemaRequest(inBuffer, sequence, replyToExchange, replyToKey);
        else if (opcode == Protocol::OP_CONSOLE_ADDED_INDICATION) handleConsoleAddedIndication();
        else if (opcode == Protocol::OP_GET_QUERY) handleGetQuery(inBuffer, sequence, replyToKey, userId);
        else if (opcode == Protocol::OP_METHOD_REQUEST) handleMethodRequest(inBuffer, sequence, replyToKey, userId);
        else {
            QPID_LOG(error, "AgentEngineImpl::handleRcvMessage invalid opcode=" << opcode);
            break;
        }
    }
}

bool AgentEngineImpl::getXmtMessage(Message& item) const
{
    Mutex::ScopedLock _lock(lock);
    if (xmtQueue.empty())
        return false;
    item =  xmtQueue.front()->copy();
    return true;
}

void AgentEngineImpl::popXmt()
{
    Mutex::ScopedLock _lock(lock);
    if (!xmtQueue.empty())
        xmtQueue.pop_front();
}

bool AgentEngineImpl::getEvent(AgentEvent& event) const
{
    Mutex::ScopedLock _lock(lock);
    if (eventQueue.empty())
        return false;
    event = eventQueue.front()->copy();
    return true;
}

void AgentEngineImpl::popEvent()
{
    Mutex::ScopedLock _lock(lock);
    if (!eventQueue.empty())
        eventQueue.pop_front();
}

void AgentEngineImpl::newSession()
{
    Mutex::ScopedLock _lock(lock);
    eventQueue.clear();
    xmtQueue.clear();
    eventQueue.push_back(eventDeclareQueue(queueName));
    eventQueue.push_back(eventBind("amq.direct", queueName, queueName));
    eventQueue.push_back(eventSetupComplete());
}

void AgentEngineImpl::startProtocol()
{
    Mutex::ScopedLock _lock(lock);
    char    rawbuffer[512];
    Buffer  buffer(rawbuffer, 512);

    Protocol::encodeHeader(buffer, Protocol::OP_ATTACH_REQUEST);
    buffer.putShortString("qmfa");
    systemId.encode(buffer);
    buffer.putLong(requestedBrokerBank);
    buffer.putLong(requestedAgentBank);
    sendBufferLH(buffer, QMF_EXCHANGE, BROKER_KEY);
    QPID_LOG(trace, "SENT AttachRequest: reqBroker=" << requestedBrokerBank <<
             " reqAgent=" << requestedAgentBank);
}

void AgentEngineImpl::heartbeat()
{
    Mutex::ScopedLock _lock(lock);
    Buffer buffer(outputBuffer, MA_BUFFER_SIZE);

    Protocol::encodeHeader(buffer, Protocol::OP_HEARTBEAT_INDICATION);
    buffer.putLongLong(uint64_t(Duration(now())));
    stringstream key;
    key << "console.heartbeat." << assignedBrokerBank << "." << assignedAgentBank;
    sendBufferLH(buffer, QMF_EXCHANGE, key.str());
    QPID_LOG(trace, "SENT HeartbeatIndication");
}

void AgentEngineImpl::methodResponse(uint32_t sequence, uint32_t status, char* text,
                                     const Value& argMap)
{
    Mutex::ScopedLock _lock(lock);
    map<uint32_t, AgentQueryContext::Ptr>::iterator iter = contextMap.find(sequence);
    if (iter == contextMap.end())
        return;
    AgentQueryContext::Ptr context = iter->second;
    contextMap.erase(iter);

    Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
    Protocol::encodeHeader(buffer, Protocol::OP_METHOD_RESPONSE, context->sequence);
    buffer.putLong(status);
    buffer.putMediumString(text);
    if (status == 0) {
        for (vector<SchemaArgumentImpl*>::const_iterator aIter = context->schemaMethod->arguments.begin();
             aIter != context->schemaMethod->arguments.end(); aIter++) {
            const SchemaArgumentImpl* schemaArg = *aIter;
            if (schemaArg->dir == DIR_OUT || schemaArg->dir == DIR_IN_OUT) {
                if (argMap.keyInMap(schemaArg->name.c_str())) {
                    const Value* val = argMap.byKey(schemaArg->name.c_str());
                    val->impl->encode(buffer);
                } else {
                    Value val(schemaArg->typecode);
                    val.impl->encode(buffer);
                }
            }
        }
    }
    sendBufferLH(buffer, context->exchange, context->key);
    QPID_LOG(trace, "SENT MethodResponse");
}

void AgentEngineImpl::queryResponse(uint32_t sequence, Object& object, bool prop, bool stat)
{
    Mutex::ScopedLock _lock(lock);
    map<uint32_t, AgentQueryContext::Ptr>::iterator iter = contextMap.find(sequence);
    if (iter == contextMap.end())
        return;
    AgentQueryContext::Ptr context = iter->second;

    Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
    Protocol::encodeHeader(buffer, Protocol::OP_OBJECT_INDICATION, context->sequence);

    object.impl->encodeSchemaKey(buffer);
    object.impl->encodeManagedObjectData(buffer);
    if (prop)
        object.impl->encodeProperties(buffer);
    if (stat)
        object.impl->encodeStatistics(buffer);
    
    sendBufferLH(buffer, context->exchange, context->key);
    QPID_LOG(trace, "SENT ContentIndication");
}

void AgentEngineImpl::queryComplete(uint32_t sequence)
{
    Mutex::ScopedLock _lock(lock);
    map<uint32_t, AgentQueryContext::Ptr>::iterator iter = contextMap.find(sequence);
    if (iter == contextMap.end())
        return;

    AgentQueryContext::Ptr context = iter->second;
    contextMap.erase(iter);
    sendCommandCompleteLH(context->exchange, context->key, context->sequence, 0, "OK");
}

void AgentEngineImpl::registerClass(SchemaObjectClass* cls)
{
    Mutex::ScopedLock _lock(lock);
    SchemaObjectClassImpl* impl = cls->impl;

    map<string, ClassMaps>::iterator iter = packages.find(impl->package);
    if (iter == packages.end()) {
        packages[impl->package] = ClassMaps();
        iter = packages.find(impl->getClassKey()->getPackageName());
        // TODO: Indicate this package if connected
    }

    AgentClassKey key(impl->getClassKey()->getClassName(), impl->getClassKey()->getHash());
    iter->second.objectClasses[key] = impl;

    // TODO: Indicate this schema if connected.
}

void AgentEngineImpl::registerClass(SchemaEventClass* cls)
{
    Mutex::ScopedLock _lock(lock);
    SchemaEventClassImpl* impl = cls->impl;

    map<string, ClassMaps>::iterator iter = packages.find(impl->package);
    if (iter == packages.end()) {
        packages[impl->package] = ClassMaps();
        iter = packages.find(impl->getClassKey()->getPackageName());
        // TODO: Indicate this package if connected
    }

    AgentClassKey key(impl->getClassKey()->getClassName(), impl->getClassKey()->getHash());
    iter->second.eventClasses[key] = impl;

    // TODO: Indicate this schema if connected.
}

const ObjectId* AgentEngineImpl::addObject(Object&, uint64_t)
{
    Mutex::ScopedLock _lock(lock);
    return 0;
}

const ObjectId* AgentEngineImpl::allocObjectId(uint64_t persistId)
{
    Mutex::ScopedLock _lock(lock);
    uint16_t sequence  = persistId ? 0 : bootSequence;
    uint64_t objectNum = persistId ? persistId : nextObjectId++;

    ObjectIdImpl* oid = new ObjectIdImpl(&attachment, 0, sequence, objectNum);
    return oid->envelope;
}

const ObjectId* AgentEngineImpl::allocObjectId(uint32_t persistIdLo, uint32_t persistIdHi)
{
    return allocObjectId(((uint64_t) persistIdHi) << 32 | (uint64_t) persistIdLo);
}

void AgentEngineImpl::raiseEvent(Event&)
{
    Mutex::ScopedLock _lock(lock);
}

AgentEventImpl::Ptr AgentEngineImpl::eventDeclareQueue(const string& name)
{
    AgentEventImpl::Ptr event(new AgentEventImpl(AgentEvent::DECLARE_QUEUE));
    event->name = name;

    return event;
}

AgentEventImpl::Ptr AgentEngineImpl::eventBind(const string& exchange, const string& queue,
                                               const string& key)
{
    AgentEventImpl::Ptr event(new AgentEventImpl(AgentEvent::BIND));
    event->name       = queue;
    event->exchange   = exchange;
    event->bindingKey = key;

    return event;
}

AgentEventImpl::Ptr AgentEngineImpl::eventSetupComplete()
{
    AgentEventImpl::Ptr event(new AgentEventImpl(AgentEvent::SETUP_COMPLETE));
    return event;
}

AgentEventImpl::Ptr AgentEngineImpl::eventQuery(uint32_t num, const string& userId, const string& package,
                                                const string& cls, boost::shared_ptr<ObjectId> oid)
{
    AgentEventImpl::Ptr event(new AgentEventImpl(AgentEvent::GET_QUERY));
    event->sequence = num;
    event->authUserId = userId;
    event->query.impl->packageName = package;    
    event->query.impl->className = cls;
    event->query.impl->oid = oid;
    return event;
}

AgentEventImpl::Ptr AgentEngineImpl::eventMethod(uint32_t num, const string& userId, const string& method,
                                                 boost::shared_ptr<ObjectId> oid, boost::shared_ptr<Value> argMap,
                                                 SchemaObjectClass* objectClass)
{
    AgentEventImpl::Ptr event(new AgentEventImpl(AgentEvent::METHOD_CALL));
    event->sequence = num;
    event->authUserId = userId;
    event->name = method;
    event->objectId = oid;
    event->arguments = argMap;
    event->objectClass = objectClass;
    return event;
}

void AgentEngineImpl::sendBufferLH(Buffer& buf, const string& destination, const string& routingKey)
{
    uint32_t length = buf.getPosition();
    MessageImpl::Ptr message(new MessageImpl);

    buf.reset();
    buf.getRawData(message->body, length);
    message->destination   = destination;
    message->routingKey    = routingKey;
    message->replyExchange = "amq.direct";
    message->replyKey      = queueName;

    xmtQueue.push_back(message);
}

void AgentEngineImpl::sendPackageIndicationLH(const string& packageName)
{
    Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
    Protocol::encodeHeader(buffer, Protocol::OP_PACKAGE_INDICATION);
    buffer.putShortString(packageName);
    sendBufferLH(buffer, QMF_EXCHANGE, BROKER_KEY);
    QPID_LOG(trace, "SENT PackageIndication:  package_name=" << packageName);
}

void AgentEngineImpl::sendClassIndicationLH(ClassKind kind, const string& packageName, const AgentClassKey& key)
{
    Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
    Protocol::encodeHeader(buffer, Protocol::OP_CLASS_INDICATION);
    buffer.putOctet((int) kind);
    buffer.putShortString(packageName);
    buffer.putShortString(key.name);
    buffer.putBin128(const_cast<uint8_t*>(key.hash)); // const_cast needed for older Qpid libraries
    sendBufferLH(buffer, QMF_EXCHANGE, BROKER_KEY);
    QPID_LOG(trace, "SENT ClassIndication:  package_name=" << packageName << " class_name=" << key.name);
}

void AgentEngineImpl::sendCommandCompleteLH(const string& exchange, const string& replyToKey,
                                            uint32_t sequence, uint32_t code, const string& text)
{
    Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
    Protocol::encodeHeader(buffer, Protocol::OP_COMMAND_COMPLETE, sequence);
    buffer.putLong(code);
    buffer.putShortString(text);
    sendBufferLH(buffer, exchange, replyToKey);
    QPID_LOG(trace, "SENT CommandComplete: seq=" << sequence << " code=" << code << " text=" << text);
}

void AgentEngineImpl::sendMethodErrorLH(uint32_t sequence, const string& key, uint32_t code, const string& text)
{
    Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
    Protocol::encodeHeader(buffer, Protocol::OP_METHOD_RESPONSE, sequence);
    buffer.putLong(code);

    string fulltext;
    switch (code) {
    case MERR_UNKNOWN_PACKAGE: fulltext = "Unknown Package";  break;
    case MERR_UNKNOWN_CLASS:   fulltext = "Unknown Class"; break;
    case MERR_UNKNOWN_METHOD:  fulltext = "Unknown Method"; break;
    case MERR_INTERNAL_ERROR:  fulltext = "Internal Error"; break;
    default:                   fulltext = "Unspecified Error"; break;
    }

    if (!text.empty()) {
        fulltext += " (";
        fulltext += text;
        fulltext += ")";
    }

    buffer.putMediumString(fulltext);
    sendBufferLH(buffer, DIR_EXCHANGE, key);
    QPID_LOG(trace, "SENT MethodResponse: errorCode=" << code << " text=" << fulltext);
}

void AgentEngineImpl::handleAttachResponse(Buffer& inBuffer)
{
    Mutex::ScopedLock _lock(lock);

    assignedBrokerBank = inBuffer.getLong();
    assignedAgentBank  = inBuffer.getLong();

    QPID_LOG(trace, "RCVD AttachResponse: broker=" << assignedBrokerBank << " agent=" << assignedAgentBank);

    if ((assignedBrokerBank != requestedBrokerBank) ||
        (assignedAgentBank  != requestedAgentBank)) {
        if (requestedAgentBank == 0) {
            QPID_LOG(notice, "Initial object-id bank assigned: " << assignedBrokerBank << "." <<
                     assignedAgentBank);
        } else {
            QPID_LOG(warning, "Collision in object-id! New bank assigned: " << assignedBrokerBank <<
                     "." << assignedAgentBank);
        }
        //storeData(); // TODO
        requestedBrokerBank = assignedBrokerBank;
        requestedAgentBank = assignedAgentBank;
    }

    attachment.setBanks(assignedBrokerBank, assignedAgentBank);

    // Bind to qpid.management to receive commands
    stringstream key;
    key << "agent." << assignedBrokerBank << "." << assignedAgentBank;
    eventQueue.push_back(eventBind(QMF_EXCHANGE, queueName, key.str()));

    // Send package indications for all local packages
    for (map<string, ClassMaps>::iterator pIter = packages.begin();
         pIter != packages.end();
         pIter++) {
        sendPackageIndicationLH(pIter->first);

        // Send class indications for all local classes
        ClassMaps cMap = pIter->second;
        for (ObjectClassMap::iterator cIter = cMap.objectClasses.begin();
             cIter != cMap.objectClasses.end(); cIter++)
            sendClassIndicationLH(CLASS_OBJECT, pIter->first, cIter->first);
        for (EventClassMap::iterator cIter = cMap.eventClasses.begin();
             cIter != cMap.eventClasses.end(); cIter++)
            sendClassIndicationLH(CLASS_EVENT, pIter->first, cIter->first);
    }
}

void AgentEngineImpl::handlePackageRequest(Buffer&)
{
    Mutex::ScopedLock _lock(lock);
}

void AgentEngineImpl::handleClassQuery(Buffer&)
{
    Mutex::ScopedLock _lock(lock);
}

void AgentEngineImpl::handleSchemaRequest(Buffer& inBuffer, uint32_t sequence,
                                          const string& replyExchange, const string& replyKey)
{
    Mutex::ScopedLock _lock(lock);
    string rExchange(replyExchange);
    string rKey(replyKey);
    string packageName;
    inBuffer.getShortString(packageName);
    AgentClassKey key(inBuffer);

    if (rExchange.empty())
        rExchange = QMF_EXCHANGE;
    if (rKey.empty())
        rKey = BROKER_KEY;

    QPID_LOG(trace, "RCVD SchemaRequest: package=" << packageName << " class=" << key.name);

    map<string, ClassMaps>::iterator pIter = packages.find(packageName);
    if (pIter == packages.end()) {
        sendCommandCompleteLH(rExchange, rKey, sequence, 1, "package not found");
        return;
    }

    ClassMaps cMap = pIter->second;
    ObjectClassMap::iterator ocIter = cMap.objectClasses.find(key);
    if (ocIter != cMap.objectClasses.end()) {
        SchemaObjectClassImpl* oImpl = ocIter->second;
        Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
        Protocol::encodeHeader(buffer, Protocol::OP_SCHEMA_RESPONSE, sequence);
        oImpl->encode(buffer);
        sendBufferLH(buffer, rExchange, rKey);
        QPID_LOG(trace, "SENT SchemaResponse: (object) package=" << packageName << " class=" << key.name);
        return;
    }

    EventClassMap::iterator ecIter = cMap.eventClasses.find(key);
    if (ecIter != cMap.eventClasses.end()) {
        SchemaEventClassImpl* eImpl = ecIter->second;
        Buffer buffer(outputBuffer, MA_BUFFER_SIZE);
        Protocol::encodeHeader(buffer, Protocol::OP_SCHEMA_RESPONSE, sequence);
        eImpl->encode(buffer);
        sendBufferLH(buffer, rExchange, rKey);
        QPID_LOG(trace, "SENT SchemaResponse: (event) package=" << packageName << " class=" << key.name);
        return;
    }

    sendCommandCompleteLH(rExchange, rKey, sequence, 1, "class not found");
}

void AgentEngineImpl::handleGetQuery(Buffer& inBuffer, uint32_t sequence, const string& replyTo, const string& userId)
{
    Mutex::ScopedLock _lock(lock);
    FieldTable ft;
    FieldTable::ValuePtr value;
    map<string, ClassMaps>::const_iterator pIter = packages.end();
    string pname;
    string cname;
    string oidRepr;
    boost::shared_ptr<ObjectId> oid;

    ft.decode(inBuffer);
    
    QPID_LOG(trace, "RCVD GetQuery: map=" << ft);

    value = ft.get("_package");
    if (value.get() && value->convertsTo<string>()) {
        pname = value->get<string>();
        pIter = packages.find(pname);
        if (pIter == packages.end()) {
            sendCommandCompleteLH(DIR_EXCHANGE, replyTo, sequence);
            return;
        }
    }

    value = ft.get("_class");
    if (value.get() && value->convertsTo<string>()) {
        cname = value->get<string>();
        // TODO - check for validity of class (in package or any package)
        if (pIter == packages.end()) {
        } else {
            
        }
    }

    value = ft.get("_objectid");
    if (value.get() && value->convertsTo<string>()) {
        oidRepr = value->get<string>();
        oid.reset(new ObjectId());
        oid->impl->fromString(oidRepr);
    }

    AgentQueryContext::Ptr context(new AgentQueryContext);
    uint32_t contextNum = nextContextNum++;
    context->sequence = sequence;
    context->exchange = DIR_EXCHANGE;
    context->key = replyTo;
    contextMap[contextNum] = context;

    eventQueue.push_back(eventQuery(contextNum, userId, pname, cname, oid));
}

void AgentEngineImpl::handleMethodRequest(Buffer& buffer, uint32_t sequence, const string& replyTo, const string& userId)
{
    Mutex::ScopedLock _lock(lock);
    string pname;
    string method;
    ObjectIdImpl* oidImpl = new ObjectIdImpl(buffer);
    boost::shared_ptr<ObjectId> oid(oidImpl->envelope);
    buffer.getShortString(pname);
    AgentClassKey classKey(buffer);
    buffer.getShortString(method);

    map<string, ClassMaps>::const_iterator pIter = packages.find(pname);
    if (pIter == packages.end()) {
        sendMethodErrorLH(sequence, replyTo, MERR_UNKNOWN_PACKAGE, pname);
        return;
    }

    ObjectClassMap::const_iterator cIter = pIter->second.objectClasses.find(classKey);
    if (cIter == pIter->second.objectClasses.end()) {
        sendMethodErrorLH(sequence, replyTo, MERR_UNKNOWN_CLASS, classKey.repr());
        return;
    }

    const SchemaObjectClassImpl* schema = cIter->second;
    vector<SchemaMethodImpl*>::const_iterator mIter = schema->methods.begin();
    for (; mIter != schema->methods.end(); mIter++) {
        if ((*mIter)->name == method)
            break;
    }

    if (mIter == schema->methods.end()) {
        sendMethodErrorLH(sequence, replyTo, MERR_UNKNOWN_METHOD, method);
        return;
    }

    SchemaMethodImpl* schemaMethod = *mIter;
    boost::shared_ptr<Value> argMap(new Value(TYPE_MAP));
    ValueImpl* value;
    for (vector<SchemaArgumentImpl*>::const_iterator aIter = schemaMethod->arguments.begin();
         aIter != schemaMethod->arguments.end(); aIter++) {
        const SchemaArgumentImpl* schemaArg = *aIter;
        if (schemaArg->dir == DIR_IN || schemaArg->dir == DIR_IN_OUT)
            value = new ValueImpl(schemaArg->typecode, buffer);
        else
            value = new ValueImpl(schemaArg->typecode);
        argMap->insert(schemaArg->name.c_str(), value->envelope);
    }

    AgentQueryContext::Ptr context(new AgentQueryContext);
    uint32_t contextNum = nextContextNum++;
    context->sequence = sequence;
    context->exchange = DIR_EXCHANGE;
    context->key = replyTo;
    context->schemaMethod = schemaMethod;
    contextMap[contextNum] = context;

    eventQueue.push_back(eventMethod(contextNum, userId, method, oid, argMap, schema->envelope));
}

void AgentEngineImpl::handleConsoleAddedIndication()
{
    Mutex::ScopedLock _lock(lock);
}

//==================================================================
// Wrappers
//==================================================================

AgentEngine::AgentEngine(char* label, bool internalStore) { impl = new AgentEngineImpl(label, internalStore); }
AgentEngine::~AgentEngine() { delete impl; }
void AgentEngine::setStoreDir(const char* path) { impl->setStoreDir(path); }
void AgentEngine::setTransferDir(const char* path) { impl->setTransferDir(path); }
void AgentEngine::handleRcvMessage(Message& message) { impl->handleRcvMessage(message); }
bool AgentEngine::getXmtMessage(Message& item) const { return impl->getXmtMessage(item); }
void AgentEngine::popXmt() { impl->popXmt(); }
bool AgentEngine::getEvent(AgentEvent& event) const { return impl->getEvent(event); }
void AgentEngine::popEvent() { impl->popEvent(); }
void AgentEngine::newSession() { impl->newSession(); }
void AgentEngine::startProtocol() { impl->startProtocol(); }
void AgentEngine::heartbeat() { impl->heartbeat(); }
void AgentEngine::methodResponse(uint32_t sequence, uint32_t status, char* text, const Value& arguments) { impl->methodResponse(sequence, status, text, arguments); }
void AgentEngine::queryResponse(uint32_t sequence, Object& object, bool prop, bool stat) { impl->queryResponse(sequence, object, prop, stat); }
void AgentEngine::queryComplete(uint32_t sequence) { impl->queryComplete(sequence); }
void AgentEngine::registerClass(SchemaObjectClass* cls) { impl->registerClass(cls); }
void AgentEngine::registerClass(SchemaEventClass* cls) { impl->registerClass(cls); }
const ObjectId* AgentEngine::addObject(Object& obj, uint64_t persistId) { return impl->addObject(obj, persistId); }
const ObjectId* AgentEngine::allocObjectId(uint64_t persistId) { return impl->allocObjectId(persistId); }
const ObjectId* AgentEngine::allocObjectId(uint32_t persistIdLo, uint32_t persistIdHi) { return impl->allocObjectId(persistIdLo, persistIdHi); }
void AgentEngine::raiseEvent(Event& event) { impl->raiseEvent(event); }

