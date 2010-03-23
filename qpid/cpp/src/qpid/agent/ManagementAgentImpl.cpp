
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
// 
//   http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

#include "qpid/management/Manageable.h"
#include "qpid/management/ManagementObject.h"
#include "qpid/log/Statement.h"
#include "qpid/agent/ManagementAgentImpl.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/ListContent.h"
#include "qpid/messaging/MapContent.h"
#include <list>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>
#include <iostream>
#include <fstream>


using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::sys;
using namespace std;
using std::stringstream;
using std::ofstream;
using std::ifstream;
using std::string;
using std::cout;
using std::endl;

Mutex            ManagementAgent::Singleton::lock;
bool             ManagementAgent::Singleton::disabled = false;
ManagementAgent* ManagementAgent::Singleton::agent    = 0;
int              ManagementAgent::Singleton::refCount = 0;

ManagementAgent::Singleton::Singleton(bool disableManagement)
{
    Mutex::ScopedLock _lock(lock);
    if (disableManagement && !disabled) {
        disabled = true;
        assert(refCount == 0); // can't disable after agent has been allocated
    }
    if (refCount == 0 && !disabled)
        agent = new ManagementAgentImpl();
    refCount++;
}

ManagementAgent::Singleton::~Singleton()
{
    Mutex::ScopedLock _lock(lock);
    refCount--;
    if (refCount == 0 && !disabled) {
        delete agent;
        agent = 0;
    }
}

ManagementAgent* ManagementAgent::Singleton::getInstance()
{
    return agent;
}

const string ManagementAgentImpl::storeMagicNumber("MA02");

ManagementAgentImpl::ManagementAgentImpl() :
    interval(10), extThread(false), pipeHandle(0), notifyCallback(0), notifyContext(0),
    notifyable(0), inCallback(false),
    initialized(false), connected(false), useMapMsg(false), lastFailure("never connected"),
    clientWasAdded(true), requestedBrokerBank(0), requestedAgentBank(0),
    assignedBrokerBank(0), assignedAgentBank(0), bootSequence(0),
    connThreadBody(*this), connThread(connThreadBody),
    pubThreadBody(*this), pubThread(pubThreadBody)
{
}

ManagementAgentImpl::~ManagementAgentImpl()
{
    // shutdown & cleanup all threads
    connThreadBody.close();
    pubThreadBody.close();

    connThread.join();
    pubThread.join();

    // Release the memory associated with stored management objects.
    {
        Mutex::ScopedLock lock(agentLock);

        moveNewObjectsLH();
        for (ManagementObjectMap::iterator iter = managementObjects.begin ();
             iter != managementObjects.end ();
             iter++) {
            ManagementObject* object = iter->second;
            delete object;
        }
        managementObjects.clear();
    }
    if (pipeHandle) {
        delete pipeHandle;
        pipeHandle = 0;
    }
}

void ManagementAgentImpl::setName(const string& vendor, const string& product, const string& instance)
{
    attrMap["_vendor"] = vendor;
    attrMap["_product"] = product;
    string inst;
    if (instance.empty()) {
        inst = qpid::messaging::Uuid(true).str();
    } else
        inst = instance;

   name_address = vendor + ":" + product + ":" + inst;
   attrMap["_instance"] = inst;
   attrMap["_name"] = name_address;
}

void ManagementAgentImpl::init(const string& brokerHost,
                               uint16_t brokerPort,
                               uint16_t intervalSeconds,
                               bool useExternalThread,
                               const string& _storeFile,
                               const string& uid,
                               const string& pwd,
                               const string& mech,
                               const string& proto)
{
    client::ConnectionSettings settings;
    settings.protocol = proto;
    settings.host = brokerHost;
    settings.port = brokerPort;
    settings.username = uid;
    settings.password = pwd;
    settings.mechanism = mech;
    init(settings, intervalSeconds, useExternalThread, _storeFile);
}

void ManagementAgentImpl::init(const qpid::client::ConnectionSettings& settings,
                               uint16_t intervalSeconds,
                               bool useExternalThread,
                               const string& _storeFile)
{
    interval     = intervalSeconds;
    extThread    = useExternalThread;
    storeFile    = _storeFile;
    nextObjectId = 1;

    QPID_LOG(info, "QMF Agent Initialized: broker=" << settings.host << ":" << settings.port <<
             " interval=" << intervalSeconds << " storeFile=" << _storeFile);
    connectionSettings = settings;

    retrieveData();
    bootSequence++;
    if ((bootSequence & 0xF000) != 0)
        bootSequence = 1;
    storeData(true);

    initialized = true;
}

