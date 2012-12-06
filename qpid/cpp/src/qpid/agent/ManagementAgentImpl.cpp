
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
#include "qpid/amqp_0_10/Codecs.h"
#include <list>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>
#include <iostream>
#include <fstream>
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace management {

using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;
using namespace std;
using std::stringstream;
using std::ofstream;
using std::ifstream;
using std::string;
using std::endl;
using qpid::types::Variant;
using qpid::amqp_0_10::MapCodec;
using qpid::amqp_0_10::ListCodec;

namespace {
    qpid::sys::Mutex lock;
    bool disabled = false;
    ManagementAgent* agent = 0;
    int refCount = 0;

    const string defaultVendorName("vendor");
    const string defaultProductName("product");

    // Create a valid binding key substring by
    // replacing all '.' chars with '_'
    const string keyifyNameStr(const string& name)
    {
        string n2 = name;

        size_t pos = n2.find('.');
        while (pos != n2.npos) {
            n2.replace(pos, 1, "_");
            pos = n2.find('.', pos);
        }
        return n2;
    }
}

ManagementAgent::Singleton::Singleton(bool disableManagement)
{
    sys::Mutex::ScopedLock _lock(lock);
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
    sys::Mutex::ScopedLock _lock(lock);
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
    topicExchange("qmf.default.topic"), directExchange("qmf.default.direct"),
    schemaTimestamp(Duration(EPOCH, now())),
    publishAllData(true), requestedBrokerBank(0), requestedAgentBank(0),
    assignedBrokerBank(0), assignedAgentBank(0), bootSequence(0),
    maxV2ReplyObjs(10),   // KAG todo: make this a tuneable parameter
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

    if (pipeHandle) {
        delete pipeHandle;
        pipeHandle = 0;
    }
}

void ManagementAgentImpl::setName(const string& vendor, const string& product, const string& instance)
{
    if (vendor.find(':') != vendor.npos) {
        throw Exception("vendor string cannot contain a ':' character.");
    }
    if (product.find(':') != product.npos) {
        throw Exception("product string cannot contain a ':' character.");
    }

    attrMap["_vendor"] = vendor;
    attrMap["_product"] = product;
    if (!instance.empty()) {
        attrMap["_instance"] = instance;
    }
}


void ManagementAgentImpl::getName(string& vendor, string& product, string& instance)
{
    vendor = std::string(attrMap["_vendor"]);
    product = std::string(attrMap["_product"]);
    instance = std::string(attrMap["_instance"]);
}


const std::string& ManagementAgentImpl::getAddress()
{
    return name_address;
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
    management::ConnectionSettings settings;
    settings.protocol = proto;
    settings.host = brokerHost;
    settings.port = brokerPort;
    settings.username = uid;
    settings.password = pwd;
    settings.mechanism = mech;
    settings.heartbeat = 10;
    init(settings, intervalSeconds, useExternalThread, _storeFile);
}

void ManagementAgentImpl::init(const qpid::management::ConnectionSettings& settings,
                               uint16_t intervalSeconds,
                               bool useExternalThread,
                               const string& _storeFile)
{
    std::string cfgVendor, cfgProduct, cfgInstance;

    interval     = intervalSeconds;
    extThread    = useExternalThread;
    storeFile    = _storeFile;
    nextObjectId = 1;

    //
    // Convert from management::ConnectionSettings to client::ConnectionSettings
    //
    connectionSettings.protocol     = settings.protocol;
    connectionSettings.host         = settings.host;
    connectionSettings.port         = settings.port;
    connectionSettings.virtualhost  = settings.virtualhost;
    connectionSettings.username     = settings.username;
    connectionSettings.password     = settings.password;
    connectionSettings.mechanism    = settings.mechanism;
    connectionSettings.locale       = settings.locale;
    connectionSettings.heartbeat    = settings.heartbeat;
    connectionSettings.maxChannels  = settings.maxChannels;
    connectionSettings.maxFrameSize = settings.maxFrameSize;
    connectionSettings.bounds       = settings.bounds;
    connectionSettings.tcpNoDelay   = settings.tcpNoDelay;
    connectionSettings.service      = settings.service;
    connectionSettings.minSsf       = settings.minSsf;
    connectionSettings.maxSsf       = settings.maxSsf;

    retrieveData(cfgVendor, cfgProduct, cfgInstance);

    bootSequence++;
    if ((bootSequence & 0xF000) != 0)
        bootSequence = 1;

    // setup the agent's name.  The name may be set via a call to setName().  If setName()
    // has not been called, the name can be read from the configuration file.  If there is
    // no name in the configuration file, a unique default name is provided.
    if (attrMap.empty()) {
        // setName() never called by application, so use names retrieved from config, otherwise defaults.
        setName(cfgVendor.empty() ? defaultVendorName : cfgVendor,
                cfgProduct.empty() ? defaultProductName : cfgProduct,
                cfgInstance.empty() ? qpid::types::Uuid(true).str() : cfgInstance);
    } else if (attrMap.find("_instance") == attrMap.end()) {
        // setName() called, but instance was not specified, use config or generate a uuid
        setName(attrMap["_vendor"].asString(), attrMap["_product"].asString(),
                cfgInstance.empty() ? qpid::types::Uuid(true).str() : cfgInstance);
    }

    name_address = attrMap["_vendor"].asString() + ":" + attrMap["_product"].asString() + ":" + attrMap["_instance"].asString();
    vendorNameKey = keyifyNameStr(attrMap["_vendor"].asString());
    productNameKey = keyifyNameStr(attrMap["_product"].asString());
    instanceNameKey = keyifyNameStr(attrMap["_instance"].asString());
    attrMap["_name"] = name_address;

    storeData(true);

    QPID_LOG(info, "QMF Agent Initialized: broker=" << settings.host << ":" << settings.port <<
             " interval=" << intervalSeconds << " storeFile=" << _storeFile << " name=" << name_address);

    initialized = true;
}

void ManagementAgentImpl::registerClass(const string& packageName,
                                        const string& className,
                                        uint8_t*     md5Sum,
                                        ManagementObject::writeSchemaCall_t schemaCall)
{ 
    sys::Mutex::ScopedLock lock(agentLock);
    PackageMap::iterator pIter = findOrAddPackage(packageName);
    addClassLocal(ManagementItem::CLASS_KIND_TABLE, pIter, className, md5Sum, schemaCall);
}

void ManagementAgentImpl::registerEvent(const string& packageName,
                                        const string& eventName,
                                        uint8_t*     md5Sum,
                                        ManagementObject::writeSchemaCall_t schemaCall)
{ 
    sys::Mutex::ScopedLock lock(agentLock);
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
    sys::Mutex::ScopedLock lock(addLock);

    uint16_t sequence  = persistent ? 0 : bootSequence;

    ObjectId objectId(&attachment, 0, sequence);
    if (key.empty())
        objectId.setV2Key(*object);  // let object generate the key
    else
        objectId.setV2Key(key);
    objectId.setAgentName(name_address);

    object->setObjectId(objectId);
    newManagementObjects[objectId] = boost::shared_ptr<ManagementObject>(object);
    return objectId;
}


void ManagementAgentImpl::raiseEvent(const ManagementEvent& event, severity_t severity)
{
    static const std::string severityStr[] = {
        "emerg", "alert", "crit", "error", "warn",
        "note", "info", "debug"
    };
    string content;
    stringstream key;
    Variant::Map headers;

    {
        sys::Mutex::ScopedLock lock(agentLock);
        Buffer outBuffer(eventBuffer, MA_BUFFER_SIZE);
        uint8_t sev = (severity == SEV_DEFAULT) ? event.getSeverity() : (uint8_t) severity;

        // key << "console.event." << assignedBrokerBank << "." << assignedAgentBank << "." <<
        // event.getPackageName() << "." << event.getEventName();
        key << "agent.ind.event." << keyifyNameStr(event.getPackageName())
            << "." << keyifyNameStr(event.getEventName())
            << "." << severityStr[sev]
            << "." << vendorNameKey
            << "." << productNameKey
            << "." << instanceNameKey;

        Variant::Map map_;
        Variant::Map schemaId;
        Variant::Map values;

        map_["_schema_id"] = mapEncodeSchemaId(event.getPackageName(),
                                               event.getEventName(),
                                               event.getMd5Sum(),
                                               ManagementItem::CLASS_KIND_EVENT);
        event.mapEncode(values);
        map_["_values"] = values;
        map_["_timestamp"] = uint64_t(Duration(EPOCH, now()));
        map_["_severity"] = sev;

        headers["method"] = "indication";
        headers["qmf.opcode"] = "_data_indication";
        headers["qmf.content"] = "_event";
        headers["qmf.agent"] = name_address;

        Variant::List list;
        list.push_back(map_);
        ListCodec::encode(list, content);
    }

    connThreadBody.sendBuffer(content, "", headers, topicExchange, key.str(), "amqp/list");
}

uint32_t ManagementAgentImpl::pollCallbacks(uint32_t callLimit)
{
    sys::Mutex::ScopedLock lock(agentLock);

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
            sys::Mutex::ScopedUnlock unlock(agentLock);
            invokeMethodRequest(item->body, item->cid, item->replyToExchange, item->replyToKey, item->userId);
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
    sys::Mutex::ScopedLock lock(agentLock);
    notifyCallback = callback;
    notifyContext  = context;
}

void ManagementAgentImpl::setSignalCallback(Notifyable& _notifyable)
{
    sys::Mutex::ScopedLock lock(agentLock);
    notifyable = &_notifyable;
}

void ManagementAgentImpl::startProtocol()
{
    sendHeartbeat();
    {
        sys::Mutex::ScopedLock lock(agentLock);
        publishAllData = true;
    }
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

            if (attrMap.find("_vendor") != attrMap.end())
                outFile << "vendor=" << attrMap["_vendor"] << endl;
            if (attrMap.find("_product") != attrMap.end())
                outFile << "product=" << attrMap["_product"] << endl;
            if (attrMap.find("_instance") != attrMap.end())
                outFile << "instance=" << attrMap["_instance"] << endl;

            outFile.close();
        }
    }
}

