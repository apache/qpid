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
 
#include "ManagementBroker.h"
#include "IdAllocator.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/log/Statement.h"
#include <qpid/broker/Message.h>
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/sys/Time.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/broker/AclModule.h"
#include <list>
#include <iostream>
#include <fstream>

using boost::intrusive_ptr;
using qpid::framing::Uuid;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace std;
namespace _qmf = qmf::org::apache::qpid::broker;

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
        agent = new ManagementBroker();
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

ManagementBroker::RemoteAgent::~RemoteAgent ()
{
    if (mgmtObject != 0)
        mgmtObject->resourceDestroy();
}

ManagementBroker::ManagementBroker () :
    threadPoolSize(1), interval(10), broker(0), startTime(uint64_t(Duration(now())))
{
    nextObjectId   = 1;
    brokerBank     = 1;
    bootSequence   = 1;
    nextRemoteBank = 10;
    nextRequestSequence = 1;
    clientWasAdded = false;
}

ManagementBroker::~ManagementBroker ()
{
    timer.stop();
    {
        Mutex::ScopedLock lock (userLock);

        // Reset the shared pointers to exchanges.  If this is not done now, the exchanges
        // will stick around until dExchange and mExchange are implicitely destroyed (long
        // after this destructor completes).  Those exchanges hold references to management
        // objects that will be invalid.
        dExchange.reset();
        mExchange.reset();

        moveNewObjectsLH();
        for (ManagementObjectMap::iterator iter = managementObjects.begin ();
             iter != managementObjects.end ();
             iter++) {
            ManagementObject* object = iter->second;
            delete object;
        }
        managementObjects.clear();
    }
}

void ManagementBroker::configure(const string& _dataDir, uint16_t _interval,
                                 qpid::broker::Broker* _broker, int _threads)
{
    dataDir        = _dataDir;
    interval       = _interval;
    broker         = _broker;
    threadPoolSize = _threads;
    timer.add (intrusive_ptr<TimerTask> (new Periodic(*this, interval)));

    // Get from file or generate and save to file.
    if (dataDir.empty())
    {
        uuid.generate();
        QPID_LOG (info, "ManagementBroker has no data directory, generated new broker ID: "
                  << uuid);
    }
    else
    {
        string   filename(dataDir + "/.mbrokerdata");
        ifstream inFile(filename.c_str ());

        if (inFile.good())
        {
            inFile >> uuid;
            inFile >> bootSequence;
            inFile >> nextRemoteBank;
            inFile.close();
            QPID_LOG (debug, "ManagementBroker restored broker ID: " << uuid);

            // if sequence goes beyond a 12-bit field, skip zero and wrap to 1.
            bootSequence++;
            if (bootSequence & 0xF000)
                bootSequence = 1;
            writeData();
        }
        else
        {
            uuid.generate();
            QPID_LOG (info, "ManagementBroker generated broker ID: " << uuid);
            writeData();
        }

        QPID_LOG (debug, "ManagementBroker boot sequence: " << bootSequence);
    }
}

void ManagementBroker::writeData ()
{
    string   filename (dataDir + "/.mbrokerdata");
    ofstream outFile (filename.c_str ());

    if (outFile.good())
    {
        outFile << uuid << " " << bootSequence << " " << nextRemoteBank << endl;
        outFile.close();
    }
}

void ManagementBroker::setExchange (qpid::broker::Exchange::shared_ptr _mexchange,
                                    qpid::broker::Exchange::shared_ptr _dexchange)
{
    mExchange = _mexchange;
    dExchange = _dexchange;
}

void ManagementBroker::registerClass (const string&  packageName,
                                      const string&  className,
                                      uint8_t* md5Sum,
                                      ManagementObject::writeSchemaCall_t schemaCall)
{
    Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    addClassLH(ManagementItem::CLASS_KIND_TABLE, pIter, className, md5Sum, schemaCall);
}

void ManagementBroker::registerEvent (const string&  packageName,
                                      const string&  eventName,
                                      uint8_t* md5Sum,
                                      ManagementObject::writeSchemaCall_t schemaCall)
{
    Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    addClassLH(ManagementItem::CLASS_KIND_EVENT, pIter, eventName, md5Sum, schemaCall);
}

