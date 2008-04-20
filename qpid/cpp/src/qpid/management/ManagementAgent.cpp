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
 
#include "ManagementAgent.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/log/Statement.h"
#include <qpid/broker/Message.h>
#include <qpid/broker/MessageDelivery.h>
#include "qpid/framing/MessageXTransferBody.h"
#include <list>
#include <iostream>
#include <fstream>

using boost::intrusive_ptr;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace std;

ManagementAgent::shared_ptr ManagementAgent::agent;
bool                        ManagementAgent::enabled = 0;

ManagementAgent::ManagementAgent (string _dataDir, uint16_t _interval) :
    dataDir (_dataDir), interval (_interval)
{
    timer.add (intrusive_ptr<TimerTask> (new Periodic(*this, interval)));
    localBank        = 3;
    nextObjectId     = 1;
    nextRemotePrefix = 101;

    // Get from file or generate and save to file.
    if (dataDir.empty ())
    {
        uuid.generate ();
        bootSequence = 1;
        QPID_LOG (info, "ManagementAgent has no data directory, generated new broker ID: "
                  << uuid);
    }
    else
    {
        string   filename    (dataDir + "/brokerId");
        string   seqFilename (dataDir + "/bootseq");
        ifstream inFile      (filename.c_str ());
        ifstream seqFile     (seqFilename.c_str ());

        if (inFile.good ())
        {
            inFile >> uuid;
            inFile.close ();
            QPID_LOG (debug, "ManagementAgent restored broker ID: " << uuid);
        }
        else
        {
            uuid.generate ();
            QPID_LOG (info, "ManagementAgent generated broker ID: " << uuid);

            ofstream outFile (filename.c_str ());
            if (outFile.good ())
            {
                outFile << uuid << endl;
                outFile.close ();
                QPID_LOG (debug, "ManagementAgent saved broker ID");
            }
            else
            {
                QPID_LOG (warning, "ManagementAgent unable to save broker ID");
            }
        }

        if (seqFile.good ())
        {
            seqFile >> bootSequence;
            seqFile.close ();
        }
        else
            bootSequence = 1;

        ofstream seqOut (seqFilename.c_str ());
        if (seqOut.good ())
        {
            uint16_t nextSeq = (bootSequence + 1) & 0x7FFF;
            if (nextSeq == 0)
                nextSeq = 1;
            seqOut << nextSeq << endl;
            seqOut.close ();
        }

        QPID_LOG (debug, "ManagementAgent boot sequence: " << bootSequence);
    }
}

ManagementAgent::~ManagementAgent () {}

void ManagementAgent::enableManagement (string dataDir, uint16_t interval)
{
    enabled = 1;
    if (agent.get () == 0)
        agent = shared_ptr (new ManagementAgent (dataDir, interval));
}

ManagementAgent::shared_ptr ManagementAgent::getAgent (void)
{
    return agent;
}

void ManagementAgent::shutdown (void)
{
    if (agent.get () != 0)
    {
        agent->mExchange.reset ();
        agent->dExchange.reset ();
        agent.reset ();
    }
}

void ManagementAgent::setExchange (broker::Exchange::shared_ptr _mexchange,
                                   broker::Exchange::shared_ptr _dexchange)
{
    mExchange = _mexchange;
    dExchange = _dexchange;
}

void ManagementAgent::RegisterClass (string   packageName,
                                     string   className,
                                     uint8_t* md5Sum,
                                     ManagementObject::writeSchemaCall_t schemaCall)
{
    Mutex::ScopedLock lock (userLock);
    PackageMap::iterator pIter = FindOrAddPackage (packageName);
    AddClassLocal (pIter, className, md5Sum, schemaCall);
}

void ManagementAgent::addObject (ManagementObject::shared_ptr object,
                                 uint32_t                     persistId,
                                 uint32_t                     persistBank)
{
    Mutex::ScopedLock lock (userLock);
    uint64_t objectId;

    if (persistId == 0)
        objectId = ((uint64_t) bootSequence) << 48 |
            ((uint64_t) localBank) << 24 | nextObjectId++;
    else
        objectId = ((uint64_t) persistBank) << 24 | persistId;

    object->setObjectId (objectId);
    managementObjects[objectId] = object;
}

ManagementAgent::Periodic::Periodic (ManagementAgent& _agent, uint32_t _seconds)
    : TimerTask (qpid::sys::Duration (_seconds * qpid::sys::TIME_SEC)), agent(_agent) {}

ManagementAgent::Periodic::~Periodic () {}

void ManagementAgent::Periodic::fire ()
{
    agent.timer.add (intrusive_ptr<TimerTask> (new Periodic (agent, agent.interval)));
    agent.PeriodicProcessing ();
}