void ManagementAgentImpl::retrieveData(std::string& vendor, std::string& product, std::string& inst)
{
    vendor.clear();
    product.clear();
    inst.clear();

    if (!storeFile.empty()) {
        ifstream inFile(storeFile.c_str());
        string   mn;

        if (inFile.good()) {
            inFile >> mn;
            if (mn == storeMagicNumber) {
                std::string inText;

                inFile >> requestedBrokerBank;
                inFile >> requestedAgentBank;
                inFile >> bootSequence;

                while (inFile.good()) {
                    std::getline(inFile, inText);
                    if (!inText.compare(0, 7, "vendor=")) {
                        vendor = inText.substr(7);
                        QPID_LOG(debug, "read vendor name [" << vendor << "] from configuration file.");
                    } else if (!inText.compare(0, 8, "product=")) {
                        product = inText.substr(8);
                        QPID_LOG(debug, "read product name [" << product << "] from configuration file.");
                    } else if (!inText.compare(0, 9, "instance=")) {
                        inst = inText.substr(9);
                        QPID_LOG(debug, "read instance name [" << inst << "] from configuration file.");
                    }
                }
            }
            inFile.close();
        }
    }
}

void ManagementAgentImpl::sendHeartbeat()
{
    static const string addr_key_base("agent.ind.heartbeat.");

    Variant::Map map;
    Variant::Map headers;
    string content;
    std::stringstream addr_key;

    addr_key << addr_key_base << vendorNameKey
             << "." << productNameKey
             << "." << instanceNameKey;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_heartbeat_indication";
    headers["qmf.agent"] = name_address;

    getHeartbeatContent(map);
    MapCodec::encode(map, content);

    // Set TTL (in msecs) on outgoing heartbeat indications based on the interval
    // time to prevent stale heartbeats from getting to the consoles.

    connThreadBody.sendBuffer(content, "", headers, topicExchange, addr_key.str(),
                              "amqp/map", interval * 2 * 1000);

    QPID_LOG(trace, "SENT AgentHeartbeat name=" << name_address);
}

