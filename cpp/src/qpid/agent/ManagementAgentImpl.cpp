
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
#include "ManagementAgentImpl.h"
#include <list>
#include <unistd.h>

using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::sys;
using std::stringstream;
using std::string;
using std::cout;
using std::endl;

ManagementAgent* ManagementAgent::getAgent()
{
    //static ManagementAgent* agent = 0;

    //if (agent == 0)
    //    agent = new ManagementAgentImpl();
    //return agent;
    return 0;
}

ManagementAgentImpl::ManagementAgentImpl() :
    clientWasAdded(true), objIdPrefix(0), bgThread(*this), thread(bgThread)
{
    // TODO: Establish system ID
}

void ManagementAgentImpl::init (std::string brokerHost,
                                uint16_t    brokerPort,
                                uint16_t    intervalSeconds,
                                bool        useExternalThread)
{
    interval     = intervalSeconds;
    extThread    = useExternalThread;
    nextObjectId = 1;

    sessionId.generate();
    queueName << "qmfagent-" << sessionId;
    string dest = "qmfagent";

    connection.open(brokerHost.c_str(), brokerPort);
    session = connection.newSession (queueName.str());
    dispatcher = new client::Dispatcher(session);


    session.queueDeclare (arg::queue=queueName.str());
    session.exchangeBind (arg::exchange="amq.direct", arg::queue=queueName.str(),
                          arg::bindingKey=queueName.str ());
    session.messageSubscribe (arg::queue=queueName.str(),
                              arg::destination=dest);
    session.messageFlow (arg::destination=dest, arg::unit=0, arg::value=0xFFFFFFFF);
    session.messageFlow (arg::destination=dest, arg::unit=1, arg::value=0xFFFFFFFF);

    Message attachRequest;
    char    rawbuffer[512];   // TODO: Modify Buffer so it can use stringstream
    Buffer  buffer (rawbuffer, 512);

    attachRequest.getDeliveryProperties().setRoutingKey("agent");
    attachRequest.getMessageProperties().setReplyTo(ReplyTo("amq.direct", queueName.str()));

    EncodeHeader (buffer, 'A');
    buffer.putShortString ("RemoteAgent [C++]");
    buffer.putShortString (queueName.str());
    systemId.encode  (buffer);
    buffer.putLong (11);

    size_t length = 512 - buffer.available ();
    string stringBuffer (rawbuffer, length);
    attachRequest.setData (stringBuffer);

    session.messageTransfer (arg::content=attachRequest, arg::destination="qpid.management");

    dispatcher->listen (dest, this);
    dispatcher->start ();
}

ManagementAgentImpl::~ManagementAgentImpl ()
{
    dispatcher->stop ();
    delete dispatcher;
}

void ManagementAgentImpl::RegisterClass (std::string packageName,
                                         std::string className,
                                         uint8_t*    md5Sum,
                                         management::ManagementObject::writeSchemaCall_t schemaCall)
{ 
    Mutex::ScopedLock lock(agentLock);
    PackageMap::iterator pIter = FindOrAddPackage (packageName);
    AddClassLocal (pIter, className, md5Sum, schemaCall);
}

uint64_t ManagementAgentImpl::addObject (ManagementObject* object,
                                         uint32_t          /*persistId*/,
                                         uint32_t          /*persistBank*/)
{
    Mutex::ScopedLock lock(addLock);
    uint64_t objectId;

    // TODO: fix object-id handling
    objectId = objIdPrefix | ((nextObjectId++) & 0x00FFFFFF);
    object->setObjectId (objectId);
    newManagementObjects[objectId] = object;
    return objectId;
}

uint32_t ManagementAgentImpl::pollCallbacks (uint32_t /*callLimit*/)
{
    return 0;
}

int ManagementAgentImpl::getSignalFd (void)
{
    return -1;
}

void ManagementAgentImpl::handleAttachResponse (Buffer& inBuffer)
{
    Mutex::ScopedLock lock(agentLock);
    uint32_t assigned;

    assigned = inBuffer.getLong();
    objIdPrefix = ((uint64_t) assigned) << 24;

    // Send package indications for all local packages
    for (PackageMap::iterator pIter = packages.begin();
         pIter != packages.end();
         pIter++) {
        Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        EncodeHeader(outBuffer, 'p');
        EncodePackageIndication(outBuffer, pIter);
        outLen = MA_BUFFER_SIZE - outBuffer.available ();
        outBuffer.reset();
        SendBuffer(outBuffer, outLen, "qpid.management", "agent");

        // Send class indications for all local classes
        ClassMap cMap = pIter->second;
        for (ClassMap::iterator cIter = cMap.begin(); cIter != cMap.end(); cIter++) {
            outBuffer.reset();
            EncodeHeader(outBuffer, 'q');
            EncodeClassIndication(outBuffer, pIter, cIter);
            outLen = MA_BUFFER_SIZE - outBuffer.available ();
            outBuffer.reset();
            SendBuffer(outBuffer, outLen, "qpid.management", "agent");
        }
    }
}

void ManagementAgentImpl::handleSchemaRequest(Buffer& inBuffer, uint32_t sequence)
{
    Mutex::ScopedLock lock(agentLock);
    string packageName;
    SchemaClassKey key;

    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);

    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end()) {
        ClassMap cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find(key);
        if (cIter != cMap.end()) {
            SchemaClass schema = cIter->second;
             Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
             uint32_t outLen;

             EncodeHeader(outBuffer, 's', sequence);
             schema.writeSchemaCall(outBuffer);
             outLen = MA_BUFFER_SIZE - outBuffer.available ();
             outBuffer.reset();
             SendBuffer(outBuffer, outLen, "qpid.management", "agent");
        }
    }
}

