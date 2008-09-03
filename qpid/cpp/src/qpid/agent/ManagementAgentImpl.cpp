
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
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <iostream>
#include <fstream>


using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::sys;
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

const string ManagementAgentImpl::storeMagicNumber("MA01");

ManagementAgentImpl::ManagementAgentImpl() :
    extThread(false), writeFd(-1), readFd(-1),
    clientWasAdded(true), requestedBank(0),
    assignedBank(0), brokerBank(0), bootSequence(0),
    connThreadBody(*this), connThread(connThreadBody),
    pubThreadBody(*this), pubThread(pubThreadBody)
{
    // TODO: Establish system ID
}

void ManagementAgentImpl::init(string    brokerHost,
                               uint16_t  brokerPort,
                               uint16_t  intervalSeconds,
                               bool      useExternalThread,
                               string    _storeFile)
{
    interval     = intervalSeconds;
    extThread    = useExternalThread;
    storeFile    = _storeFile;
    nextObjectId = 1;
    host         = brokerHost;
    port         = brokerPort;

    // TODO: Abstract the socket calls for portability
    if (extThread) {
        int pair[2];
        int result = socketpair(PF_LOCAL, SOCK_STREAM, 0, pair);
        if (result == -1) {
            return;
        }
        writeFd = pair[0];
        readFd  = pair[1];

        // Set the readFd to non-blocking
        int flags = fcntl(readFd, F_GETFL);
        fcntl(readFd, F_SETFL, flags | O_NONBLOCK);
    }

    retrieveData();
    bootSequence++;
    if ((bootSequence & 0xF000) != 0)
        bootSequence = 1;
    storeData(true);
}

ManagementAgentImpl::~ManagementAgentImpl()
{
}

void ManagementAgentImpl::RegisterClass(std::string packageName,
                                        std::string className,
                                        uint8_t*    md5Sum,
                                        management::ManagementObject::writeSchemaCall_t schemaCall)
{ 
    Mutex::ScopedLock lock(agentLock);
    PackageMap::iterator pIter = FindOrAddPackage(packageName);
    AddClassLocal(pIter, className, md5Sum, schemaCall);
}

ObjectId ManagementAgentImpl::addObject(ManagementObject* object,
                                        uint64_t          persistId)
{
    Mutex::ScopedLock lock(addLock);
    uint16_t sequence  = persistId ? 0 : bootSequence;
    uint64_t objectNum = persistId ? persistId : nextObjectId++;

    ObjectId objectId(&attachment, 0, sequence, objectNum);

    // TODO: fix object-id handling
    object->setObjectId(objectId);
    newManagementObjects[objectId] = object;
    return objectId;
}

uint32_t ManagementAgentImpl::pollCallbacks(uint32_t callLimit)
{
    Mutex::ScopedLock lock(agentLock);

    for (uint32_t idx = 0; callLimit == 0 || idx < callLimit; idx++) {
        if (methodQueue.empty())
            break;

        QueuedMethod* item = methodQueue.front();
        methodQueue.pop_front();
        {
            Mutex::ScopedUnlock unlock(agentLock);
            Buffer inBuffer(const_cast<char*>(item->body.c_str()), item->body.size());
            invokeMethodRequest(inBuffer, item->sequence, item->replyTo);
            delete item;
        }
    }

    uint8_t rbuf[100];
    while (read(readFd, rbuf, 100) > 0); // Consume all signaling bytes
    return methodQueue.size();
}

int ManagementAgentImpl::getSignalFd(void)
{
    return readFd;
}

void ManagementAgentImpl::startProtocol()
{
    char    rawbuffer[512];
    Buffer  buffer(rawbuffer, 512);

    EncodeHeader(buffer, 'A');
    buffer.putShortString("RemoteAgent [C++]");
    systemId.encode (buffer);
    buffer.putLong(requestedBank);
    uint32_t length = 512 - buffer.available();
    buffer.reset();
    connThreadBody.sendBuffer(buffer, length, "qpid.management", "broker");
}

void ManagementAgentImpl::storeData(bool requested)
{
    if (!storeFile.empty()) {
        ofstream outFile(storeFile.c_str());
        uint32_t bankToWrite = requested ? requestedBank : assignedBank;

        if (outFile.good()) {
            outFile << storeMagicNumber << " " << bankToWrite << " " << bootSequence << endl;
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
                inFile >> requestedBank;
                inFile >> bootSequence;
            }
            inFile.close();
        }
    }
}