void ManagementAgentImpl::registerClass(const string& packageName,
                                        const string& className,
                                        uint8_t*     md5Sum,
                                        qpid::management::ManagementObject::writeSchemaCall_t schemaCall)
{ 
    Mutex::ScopedLock lock(agentLock);
    PackageMap::iterator pIter = findOrAddPackage(packageName);
    addClassLocal(ManagementItem::CLASS_KIND_TABLE, pIter, className, md5Sum, schemaCall);
}

void ManagementAgentImpl::registerEvent(const string& packageName,
                                        const string& eventName,
                                        uint8_t*     md5Sum,
                                        qpid::management::ManagementObject::writeSchemaCall_t schemaCall)
{ 
    Mutex::ScopedLock lock(agentLock);
    PackageMap::iterator pIter = findOrAddPackage(packageName);
    addClassLocal(ManagementItem::CLASS_KIND_EVENT, pIter, eventName, md5Sum, schemaCall);
}

// old-style add object: 64bit id - deprecated
ObjectId ManagementAgentImpl::addObject(ManagementObject* object,
                                        uint64_t          persistId)
{
    std::string key;
    if (persistId) {
        key = boost::lexical_cast<std::string>(persistId);
    }
    return addObject(object, key, persistId != 0);
}


// new style add object - use this approach!
ObjectId ManagementAgentImpl::addObject(ManagementObject* object,
                                        const std::string& key,
                                        bool persistent)
{
    Mutex::ScopedLock lock(addLock);

    uint16_t sequence  = persistent ? 0 : bootSequence;

    ObjectId objectId(&attachment, 0, sequence);
    if (key.empty())
        objectId.setV2Key(*object);  // let object generate the key
    else
        objectId.setV2Key(key);

    object->setObjectId(objectId);
    newManagementObjects[objectId] = object;
    return objectId;
}


void ManagementAgentImpl::raiseEvent(const ManagementEvent& event, severity_t severity)
{
    Mutex::ScopedLock lock(agentLock);
    Buffer outBuffer(eventBuffer, MA_BUFFER_SIZE);
    uint8_t sev = (severity == SEV_DEFAULT) ? event.getSeverity() : (uint8_t) severity;
    stringstream key;

    key << "console.event." << assignedBrokerBank << "." << assignedAgentBank << "." <<
        event.getPackageName() << "." << event.getEventName();

    ::qpid::messaging::Message msg;
    ::qpid::messaging::MapContent content(msg);
    ::qpid::messaging::VariantMap &map_ = content.asMap();
    ::qpid::messaging::VariantMap schemaId;
    ::qpid::messaging::VariantMap values;
    ::qpid::messaging::VariantMap headers;

    map_["_schema_id"] = mapEncodeSchemaId(event.getPackageName(),
                                           event.getEventName(),
                                           event.getMd5Sum());
    event.mapEncode(values);
    map_["_values"] = values;
    map_["_timestamp"] = uint64_t(Duration(now()));
    map_["_severity"] = sev;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_data_indication";
    headers["qmf.content"] = "_event";
    headers["qmf.agent"] = name_address;

    content.encode();
    connThreadBody.sendBuffer(msg.getContent(), "",
                              headers,
                              "qmf.default.topic", key.str());
}

uint32_t ManagementAgentImpl::pollCallbacks(uint32_t callLimit)
{
    Mutex::ScopedLock lock(agentLock);

    if (inCallback) {
        QPID_LOG(critical, "pollCallbacks invoked from the agent's thread!");
        return 0;
    }

    for (uint32_t idx = 0; callLimit == 0 || idx < callLimit; idx++) {
        if (methodQueue.empty())
            break;

        QueuedMethod* item = methodQueue.front();
        methodQueue.pop_front();
        {
            Mutex::ScopedUnlock unlock(agentLock);
            invokeMethodRequest(item->body, item->cid, item->replyTo);
            delete item;
        }
    }

    if (pipeHandle != 0) {
        char rbuf[100];
        while (pipeHandle->read(rbuf, 100) > 0) ; // Consume all signaling bytes
    }
    return methodQueue.size();
}

int ManagementAgentImpl::getSignalFd()
{
    if (extThread) {
        if (pipeHandle == 0)
            pipeHandle = new PipeHandle(true);
        return pipeHandle->getReadHandle();
    }

    return -1;
}