void ManagementAgent::clientAdded (void)
{
    for (ManagementObjectMap::iterator iter = managementObjects.begin ();
         iter != managementObjects.end ();
         iter++)
    {
        ManagementObject::shared_ptr object = iter->second;
        object->setAllChanged ();
    }
}

void ManagementAgent::EncodeHeader (Buffer& buf, uint8_t opcode, uint32_t seq)
{
    buf.putOctet ('A');
    buf.putOctet ('M');
    buf.putOctet ('1');
    buf.putOctet (opcode);
    buf.putLong  (seq);
}

bool ManagementAgent::CheckHeader (Buffer& buf, uint8_t *opcode, uint32_t *seq)
{
    uint8_t h1 = buf.getOctet ();
    uint8_t h2 = buf.getOctet ();
    uint8_t h3 = buf.getOctet ();

    *opcode = buf.getOctet ();
    *seq    = buf.getLong  ();

    return h1 == 'A' && h2 == 'M' && h3 == '1';
}

void ManagementAgent::SendBuffer (Buffer&  buf,
                                  uint32_t length,
                                  broker::Exchange::shared_ptr exchange,
                                  string   routingKey)
{
    if (exchange.get() == 0)
        return;

    intrusive_ptr<Message> msg (new Message ());
    AMQFrame method (in_place<MessageXTransferBody>(
                         ProtocolVersion(), 0, exchange->getName (), 0, 0));
    AMQFrame header (in_place<AMQHeaderBody>());
    AMQFrame content(in_place<AMQContentBody>());

    content.castBody<AMQContentBody>()->decode(buf, length);

    method.setEof  (false);
    header.setBof  (false);
    header.setEof  (false);
    content.setBof (false);

    msg->getFrames().append(method);
    msg->getFrames().append(header);

    PreviewMessageProperties* props =
        msg->getFrames().getHeaders()->get<PreviewMessageProperties>(true);
    props->setContentLength(length);
    msg->getFrames().append(content);

    DeliverableMessage deliverable (msg);
    exchange->route (deliverable, routingKey, 0);
}

void ManagementAgent::PeriodicProcessing (void)
{
#define BUFSIZE   65536
    Mutex::ScopedLock lock (userLock);
    char                msgChars[BUFSIZE];
    uint32_t            contentSize;
    string              routingKey;
    std::list<uint64_t> deleteList;

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
            object->writeConfig (msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt." + uuid.str() + ".config." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        }
        
        if (object->getInstChanged ())
        {
            Buffer msgBuffer (msgChars, BUFSIZE);
            EncodeHeader (msgBuffer, 'i');
            object->writeInstrumentation (msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt." + uuid.str () + ".inst." + object->getClassName ();
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

void ManagementAgent::sendCommandComplete (string replyToKey, uint32_t sequence,
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

void ManagementAgent::dispatchCommand (Deliverable&      deliverable,
                                       const string&     routingKey,
                                       const FieldTable* /*args*/)
{
    Mutex::ScopedLock lock (userLock);
    Message&  msg = ((DeliverableMessage&) deliverable).getMessage ();

    if (routingKey.compare (0, 13, "agent.method.") == 0)
        dispatchMethod (msg, routingKey, 13);

    else if (routingKey.length () == 5 &&
        routingKey.compare (0, 5, "agent") == 0)
        dispatchAgentCommand (msg);

    else
    {
        QPID_LOG (debug, "Illegal routing key for dispatch: " << routingKey);
        return;
    }
}

void ManagementAgent::dispatchMethod (Message&      msg,
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

    uint64_t   objId = inBuffer.getLongLong ();
    string     replyToKey;

    const framing::PreviewMessageProperties* p =
        msg.getFrames().getHeaders()->get<framing::PreviewMessageProperties>();
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

void ManagementAgent::handleBrokerRequest (Buffer&, string replyToKey, uint32_t sequence)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader (outBuffer, 'b', sequence);
    uuid.encode  (outBuffer);

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, dExchange, replyToKey);
}

void ManagementAgent::handlePackageQuery (Buffer&, string replyToKey, uint32_t sequence)
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

void ManagementAgent::handlePackageInd (Buffer& inBuffer, string /*replyToKey*/, uint32_t /*sequence*/)
{
    std::string packageName;

    inBuffer.getShortString (packageName);
    FindOrAddPackage (packageName);
}

void ManagementAgent::handleClassQuery (Buffer& inBuffer, string replyToKey, uint32_t sequence)
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
            Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            EncodeHeader (outBuffer, 'q', sequence);
            EncodeClassIndication (outBuffer, pIter, cIter);
            outLen = MA_BUFFER_SIZE - outBuffer.available ();
            outBuffer.reset ();
            SendBuffer (outBuffer, outLen, dExchange, replyToKey);
        }
    }

    sendCommandComplete (replyToKey, sequence);
}

void ManagementAgent::handleSchemaQuery (Buffer& inBuffer, string replyToKey, uint32_t sequence)
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

            if (classInfo.writeSchemaCall != 0)
            {
                EncodeHeader (outBuffer, 's', sequence);
                classInfo.writeSchemaCall (outBuffer);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                SendBuffer (outBuffer, outLen, dExchange, replyToKey);
            }
            else
            {
                // TODO: Forward request to remote agent.
            }

            clientAdded ();
            // TODO: Send client-added to each remote agent.
        }
    }
}