void ManagementAgentImpl::sendException(const string& rte, const string& rtk, const string& cid,
                                        const string& text, uint32_t code)
{
    Variant::Map map;
    Variant::Map headers;
    Variant::Map values;
    string content;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_exception";
    headers["qmf.agent"] = name_address;

    values["error_code"] = code;
    values["error_text"] = text;
    map["_values"] = values;

    MapCodec::encode(map, content);
    connThreadBody.sendBuffer(content, cid, headers, rte, rtk);

    QPID_LOG(trace, "SENT Exception code=" << code <<" text=" << text);
}

void ManagementAgentImpl::handleSchemaRequest(Buffer& inBuffer, uint32_t sequence, const string& rte, const string& rtk)
{
    string packageName;
    SchemaClassKey key;
    uint32_t outLen(0);
    char localBuffer[MA_BUFFER_SIZE];
    Buffer outBuffer(localBuffer, MA_BUFFER_SIZE);
    bool found(false);

    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);

    QPID_LOG(trace, "RCVD SchemaRequest: package=" << packageName << " class=" << key.name);

    {
        sys::Mutex::ScopedLock lock(agentLock);
        PackageMap::iterator pIter = packages.find(packageName);
        if (pIter != packages.end()) {
            ClassMap& cMap = pIter->second;
            ClassMap::iterator cIter = cMap.find(key);
            if (cIter != cMap.end()) {
                SchemaClass& schema = cIter->second;
                string   body;

                encodeHeader(outBuffer, 's', sequence);
                schema.writeSchemaCall(body);
                outBuffer.putRawData(body);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                found = true;
            }
        }
    }

    if (found) {
        connThreadBody.sendBuffer(outBuffer, outLen, rte, rtk);
        QPID_LOG(trace, "SENT SchemaInd: package=" << packageName << " class=" << key.name);
    }
}

