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

ManagementAgent::shared_ptr ManagementBroker::agent;
bool                        ManagementBroker::enabled = 0;

ManagementBroker::RemoteAgent::~RemoteAgent ()
{
    if (mgmtObject.get () != 0)
        mgmtObject->resourceDestroy ();
}

ManagementBroker::ManagementBroker (string _dataDir, uint16_t _interval, Manageable* _broker) :
    dataDir (_dataDir), interval (_interval), broker (_broker)
{
    timer.add (intrusive_ptr<TimerTask> (new Periodic(*this, interval)));
    localBank      = 5;
    nextObjectId   = 1;
    bootSequence   = 1;
    nextRemoteBank = 10;
    clientWasAdded = false;

    // Get from file or generate and save to file.
    if (dataDir.empty ())
    {
        uuid.generate ();
        QPID_LOG (info, "ManagementBroker has no data directory, generated new broker ID: "
                  << uuid);
    }
    else
    {
        string   filename (dataDir + "/.mbrokerdata");
        ifstream inFile   (filename.c_str ());

        if (inFile.good ())
        {
            inFile >> uuid;
            inFile >> bootSequence;
            inFile >> nextRemoteBank;
            inFile.close ();
            QPID_LOG (debug, "ManagementBroker restored broker ID: " << uuid);

            bootSequence++;
            writeData ();
        }
        else
        {
            uuid.generate ();
            QPID_LOG (info, "ManagementBroker generated broker ID: " << uuid);
            writeData ();
        }

        QPID_LOG (debug, "ManagementBroker boot sequence: " << bootSequence);
    }
}

ManagementBroker::~ManagementBroker () {}

void ManagementBroker::writeData ()
{
    string   filename (dataDir + "/.mbrokerdata");
    ofstream outFile (filename.c_str ());

    if (outFile.good ())
    {
        outFile << uuid << " " << bootSequence << " " << nextRemoteBank << endl;
        outFile.close ();
    }
}

void ManagementBroker::enableManagement (string dataDir, uint16_t interval, Manageable* broker)
{
    enabled = 1;
    if (agent.get () == 0)
        agent = shared_ptr (new ManagementBroker (dataDir, interval, broker));
}

ManagementAgent::shared_ptr ManagementAgent::getAgent (void)
{
    return ManagementBroker::agent;
}

void ManagementBroker::shutdown (void)
{
    if (agent.get () != 0)
    {
        ManagementBroker* broker = (ManagementBroker*) agent.get();

        broker->mExchange.reset ();
        broker->dExchange.reset ();
        agent.reset ();
    }
}

void ManagementBroker::setExchange (broker::Exchange::shared_ptr _mexchange,
                                    broker::Exchange::shared_ptr _dexchange)
{
    mExchange = _mexchange;
    dExchange = _dexchange;
}

void ManagementBroker::RegisterClass (string   packageName,
                                      string   className,
                                      uint8_t* md5Sum,
                                      ManagementObject::writeSchemaCall_t schemaCall)
{
    Mutex::ScopedLock lock (userLock);
    PackageMap::iterator pIter = FindOrAddPackage (packageName);
    AddClassLocal (pIter, className, md5Sum, schemaCall);
}