ObjectId ManagementBroker::addObject (ManagementObject* object,
                                      uint64_t          persistId)
{
    Mutex::ScopedLock lock (addLock);
    uint16_t sequence;
    uint64_t objectNum;

    if (persistId == 0) {
        sequence  = bootSequence;
        objectNum = nextObjectId++;
    } else {
        sequence  = 0;
        objectNum = persistId;
    }

    ObjectId objId(0 /*flags*/ , sequence, brokerBank, 0, objectNum);

    object->setObjectId(objId);
    newManagementObjects[objId] = object;
    return objId;
}

void ManagementBroker::raiseEvent(const ManagementEvent& event, severity_t severity)
{
    Mutex::ScopedLock lock (userLock);
    Buffer outBuffer(eventBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;
    uint8_t sev = (severity == SEV_DEFAULT) ? event.getSeverity() : (uint8_t) severity;

    encodeHeader(outBuffer, 'e');
    outBuffer.putShortString(event.getPackageName());
    outBuffer.putShortString(event.getEventName());
    outBuffer.putBin128(event.getMd5Sum());
    outBuffer.putLongLong(uint64_t(Duration(now())));
    outBuffer.putOctet(sev);
    event.encode(outBuffer);
    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    sendBuffer(outBuffer, outLen, mExchange,
               "console.event.1.0." + event.getPackageName() + "." + event.getEventName());
}

ManagementBroker::Periodic::Periodic (ManagementBroker& _broker, uint32_t _seconds)
    : TimerTask (qpid::sys::Duration ((_seconds ? _seconds : 1) * qpid::sys::TIME_SEC)), broker(_broker) {}

ManagementBroker::Periodic::~Periodic () {}

void ManagementBroker::Periodic::fire ()
{
    broker.timer.add (intrusive_ptr<TimerTask> (new Periodic (broker, broker.interval)));
    broker.periodicProcessing ();
}

void ManagementBroker::clientAdded (const std::string& routingKey)
{
    if (routingKey.find("console") != 0)
        return;

    clientWasAdded = true;
    for (RemoteAgentMap::iterator aIter = remoteAgents.begin();
         aIter != remoteAgents.end();
         aIter++) {
        char     localBuffer[16];
        Buffer   outBuffer(localBuffer, 16);
        uint32_t outLen;

        encodeHeader(outBuffer, 'x');
        outLen = outBuffer.getPosition();
        outBuffer.reset();
        sendBuffer(outBuffer, outLen, dExchange, aIter->second->routingKey);
    }
}

void ManagementBroker::encodeHeader (Buffer& buf, uint8_t opcode, uint32_t seq)
{
    buf.putOctet ('A');
    buf.putOctet ('M');
    buf.putOctet ('2');
    buf.putOctet (opcode);
    buf.putLong  (seq);
}

bool ManagementBroker::checkHeader (Buffer& buf, uint8_t *opcode, uint32_t *seq)
{
    uint8_t h1 = buf.getOctet();
    uint8_t h2 = buf.getOctet();
    uint8_t h3 = buf.getOctet();

    *opcode = buf.getOctet();
    *seq    = buf.getLong();

    return h1 == 'A' && h2 == 'M' && h3 == '2';
}

void ManagementBroker::sendBuffer(Buffer&  buf,
                                  uint32_t length,
                                  qpid::broker::Exchange::shared_ptr exchange,
                                  string   routingKey)
{
    if (exchange.get() == 0)
        return;

    intrusive_ptr<Message> msg(new Message());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), exchange->getName (), 0, 0)));
    AMQFrame header((AMQHeaderBody()));
    AMQFrame content((AMQContentBody()));

    content.castBody<AMQContentBody>()->decode(buf, length);

    method.setEof(false);
    header.setBof(false);
    header.setEof(false);
    content.setBof(false);

    msg->getFrames().append(method);
    msg->getFrames().append(header);

    MessageProperties* props =
        msg->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setContentLength(length);
    msg->getFrames().append(content);

    DeliverableMessage deliverable (msg);
    try {
        exchange->route(deliverable, routingKey, 0);
    } catch(exception&) {}
}