void ManagementAgentImpl::handleConsoleAddedIndication()
{
    sys::Mutex::ScopedLock lock(agentLock);
    publishAllData = true;

    QPID_LOG(trace, "RCVD ConsoleAddedInd");
}

void ManagementAgentImpl::invokeMethodRequest(const string& body, const string& cid, const string& rte, const string& rtk, const string& userId)
{
    string  methodName;
    bool    failed = false;
    Variant::Map inMap;
    Variant::Map outMap;
    Variant::Map::const_iterator oid, mid;
    string content;

    MapCodec::decode(body, inMap);

    if ((oid = inMap.find("_object_id")) == inMap.end() ||
        (mid = inMap.find("_method_name")) == inMap.end()) {
        sendException(rte, rtk, cid, Manageable::StatusText(Manageable::STATUS_PARAMETER_INVALID),
                      Manageable::STATUS_PARAMETER_INVALID);
        failed = true;
    } else {
        string methodName;
        ObjectId objId;
        Variant::Map inArgs;
        Variant::Map callMap;

        try {
            // conversions will throw if input is invalid.
            objId = ObjectId(oid->second.asMap());
            methodName = mid->second.getString();

            mid = inMap.find("_arguments");
            if (mid != inMap.end()) {
                inArgs = (mid->second).asMap();
            }

            QPID_LOG(trace, "Invoking Method: name=" << methodName << " args=" << inArgs);

            boost::shared_ptr<ManagementObject> oPtr;
            {
                sys::Mutex::ScopedLock lock(agentLock);
                ObjectMap::iterator iter = managementObjects.find(objId);
                if (iter != managementObjects.end() && !iter->second->isDeleted())
                    oPtr = iter->second;
            }

            if (oPtr.get() == 0) {
                sendException(rte, rtk, cid, Manageable::StatusText(Manageable::STATUS_UNKNOWN_OBJECT),
                              Manageable::STATUS_UNKNOWN_OBJECT);
                failed = true;
            } else {
                oPtr->doMethod(methodName, inArgs, callMap, userId);

                if (callMap["_status_code"].asUint32() == 0) {
                    outMap["_arguments"] = Variant::Map();
                    for (Variant::Map::const_iterator iter = callMap.begin();
                         iter != callMap.end(); iter++)
                        if (iter->first != "_status_code" && iter->first != "_status_text")
                            outMap["_arguments"].asMap()[iter->first] = iter->second;
                } else {
                    sendException(rte, rtk, cid, callMap["_status_text"], callMap["_status_code"]);
                    failed = true;
                }
            }

        } catch(types::InvalidConversion& e) {
            sendException(rte, rtk, cid, e.what(), Manageable::STATUS_EXCEPTION);
            failed = true;
        }
    }

    if (!failed) {
        Variant::Map headers;
        headers["method"] = "response";
        headers["qmf.agent"] = name_address;
        headers["qmf.opcode"] = "_method_response";
        QPID_LOG(trace, "SENT MethodResponse map=" << outMap);
        MapCodec::encode(outMap, content);
        connThreadBody.sendBuffer(content, cid, headers, rte, rtk);
    }
}