void ManagementBroker::addObject (ManagementObject::shared_ptr object,
                                  uint32_t                     persistId,
                                  uint32_t                     persistBank)
{
    Mutex::ScopedLock lock (addLock);
    uint64_t objectId;

    if (persistId == 0)
    {
        objectId = ((uint64_t) bootSequence) << 48 |
            ((uint64_t) localBank) << 24 | nextObjectId++;
        if ((nextObjectId & 0xFF000000) != 0)
        {
            nextObjectId = 1;
            localBank++;
        }
    }
    else
        objectId = ((uint64_t) persistBank) << 24 | persistId;

    object->setObjectId (objectId);
    newManagementObjects[objectId] = object;
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
                                   broker::Exchange::shared_ptr exchange,
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
    std::list<uint64_t> deleteList;

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
            ManagementObject::shared_ptr object = iter->second;
            object->setAllChanged ();
        }
    }

    if (managementObjects.empty ())
        return;
        
    for (ManagementObjectMap::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++)
    {
        ManagementObject::shared_ptr object = iter->second;

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
    for (std::list<uint64_t>::reverse_iterator iter = deleteList.rbegin ();
         iter != deleteList.rend ();
         iter++)
        managementObjects.erase (*iter);

    deleteList.clear ();
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

void ManagementBroker::dispatchCommand (Deliverable&      deliverable,
                                        const string&     routingKey,
                                        const FieldTable* /*args*/)
{
    Mutex::ScopedLock lock (userLock);
    Message&  msg = ((DeliverableMessage&) deliverable).getMessage ();

    if (routingKey.compare (0, 13, "agent.method.") == 0)
        dispatchMethodLH (msg, routingKey, 13);

    else if (routingKey.length () == 5 &&
        routingKey.compare (0, 5, "agent") == 0)
        dispatchAgentCommandLH (msg);

    else
    {
        QPID_LOG (debug, "Illegal routing key for dispatch: " << routingKey);
        return;
    }
}

void ManagementBroker::dispatchMethodLH (Message&      msg,
                                         const string& routingKey,
                                         size_t        first)
{
    size_t    pos, start = first;
    uint32_t  contentSize;

    if (routingKey.length () == start)
    {
        QPID_LOG (debug, "Missing package-name in routing key: " << routingKey);
        return;
    }

    pos = routingKey.find ('.', start);
    if (pos == string::npos || routingKey.length () == pos + 1)
    {
        QPID_LOG (debug, "Missing class-name in routing key: " << routingKey);
        return;
    }

    string packageName = routingKey.substr (start, pos - start);

    start = pos + 1;
    pos = routingKey.find ('.', start);
    if (pos == string::npos || routingKey.length () == pos + 1)
    {
        QPID_LOG (debug, "Missing method-name in routing key: " << routingKey);
        return;
    }

    string className = routingKey.substr (start, pos - start);

    start = pos + 1;
    string methodName = routingKey.substr (start, routingKey.length () - start);

    contentSize = msg.encodedContentSize ();
    if (contentSize < 8 || contentSize > MA_BUFFER_SIZE)
        return;

    Buffer   inBuffer  (inputBuffer,  MA_BUFFER_SIZE);
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen, sequence;
    uint8_t  opcode;

    if (msg.encodedSize() > MA_BUFFER_SIZE) {
        QPID_LOG(debug, "ManagementBroker::dispatchMethodLH: Message too large: " <<
                 msg.encodedSize());
        return;
    }

    msg.encodeContent (inBuffer);
    inBuffer.reset ();

    if (!CheckHeader (inBuffer, &opcode, &sequence))
    {
        QPID_LOG (debug, "    Invalid content header");
        return;
    }

    if (opcode != 'M')
    {
        QPID_LOG (debug, "    Unexpected opcode " << opcode);
        return;
    }

    uint64_t objId = inBuffer.getLongLong ();
    string   replyToKey;

    const framing::MessageProperties* p =
        msg.getFrames().getHeaders()->get<framing::MessageProperties>();
    if (p && p->hasReplyTo())
    {
        const framing::ReplyTo& rt = p->getReplyTo ();
        replyToKey = rt.getRoutingKey ();
    }
    else
    {
        QPID_LOG (debug, "    Reply-to missing");
        return;
    }

    EncodeHeader (outBuffer, 'm', sequence);

    ManagementObjectMap::iterator iter = managementObjects.find (objId);
    if (iter == managementObjects.end () || iter->second->isDeleted ())
    {
        outBuffer.putLong        (Manageable::STATUS_UNKNOWN_OBJECT);
        outBuffer.putShortString (Manageable::StatusText (Manageable::STATUS_UNKNOWN_OBJECT));
    }
    else
    {
        iter->second->doMethod (methodName, inBuffer, outBuffer);
    }

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, dExchange, replyToKey);
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

void ManagementBroker::handlePackageIndLH (Buffer& /*inBuffer*/, string /*replyToKey*/, uint32_t /*sequence*/)
{
}

void ManagementBroker::handleClassQueryLH (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    std::string packageName;

    inBuffer.getShortString (packageName);
    PackageMap::iterator pIter = packages.find (packageName);
    if (pIter != packages.end ())
    {
        ClassMap cMap = pIter->second;
        for (ClassMap::iterator cIter = cMap.begin ();
             cIter != cMap.end ();
             cIter++)
        {
            if (cIter->second.hasSchema ())
            {
                Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
                uint32_t outLen;

                EncodeHeader (outBuffer, 'q', sequence);
                EncodeClassIndication (outBuffer, pIter, cIter);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                SendBuffer (outBuffer, outLen, dExchange, replyToKey);
            }
        }
    }

    sendCommandComplete (replyToKey, sequence);
}

void ManagementBroker::SchemaClass::appendSchema (Buffer& buf)
{
    // If the management package is attached locally (embedded in the broker or
    // linked in via plug-in), call the schema handler directly.  If the package
    // is from a remote management agent, send the stored schema information.

    if (writeSchemaCall != 0)
        writeSchemaCall (buf);
    else
        buf.putRawData (buffer, bufferLen);
}

void ManagementBroker::handleSchemaRequestLH (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string         packageName;
    SchemaClassKey key;

    inBuffer.getShortString (packageName);
    inBuffer.getShortString (key.name);
    inBuffer.getBin128      (key.hash);

    PackageMap::iterator pIter = packages.find (packageName);
    if (pIter != packages.end ())
    {
        ClassMap cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find (key);
        if (cIter != cMap.end ())
        {
            Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;
            SchemaClass classInfo = cIter->second;

            if (classInfo.hasSchema())
            {
                EncodeHeader (outBuffer, 's', sequence);
                classInfo.appendSchema (outBuffer);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                SendBuffer (outBuffer, outLen, dExchange, replyToKey);
            }
            else
                sendCommandComplete (replyToKey, sequence, 1, "Schema not available");
        }
        else
            sendCommandComplete (replyToKey, sequence, 1, "Class key not found");
    }
    else
        sendCommandComplete (replyToKey, sequence, 1, "Package not found");
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

void ManagementBroker::handleAttachRequestLH (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string   label;
    uint32_t requestedBank;
    uint32_t assignedBank;
    Uuid     sessionId;
    Uuid     systemId;

    inBuffer.getShortString (label);
    sessionId.decode (inBuffer);
    systemId.decode  (inBuffer);
    requestedBank = inBuffer.getLong ();
    assignedBank  = assignBankLH (requestedBank);

    RemoteAgentMap::iterator aIter = remoteAgents.find (sessionId);
    if (aIter != remoteAgents.end())
    {
        // There already exists an agent on this session.  Reject the request.
        sendCommandComplete (replyToKey, sequence, 1, "Session already has remote agent");
        return;
    }

    RemoteAgent* agent = new RemoteAgent;
    agent->objIdBank  = assignedBank;
    agent->mgmtObject = management::Agent::shared_ptr
        (new management::Agent (agent));
    agent->mgmtObject->set_sessionId    (sessionId);
    agent->mgmtObject->set_label        (label);
    agent->mgmtObject->set_registeredTo (broker->GetManagementObject()->getObjectId());
    agent->mgmtObject->set_systemId     (systemId);
    addObject (agent->mgmtObject);

    remoteAgents[sessionId] = agent;

    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader (outBuffer, 'a', sequence);
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
        ManagementObject::shared_ptr object = iter->second;
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

void ManagementBroker::dispatchAgentCommandLH (Message& msg)
{
    Buffer   inBuffer (inputBuffer, MA_BUFFER_SIZE);
    uint8_t  opcode;
    uint32_t sequence;
    string   replyToKey;

    const framing::MessageProperties* p =
        msg.getFrames().getHeaders()->get<framing::MessageProperties>();
    if (p && p->hasReplyTo())
    {
        const framing::ReplyTo& rt = p->getReplyTo ();
        replyToKey = rt.getRoutingKey ();
    }
    else
        return;

    if (msg.encodedSize() > MA_BUFFER_SIZE) {
        QPID_LOG(debug, "ManagementBroker::dispatchAgentCommandLH: Message too large: " <<
                 msg.encodedSize());
        return;
    }

    msg.encodeContent (inBuffer);
    inBuffer.reset ();

    if (!CheckHeader (inBuffer, &opcode, &sequence))
        return;

    if      (opcode == 'B') handleBrokerRequestLH (inBuffer, replyToKey, sequence);
    else if (opcode == 'P') handlePackageQueryLH  (inBuffer, replyToKey, sequence);
    else if (opcode == 'p') handlePackageIndLH    (inBuffer, replyToKey, sequence);
    else if (opcode == 'Q') handleClassQueryLH    (inBuffer, replyToKey, sequence);
    else if (opcode == 'S') handleSchemaRequestLH (inBuffer, replyToKey, sequence);
  //else if (opcode == 'A') handleAttachRequestLH (inBuffer, replyToKey, sequence);
    else if (opcode == 'G') handleGetQueryLH      (inBuffer, replyToKey, sequence);
}

ManagementBroker::PackageMap::iterator ManagementBroker::FindOrAddPackage (std::string name)
{
    PackageMap::iterator pIter = packages.find (name);
    if (pIter != packages.end ())
        return pIter;

    // No such package found, create a new map entry.
    pair<PackageMap::iterator, bool> result =
        packages.insert (pair<string, ClassMap> (name, ClassMap ()));
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

void ManagementBroker::AddClassLocal (PackageMap::iterator  pIter,
                                      string                className,
                                      uint8_t*              md5Sum,
                                      ManagementObject::writeSchemaCall_t schemaCall)
{
    SchemaClassKey key;
    ClassMap&      cMap = pIter->second;

    key.name = className;
    memcpy (&key.hash, md5Sum, 16);

    ClassMap::iterator cIter = cMap.find (key);
    if (cIter != cMap.end ())
        return;

    // No such class found, create a new class with local information.
    QPID_LOG (debug, "ManagementBroker added class " << pIter->first << "." <<
              key.name);
    SchemaClass classInfo;

    classInfo.writeSchemaCall = schemaCall;
    cMap[key] = classInfo;

    // TODO: Publish a class-indication message
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