void ManagementAgentImpl::sendCommandComplete(string replyToKey, uint32_t sequence,
                                              uint32_t code, string text)
{
    Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader(outBuffer, 'z', sequence);
    outBuffer.putLong(code);
    outBuffer.putShortString(text);
    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    connThreadBody.sendBuffer(outBuffer, outLen, "amq.direct", replyToKey);
}

void ManagementAgentImpl::handleAttachResponse(Buffer& inBuffer)
{
    Mutex::ScopedLock lock(agentLock);

    brokerBank   = inBuffer.getLong();
    assignedBank = inBuffer.getLong();
    if (assignedBank != requestedBank) {
        if (requestedBank == 0)
            cout << "Initial object-id bank assigned: " << assignedBank << endl;
        else
            cout << "Collision in object-id! New bank assigned: " << assignedBank << endl;
        storeData();
    }

    attachment.setBanks(brokerBank, assignedBank);

    // Bind to qpid.management to receive commands
    connThreadBody.bindToBank(assignedBank);

    // Send package indications for all local packages
    for (PackageMap::iterator pIter = packages.begin();
         pIter != packages.end();
         pIter++) {
        Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        EncodeHeader(outBuffer, 'p');
        EncodePackageIndication(outBuffer, pIter);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        connThreadBody.sendBuffer(outBuffer, outLen, "qpid.management", "broker");

        // Send class indications for all local classes
        ClassMap cMap = pIter->second;
        for (ClassMap::iterator cIter = cMap.begin(); cIter != cMap.end(); cIter++) {
            outBuffer.reset();
            EncodeHeader(outBuffer, 'q');
            EncodeClassIndication(outBuffer, pIter, cIter);
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            connThreadBody.sendBuffer(outBuffer, outLen, "qpid.management", "broker");
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
             outLen = MA_BUFFER_SIZE - outBuffer.available();
             outBuffer.reset();
             connThreadBody.sendBuffer(outBuffer, outLen, "qpid.management", "broker");
        }
    }
}

void ManagementAgentImpl::handleConsoleAddedIndication()
{
    Mutex::ScopedLock lock(agentLock);
    clientWasAdded = true;
}

void ManagementAgentImpl::invokeMethodRequest(Buffer& inBuffer, uint32_t sequence, string replyTo)
{
    string   methodName;
    string   packageName;
    string   className;
    uint8_t  hash[16];
    Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    ObjectId objId(inBuffer);
    inBuffer.getShortString(packageName);
    inBuffer.getShortString(className);
    inBuffer.getBin128(hash);
    inBuffer.getShortString(methodName);

    EncodeHeader(outBuffer, 'm', sequence);

    ManagementObjectMap::iterator iter = managementObjects.find(objId);
    if (iter == managementObjects.end() || iter->second->isDeleted()) {
        outBuffer.putLong       (Manageable::STATUS_UNKNOWN_OBJECT);
        outBuffer.putShortString(Manageable::StatusText(Manageable::STATUS_UNKNOWN_OBJECT));
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
    connThreadBody.sendBuffer(outBuffer, outLen, "amq.direct", replyTo);
}

void ManagementAgentImpl::handleGetQuery(Buffer& inBuffer, uint32_t sequence, string replyTo)
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

    string className(value->get<string>());

    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         iter++)
    {
        ManagementObject* object = iter->second;
        if (object->getClassName() == className)
        {
            Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            EncodeHeader(outBuffer, 'g', sequence);
            object->writeProperties(outBuffer);
            object->writeStatistics(outBuffer, true);
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            connThreadBody.sendBuffer(outBuffer, outLen, "amq.direct", replyTo);
        }
    }

    sendCommandComplete(replyTo, sequence);
}

void ManagementAgentImpl::handleMethodRequest(Buffer& inBuffer, uint32_t sequence, string replyTo)
{
    if (extThread) {
        Mutex::ScopedLock lock(agentLock);
        string body;

        inBuffer.getRawData(body, inBuffer.available());
        methodQueue.push_back(new QueuedMethod(sequence, replyTo, body));
        write(writeFd, "X", 1);
    } else {
        invokeMethodRequest(inBuffer, sequence, replyTo);
    }
}