void ManagementAgentImpl::handleGetQuery(const string& body, const string& cid, const string& rte, const string& rtk)
{
    {
        sys::Mutex::ScopedLock lock(agentLock);
        moveNewObjectsLH(lock);
    }

    Variant::Map inMap;
    Variant::Map::const_iterator i;
    Variant::Map headers;

    MapCodec::decode(body, inMap);
    QPID_LOG(trace, "RCVD GetQuery: map=" << inMap << " cid=" << cid);

    headers["method"] = "response";
    headers["qmf.opcode"] = "_query_response";
    headers["qmf.agent"] = name_address;
    headers["partial"] = Variant();

    Variant::List list_;
    Variant::Map  map_;
    Variant::Map values;
    Variant::Map oidMap;
    string content;

    /*
     * Unpack the _what element of the query.  Currently we only support OBJECT queries.
     */
    i = inMap.find("_what");
    if (i == inMap.end()) {
        sendException(rte, rtk, cid, "_what element missing in Query");
        return;
    }

    if (i->second.getType() != qpid::types::VAR_STRING) {
        sendException(rte, rtk, cid, "_what element is not a string");
        return;
    }

    if (i->second.asString() == "OBJECT") {
        headers["qmf.content"] = "_data";
        /*
         * Unpack the _object_id element of the query if it is present.  If it is present, find that one
         * object and return it.  If it is not present, send a class-based result.
         */
        i = inMap.find("_object_id");
        if (i != inMap.end() && i->second.getType() == qpid::types::VAR_MAP) {
            ObjectId objId(i->second.asMap());
            boost::shared_ptr<ManagementObject> object;

            {
                sys::Mutex::ScopedLock lock(agentLock);
                ObjectMap::iterator iter = managementObjects.find(objId);
                if (iter != managementObjects.end())
                    object = iter->second;
            }

            if (object.get() != 0) {
                if (object->getConfigChanged() || object->getInstChanged())
                    object->setUpdateTime();

                object->mapEncodeValues(values, true, true); // write both stats and properties
                objId.mapEncode(oidMap);
                map_["_values"] = values;
                map_["_object_id"] = oidMap;
                object->writeTimestamps(map_);
                map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                       object->getClassName(),
                                                       object->getMd5Sum());
                list_.push_back(map_);
                headers.erase("partial");

                ListCodec::encode(list_, content);
                connThreadBody.sendBuffer(content, cid, headers, rte, rtk, "amqp/list");
                QPID_LOG(trace, "SENT QueryResponse (query by object_id) to=" << rte << "/" << rtk);
                return;
            }
        } else { // match using schema_id, if supplied

            string className;
            string packageName;

            i = inMap.find("_schema_id");
            if (i != inMap.end() && i->second.getType() == qpid::types::VAR_MAP) {
                const Variant::Map& schemaIdMap(i->second.asMap());

                Variant::Map::const_iterator s_iter = schemaIdMap.find("_class_name");
                if (s_iter != schemaIdMap.end() && s_iter->second.getType() == qpid::types::VAR_STRING)
                    className = s_iter->second.asString();

                s_iter = schemaIdMap.find("_package_name");
                if (s_iter != schemaIdMap.end() && s_iter->second.getType() == qpid::types::VAR_STRING)
                    packageName = s_iter->second.asString();

                typedef list<boost::shared_ptr<ManagementObject> > StageList;
                StageList staging;

                {
                    sys::Mutex::ScopedLock lock(agentLock);
                    for (ObjectMap::iterator iter = managementObjects.begin();
                         iter != managementObjects.end();
                         iter++) {
                        ManagementObject* object = iter->second.get();
                        if (object->getClassName() == className &&
                            (packageName.empty() || object->getPackageName() == packageName))
                            staging.push_back(iter->second);
                    }
                }

                unsigned int objCount = 0;
                for (StageList::iterator iter = staging.begin(); iter != staging.end(); iter++) {
                    ManagementObject* object = iter->get();
                    if (object->getClassName() == className &&
                        (packageName.empty() || object->getPackageName() == packageName)) {

                        values.clear();
                        oidMap.clear();
                        map_.clear();

                        if (object->getConfigChanged() || object->getInstChanged())
                            object->setUpdateTime();

                        object->mapEncodeValues(values, true, true); // write both stats and properties
                        object->getObjectId().mapEncode(oidMap);
                        map_["_values"] = values;
                        map_["_object_id"] = oidMap;
                        object->writeTimestamps(map_);
                        map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                               object->getClassName(),
                                                               object->getMd5Sum());
                        list_.push_back(map_);

                        if (++objCount >= maxV2ReplyObjs) {
                            objCount = 0;
                            ListCodec::encode(list_, content);
                            connThreadBody.sendBuffer(content, cid, headers, rte, rtk, "amqp/list");
                            QPID_LOG(trace, "SENT QueryResponse (query by schema_id) to=" << rte << "/" << rtk);
                            content.clear();
                            list_.clear();
                        }
                    }
                }
            }
        }

        // Send last "non-partial" message to indicate CommandComplete
        headers.erase("partial");
        ListCodec::encode(list_, content);
        connThreadBody.sendBuffer(content, cid, headers, rte, rtk, "amqp/list");
        QPID_LOG(trace, "SENT QueryResponse (last message, no 'partial' indicator) to=" << rte << "/" << rtk);

    } else if (i->second.asString() == "SCHEMA_ID") {
        headers["qmf.content"] = "_schema_id";
        /**
         * @todo - support for a predicate.  For now, send a list of all known schema class keys.
         */
        for (PackageMap::iterator pIter = packages.begin();
             pIter != packages.end(); pIter++) {
            for (ClassMap::iterator cIter = pIter->second.begin();
                 cIter != pIter->second.end(); cIter++) {

                list_.push_back(mapEncodeSchemaId( pIter->first,
                                                   cIter->first.name,
                                                   cIter->first.hash,
                                                   cIter->second.kind ));
            }
        }

        headers.erase("partial");
        ListCodec::encode(list_, content);
        connThreadBody.sendBuffer(content, cid, headers, rte, rtk, "amqp/list");
        QPID_LOG(trace, "SENT QueryResponse (SchemaId) to=" << rte << "/" << rtk);

    } else {
        // Unknown query target
        sendException(rte, rtk, cid, "Query for _what => '" + i->second.asString() + "' not supported");
    }
}