uint32_t ManagementAgent::assignPrefix (uint32_t /*requestedPrefix*/)
{
    // TODO: Allow remote agents to keep their requested prefixes if able.
    return nextRemotePrefix++;
}

void ManagementAgent::handleAttachRequest (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string   label;
    uint32_t requestedPrefix;
    uint32_t assignedPrefix;

    inBuffer.getShortString (label);
    requestedPrefix = inBuffer.getLong ();
    assignedPrefix  = assignPrefix (requestedPrefix);

    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader (outBuffer, 'a', sequence);
    outBuffer.putLong (assignedPrefix);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, dExchange, replyToKey);
}

void ManagementAgent::handleGetRequest (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    FieldTable           ft;
    FieldTable::ValuePtr value;

    ft.decode (inBuffer);
    value = ft.get ("_class");
    if (value->empty () || !value->convertsTo<string> ())
    {
        // TODO: Send completion with an error code
        return;
    }

    string className (value->get<string> ());

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
            object->writeConfig (outBuffer);
            object->writeInstrumentation (outBuffer, true);
            outLen = MA_BUFFER_SIZE - outBuffer.available ();
            outBuffer.reset ();
            SendBuffer (outBuffer, outLen, dExchange, replyToKey);
        }
    }

    sendCommandComplete (replyToKey, sequence);
}

void ManagementAgent::dispatchAgentCommand (Message& msg)
{
    Buffer   inBuffer (inputBuffer,  MA_BUFFER_SIZE);
    uint8_t  opcode;
    uint32_t sequence;
    string   replyToKey;

    const framing::PreviewMessageProperties* p =
        msg.getFrames().getHeaders()->get<framing::PreviewMessageProperties>();
    if (p && p->hasReplyTo())
    {
        const framing::ReplyTo& rt = p->getReplyTo ();
        replyToKey = rt.getRoutingKey ();
    }
    else
        return;

    msg.encodeContent (inBuffer);
    inBuffer.reset ();

    if (!CheckHeader (inBuffer, &opcode, &sequence))
        return;

    if      (opcode == 'B') handleBrokerRequest (inBuffer, replyToKey, sequence);
    else if (opcode == 'P') handlePackageQuery  (inBuffer, replyToKey, sequence);
    else if (opcode == 'p') handlePackageInd    (inBuffer, replyToKey, sequence);
    else if (opcode == 'Q') handleClassQuery    (inBuffer, replyToKey, sequence);
    else if (opcode == 'S') handleSchemaQuery   (inBuffer, replyToKey, sequence);
    else if (opcode == 'A') handleAttachRequest (inBuffer, replyToKey, sequence);
    else if (opcode == 'G') handleGetRequest    (inBuffer, replyToKey, sequence);
}

ManagementAgent::PackageMap::iterator ManagementAgent::FindOrAddPackage (std::string name)
{
    PackageMap::iterator pIter = packages.find (name);
    if (pIter != packages.end ())
        return pIter;

    // No such package found, create a new map entry.
    pair<PackageMap::iterator, bool> result =
        packages.insert (pair<string, ClassMap> (name, ClassMap ()));
    QPID_LOG (debug, "ManagementAgent added package " << name);

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

void ManagementAgent::AddClassLocal (PackageMap::iterator  pIter,
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
    QPID_LOG (debug, "ManagementAgent added class " << pIter->first << "." <<
              key.name);
    SchemaClass classInfo;

    classInfo.writeSchemaCall = schemaCall;
    cMap[key] = classInfo;

    // TODO: Publish a class-indication message
}

void ManagementAgent::EncodePackageIndication (Buffer&              buf,
                                               PackageMap::iterator pIter)
{
    buf.putShortString ((*pIter).first);
}

void ManagementAgent::EncodeClassIndication (Buffer&              buf,
                                             PackageMap::iterator pIter,
                                             ClassMap::iterator   cIter)
{
    SchemaClassKey key = (*cIter).first;

    buf.putShortString ((*pIter).first);
    buf.putShortString (key.name);
    buf.putBin128      (key.hash);
}