void ManagementBroker::moveNewObjectsLH()
{
    Mutex::ScopedLock lock (addLock);
    for (ManagementObjectMap::iterator iter = newManagementObjects.begin ();
         iter != newManagementObjects.end ();
         iter++)
        managementObjects[iter->first] = iter->second;
    newManagementObjects.clear();
}

void ManagementBroker::periodicProcessing (void)
{
#define BUFSIZE   65536
    Mutex::ScopedLock lock (userLock);
    char                msgChars[BUFSIZE];
    uint32_t            contentSize;
    string              routingKey;
    list<pair<ObjectId, ManagementObject*> > deleteList;

    uint64_t uptime = uint64_t(Duration(now())) - startTime;
    static_cast<_qmf::Broker*>(broker->GetManagementObject())->set_uptime(uptime);

    moveNewObjectsLH();

    if (clientWasAdded) {
        clientWasAdded = false;
        for (ManagementObjectMap::iterator iter = managementObjects.begin ();
             iter != managementObjects.end ();
             iter++) {
            ManagementObject* object = iter->second;
            object->setForcePublish(true);
        }
    }

    for (ManagementObjectMap::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++) {
        ManagementObject* object = iter->second;

        if (object->getConfigChanged() || object->getInstChanged())
            object->setUpdateTime();

        if (object->getConfigChanged() || object->getForcePublish() || object->isDeleted()) {
            Buffer msgBuffer (msgChars, BUFSIZE);
            encodeHeader (msgBuffer, 'c');
            object->writeProperties(msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "console.obj.1.0." + object->getPackageName() + "." + object->getClassName();
            sendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }
        
        if (object->hasInst() && (object->getInstChanged() || object->getForcePublish())) {
            Buffer msgBuffer (msgChars, BUFSIZE);
            encodeHeader (msgBuffer, 'i');
            object->writeStatistics(msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "console.obj.1.0." + object->getPackageName() + "." + object->getClassName();
            sendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }

        if (object->isDeleted())
            deleteList.push_back(pair<ObjectId, ManagementObject*>(iter->first, object));
        object->setForcePublish(false);
    }

    // Delete flagged objects
    for (list<pair<ObjectId, ManagementObject*> >::reverse_iterator iter = deleteList.rbegin();
         iter != deleteList.rend();
         iter++) {
        delete iter->second;
        managementObjects.erase(iter->first);
    }

    if (!deleteList.empty()) {
        deleteList.clear();
        deleteOrphanedAgentsLH();
    }

    {
        Buffer msgBuffer(msgChars, BUFSIZE);
        encodeHeader(msgBuffer, 'h');
        msgBuffer.putLongLong(uint64_t(Duration(now())));

        contentSize = BUFSIZE - msgBuffer.available ();
        msgBuffer.reset ();
        routingKey = "console.heartbeat.1.0";
        sendBuffer (msgBuffer, contentSize, mExchange, routingKey);
    }
}

void ManagementBroker::sendCommandComplete (string replyToKey, uint32_t sequence,
                                            uint32_t code, string text)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader (outBuffer, 'z', sequence);
    outBuffer.putLong (code);
    outBuffer.putShortString (text);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBuffer (outBuffer, outLen, dExchange, replyToKey);
}

bool ManagementBroker::dispatchCommand (Deliverable&      deliverable,
                                        const string&     routingKey,
                                        const FieldTable* /*args*/)
{
    Mutex::ScopedLock lock (userLock);
    Message&  msg = ((DeliverableMessage&) deliverable).getMessage ();

    // Parse the routing key.  This management broker should act as though it
    // is bound to the exchange to match the following keys:
    //
    //    agent.1.0.#
    //    broker
    //    schema.#

    if (routingKey == "broker") {
        dispatchAgentCommandLH(msg);
        return false;
    }

    else if (routingKey.compare(0, 9, "agent.1.0") == 0) {
        dispatchAgentCommandLH(msg);
        return false;
    }

    else if (routingKey.compare(0, 8, "agent.1.") == 0) {
        return authorizeAgentMessageLH(msg);
    }

    else if (routingKey.compare(0, 7, "schema.") == 0) {
        dispatchAgentCommandLH(msg);
        return true;
    }

    return true;
}

void ManagementBroker::handleMethodRequestLH (Buffer& inBuffer, string replyToKey,
                                              uint32_t sequence, const ConnectionToken* connToken)
{
    string   methodName;
    string   packageName;
    string   className;
    uint8_t  hash[16];
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;
    AclModule* acl = broker->getAcl();

    ObjectId objId(inBuffer);
    inBuffer.getShortString(packageName);
    inBuffer.getShortString(className);
    inBuffer.getBin128(hash);
    inBuffer.getShortString(methodName);
    encodeHeader(outBuffer, 'm', sequence);

    if (acl != 0) {
        string userId = ((const qpid::broker::ConnectionState*) connToken)->getUserId();
        map<acl::Property, string> params;
        params[acl::PROP_SCHEMAPACKAGE] = packageName;
        params[acl::PROP_SCHEMACLASS]   = className;

        if (!acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_METHOD, methodName, &params)) {
            outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
            outBuffer.putMediumString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            sendBuffer(outBuffer, outLen, dExchange, replyToKey);
            return;
        }
    }

    ManagementObjectMap::iterator iter = managementObjects.find(objId);
    if (iter == managementObjects.end() || iter->second->isDeleted()) {
        outBuffer.putLong        (Manageable::STATUS_UNKNOWN_OBJECT);
        outBuffer.putMediumString(Manageable::StatusText (Manageable::STATUS_UNKNOWN_OBJECT));
    } else {
        if ((iter->second->getPackageName() != packageName) ||
            (iter->second->getClassName()   != className)) {
            outBuffer.putLong        (Manageable::STATUS_INVALID_PARAMETER);
            outBuffer.putMediumString(Manageable::StatusText (Manageable::STATUS_INVALID_PARAMETER));
        }
        else
            try {
                outBuffer.record();
                iter->second->doMethod(methodName, inBuffer, outBuffer);
            } catch(exception& e) {
                outBuffer.restore();
                outBuffer.putLong(Manageable::STATUS_EXCEPTION);
                outBuffer.putMediumString(e.what());
            }
    }

    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    sendBuffer(outBuffer, outLen, dExchange, replyToKey);
}