void ManagementAgentImpl::setSignalCallback(cb_t callback, void* context)
{
    Mutex::ScopedLock lock(agentLock);
    notifyCallback = callback;
    notifyContext  = context;
}

void ManagementAgentImpl::setSignalCallback(Notifyable& _notifyable)
{
    Mutex::ScopedLock lock(agentLock);
    notifyable = &_notifyable;
}

void ManagementAgentImpl::startProtocol()
{
    sendHeartbeat();
}

void ManagementAgentImpl::storeData(bool requested)
{
    if (!storeFile.empty()) {
        ofstream outFile(storeFile.c_str());
        uint32_t brokerBankToWrite = requested ? requestedBrokerBank : assignedBrokerBank;
        uint32_t agentBankToWrite = requested ? requestedAgentBank : assignedAgentBank;

        if (outFile.good()) {
            outFile << storeMagicNumber << " " << brokerBankToWrite << " " <<
                agentBankToWrite << " " << bootSequence << endl;
            outFile.close();
        }
    }
}

void ManagementAgentImpl::retrieveData()
{
    if (!storeFile.empty()) {
        ifstream inFile(storeFile.c_str());
        string   mn;

        if (inFile.good()) {
            inFile >> mn;
            if (mn == storeMagicNumber) {
                inFile >> requestedBrokerBank;
                inFile >> requestedAgentBank;
                inFile >> bootSequence;
            }
            inFile.close();
        }
    }
}

void ManagementAgentImpl::sendHeartbeat()
{
    static const string addr_exchange("qmf.default.topic");
    static const string addr_key("agent.ind.heartbeat");

    messaging::Message msg;
    messaging::MapContent content(msg);
    messaging::Variant::Map& map(content.asMap());
    messaging::Variant::Map headers;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_heartbeat_indication";
    headers["qmf.agent"] = name_address;

    map["_values"] = attrMap;
    map["_values"].asMap()["timestamp"] = uint64_t(Duration(now()));
    map["_values"].asMap()["heartbeat_interval"] = interval;
    content.encode();
    connThreadBody.sendBuffer(msg.getContent(), "", headers, addr_exchange, addr_key);

    QPID_LOG(trace, "SENT AgentHeartbeat name=" << name_address);
}

void ManagementAgentImpl::sendCommandComplete(string replyToKey, uint32_t sequence,
                                              uint32_t code, string text)
{
    Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader(outBuffer, 'z', sequence);
    outBuffer.putLong(code);
    outBuffer.putShortString(text);
    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    connThreadBody.sendBuffer(outBuffer, outLen, "amq.direct", replyToKey);
    QPID_LOG(trace, "SENT CommandComplete: seq=" << sequence << " code=" << code << " text=" << text);
}

void ManagementAgentImpl::handleAttachResponse(Buffer& inBuffer)
{
    Mutex::ScopedLock lock(agentLock);

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
        storeData();
        requestedBrokerBank = assignedBrokerBank;
        requestedAgentBank = assignedAgentBank;
    }

    attachment.setBanks(assignedBrokerBank, assignedAgentBank);

    // Bind to qpid.management to receive commands
    connThreadBody.bindToBank(assignedBrokerBank, assignedAgentBank);

    // Send package indications for all local packages
    for (PackageMap::iterator pIter = packages.begin();
         pIter != packages.end();
         pIter++) {
        Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        encodeHeader(outBuffer, 'p');
        encodePackageIndication(outBuffer, pIter);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        connThreadBody.sendBuffer(outBuffer, outLen, "qpid.management", "broker");

        // Send class indications for all local classes
        ClassMap cMap = pIter->second;
        for (ClassMap::iterator cIter = cMap.begin(); cIter != cMap.end(); cIter++) {
            outBuffer.reset();
            encodeHeader(outBuffer, 'q');
            encodeClassIndication(outBuffer, pIter, cIter);
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            connThreadBody.sendBuffer(outBuffer, outLen, "qpid.management", "broker");
        }
    }
}

void ManagementAgentImpl::handleSchemaRequest(Buffer& inBuffer, uint32_t sequence, const string& replyTo)
{
    Mutex::ScopedLock lock(agentLock);
    string packageName;
    SchemaClassKey key;

    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);

    QPID_LOG(trace, "RCVD SchemaRequest: package=" << packageName << " class=" << key.name);

    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end()) {
        ClassMap& cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find(key);
        if (cIter != cMap.end()) {
            SchemaClass& schema = cIter->second;
            Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;
            string   body;

            encodeHeader(outBuffer, 's', sequence);
            schema.writeSchemaCall(body);
            outBuffer.putRawData(body);
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            connThreadBody.sendBuffer(outBuffer, outLen, "amq.direct", replyTo);

            QPID_LOG(trace, "SENT SchemaInd: package=" << packageName << " class=" << key.name);
        }
    }
}