void ManagementAgentImpl::handleConsoleAddedIndication()
{
    Mutex::ScopedLock lock(agentLock);
    clientWasAdded = true;
}

void ManagementAgentImpl::received (Message& msg)
{
    string   data = msg.getData ();
    Buffer   inBuffer (const_cast<char*>(data.c_str()), data.size());
    uint8_t  opcode;
    uint32_t sequence;

    if (CheckHeader (inBuffer, &opcode, &sequence))
    {
        if      (opcode == 'a') handleAttachResponse(inBuffer);
        else if (opcode == 'S') handleSchemaRequest(inBuffer, sequence);
        else if (opcode == 'x') handleConsoleAddedIndication();
    }
}

void ManagementAgentImpl::EncodeHeader (Buffer& buf, uint8_t opcode, uint32_t seq)
{
    buf.putOctet ('A');
    buf.putOctet ('M');
    buf.putOctet ('1');
    buf.putOctet (opcode);
    buf.putLong  (seq);
}

bool ManagementAgentImpl::CheckHeader (Buffer& buf, uint8_t *opcode, uint32_t *seq)
{
    if (buf.getSize() < 8)
        return false;

    uint8_t h1 = buf.getOctet ();
    uint8_t h2 = buf.getOctet ();
    uint8_t h3 = buf.getOctet ();

    *opcode = buf.getOctet ();
    *seq    = buf.getLong ();

    return h1 == 'A' && h2 == 'M' && h3 == '1';
}

void ManagementAgentImpl::SendBuffer (Buffer&  buf,
                                      uint32_t length,
                                      string   exchange,
                                      string   routingKey)
{
    Message msg;
    string  data;

    if (objIdPrefix == 0)
        return;

    buf.getRawData(data, length);
    msg.getDeliveryProperties().setRoutingKey(routingKey);
    msg.getMessageProperties().setReplyTo(ReplyTo("amq.direct", queueName.str()));
    msg.setData (data);
    session.messageTransfer (arg::content=msg, arg::destination=exchange);
}

ManagementAgentImpl::PackageMap::iterator ManagementAgentImpl::FindOrAddPackage (std::string name)
{
    PackageMap::iterator pIter = packages.find (name);
    if (pIter != packages.end ())
        return pIter;

    // No such package found, create a new map entry.
    std::pair<PackageMap::iterator, bool> result =
        packages.insert (std::pair<string, ClassMap> (name, ClassMap ()));

    // Publish a package-indication message
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader (outBuffer, 'p');
    EncodePackageIndication (outBuffer, result.first);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    SendBuffer (outBuffer, outLen, "qpid.management", "mgmt.schema.package");

    return result.first;
}

void ManagementAgentImpl::moveNewObjectsLH()
{
    Mutex::ScopedLock lock (addLock);
    for (ManagementObjectMap::iterator iter = newManagementObjects.begin ();
         iter != newManagementObjects.end ();
         iter++)
        managementObjects[iter->first] = iter->second;
    newManagementObjects.clear();
}

void ManagementAgentImpl::AddClassLocal (PackageMap::iterator  pIter,
                                         string                className,
                                         uint8_t*              md5Sum,
                                         management::ManagementObject::writeSchemaCall_t schemaCall)
{
    SchemaClassKey key;
    ClassMap&      cMap = pIter->second;

    key.name = className;
    memcpy (&key.hash, md5Sum, 16);

    ClassMap::iterator cIter = cMap.find (key);
    if (cIter != cMap.end ())
        return;

    // No such class found, create a new class with local information.
    SchemaClass classInfo;

    classInfo.writeSchemaCall = schemaCall;
    cMap[key] = classInfo;

    // TODO: Publish a class-indication message
}

void ManagementAgentImpl::EncodePackageIndication (Buffer&              buf,
                                                   PackageMap::iterator pIter)
{
    buf.putShortString ((*pIter).first);
}

void ManagementAgentImpl::EncodeClassIndication (Buffer&              buf,
                                                 PackageMap::iterator pIter,
                                                 ClassMap::iterator   cIter)
{
    SchemaClassKey key = (*cIter).first;

    buf.putShortString ((*pIter).first);
    buf.putShortString (key.name);
    buf.putBin128      (key.hash);
}

void ManagementAgentImpl::PeriodicProcessing()
{
#define BUFSIZE   65536
    Mutex::ScopedLock lock(agentLock);
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
        routingKey = "mgmt." + systemId.str() + ".heartbeat";
        SendBuffer (msgBuffer, contentSize, "qpid.management", routingKey);
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
            routingKey = "mgmt." + systemId.str() + ".prop." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, "qpid.management", routingKey);
        }
        
        if (object->getInstChanged ())
        {
            Buffer msgBuffer (msgChars, BUFSIZE);
            EncodeHeader (msgBuffer, 'i');
            object->writeStatistics(msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available ();
            msgBuffer.reset ();
            routingKey = "mgmt." + systemId.str () + ".stat." + object->getClassName ();
            SendBuffer (msgBuffer, contentSize, "qpid.management", routingKey);
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

void ManagementAgentImpl::BackgroundThread::run()
{
    while (true) {
        ::sleep(5);
        agent.PeriodicProcessing();
    }
}