void ManagementBroker::handleBrokerRequestLH (Buffer&, string replyToKey, uint32_t sequence)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader (outBuffer, 'b', sequence);
    uuid.encode  (outBuffer);

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBuffer (outBuffer, outLen, dExchange, replyToKey);
}

void ManagementBroker::handlePackageQueryLH (Buffer&, string replyToKey, uint32_t sequence)
{
    for (PackageMap::iterator pIter = packages.begin ();
         pIter != packages.end ();
         pIter++)
    {
        Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        encodeHeader (outBuffer, 'p', sequence);
        encodePackageIndication (outBuffer, pIter);
        outLen = MA_BUFFER_SIZE - outBuffer.available ();
        outBuffer.reset ();
        sendBuffer (outBuffer, outLen, dExchange, replyToKey);
    }

    sendCommandComplete (replyToKey, sequence);
}

void ManagementBroker::handlePackageIndLH (Buffer& inBuffer, string /*replyToKey*/, uint32_t /*sequence*/)
{
    string packageName;

    inBuffer.getShortString(packageName);
    findOrAddPackageLH(packageName);
}

void ManagementBroker::handleClassQueryLH(Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string packageName;

    inBuffer.getShortString(packageName);
    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end())
    {
        ClassMap cMap = pIter->second;
        for (ClassMap::iterator cIter = cMap.begin();
             cIter != cMap.end();
             cIter++)
        {
            if (cIter->second.hasSchema())
            {
                Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
                uint32_t outLen;

                encodeHeader(outBuffer, 'q', sequence);
                encodeClassIndication(outBuffer, pIter, cIter);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                sendBuffer(outBuffer, outLen, dExchange, replyToKey);
            }
        }
    }
    sendCommandComplete(replyToKey, sequence);
}

