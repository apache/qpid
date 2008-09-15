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
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/log/Statement.h"
#include <qpid/broker/Message.h>
#include <qpid/broker/MessageDelivery.h>
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
    threadPoolSize(1), interval(10), broker(0)
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

void ManagementBroker::configure(string _dataDir, uint16_t _interval,
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

void ManagementBroker::RegisterClass (string   packageName,
                                      string   className,
                                      uint8_t* md5Sum,
                                      ManagementObject::writeSchemaCall_t schemaCall)
{
    Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = FindOrAddPackageLH(packageName);
    AddClass(pIter, className, md5Sum, schemaCall);
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

ManagementBroker::Periodic::Periodic (ManagementBroker& _broker, uint32_t _seconds)
    : TimerTask (qpid::sys::Duration ((_seconds ? _seconds : 1) * qpid::sys::TIME_SEC)), broker(_broker) {}

ManagementBroker::Periodic::~Periodic () {}

void ManagementBroker::Periodic::fire ()
{
    broker.timer.add (intrusive_ptr<TimerTask> (new Periodic (broker, broker.interval)));
    broker.PeriodicProcessing ();
}

void ManagementBroker::clientAdded (void)
{
    Mutex::ScopedLock lock (userLock);

    clientWasAdded = true;
    for (RemoteAgentMap::iterator aIter = remoteAgents.begin();
         aIter != remoteAgents.end();
         aIter++) {
        Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        EncodeHeader (outBuffer, 'x');
        outLen = MA_BUFFER_SIZE - outBuffer.available ();
        outBuffer.reset ();
        SendBuffer (outBuffer, outLen, dExchange, aIter->second->routingKey);
    }
}

void ManagementBroker::EncodeHeader (Buffer& buf, uint8_t opcode, uint32_t seq)
{
    buf.putOctet ('A');
    buf.putOctet ('M');
    buf.putOctet ('1');
    buf.putOctet (opcode);
    buf.putLong  (seq);
}

bool ManagementBroker::CheckHeader (Buffer& buf, uint8_t *opcode, uint32_t *seq)
{
    uint8_t h1 = buf.getOctet ();
    uint8_t h2 = buf.getOctet ();
    uint8_t h3 = buf.getOctet ();

    *opcode = buf.getOctet ();
    *seq    = buf.getLong  ();

    return h1 == 'A' && h2 == 'M' && h3 == '1';
}

void ManagementBroker::SendBuffer (Buffer&  buf,
                                   uint32_t length,
                                   qpid::broker::Exchange::shared_ptr exchange,
                                   string   routingKey)
{
    if (exchange.get() == 0)
        return;

    intrusive_ptr<Message> msg (new Message ());
    AMQFrame method (in_place<MessageTransferBody>(
        ProtocolVersion(), exchange->getName (), 0, 0));
    AMQFrame header (in_place<AMQHeaderBody>());
    AMQFrame content(in_place<AMQContentBody>());

    content.castBody<AMQContentBody>()->decode(buf, length);

    method.setEof  (false);
    header.setBof  (false);
    header.setEof  (false);
    content.setBof (false);

    msg->getFrames().append(method);
    msg->getFrames().append(header);

    MessageProperties* props =
        msg->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setContentLength(length);
    msg->getFrames().append(content);

    DeliverableMessage deliverable (msg);
    exchange->route (deliverable, routingKey, 0);
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

void ManagementBroker::PeriodicProcessing (void)
{
#define BUFSIZE   65536
    Mutex::ScopedLock lock (userLock);
    char                msgChars[BUFSIZE];
    uint32_t            contentSize;
    string              routingKey;
    std::list<ObjectId> deleteList;

    {
        Buffer msgBuffer(msgChars, BUFSIZE);
        EncodeHeader(msgBuffer, 'h');
        msgBuffer.putLongLong(uint64_t(Duration(now())));

        contentSize = BUFSIZE - msgBuffer.available ();
        msgBuffer.reset ();
        routingKey = "mgmt." + uuid.str() + ".heartbeat";
        SendBuffer (msgBuffer, contentSize, mExchange, routingKey);
    }

    moveNewObjectsLH();

    if (clientWasAdded)
    {
        clientWasAdded = false;
        for (ManagementObjectMap::iterator iter = managementObjects.begin ();
             iter != managementObjects.end ();
             iter++)
        {
            ManagementObject* object = iter->second;
            object->setAllChanged ();
        }
    }

    if (managementObjects.empty ())
        return;
        
    for (ManagementObjectMap::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++)
    {
        ManagementObject* object = iter->second;

        if (object->getConfigChanged () || object->isDeleted ())
        {
            Buffer msgBuffer (msgChars, BUFSIZE);
            EncodeHeader (msgBuffer, 'c');
            object->writeProperties(msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt." + uuid.str() + ".prop." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }
        
        if (object->getInstChanged ())
        {
            Buffer msgBuffer (msgChars, BUFSIZE);
            EncodeHeader (msgBuffer, 'i');
            object->writeStatistics(msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt." + uuid.str () + ".stat." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }

        if (object->isDeleted ())
            deleteList.push_back (iter->first);
    }

    // Delete flagged objects
    for (std::list<ObjectId>::reverse_iterator iter = deleteList.rbegin ();
         iter != deleteList.rend ();
         iter++)
        managementObjects.erase (*iter);

    if (!deleteList.empty()) {
        deleteList.clear();
        deleteOrphanedAgentsLH();
    }
}

void ManagementBroker::sendCommandComplete (string replyToKey, uint32_t sequence,
                                            uint32_t code, string text)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader (outBuffer, 'z', sequence);
    outBuffer.putLong (code);
    outBuffer.putShortString (text);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, dExchange, replyToKey);
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
    //    agent.0.#
    //    broker

    if (routingKey == "broker") {
        dispatchAgentCommandLH(msg);
        return false;
    }

    else if (routingKey.compare(0, 7, "agent.0") == 0) {
        dispatchAgentCommandLH(msg);
        return false;
    }

    else if (routingKey.compare(0, 6, "agent.") == 0) {
        return authorizeAgentMessageLH(msg);
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
    EncodeHeader(outBuffer, 'm', sequence);

    if (acl != 0) {
        string userId = ((const qpid::broker::ConnectionState*) connToken)->getUserId();
        std::map<acl::Property, string> params;
        params[acl::SCHEMAPACKAGE] = packageName;
        params[acl::SCHEMACLASS]   = className;

        if (!acl->authorise(userId, acl::ACCESS, acl::METHOD, methodName, &params)) {
            outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
            outBuffer.putShortString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            SendBuffer(outBuffer, outLen, dExchange, replyToKey);
            return;
        }
    }

    ManagementObjectMap::iterator iter = managementObjects.find(objId);
    if (iter == managementObjects.end() || iter->second->isDeleted()) {
        outBuffer.putLong        (Manageable::STATUS_UNKNOWN_OBJECT);
        outBuffer.putShortString (Manageable::StatusText (Manageable::STATUS_UNKNOWN_OBJECT));
    } else {
        if ((iter->second->getPackageName() != packageName) ||
            (iter->second->getClassName()   != className)) {
            outBuffer.putLong        (Manageable::STATUS_INVALID_PARAMETER);
            outBuffer.putShortString (Manageable::StatusText (Manageable::STATUS_INVALID_PARAMETER));
        }
        else
            iter->second->doMethod(methodName, inBuffer, outBuffer);
    }

    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    SendBuffer(outBuffer, outLen, dExchange, replyToKey);
}

void ManagementBroker::handleBrokerRequestLH (Buffer&, string replyToKey, uint32_t sequence)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader (outBuffer, 'b', sequence);
    uuid.encode  (outBuffer);

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, dExchange, replyToKey);
}

void ManagementBroker::handlePackageQueryLH (Buffer&, string replyToKey, uint32_t sequence)
{
    for (PackageMap::iterator pIter = packages.begin ();
         pIter != packages.end ();
         pIter++)
    {
        Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        EncodeHeader (outBuffer, 'p', sequence);
        EncodePackageIndication (outBuffer, pIter);
        outLen = MA_BUFFER_SIZE - outBuffer.available ();
        outBuffer.reset ();
        SendBuffer (outBuffer, outLen, dExchange, replyToKey);
    }

    sendCommandComplete (replyToKey, sequence);
}

void ManagementBroker::handlePackageIndLH (Buffer& inBuffer, string /*replyToKey*/, uint32_t /*sequence*/)
{
    std::string packageName;

    inBuffer.getShortString(packageName);
    FindOrAddPackageLH(packageName);
}

void ManagementBroker::handleClassQueryLH(Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    std::string packageName;

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

                EncodeHeader(outBuffer, 'q', sequence);
                EncodeClassIndication(outBuffer, pIter, cIter);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                SendBuffer(outBuffer, outLen, dExchange, replyToKey);
            }
        }
    }
    sendCommandComplete(replyToKey, sequence);
}

void ManagementBroker::handleClassIndLH (Buffer& inBuffer, string replyToKey, uint32_t)
{
    std::string packageName;
    SchemaClassKey key;

    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);

    PackageMap::iterator pIter = FindOrAddPackageLH(packageName);
    ClassMap::iterator   cIter = pIter->second.find(key);
    if (cIter == pIter->second.end()) {
        Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;
        uint32_t sequence = nextRequestSequence++;

        EncodeHeader (outBuffer, 'S', sequence);
        outBuffer.putShortString(packageName);
        outBuffer.putShortString(key.name);
        outBuffer.putBin128(key.hash);
        outLen = MA_BUFFER_SIZE - outBuffer.available ();
        outBuffer.reset ();
        SendBuffer (outBuffer, outLen, dExchange, replyToKey);

        pIter->second.insert(pair<SchemaClassKey, SchemaClass>(key, SchemaClass(sequence)));
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
                EncodeHeader(outBuffer, 's', sequence);
                classInfo.appendSchema(outBuffer);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                SendBuffer(outBuffer, outLen, dExchange, replyToKey);
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
    inBuffer.getShortString (packageName);
    inBuffer.getShortString (key.name);
    inBuffer.getBin128      (key.hash);
    inBuffer.restore();

    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end()) {
        ClassMap& cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find(key);
        if (cIter != cMap.end() && cIter->second.pendingSequence == sequence) {
            size_t length = ValidateSchema(inBuffer);
            if (length == 0) {
                QPID_LOG(warning, "Management Broker received invalid schema response: " << packageName << "." << key.name);
                cMap.erase(key);
            }
            else {
                cIter->second.buffer    = (uint8_t*) malloc(length);
                cIter->second.bufferLen = length;
                inBuffer.getRawData(cIter->second.buffer, cIter->second.bufferLen);

                // Publish a class-indication message
                Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
                uint32_t outLen;

                EncodeHeader(outBuffer, 'q');
                EncodeClassIndication(outBuffer, pIter, cIter);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                SendBuffer(outBuffer, outLen, mExchange, "mgmt." + uuid.str() + ".schema");
            }
        }
    }
}