void ManagementAgentImpl::handleConsoleAddedIndication()
{
    Mutex::ScopedLock lock(agentLock);
    clientWasAdded = true;

    QPID_LOG(trace, "RCVD ConsoleAddedInd");
}

void ManagementAgentImpl::invokeMethodRequest(const string& body, const string& cid, const string& replyTo)
{
    string  methodName;
    bool    failed = false;
    qpid::messaging::Message inMsg(body);
    qpid::messaging::MapView inMap(inMsg);
    qpid::messaging::MapView::const_iterator oid, mid;

    qpid::messaging::Message outMsg;
    qpid::messaging::MapContent outMap(outMsg);

    if ((oid = inMap.find("_object_id")) == inMap.end() ||
        (mid = inMap.find("_method_name")) == inMap.end()) {
        (outMap["_values"].asMap())["_status"] = Manageable::STATUS_PARAMETER_INVALID;
        (outMap["_values"].asMap())["_status_text"] = Manageable::StatusText(Manageable::STATUS_PARAMETER_INVALID);
        failed = true;
    } else {
        string methodName;
        ObjectId objId;
        qpid::messaging::Variant::Map inArgs;

        try {
            // coversions will throw if input is invalid.
            objId = ObjectId(oid->second.asMap());
            methodName = mid->second.getString();

            mid = inMap.find("_arguments");
            if (mid != inMap.end()) {
                inArgs = (mid->second).asMap();
            }

            ManagementObjectMap::iterator iter = managementObjects.find(objId);
            if (iter == managementObjects.end() || iter->second->isDeleted()) {
                (outMap["_values"].asMap())["_status"] = Manageable::STATUS_UNKNOWN_OBJECT;
                (outMap["_values"].asMap())["_status_text"] = Manageable::StatusText(Manageable::STATUS_UNKNOWN_OBJECT);
                failed = true;
            } else {

                iter->second->doMethod(methodName, inArgs, outMap.asMap());
            }

        } catch(exception& e) {
            outMap.clear();
            (outMap["_values"].asMap())["_status"] = Manageable::STATUS_EXCEPTION;
            (outMap["_values"].asMap())["_status_text"] = e.what();
            failed = true;
        }
    }

    qpid::messaging::Variant::Map headers;
    headers["method"] = "response";
    headers["qmf.agent"] = name_address;
    if (failed)
        headers["qmf.opcode"] = "_exception";
    else
        headers["qmf.opcode"] = "_method_response";

    outMap.encode();
    connThreadBody.sendBuffer(outMsg.getContent(), cid, headers, "qmf.default.direct", replyTo);
}