void ManagementBroker::handleClassIndLH (Buffer& inBuffer, string replyToKey, uint32_t)
{
    string packageName;
    SchemaClassKey key;

    uint8_t kind = inBuffer.getOctet();
    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);

    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    ClassMap::iterator   cIter = pIter->second.find(key);
    if (cIter == pIter->second.end() || !cIter->second.hasSchema()) {
        Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;
        uint32_t sequence = nextRequestSequence++;

        encodeHeader (outBuffer, 'S', sequence);
        outBuffer.putShortString(packageName);
        outBuffer.putShortString(key.name);
        outBuffer.putBin128(key.hash);
        outLen = MA_BUFFER_SIZE - outBuffer.available ();
        outBuffer.reset ();
        sendBuffer (outBuffer, outLen, dExchange, replyToKey);

        if (cIter != pIter->second.end())
            pIter->second.erase(key);

        pIter->second.insert(pair<SchemaClassKey, SchemaClass>(key, SchemaClass(kind, sequence)));
    }
}

void ManagementBroker::SchemaClass::appendSchema(Buffer& buf)
{
    // If the management package is attached locally (embedded in the broker or
    // linked in via plug-in), call the schema handler directly.  If the package
    // is from a remote management agent, send the stored schema information.

    if (writeSchemaCall != 0)
        writeSchemaCall(buf);
    else
        buf.putRawData(buffer, bufferLen);
}

void ManagementBroker::handleSchemaRequestLH(Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string         packageName;
    SchemaClassKey key;

    inBuffer.getShortString (packageName);
    inBuffer.getShortString (key.name);
    inBuffer.getBin128      (key.hash);

    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end()) {
        ClassMap& cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find(key);
        if (cIter != cMap.end()) {
            Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;
            SchemaClass& classInfo = cIter->second;

            if (classInfo.hasSchema()) {
                encodeHeader(outBuffer, 's', sequence);
                classInfo.appendSchema(outBuffer);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                sendBuffer(outBuffer, outLen, dExchange, replyToKey);
            }
            else
                sendCommandComplete(replyToKey, sequence, 1, "Schema not available");
        }
        else
            sendCommandComplete(replyToKey, sequence, 1, "Class key not found");
    }
    else
        sendCommandComplete(replyToKey, sequence, 1, "Package not found");
}

void ManagementBroker::handleSchemaResponseLH(Buffer& inBuffer, string /*replyToKey*/, uint32_t sequence)
{
    string         packageName;
    SchemaClassKey key;

    inBuffer.record();
    inBuffer.getOctet();
    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);
    inBuffer.restore();

    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end()) {
        ClassMap& cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find(key);
        if (cIter != cMap.end() && cIter->second.pendingSequence == sequence) {
            size_t length = validateSchema(inBuffer, cIter->second.kind);
            if (length == 0) {
                QPID_LOG(warning, "Management Broker received invalid schema response: " << packageName << "." << key.name);
                cMap.erase(key);
            } else {
                cIter->second.buffer    = (uint8_t*) malloc(length);
                cIter->second.bufferLen = length;
                inBuffer.getRawData(cIter->second.buffer, cIter->second.bufferLen);

                // Publish a class-indication message
                Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
                uint32_t outLen;

                encodeHeader(outBuffer, 'q');
                encodeClassIndication(outBuffer, pIter, cIter);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                sendBuffer(outBuffer, outLen, mExchange, "schema.class");
            }
        }
    }
}

bool ManagementBroker::bankInUse (uint32_t bank)
{
    for (RemoteAgentMap::iterator aIter = remoteAgents.begin();
         aIter != remoteAgents.end();
         aIter++)
        if (aIter->second->agentBank == bank)
            return true;
    return false;
}

uint32_t ManagementBroker::allocateNewBank ()
{
    while (bankInUse (nextRemoteBank))
        nextRemoteBank++;

    uint32_t allocated = nextRemoteBank++;
    writeData ();
    return allocated;
}

uint32_t ManagementBroker::assignBankLH (uint32_t requestedBank)
{
    if (requestedBank == 0 || bankInUse (requestedBank))
        return allocateNewBank ();
    return requestedBank;
}