void ManagementAgentImpl::received(Message& msg)
{
    string   data = msg.getData();
    Buffer   inBuffer(const_cast<char*>(data.c_str()), data.size());
    uint8_t  opcode;
    uint32_t sequence;
    string   replyToKey;

    framing::MessageProperties p = msg.getMessageProperties();
    if (p.hasReplyTo()) {
        const framing::ReplyTo& rt = p.getReplyTo();
        replyToKey = rt.getRoutingKey();
    }

    if (CheckHeader(inBuffer, &opcode, &sequence))
    {
        if      (opcode == 'a') handleAttachResponse(inBuffer);
        else if (opcode == 'S') handleSchemaRequest(inBuffer, sequence);
        else if (opcode == 'x') handleConsoleAddedIndication();
        else if (opcode == 'G') handleGetQuery(inBuffer, sequence, replyToKey);
        else if (opcode == 'M') handleMethodRequest(inBuffer, sequence, replyToKey);
    }
}

void ManagementAgentImpl::EncodeHeader(Buffer& buf, uint8_t opcode, uint32_t seq)
{
    buf.putOctet('A');
    buf.putOctet('M');
    buf.putOctet('1');
    buf.putOctet(opcode);
    buf.putLong (seq);
}

bool ManagementAgentImpl::CheckHeader(Buffer& buf, uint8_t *opcode, uint32_t *seq)
{
    if (buf.getSize() < 8)
        return false;

    uint8_t h1 = buf.getOctet();
    uint8_t h2 = buf.getOctet();
    uint8_t h3 = buf.getOctet();

    *opcode = buf.getOctet();
    *seq    = buf.getLong();

    return h1 == 'A' && h2 == 'M' && h3 == '1';
}