void ManagementAgentImpl::handleGetQuery(const string& body, const string& contentType,
                                         const string& cid, const string& replyTo)
{
    moveNewObjectsLH();

    if (contentType != "_query_v1") {
        QPID_LOG(warning, "Support for QMF V2 Query format TBD!!!");
        return;
    }

    qpid::messaging::Message inMsg(body);
    qpid::messaging::MapView inMap(inMsg);
    qpid::messaging::MapView::const_iterator i;
    ::qpid::messaging::Variant::Map headers;

    QPID_LOG(trace, "RCVD GetQuery: map=" << inMap << " cid=" << cid);

    headers["method"] = "response";
    headers["qmf.opcode"] = "_query_response";
    headers["qmf.content"] = "_data";
    headers["qmf.agent"] = name_address;
    headers["partial"];

    ::qpid::messaging::Message outMsg;
    ::qpid::messaging::ListContent content(outMsg);
    ::qpid::messaging::Variant::List &list_ = content.asList();
    ::qpid::messaging::Variant::Map  map_;
    ::qpid::messaging::Variant::Map values;
    string className;

    i = inMap.find("_class");
    if (i != inMap.end())
        try {
            className = i->second.asString();
        } catch(exception& e) {
            className.clear();
            QPID_LOG(trace, "RCVD GetQuery: invalid format - class target ignored.");
        }

    if (className.empty()) {
        ObjectId objId;
        i = inMap.find("_object_id");
        if (i != inMap.end()) {

            try {
                objId = ObjectId(i->second.asMap());
            } catch (exception &e) {
                objId = ObjectId();   // empty object id - won't find a match (I hope).
                QPID_LOG(trace, "RCVD GetQuery (invalid Object Id format) to=" << replyTo << " seq=" << cid);
            }

            ManagementObjectMap::iterator iter = managementObjects.find(objId);
            if (iter != managementObjects.end()) {
                ManagementObject* object = iter->second;

                if (object->getConfigChanged() || object->getInstChanged())
                    object->setUpdateTime();

                object->mapEncodeValues(values, true, true); // write both stats and properties
                map_["_values"] = values;
                list_.push_back(map_);

                content.encode();
                connThreadBody.sendBuffer(outMsg.getContent(), cid, headers, "qmf.default.direct", replyTo);
            }
        }
    } else {
        for (ManagementObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++) {
            ManagementObject* object = iter->second;
            if (object->getClassName() == className) {

                // @todo support multiple object reply per message
                values.clear();
                list_.clear();

                if (object->getConfigChanged() || object->getInstChanged())
                    object->setUpdateTime();

                object->mapEncodeValues(values, true, true); // write both stats and properties
                map_["_values"] = values;
                list_.push_back(map_);

                content.encode();
                connThreadBody.sendBuffer(outMsg.getContent(), cid, headers, "qmf.default.direct", replyTo);
            }
        }
    }

    // end empty "non-partial" message to indicate CommandComplete
    list_.clear();
    headers.erase("partial");
    content.encode();
    connThreadBody.sendBuffer(outMsg.getContent(), cid, headers, "qmf.default.direct", replyTo);
    QPID_LOG(trace, "SENT ObjectInd");
}

void ManagementAgentImpl::handleLocateRequest(const string&, const string& cid, const string& replyTo)
{
    QPID_LOG(trace, "RCVD AgentLocateRequest");
    static const string addr_exchange("qmf.default.direct");

    messaging::Message msg;
    messaging::MapContent content(msg);
    messaging::Variant::Map& map(content.asMap());
    messaging::Variant::Map headers;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_locate_response";
    headers["qmf.agent"] = name_address;

    map["_values"] = attrMap;
    map["_values"].asMap()["timestamp"] = uint64_t(Duration(now()));
    map["_values"].asMap()["heartbeat_interval"] = interval;
    content.encode();
    connThreadBody.sendBuffer(msg.getContent(), cid, headers, addr_exchange, replyTo);

    QPID_LOG(trace, "SENT AgentLocateResponse replyTo=" << replyTo);
}

void ManagementAgentImpl::handleMethodRequest(const string& body, const string& cid, const string& replyTo)
{
    if (extThread) {
        Mutex::ScopedLock lock(agentLock);

        methodQueue.push_back(new QueuedMethod(cid, replyTo, body));
        if (pipeHandle != 0) {
            pipeHandle->write("X", 1);
        } else if (notifyable != 0) {
            inCallback = true;
            {
                Mutex::ScopedUnlock unlock(agentLock);
                notifyable->notify();
            }
            inCallback = false;
        } else if (notifyCallback != 0) {
            inCallback = true;
            {
                Mutex::ScopedUnlock unlock(agentLock);
                notifyCallback(notifyContext);
            }
            inCallback = false;
        }
    } else {
        invokeMethodRequest(body, cid, replyTo);
    }

    QPID_LOG(trace, "RCVD MethodRequest");
}

void ManagementAgentImpl::received(Message& msg)
{
    string   replyToKey;
    framing::MessageProperties mp = msg.getMessageProperties();
    if (mp.hasReplyTo()) {
        const framing::ReplyTo& rt = mp.getReplyTo();
        replyToKey = rt.getRoutingKey();
    }

    if (mp.hasAppId() && mp.getAppId() == "qmf2")
    {
        string opcode = mp.getApplicationHeaders().getAsString("qmf.opcode");
        string cid = msg.getMessageProperties().getCorrelationId();

        if      (opcode == "_agent_locate_request") handleLocateRequest(msg.getData(), cid, replyToKey);
        else if (opcode == "_method_request")       handleMethodRequest(msg.getData(), cid, replyToKey);
        else if (opcode == "_query_request")        handleGetQuery(msg.getData(),
                                                                   mp.getApplicationHeaders().getAsString("qmf.content"),
                                                                   cid, replyToKey);
        else {
            QPID_LOG(warning, "Support for QMF V2 Opcode [" << opcode << "] TBD!!!");
        }
        return;
    }

    // old preV2 binary messages
    
    uint32_t sequence;
    string   data = msg.getData();
    Buffer   inBuffer(const_cast<char*>(data.c_str()), data.size());
    uint8_t  opcode;


    if (checkHeader(inBuffer, &opcode, &sequence))
    {
        if      (opcode == 'a') handleAttachResponse(inBuffer);
        else if (opcode == 'S') handleSchemaRequest(inBuffer, sequence, replyToKey);
        else if (opcode == 'x') handleConsoleAddedIndication();
            QPID_LOG(warning, "Ignoring old-format QMF Request! opcode=" << char(opcode));
    }
}