void ManagementBroker::deleteOrphanedAgentsLH()
{
    vector<ObjectId> deleteList;

    for (RemoteAgentMap::iterator aIter = remoteAgents.begin(); aIter != remoteAgents.end(); aIter++) {
        ObjectId connectionRef = aIter->first;
        bool found = false;

        for (ManagementObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++) {
            if (iter->first == connectionRef && !iter->second->isDeleted()) {
                found = true;
                break;
            }
        }

        if (!found) {
            deleteList.push_back(connectionRef);
            delete aIter->second;
        }
    }

    for (vector<ObjectId>::iterator dIter = deleteList.begin(); dIter != deleteList.end(); dIter++)
        remoteAgents.erase(*dIter);

    deleteList.clear();
}

void ManagementBroker::handleAttachRequestLH (Buffer& inBuffer, string replyToKey, uint32_t sequence, const ConnectionToken* connToken)
{
    string   label;
    uint32_t requestedBrokerBank, requestedAgentBank;
    uint32_t assignedBank;
    ObjectId connectionRef = ((const ConnectionState*) connToken)->GetManagementObject()->getObjectId();
    Uuid     systemId;

    moveNewObjectsLH();
    deleteOrphanedAgentsLH();
    RemoteAgentMap::iterator aIter = remoteAgents.find(connectionRef);
    if (aIter != remoteAgents.end()) {
        // There already exists an agent on this session.  Reject the request.
        sendCommandComplete(replyToKey, sequence, 1, "Connection already has remote agent");
        return;
    }

    inBuffer.getShortString(label);
    systemId.decode(inBuffer);
    requestedBrokerBank = inBuffer.getLong();
    requestedAgentBank  = inBuffer.getLong();
    assignedBank = assignBankLH(requestedAgentBank);

    RemoteAgent* agent = new RemoteAgent;
    agent->brokerBank = brokerBank;
    agent->agentBank  = assignedBank;
    agent->routingKey = replyToKey;
    agent->connectionRef = connectionRef;
    agent->mgmtObject = new _qmf::Agent (this, agent);
    agent->mgmtObject->set_connectionRef(agent->connectionRef);
    agent->mgmtObject->set_label        (label);
    agent->mgmtObject->set_registeredTo (broker->GetManagementObject()->getObjectId());
    agent->mgmtObject->set_systemId     (systemId);
    agent->mgmtObject->set_brokerBank   (brokerBank);
    agent->mgmtObject->set_agentBank    (assignedBank);
    addObject (agent->mgmtObject);

    remoteAgents[connectionRef] = agent;

    // Send an Attach Response
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader (outBuffer, 'a', sequence);
    outBuffer.putLong (brokerBank);
    outBuffer.putLong (assignedBank);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBuffer (outBuffer, outLen, dExchange, replyToKey);
}

void ManagementBroker::handleGetQueryLH (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    FieldTable           ft;
    FieldTable::ValuePtr value;

    moveNewObjectsLH();

    ft.decode(inBuffer);
    value = ft.get("_class");
    if (value.get() == 0 || !value->convertsTo<string>()) {
        value = ft.get("_objectid");
        if (value.get() == 0 || !value->convertsTo<string>())
            return;

        ObjectId selector(value->get<string>());
        ManagementObjectMap::iterator iter = managementObjects.find(selector);
        if (iter != managementObjects.end()) {
            ManagementObject* object = iter->second;
            Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            encodeHeader(outBuffer, 'g', sequence);
            object->writeProperties(outBuffer);
            object->writeStatistics(outBuffer, true);
            outLen = MA_BUFFER_SIZE - outBuffer.available ();
            outBuffer.reset ();
            sendBuffer(outBuffer, outLen, dExchange, replyToKey);
        }
        sendCommandComplete(replyToKey, sequence);
        return;
    }

    string className (value->get<string>());

    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         iter++) {
        ManagementObject* object = iter->second;
        if (object->getClassName () == className) {
            Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            encodeHeader(outBuffer, 'g', sequence);
            object->writeProperties(outBuffer);
            object->writeStatistics(outBuffer, true);
            outLen = MA_BUFFER_SIZE - outBuffer.available ();
            outBuffer.reset ();
            sendBuffer(outBuffer, outLen, dExchange, replyToKey);
        }
    }

    sendCommandComplete(replyToKey, sequence);
}