ManagementAgentImpl::PackageMap::iterator ManagementAgentImpl::FindOrAddPackage(std::string name)
{
    PackageMap::iterator pIter = packages.find(name);
    if (pIter != packages.end())
        return pIter;

    // No such package found, create a new map entry.
    std::pair<PackageMap::iterator, bool> result =
        packages.insert(std::pair<string, ClassMap>(name, ClassMap()));

    // Publish a package-indication message
    Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    EncodeHeader(outBuffer, 'p');
    EncodePackageIndication(outBuffer, result.first);
    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    connThreadBody.sendBuffer(outBuffer, outLen, "qpid.management", "mgmt.schema.package");

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

void ManagementAgentImpl::AddClassLocal(PackageMap::iterator  pIter,
                                        string                className,
                                        uint8_t*              md5Sum,
                                        management::ManagementObject::writeSchemaCall_t schemaCall)
{
    SchemaClassKey key;
    ClassMap&      cMap = pIter->second;

    key.name = className;
    memcpy(&key.hash, md5Sum, 16);

    ClassMap::iterator cIter = cMap.find(key);
    if (cIter != cMap.end())
        return;

    // No such class found, create a new class with local information.
    SchemaClass classInfo;

    classInfo.writeSchemaCall = schemaCall;
    cMap[key] = classInfo;

    // TODO: Publish a class-indication message
}

void ManagementAgentImpl::EncodePackageIndication(Buffer&              buf,
                                                  PackageMap::iterator pIter)
{
    buf.putShortString((*pIter).first);
}

void ManagementAgentImpl::EncodeClassIndication(Buffer&              buf,
                                                PackageMap::iterator pIter,
                                                ClassMap::iterator   cIter)
{
    SchemaClassKey key = (*cIter).first;

    buf.putShortString((*pIter).first);
    buf.putShortString(key.name);
    buf.putBin128     (key.hash);
}

void ManagementAgentImpl::PeriodicProcessing()
{
#define BUFSIZE   65536
    Mutex::ScopedLock lock(agentLock);
    char                msgChars[BUFSIZE];
    uint32_t            contentSize;
    string              routingKey;
    std::list<ObjectId> deleteList;

    {
        Buffer msgBuffer(msgChars, BUFSIZE);
        EncodeHeader(msgBuffer, 'h');
        msgBuffer.putLongLong(uint64_t(Duration(now())));

        contentSize = BUFSIZE - msgBuffer.available();
        msgBuffer.reset();
        routingKey = "mgmt." + systemId.str() + ".heartbeat";
        connThreadBody.sendBuffer(msgBuffer, contentSize, "qpid.management", routingKey);
    }

    moveNewObjectsLH();

    if (clientWasAdded)
    {
        clientWasAdded = false;
        for (ManagementObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++)
        {
            ManagementObject* object = iter->second;
            object->setAllChanged();
        }
    }

    if (managementObjects.empty())
        return;
        
    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         iter++)
    {
        ManagementObject* object = iter->second;

        if (object->getConfigChanged() || object->isDeleted())
        {
            Buffer msgBuffer(msgChars, BUFSIZE);
            EncodeHeader(msgBuffer, 'c');
            object->writeProperties(msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available();
            msgBuffer.reset();
            routingKey = "mgmt." + systemId.str() + ".prop." + object->getClassName();
            connThreadBody.sendBuffer(msgBuffer, contentSize, "qpid.management", routingKey);
        }
        
        if (object->getInstChanged())
        {
            Buffer msgBuffer(msgChars, BUFSIZE);
            EncodeHeader(msgBuffer, 'i');
            object->writeStatistics(msgBuffer);

            contentSize = BUFSIZE - msgBuffer.available();
            msgBuffer.reset();
            routingKey = "mgmt." + systemId.str() + ".stat." + object->getClassName();
            connThreadBody.sendBuffer(msgBuffer, contentSize, "qpid.management", routingKey);
        }

        if (object->isDeleted())
            deleteList.push_back(iter->first);
    }

    // Delete flagged objects
    for (std::list<ObjectId>::reverse_iterator iter = deleteList.rbegin();
         iter != deleteList.rend();
         iter++)
        managementObjects.erase(*iter);

    deleteList.clear();
}

void ManagementAgentImpl::ConnectionThread::run()
{
    static const int delayMin(1);
    static const int delayMax(128);
    static const int delayFactor(2);
    int delay(delayMin);
    string dest("qmfagent");

    sessionId.generate();
    queueName << "qmfagent-" << sessionId;

    while (true) {
        try {
            if (!agent.host.empty()) {
                connection.open(agent.host.c_str(), agent.port);
                session = connection.newSession(queueName.str());
                subscriptions = new client::SubscriptionManager(session);

                session.queueDeclare(arg::queue=queueName.str());
                session.exchangeBind(arg::exchange="amq.direct", arg::queue=queueName.str(),
                                     arg::bindingKey=queueName.str());

                subscriptions->subscribe(agent, queueName.str(), dest);
                {
                    Mutex::ScopedLock _lock(connLock);
                    operational = true;
                    agent.startProtocol();
                    try {
                        Mutex::ScopedUnlock _unlock(connLock);
                        subscriptions->run();
                    } catch (std::exception) {}

                    operational = false;
                }
                delay = delayMin;
                delete subscriptions;
                subscriptions = 0;
                session.close();
            }
        } catch (std::exception &e) {
            if (delay < delayMax)
                delay *= delayFactor;
        }

        ::sleep(delay);
    }
}

ManagementAgentImpl::ConnectionThread::~ConnectionThread()
{
    if (subscriptions != 0) {
        delete subscriptions;
    }
}

void ManagementAgentImpl::ConnectionThread::sendBuffer(Buffer&  buf,
                                                       uint32_t length,
                                                       string   exchange,
                                                       string   routingKey)
{
    {
        Mutex::ScopedLock _lock(connLock);
        if (!operational)
            return;
    }

    Message msg;
    string  data;

    buf.getRawData(data, length);
    msg.getDeliveryProperties().setRoutingKey(routingKey);
    msg.getMessageProperties().setReplyTo(ReplyTo("amq.direct", queueName.str()));
    msg.setData(data);
    session.messageTransfer(arg::content=msg, arg::destination=exchange);
}

void ManagementAgentImpl::ConnectionThread::bindToBank(uint32_t agentBank)
{
    stringstream key;
    key << "agent." << agentBank;
    session.exchangeBind(arg::exchange="qpid.management", arg::queue=queueName.str(),
                          arg::bindingKey=key.str());
}


void ManagementAgentImpl::PublishThread::run()
{
    while (true) {
        ::sleep(5);
        agent.PeriodicProcessing();
    }
}

Mutex& ManagementAgentImpl::getMutex()
{
    return agentLock;
}

Buffer* ManagementAgentImpl::startEventLH()
{
    Buffer* outBuffer(new Buffer(eventBuffer, MA_BUFFER_SIZE));
    EncodeHeader(*outBuffer, 'e');
    outBuffer->putLongLong(uint64_t(Duration(now())));
    return outBuffer;
}

void ManagementAgentImpl::finishEventLH(Buffer* outBuffer)
{
    uint32_t outLen = MA_BUFFER_SIZE - outBuffer->available();
    outBuffer->reset();
    connThreadBody.sendBuffer(*outBuffer, outLen, "qpid.management", "mgmt.event");
    delete outBuffer;
}