void ManagementAgentImpl::encodeHeader(Buffer& buf, uint8_t opcode, uint32_t seq)
{
    buf.putOctet('A');
    buf.putOctet('M');
    buf.putOctet('2');
    buf.putOctet(opcode);
    buf.putLong (seq);
}

qpid::messaging::Variant::Map ManagementAgentImpl::mapEncodeSchemaId(const string& pname,
                                                                     const string& cname,
                                                                     const uint8_t *md5Sum)
{
    qpid::messaging::Variant::Map map_;

    map_["_package_name"] = pname;
    map_["_class_name"] = cname;
    map_["_hash_str"] = messaging::Uuid(md5Sum);
    return map_;
}


bool ManagementAgentImpl::checkHeader(Buffer& buf, uint8_t *opcode, uint32_t *seq)
{
    if (buf.getSize() < 8)
        return false;

    uint8_t h1 = buf.getOctet();
    uint8_t h2 = buf.getOctet();
    uint8_t h3 = buf.getOctet();

    *opcode = buf.getOctet();
    *seq    = buf.getLong();

    return h1 == 'A' && h2 == 'M' && h3 == '2';
}

ManagementAgentImpl::PackageMap::iterator ManagementAgentImpl::findOrAddPackage(const string& name)
{
    PackageMap::iterator pIter = packages.find(name);
    if (pIter != packages.end())
        return pIter;

    // No such package found, create a new map entry.
    pair<PackageMap::iterator, bool> result =
        packages.insert(pair<string, ClassMap>(name, ClassMap()));

    if (connected) {
        // Publish a package-indication message
        Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        encodeHeader(outBuffer, 'p');
        encodePackageIndication(outBuffer, result.first);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        connThreadBody.sendBuffer(outBuffer, outLen, "qpid.management", "schema.package");
    }

    return result.first;
}

void ManagementAgentImpl::moveNewObjectsLH()
{
    Mutex::ScopedLock lock(addLock);
    for (ManagementObjectMap::iterator iter = newManagementObjects.begin();
         iter != newManagementObjects.end();
         iter++)
        managementObjects[iter->first] = iter->second;
    newManagementObjects.clear();
}

void ManagementAgentImpl::addClassLocal(uint8_t               classKind,
                                        PackageMap::iterator  pIter,
                                        const string&         className,
                                        uint8_t*              md5Sum,
                                        qpid::management::ManagementObject::writeSchemaCall_t schemaCall)
{
    SchemaClassKey key;
    ClassMap&      cMap = pIter->second;

    key.name = className;
    memcpy(&key.hash, md5Sum, 16);

    ClassMap::iterator cIter = cMap.find(key);
    if (cIter != cMap.end())
        return;

    // No such class found, create a new class with local information.
    cMap.insert(pair<SchemaClassKey, SchemaClass>(key, SchemaClass(schemaCall, classKind)));
}

void ManagementAgentImpl::encodePackageIndication(Buffer&              buf,
                                                  PackageMap::iterator pIter)
{
    buf.putShortString((*pIter).first);

    QPID_LOG(trace, "SENT PackageInd: package=" << (*pIter).first);
}

void ManagementAgentImpl::encodeClassIndication(Buffer&              buf,
                                                PackageMap::iterator pIter,
                                                ClassMap::iterator   cIter)
{
    SchemaClassKey key = (*cIter).first;

    buf.putOctet((*cIter).second.kind);
    buf.putShortString((*pIter).first);
    buf.putShortString(key.name);
    buf.putBin128(key.hash);

    QPID_LOG(trace, "SENT ClassInd: package=" << (*pIter).first << " class=" << key.name);
}