bool ManagementBroker::bankInUse (uint32_t bank)
{
    for (RemoteAgentMap::iterator aIter = remoteAgents.begin();
         aIter != remoteAgents.end();
         aIter++)
        if (aIter->second->objIdBank == bank)
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
    uint32_t requestedBank;
    uint32_t assignedBank;
    ObjectId connectionRef = ((const ConnectionState*) connToken)->GetManagementObject()->getObjectId();
    Uuid     systemId;

    moveNewObjectsLH();
    deleteOrphanedAgentsLH();
    RemoteAgentMap::iterator aIter = remoteAgents.find(connectionRef);
    if (aIter != remoteAgents.end()) {
        // There already exists an agent on this session.  Reject the request.
        sendCommandComplete (replyToKey, sequence, 1, "Connection already has remote agent");
        return;
    }

    inBuffer.getShortString (label);
    systemId.decode  (inBuffer);
    requestedBank = inBuffer.getLong ();
    assignedBank  = assignBankLH (requestedBank);

    RemoteAgent* agent = new RemoteAgent;
    agent->objIdBank  = assignedBank;
    agent->routingKey = replyToKey;
    agent->connectionRef = connectionRef;
    agent->mgmtObject = new _qmf::Agent (this, agent);
    agent->mgmtObject->set_connectionRef(agent->connectionRef);
    agent->mgmtObject->set_label        (label);
    agent->mgmtObject->set_registeredTo (broker->GetManagementObject()->getObjectId());
    agent->mgmtObject->set_systemId     (systemId);
    agent->mgmtObject->set_objectIdBank (assignedBank);
    addObject (agent->mgmtObject);

    remoteAgents[connectionRef] = agent;

    // Send an Attach Response
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader (outBuffer, 'a', sequence);
    outBuffer.putLong (brokerBank);
    outBuffer.putLong (assignedBank);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, dExchange, replyToKey);
}