void ManagementAgentImpl::handleLocateRequest(const string&, const string& cid, const string& rte, const string& rtk)
{
    QPID_LOG(trace, "RCVD AgentLocateRequest");

    Variant::Map map;
    Variant::Map headers;
    string content;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_locate_response";
    headers["qmf.agent"] = name_address;

    getHeartbeatContent(map);
    MapCodec::encode(map, content);
    connThreadBody.sendBuffer(content, cid, headers, rte, rtk);

    QPID_LOG(trace, "SENT AgentLocateResponse replyTo=" << rte << "/" << rtk);

    {
        sys::Mutex::ScopedLock lock(agentLock);
        publishAllData = true;
    }
}

void ManagementAgentImpl::handleMethodRequest(const string& body, const string& cid, const string& rte, const string& rtk, const string& userId)
{
    if (extThread) {
        sys::Mutex::ScopedLock lock(agentLock);

        methodQueue.push_back(new QueuedMethod(cid, rte, rtk, body, userId));
        if (pipeHandle != 0) {
            pipeHandle->write("X", 1);
        } else if (notifyable != 0) {
            inCallback = true;
            {
                sys::Mutex::ScopedUnlock unlock(agentLock);
                notifyable->notify();
            }
            inCallback = false;
        } else if (notifyCallback != 0) {
            inCallback = true;
            {
                sys::Mutex::ScopedUnlock unlock(agentLock);
                notifyCallback(notifyContext);
            }
            inCallback = false;
        }
    } else {
        invokeMethodRequest(body, cid, rte, rtk, userId);
    }

    QPID_LOG(trace, "RCVD MethodRequest");
}