void ManagementAgentImpl::periodicProcessing()
{
    Mutex::ScopedLock lock(agentLock);
    list<pair<ObjectId, ManagementObject*> > deleteList;

    if (!connected)
        return;

    moveNewObjectsLH();

    //
    //  Clear the been-here flag on all objects in the map.
    //
    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         iter++) {
        ManagementObject* object = iter->second;
        object->setFlags(0);
        if (clientWasAdded) {
            object->setForcePublish(true);
        }
    }

    clientWasAdded = false;

    //
    //  Process the entire object map.
    //
    for (ManagementObjectMap::iterator baseIter = managementObjects.begin();
         baseIter != managementObjects.end();
         baseIter++) {
        ManagementObject* baseObject = baseIter->second;

        //
        //  Skip until we find a base object requiring a sent message.
        //
        if (baseObject->getFlags() == 1 ||
            (!baseObject->getConfigChanged() &&
             !baseObject->getInstChanged() &&
             !baseObject->getForcePublish() &&
             !baseObject->isDeleted()))
            continue;

        ::qpid::messaging::Message m;
        ::qpid::messaging::ListContent content(m);
        ::qpid::messaging::Variant::List &list_ = content.asList();

        for (ManagementObjectMap::iterator iter = baseIter;
             iter != managementObjects.end();
             iter++) {
            ManagementObject* object = iter->second;
            bool send_stats, send_props;
            if (baseObject->isSameClass(*object) && object->getFlags() == 0) {
                object->setFlags(1);
                if (object->getConfigChanged() || object->getInstChanged())
                    object->setUpdateTime();

                send_props = (object->getConfigChanged() || object->getForcePublish() || object->isDeleted());
                send_stats = (object->hasInst() && (object->getInstChanged() || object->getForcePublish()));

                if (send_stats || send_props) {
                    ::qpid::messaging::Variant::Map map_;
                    ::qpid::messaging::Variant::Map values;
                    ::qpid::messaging::Variant::Map oid;

                    object->getObjectId().mapEncode(oid);
                    map_["_object_id"] = oid;
                    map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                           object->getClassName(),
                                                           object->getMd5Sum());
                    object->mapEncodeValues(values, send_props, send_stats);
                    map_["_values"] = values;
                    list_.push_back(map_);
                }

                if (object->isDeleted())
                    deleteList.push_back(pair<ObjectId, ManagementObject*>(iter->first, object));
                object->setForcePublish(false);
            }
        }

        content.encode();
        const string &str = m.getContent();
        if (str.length()) {
            ::qpid::messaging::Variant::Map  headers;
            headers["method"] = "indication";
            headers["qmf.opcode"] = "_data_indication";
            headers["qmf.content"] = "_data";
            headers["qmf.agent"] = name_address;

            connThreadBody.sendBuffer(str, "", headers, "qmf.default.topic", "agent.ind.data", "amqp/list");
            QPID_LOG(trace, "SENT DataIndication");
        }
    }

    // Delete flagged objects
    for (list<pair<ObjectId, ManagementObject*> >::reverse_iterator iter = deleteList.rbegin();
         iter != deleteList.rend();
         iter++) {
        delete iter->second;
        managementObjects.erase(iter->first);
    }

    deleteList.clear();
    sendHeartbeat();
}