void ManagementBroker::handleGetQueryLH (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    FieldTable           ft;
    FieldTable::ValuePtr value;

    moveNewObjectsLH();

    ft.decode(inBuffer);
    value = ft.get("_class");
    if (value.get() == 0 || !value->convertsTo<string>())
    {
        // TODO: Send completion with an error code
        return;
    }

    string className (value->get<string>());

    for (ManagementObjectMap::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++)
    {
        ManagementObject* object = iter->second;
        if (object->getClassName () == className)
        {
            Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            EncodeHeader (outBuffer, 'g', sequence);
            object->writeProperties(outBuffer);
            object->writeStatistics(outBuffer, true);
            outLen = MA_BUFFER_SIZE - outBuffer.available ();
            outBuffer.reset ();
            SendBuffer (outBuffer, outLen, dExchange, replyToKey);
        }
    }

    sendCommandComplete (replyToKey, sequence);
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

    if (!CheckHeader(inBuffer, &opcode, &sequence))
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

        std::map<acl::Property, string> params;
        ObjectId objId(inBuffer);
        inBuffer.getShortString(packageName);
        inBuffer.getShortString(className);
        inBuffer.getBin128(hash);
        inBuffer.getShortString(methodName);

        params[acl::SCHEMAPACKAGE] = packageName;
        params[acl::SCHEMACLASS]   = className;

        if (acl->authorise(userId, acl::ACCESS, acl::METHOD, methodName, &params))
            return true;

        const framing::MessageProperties* p =
            msg.getFrames().getHeaders()->get<framing::MessageProperties>();
        if (p && p->hasReplyTo()) {
            const framing::ReplyTo& rt = p->getReplyTo();
            replyToKey = rt.getRoutingKey();

            Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            EncodeHeader(outBuffer, 'm', sequence);
            outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
            outBuffer.putShortString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            SendBuffer(outBuffer, outLen, dExchange, replyToKey);
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
    inBuffer.reset();

    if (!CheckHeader(inBuffer, &opcode, &sequence))
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

ManagementBroker::PackageMap::iterator ManagementBroker::FindOrAddPackageLH(std::string name)
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

    EncodeHeader (outBuffer, 'p');
    EncodePackageIndication (outBuffer, result.first);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, mExchange, "mgmt." + uuid.str() + ".schema.package");

    return result.first;
}