bool ManagementBroker::authorizeAgentMessageLH(Message& msg)
{
    Buffer   inBuffer (inputBuffer, MA_BUFFER_SIZE);
    uint8_t  opcode;
    uint32_t sequence;
    string   replyToKey;

    if (msg.encodedSize() > MA_BUFFER_SIZE)
        return false;

    msg.encodeContent(inBuffer);
    inBuffer.reset();

    if (!checkHeader(inBuffer, &opcode, &sequence))
        return false;

    if (opcode == 'M') {
        // TODO: check method call against ACL list.
        AclModule* acl = broker->getAcl();
        if (acl == 0)
            return true;

        string  userId = ((const qpid::broker::ConnectionState*) msg.getPublisher())->getUserId();
        string  packageName;
        string  className;
        uint8_t hash[16];
        string  methodName;

        map<acl::Property, string> params;
        ObjectId objId(inBuffer);
        inBuffer.getShortString(packageName);
        inBuffer.getShortString(className);
        inBuffer.getBin128(hash);
        inBuffer.getShortString(methodName);

        params[acl::PROP_SCHEMAPACKAGE] = packageName;
        params[acl::PROP_SCHEMACLASS]   = className;

        if (acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_METHOD, methodName, &params))
            return true;

        const framing::MessageProperties* p =
            msg.getFrames().getHeaders()->get<framing::MessageProperties>();
        if (p && p->hasReplyTo()) {
            const framing::ReplyTo& rt = p->getReplyTo();
            replyToKey = rt.getRoutingKey();

            Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            encodeHeader(outBuffer, 'm', sequence);
            outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
            outBuffer.putMediumString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            sendBuffer(outBuffer, outLen, dExchange, replyToKey);
        }

        return false;
    }

    return true;
}

void ManagementBroker::dispatchAgentCommandLH(Message& msg)
{
    Buffer   inBuffer(inputBuffer, MA_BUFFER_SIZE);
    uint8_t  opcode;
    uint32_t sequence;
    string   replyToKey;

    const framing::MessageProperties* p =
        msg.getFrames().getHeaders()->get<framing::MessageProperties>();
    if (p && p->hasReplyTo()) {
        const framing::ReplyTo& rt = p->getReplyTo();
        replyToKey = rt.getRoutingKey();
    }
    else
        return;

    if (msg.encodedSize() > MA_BUFFER_SIZE) {
        QPID_LOG(debug, "ManagementBroker::dispatchAgentCommandLH: Message too large: " <<
                 msg.encodedSize());
        return;
    }

    msg.encodeContent(inBuffer);
    uint32_t bufferLen = inBuffer.getPosition();
    inBuffer.reset();

    while (inBuffer.getPosition() < bufferLen) {
        if (!checkHeader(inBuffer, &opcode, &sequence))
            return;

        if      (opcode == 'B') handleBrokerRequestLH  (inBuffer, replyToKey, sequence);
        else if (opcode == 'P') handlePackageQueryLH   (inBuffer, replyToKey, sequence);
        else if (opcode == 'p') handlePackageIndLH     (inBuffer, replyToKey, sequence);
        else if (opcode == 'Q') handleClassQueryLH     (inBuffer, replyToKey, sequence);
        else if (opcode == 'q') handleClassIndLH       (inBuffer, replyToKey, sequence);
        else if (opcode == 'S') handleSchemaRequestLH  (inBuffer, replyToKey, sequence);
        else if (opcode == 's') handleSchemaResponseLH (inBuffer, replyToKey, sequence);
        else if (opcode == 'A') handleAttachRequestLH  (inBuffer, replyToKey, sequence, msg.getPublisher());
        else if (opcode == 'G') handleGetQueryLH       (inBuffer, replyToKey, sequence);
        else if (opcode == 'M') handleMethodRequestLH  (inBuffer, replyToKey, sequence, msg.getPublisher());
    }
}