void ManagementAgentImpl::ConnectionThread::run()
{
    static const int delayMin(1);
    static const int delayMax(128);
    static const int delayFactor(2);
    int delay(delayMin);
    string dest("qmfagent");
    ConnectionThread::shared_ptr tmp;

    sessionId.generate();
    queueName << "qmfagent-" << sessionId;

    while (true) {
        try {
            if (agent.initialized) {
                QPID_LOG(debug, "QMF Agent attempting to connect to the broker...");
                connection.open(agent.connectionSettings);
                session = connection.newSession(queueName.str());
                subscriptions.reset(new client::SubscriptionManager(session));

                session.queueDeclare(arg::queue=queueName.str(), arg::autoDelete=true,
                                     arg::exclusive=true);
                session.exchangeBind(arg::exchange="amq.direct", arg::queue=queueName.str(),
                                     arg::bindingKey=queueName.str());
                session.exchangeBind(arg::exchange="qmf.default.direct", arg::queue=queueName.str(),
                                     arg::bindingKey=agent.name_address);
                session.exchangeBind(arg::exchange="qmf.default.topic", arg::queue=queueName.str(),
                                     arg::bindingKey="console.#");

                subscriptions->subscribe(agent, queueName.str(), dest);
                QPID_LOG(info, "Connection established with broker");
                {
                    Mutex::ScopedLock _lock(connLock);
                    if (shutdown)
                        return;
                    operational = true;
                    agent.connected = true;
                    agent.startProtocol();
                    try {
                        Mutex::ScopedUnlock _unlock(connLock);
                        subscriptions->run();
                    } catch (exception) {}

                    QPID_LOG(warning, "Connection to the broker has been lost");

                    operational = false;
                    agent.connected = false;
                    tmp = subscriptions;
                    subscriptions.reset();
                }
                tmp.reset();    // frees the subscription outside the lock
                delay = delayMin;
                connection.close();
            }
        } catch (exception &e) {
            if (delay < delayMax)
                delay *= delayFactor;
            QPID_LOG(debug, "Connection failed: exception=" << e.what());
        }

        {
            // sleep for "delay" seconds, but peridically check if the
            // agent is shutting down so we don't hang for up to delayMax 
            // seconds during agent shutdown
             Mutex::ScopedLock _lock(connLock);
             if (shutdown)
                 return;
             sleeping = true;
             int totalSleep = 0;
             do {
                 Mutex::ScopedUnlock _unlock(connLock);
                 ::sleep(delayMin);
                 totalSleep += delayMin;
             } while (totalSleep < delay && !shutdown);
             sleeping = false;
             if (shutdown)
                 return;
        }
    }
}

ManagementAgentImpl::ConnectionThread::~ConnectionThread()
{
}

void ManagementAgentImpl::ConnectionThread::sendBuffer(Buffer&  buf,
                                                       uint32_t length,
                                                       const string& exchange,
                                                       const string& routingKey)
{
    Message msg;
    string  data;

    buf.getRawData(data, length);
    msg.setData(data);
    sendMessage(msg, exchange, routingKey);
}



void ManagementAgentImpl::ConnectionThread::sendBuffer(const string& data,
                                                       const string& cid,
                                                       const qpid::messaging::VariantMap headers,
                                                       const string& exchange,
                                                       const string& routingKey,
                                                       const string& contentType)
{
    Message msg;
    qpid::messaging::VariantMap::const_iterator i;

    if (!cid.empty())
        msg.getMessageProperties().setCorrelationId(cid);

    if (!contentType.empty())
        msg.getMessageProperties().setContentType(contentType);
    for (i = headers.begin(); i != headers.end(); ++i) {
        msg.getHeaders().setString(i->first, i->second.asString());
    }
    msg.getHeaders().setString("app_id", "qmf2");

    msg.setData(data);
    sendMessage(msg, exchange, routingKey);
}





void ManagementAgentImpl::ConnectionThread::sendMessage(Message msg,
                                                        const string& exchange,
                                                        const string& routingKey)
{
    ConnectionThread::shared_ptr s;
    {
        Mutex::ScopedLock _lock(connLock);
        if (!operational)
            return;
        s = subscriptions;
    }

    msg.getDeliveryProperties().setRoutingKey(routingKey);
    msg.getMessageProperties().setReplyTo(ReplyTo("amq.direct", queueName.str()));
    msg.getMessageProperties().getApplicationHeaders().setString("qmf.agent", agent.name_address);
    try {
        session.messageTransfer(arg::content=msg, arg::destination=exchange);
    } catch(exception& e) {
        QPID_LOG(error, "Exception caught in sendMessage: " << e.what());
        // Bounce the connection
        if (s)
            s->stop();
    }
}



void ManagementAgentImpl::ConnectionThread::bindToBank(uint32_t brokerBank, uint32_t agentBank)
{
    stringstream key;
    key << "agent." << brokerBank << "." << agentBank;
    session.exchangeBind(arg::exchange="qpid.management", arg::queue=queueName.str(),
                         arg::bindingKey=key.str());
}

void ManagementAgentImpl::ConnectionThread::close()
{
    ConnectionThread::shared_ptr s;
    {
        Mutex::ScopedLock _lock(connLock);
        shutdown = true;
        s = subscriptions;
    }
    if (s)
        s->stop();
}

bool ManagementAgentImpl::ConnectionThread::isSleeping() const
{
    Mutex::ScopedLock _lock(connLock);
    return sleeping;
}


void ManagementAgentImpl::PublishThread::run()
{
    uint16_t    totalSleep;

    while (!shutdown) {
        agent.periodicProcessing();
        totalSleep = 0;
        while (totalSleep++ < agent.getInterval() && !shutdown) {
            ::sleep(1);
        }
    }
}