void ManagementBroker::AddClass(PackageMap::iterator  pIter,
                                string                className,
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
    QPID_LOG (debug, "ManagementBroker added class " << pIter->first << "." <<
              key.name);

    cMap.insert(pair<SchemaClassKey, SchemaClass>(key, SchemaClass(schemaCall)));
    cIter = cMap.find(key);
}

void ManagementBroker::EncodePackageIndication (Buffer&              buf,
                                                PackageMap::iterator pIter)
{
    buf.putShortString ((*pIter).first);
}

void ManagementBroker::EncodeClassIndication (Buffer&              buf,
                                              PackageMap::iterator pIter,
                                              ClassMap::iterator   cIter)
{
    SchemaClassKey key = (*cIter).first;

    buf.putShortString ((*pIter).first);
    buf.putShortString (key.name);
    buf.putBin128      (key.hash);
}

size_t ManagementBroker::ValidateSchema(Buffer& inBuffer)
{
    uint32_t start = inBuffer.getPosition();
    uint32_t end;
    string   text;
    uint8_t  hash[16];

    inBuffer.record();
    inBuffer.getShortString(text);
    inBuffer.getShortString(text);
    inBuffer.getBin128(hash);

    uint16_t propCount = inBuffer.getShort();
    uint16_t statCount = inBuffer.getShort();
    uint16_t methCount = inBuffer.getShort();
    uint16_t evntCount = inBuffer.getShort();

    for (uint16_t idx = 0; idx < propCount + statCount; idx++) {
        FieldTable ft;
        ft.decode(inBuffer);
    }

    for (uint16_t idx = 0; idx < methCount; idx++) {
        FieldTable ft;
        ft.decode(inBuffer);
        if (!ft.isSet("argCount"))
            return 0;
        int argCount = ft.getInt("argCount");
        for (int mIdx = 0; mIdx < argCount; mIdx++) {
            FieldTable aft;
            aft.decode(inBuffer);
        }
    }

    for (uint16_t idx = 0; idx < evntCount; idx++) {
        FieldTable ft;
        ft.decode(inBuffer);
        if (!ft.isSet("argCount"))
            return 0;
        int argCount = ft.getInt("argCount");
        for (int mIdx = 0; mIdx < argCount; mIdx++) {
            FieldTable aft;
            aft.decode(inBuffer);
        }
    }

    end = inBuffer.getPosition();
    inBuffer.restore(); // restore original position
    return end - start;
}

Mutex& ManagementBroker::getMutex()
{
    return userLock;
}

Buffer* ManagementBroker::startEventLH()
{
    Buffer* outBuffer(new Buffer(eventBuffer, MA_BUFFER_SIZE));
    EncodeHeader(*outBuffer, 'e');
    outBuffer->putLongLong(uint64_t(Duration(now())));
    return outBuffer;
}

void ManagementBroker::finishEventLH(Buffer* outBuffer)
{
    uint32_t outLen = MA_BUFFER_SIZE - outBuffer->available();
    outBuffer->reset();
    SendBuffer(*outBuffer, outLen, mExchange, "mgmt.event");
    delete outBuffer;
}