ManagementBroker::PackageMap::iterator ManagementBroker::findOrAddPackageLH(string name)
{
    PackageMap::iterator pIter = packages.find (name);
    if (pIter != packages.end ())
        return pIter;

    // No such package found, create a new map entry.
    pair<PackageMap::iterator, bool> result =
        packages.insert(pair<string, ClassMap>(name, ClassMap()));
    QPID_LOG (debug, "ManagementBroker added package " << name);

    // Publish a package-indication message
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader (outBuffer, 'p');
    encodePackageIndication (outBuffer, result.first);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBuffer (outBuffer, outLen, mExchange, "schema.package");

    return result.first;
}

void ManagementBroker::addClassLH(uint8_t               kind,
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
    QPID_LOG (debug, "ManagementBroker added class " << pIter->first << ":" <<
              key.name);

    cMap.insert(pair<SchemaClassKey, SchemaClass>(key, SchemaClass(kind, schemaCall)));
    cIter = cMap.find(key);
}

void ManagementBroker::encodePackageIndication(Buffer&              buf,
                                               PackageMap::iterator pIter)
{
    buf.putShortString((*pIter).first);
}

void ManagementBroker::encodeClassIndication(Buffer&              buf,
                                             PackageMap::iterator pIter,
                                             ClassMap::iterator   cIter)
{
    SchemaClassKey key = (*cIter).first;

    buf.putOctet((*cIter).second.kind);
    buf.putShortString((*pIter).first);
    buf.putShortString(key.name);
    buf.putBin128(key.hash);
}

size_t ManagementBroker::validateSchema(Buffer& inBuffer, uint8_t kind)
{
    if      (kind == ManagementItem::CLASS_KIND_TABLE)
        return validateTableSchema(inBuffer);
    else if (kind == ManagementItem::CLASS_KIND_EVENT)
        return validateEventSchema(inBuffer);
    return 0;
}

size_t ManagementBroker::validateTableSchema(Buffer& inBuffer)
{
    uint32_t start = inBuffer.getPosition();
    uint32_t end;
    string   text;
    uint8_t  hash[16];

    try {
        inBuffer.record();
        uint8_t kind = inBuffer.getOctet();
        if (kind != ManagementItem::CLASS_KIND_TABLE)
            return 0;

        inBuffer.getShortString(text);
        inBuffer.getShortString(text);
        inBuffer.getBin128(hash);

        uint16_t propCount = inBuffer.getShort();
        uint16_t statCount = inBuffer.getShort();
        uint16_t methCount = inBuffer.getShort();

        for (uint16_t idx = 0; idx < propCount + statCount; idx++) {
            FieldTable ft;
            ft.decode(inBuffer);
        }

        for (uint16_t idx = 0; idx < methCount; idx++) {
            FieldTable ft;
            ft.decode(inBuffer);
            if (!ft.isSet("argCount"))
                return 0;
            int argCount = ft.getAsInt("argCount");
            for (int mIdx = 0; mIdx < argCount; mIdx++) {
                FieldTable aft;
                aft.decode(inBuffer);
            }
        }
    } catch (exception& /*e*/) {
        return 0;
    }

    end = inBuffer.getPosition();
    inBuffer.restore(); // restore original position
    return end - start;
}

size_t ManagementBroker::validateEventSchema(Buffer& inBuffer)
{
    uint32_t start = inBuffer.getPosition();
    uint32_t end;
    string   text;
    uint8_t  hash[16];

    try {
        inBuffer.record();
        uint8_t kind = inBuffer.getOctet();
        if (kind != ManagementItem::CLASS_KIND_EVENT)
            return 0;

        inBuffer.getShortString(text);
        inBuffer.getShortString(text);
        inBuffer.getBin128(hash);

        uint16_t argCount = inBuffer.getShort();

        for (uint16_t idx = 0; idx < argCount; idx++) {
            FieldTable ft;
            ft.decode(inBuffer);
        }
    } catch (exception& /*e*/) {
        return 0;
    }

    end = inBuffer.getPosition();
    inBuffer.restore(); // restore original position
    return end - start;
}

void ManagementBroker::setAllocator(std::auto_ptr<IdAllocator> a)
{
    Mutex::ScopedLock lock (addLock);
    allocator = a;
}

uint64_t ManagementBroker::allocateId(Manageable* object)
{
    Mutex::ScopedLock lock (addLock);
    if (allocator.get()) return allocator->getIdFor(object);
    return 0;
}