void ManagementAgentImpl::received(Message& msg)
{
    string   replyToExchange;
    string   replyToKey;
    framing::MessageProperties mp = msg.getMessageProperties();
    if (mp.hasReplyTo()) {
        const framing::ReplyTo& rt = mp.getReplyTo();
        replyToExchange = rt.getExchange();
        replyToKey = rt.getRoutingKey();
    }

    string userId;
    if (mp.hasUserId())
        userId = mp.getUserId();

    if (mp.hasAppId() && mp.getAppId() == "qmf2")
    {
        string opcode = mp.getApplicationHeaders().getAsString("qmf.opcode");
        string cid = msg.getMessageProperties().getCorrelationId();

        if      (opcode == "_agent_locate_request") handleLocateRequest(msg.getData(), cid, replyToExchange, replyToKey);
        else if (opcode == "_method_request")       handleMethodRequest(msg.getData(), cid, replyToExchange, replyToKey, userId);
        else if (opcode == "_query_request")        handleGetQuery(msg.getData(), cid, replyToExchange, replyToKey);
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
        if      (opcode == 'S') handleSchemaRequest(inBuffer, sequence, replyToExchange, replyToKey);
        else if (opcode == 'x') handleConsoleAddedIndication();
        else
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

Variant::Map ManagementAgentImpl::mapEncodeSchemaId(const string& pname,
                                                    const string& cname,
                                                    const uint8_t *md5Sum,
                                                    uint8_t type)
{
    Variant::Map map_;

    map_["_package_name"] = pname;
    map_["_class_name"] = cname;
    map_["_hash"] = types::Uuid(md5Sum);
    if (type == ManagementItem::CLASS_KIND_EVENT)
        map_["_type"] = "_event";
    else
        map_["_type"] = "_data";

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

    return result.first;
}

// note well: caller must hold agentLock when calling this!
void ManagementAgentImpl::moveNewObjectsLH(const sys::Mutex::ScopedLock& /*agentLock*/)
{
    sys::Mutex::ScopedLock lock(addLock);
    ObjectMap::iterator newObj = newManagementObjects.begin();
    while (newObj != newManagementObjects.end()) {
        // before adding a new mgmt object, check for duplicates:
        ObjectMap::iterator oldObj = managementObjects.find(newObj->first);
        if (oldObj == managementObjects.end()) {
            managementObjects[newObj->first] = newObj->second;
            newManagementObjects.erase(newObj++);  // post inc iterator safe!
        } else {
            // object exists with same object id.  This may be legit, for example, when a
            // recently deleted object is re-added before the mgmt poll runs.
            if (newObj->second->isDeleted()) {
                // @TODO fixme: we missed an add-delete for the new object
                QPID_LOG(warning, "Mgmt Object deleted before update sent, oid=" << newObj->first);
                newManagementObjects.erase(newObj++);  // post inc iterator safe!
            } else if (oldObj->second->isDeleted()) {
                // skip adding newObj, try again later once oldObj has been cleaned up by poll
                ++newObj;
            } else {
                // real bad - two objects exist with same OID.  This is a bug in the application
                QPID_LOG(error, "Detected two Mgmt Objects using the same object id! oid=" << newObj->first
                         << ", this is bad!");
                // what to do here?  Can't erase an active obj - owner has a pointer to it.
                // for now I punt.  Maybe the flood of log messages will get someone's attention :P
                ++newObj;
            }
        }
    }
}

void ManagementAgentImpl::addClassLocal(uint8_t               classKind,
                                        PackageMap::iterator  pIter,
                                        const string&         className,
                                        uint8_t*              md5Sum,
                                        ManagementObject::writeSchemaCall_t schemaCall)
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
    schemaTimestamp = Duration(EPOCH, now());
    QPID_LOG(trace, "Updated schema timestamp, now=" << uint64_t(schemaTimestamp));
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

struct MessageItem {
    string content;
    Variant::Map headers;
    string key;
    MessageItem(const Variant::Map& h, const string& k) : headers(h), key(k) {}
};

void ManagementAgentImpl::periodicProcessing()
{
    string addr_key_base = "agent.ind.data.";
    list<ObjectId> deleteList;
    list<boost::shared_ptr<MessageItem> > message_list;

    sendHeartbeat();

    {
        sys::Mutex::ScopedLock lock(agentLock);

        if (!connected)
            return;

        moveNewObjectsLH(lock);

        //
        //  Clear the been-here flag on all objects in the map.
        //
        for (ObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++) {
            ManagementObject* object = iter->second.get();
            object->setFlags(0);
            if (publishAllData) {
                object->setForcePublish(true);
            }
        }

        publishAllData = false;

        //
        //  Process the entire object map.
        //
        uint32_t v2Objs = 0;

        for (ObjectMap::iterator baseIter = managementObjects.begin();
             baseIter != managementObjects.end();
             baseIter++) {
            ManagementObject* baseObject = baseIter->second.get();

            //
            //  Skip until we find a base object requiring a sent message.
            //
            if (baseObject->getFlags() == 1 ||
                (!baseObject->getConfigChanged() &&
                 !baseObject->getInstChanged() &&
                 !baseObject->getForcePublish() &&
                 !baseObject->isDeleted()))
                continue;

            std::string packageName = baseObject->getPackageName();
            std::string className = baseObject->getClassName();

            Variant::List list_;
            std::stringstream addr_key;
            Variant::Map  headers;

            addr_key << addr_key_base;
            addr_key << keyifyNameStr(packageName)
                     << "." << keyifyNameStr(className)
                     << "." << vendorNameKey
                     << "." << productNameKey
                     << "." << instanceNameKey;

            headers["method"] = "indication";
            headers["qmf.opcode"] = "_data_indication";
            headers["qmf.content"] = "_data";
            headers["qmf.agent"] = name_address;

            for (ObjectMap::iterator iter = baseIter;
                 iter != managementObjects.end();
                 iter++) {
                ManagementObject* object = iter->second.get();
                bool send_stats, send_props;
                if (baseObject->isSameClass(*object) && object->getFlags() == 0) {
                    object->setFlags(1);
                    if (object->getConfigChanged() || object->getInstChanged())
                        object->setUpdateTime();

                    send_props = (object->getConfigChanged() || object->getForcePublish() || object->isDeleted());
                    send_stats = (object->hasInst() && (object->getInstChanged() || object->getForcePublish()));

                    if (send_stats || send_props) {
                        Variant::Map map_;
                        Variant::Map values;
                        Variant::Map oid;

                        object->getObjectId().mapEncode(oid);
                        map_["_object_id"] = oid;
                        map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                               object->getClassName(),
                                                               object->getMd5Sum());
                        object->writeTimestamps(map_);
                        object->mapEncodeValues(values, send_props, send_stats);
                        map_["_values"] = values;
                        list_.push_back(map_);

                        if (++v2Objs >= maxV2ReplyObjs) {
                            v2Objs = 0;
                            boost::shared_ptr<MessageItem> item(new MessageItem(headers, addr_key.str()));
                            ListCodec::encode(list_, item->content);
                            message_list.push_back(item);
                            list_.clear();
                        }
                    }

                    if (object->isDeleted())
                        deleteList.push_back(iter->first);
                    object->setForcePublish(false);
                }
            }

            if (!list_.empty()) {
                boost::shared_ptr<MessageItem> item(new MessageItem(headers, addr_key.str()));
                ListCodec::encode(list_, item->content);
                message_list.push_back(item);
            }
        }

        // Delete flagged objects
        for (list<ObjectId>::reverse_iterator iter = deleteList.rbegin();
             iter != deleteList.rend();
             iter++)
            managementObjects.erase(*iter);
    }

    while (!message_list.empty()) {
        boost::shared_ptr<MessageItem> item(message_list.front());
        message_list.pop_front();
        connThreadBody.sendBuffer(item->content, "", item->headers, topicExchange, item->key, "amqp/list");
        QPID_LOG(trace, "SENT DataIndication");
    }
}


void ManagementAgentImpl::getHeartbeatContent(qpid::types::Variant::Map& map)
{
    map["_values"] = attrMap;
    map["_values"].asMap()["_timestamp"] = uint64_t(Duration(EPOCH, now()));
    map["_values"].asMap()["_heartbeat_interval"] = interval;
    map["_values"].asMap()["_epoch"] = bootSequence;
    map["_values"].asMap()["_schema_updated"] = uint64_t(schemaTimestamp);
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
                session.exchangeBind(arg::exchange=agent.directExchange, arg::queue=queueName.str(),
                                     arg::bindingKey=agent.name_address);
                session.exchangeBind(arg::exchange=agent.topicExchange, arg::queue=queueName.str(),
                                     arg::bindingKey="console.#");

                subscriptions->subscribe(agent, queueName.str(), dest);
                QPID_LOG(info, "Connection established with broker");
                {
                    sys::Mutex::ScopedLock _lock(connLock);
                    if (shutdown)
                        return;
                    operational = true;
                    agent.connected = true;
                    agent.startProtocol();
                    try {
                        sys::Mutex::ScopedUnlock _unlock(connLock);
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
             sys::Mutex::ScopedLock _lock(connLock);
             if (shutdown)
                 return;
             sleeping = true;
             int totalSleep = 0;
             do {
                 sys::Mutex::ScopedUnlock _unlock(connLock);
                 qpid::sys::sleep(delayMin);
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
                                                       const Variant::Map headers,
                                                       const string& exchange,
                                                       const string& routingKey,
                                                       const string& contentType,
                                                       uint64_t ttl_msec)
{
    Message msg;
    Variant::Map::const_iterator i;

    if (!cid.empty())
        msg.getMessageProperties().setCorrelationId(cid);

    if (!contentType.empty())
        msg.getMessageProperties().setContentType(contentType);

    if (ttl_msec)
        msg.getDeliveryProperties().setTtl(ttl_msec);

    for (i = headers.begin(); i != headers.end(); ++i) {
        msg.getHeaders().setString(i->first, i->second.asString());
    }

    msg.setData(data);
    sendMessage(msg, exchange, routingKey);
}





void ManagementAgentImpl::ConnectionThread::sendMessage(Message msg,
                                                        const string& exchange,
                                                        const string& routingKey)
{
    ConnectionThread::shared_ptr s;
    {
        sys::Mutex::ScopedLock _lock(connLock);
        if (!operational)
            return;
        s = subscriptions;
    }

    msg.getDeliveryProperties().setRoutingKey(routingKey);
    msg.getMessageProperties().setReplyTo(ReplyTo("amq.direct", queueName.str()));
    msg.getMessageProperties().getApplicationHeaders().setString("qmf.agent", agent.name_address);
    msg.getMessageProperties().setAppId("qmf2");
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
        sys::Mutex::ScopedLock _lock(connLock);
        shutdown = true;
        s = subscriptions;
    }
    if (s)
        s->stop();
}

bool ManagementAgentImpl::ConnectionThread::isSleeping() const
{
    sys::Mutex::ScopedLock _lock(connLock);
    return sleeping;
}


void ManagementAgentImpl::PublishThread::run()
{
    uint16_t totalSleep;
    uint16_t sleepTime;

    while (!shutdown) {
        agent.periodicProcessing();
        totalSleep = 0;

        //
        // Calculate a sleep time that is no greater than 5 seconds and
        // no less than 1 second.
        //
        sleepTime = agent.getInterval();
        if (sleepTime > 5)
            sleepTime = 5;
        else if (sleepTime == 0)
            sleepTime = 1;

        while (totalSleep < agent.getInterval() && !shutdown) {
            qpid::sys::sleep(sleepTime);
            totalSleep += sleepTime;
        }
    }
}

}}

