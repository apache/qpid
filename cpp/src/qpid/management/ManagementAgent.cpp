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
 
#include "qpid/management/ManagementAgent.h"
#include "qpid/management/ManagementObject.h"
#include "qpid/management/IdAllocator.h"
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
#include <sstream>

using boost::intrusive_ptr;
using qpid::framing::Uuid;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace std;
namespace _qmf = qmf::org::apache::qpid::broker;

ManagementAgent::RemoteAgent::~RemoteAgent ()
{
    QPID_LOG(trace, "Remote Agent removed bank=[" << brokerBank << "." << agentBank << "]");
    if (mgmtObject != 0) {
        mgmtObject->resourceDestroy();
        agent.deleteObjectNowLH(mgmtObject->getObjectId());
    }
}

ManagementAgent::ManagementAgent () :
    threadPoolSize(1), interval(10), broker(0), timer(0),
    startTime(uint64_t(Duration(now()))),
    suppressed(false)
{
    nextObjectId   = 1;
    brokerBank     = 1;
    bootSequence   = 1;
    nextRemoteBank = 10;
    nextRequestSequence = 1;
    clientWasAdded = false;
}

ManagementAgent::~ManagementAgent ()
{
    {
        Mutex::ScopedLock lock (userLock);

        // Reset the shared pointers to exchanges.  If this is not done now, the exchanges
        // will stick around until dExchange and mExchange are implicitly destroyed (long
        // after this destructor completes).  Those exchanges hold references to management
        // objects that will be invalid.
        dExchange.reset();
        mExchange.reset();
        v2Topic.reset();
        v2Direct.reset();

        moveNewObjectsLH();
        for (ManagementObjectMap::iterator iter = managementObjects.begin ();
             iter != managementObjects.end ();
             iter++) {
            ManagementObject* object = iter->second;
            delete object;
        }
        managementObjects.clear();

        while (!deletedManagementObjects.empty()) {
            ManagementObject* object = deletedManagementObjects.back();
            delete object;
            deletedManagementObjects.pop_back();
        }
    }
}

void ManagementAgent::configure(const string& _dataDir, uint16_t _interval,
                                qpid::broker::Broker* _broker, int _threads)
{
    dataDir        = _dataDir;
    interval       = _interval;
    broker         = _broker;
    threadPoolSize = _threads;
    ManagementObject::maxThreads = threadPoolSize;

    // Get from file or generate and save to file.
    if (dataDir.empty())
    {
        uuid.generate();
        QPID_LOG (info, "ManagementAgent has no data directory, generated new broker ID: "
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
            QPID_LOG (debug, "ManagementAgent restored broker ID: " << uuid);

            // if sequence goes beyond a 12-bit field, skip zero and wrap to 1.
            bootSequence++;
            if (bootSequence & 0xF000)
                bootSequence = 1;
            writeData();
        }
        else
        {
            uuid.generate();
            QPID_LOG (info, "ManagementAgent generated broker ID: " << uuid);
            writeData();
        }

        QPID_LOG (debug, "ManagementAgent boot sequence: " << bootSequence);
    }
}

void ManagementAgent::pluginsInitialized() {
    // Do this here so cluster plugin has the chance to set up the timer.
    timer          = &broker->getClusterTimer();
    timer->add(new Periodic(*this, interval));
}

void ManagementAgent::writeData ()
{
    string   filename (dataDir + "/.mbrokerdata");
    ofstream outFile (filename.c_str ());

    if (outFile.good())
    {
        outFile << uuid << " " << bootSequence << " " << nextRemoteBank << endl;
        outFile.close();
    }
}

void ManagementAgent::setExchange(qpid::broker::Exchange::shared_ptr _mexchange,
                                  qpid::broker::Exchange::shared_ptr _dexchange)
{
    mExchange = _mexchange;
    dExchange = _dexchange;
}

void ManagementAgent::setExchangeV2(qpid::broker::Exchange::shared_ptr _texchange,
                                    qpid::broker::Exchange::shared_ptr _dexchange)
{
    v2Topic = _texchange;
    v2Direct = _dexchange;
}

void ManagementAgent::registerClass (const string&  packageName,
                                     const string&  className,
                                     uint8_t* md5Sum,
                                     ManagementObject::writeSchemaCall_t schemaCall)
{
    Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    addClassLH(ManagementItem::CLASS_KIND_TABLE, pIter, className, md5Sum, schemaCall);
}

void ManagementAgent::registerEvent (const string&  packageName,
                                     const string&  eventName,
                                     uint8_t* md5Sum,
                                     ManagementObject::writeSchemaCall_t schemaCall)
{
    Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    addClassLH(ManagementItem::CLASS_KIND_EVENT, pIter, eventName, md5Sum, schemaCall);
}

ObjectId ManagementAgent::addObject(ManagementObject* object, uint64_t persistId)
{
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
    objId.setV2Key(*object);
    object->setObjectId(objId);

    {
        Mutex::ScopedLock lock (addLock);
        ManagementObjectMap::iterator destIter = newManagementObjects.find(objId);
        if (destIter != newManagementObjects.end()) {
            if (destIter->second->isDeleted()) {
                newDeletedManagementObjects.push_back(destIter->second);
                newManagementObjects.erase(destIter);
            } else {
                QPID_LOG(error, "ObjectId collision in addObject. class=" << object->getClassName() <<
                         " key=" << objId.getV2Key());
                return objId;
            }
        }
        newManagementObjects[objId] = object;
    }

    return objId;
}

void ManagementAgent::raiseEvent(const ManagementEvent& event, severity_t severity)
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

ManagementAgent::Periodic::Periodic (ManagementAgent& _agent, uint32_t _seconds)
    : TimerTask (qpid::sys::Duration((_seconds ? _seconds : 1) * qpid::sys::TIME_SEC),
                 "ManagementAgent::periodicProcessing"),
      agent(_agent) {}

ManagementAgent::Periodic::~Periodic () {}

void ManagementAgent::Periodic::fire ()
{
    agent.timer->add (new Periodic (agent, agent.interval));
    agent.periodicProcessing ();
}

void ManagementAgent::clientAdded (const std::string& routingKey)
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
        QPID_LOG(trace, "SEND ConsoleAddedIndication to=" << aIter->second->routingKey);
    }
}

void ManagementAgent::clusterUpdate() {
    // Called on all cluster memebers when a new member joins a cluster.
    // Set clientWasAdded so that on the next periodicProcessing we will do 
    // a full update on all cluster members.
    clientWasAdded = true;
    QPID_LOG(debug, "cluster update " << debugSnapshot());
}

void ManagementAgent::encodeHeader (Buffer& buf, uint8_t opcode, uint32_t seq)
{
    buf.putOctet ('A');
    buf.putOctet ('M');
    buf.putOctet ('2');
    buf.putOctet (opcode);
    buf.putLong  (seq);
}

bool ManagementAgent::checkHeader (Buffer& buf, uint8_t *opcode, uint32_t *seq)
{
    uint8_t h1 = buf.getOctet();
    uint8_t h2 = buf.getOctet();
    uint8_t h3 = buf.getOctet();

    *opcode = buf.getOctet();
    *seq    = buf.getLong();

    return h1 == 'A' && h2 == 'M' && h3 == '2';
}

void ManagementAgent::sendBuffer(Buffer&  buf,
                                 uint32_t length,
                                 qpid::broker::Exchange::shared_ptr exchange,
                                 string   routingKey)
{
    if (suppressed) {
        QPID_LOG(trace, "Suppressed management message to " << routingKey);
        return;
    }
    if (exchange.get() == 0) return;

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

    DeliveryProperties* dp =
        msg->getFrames().getHeaders()->get<DeliveryProperties>(true);
    dp->setRoutingKey(routingKey);

    msg->getFrames().append(content);

    DeliverableMessage deliverable (msg);
    try {
        exchange->route(deliverable, routingKey, 0);
    } catch(exception&) {}
}

void ManagementAgent::moveNewObjectsLH()
{
    Mutex::ScopedLock lock (addLock);
    for (ManagementObjectMap::iterator iter = newManagementObjects.begin ();
         iter != newManagementObjects.end ();
         iter++) {
        bool skip = false;
        ManagementObjectMap::iterator destIter = managementObjects.find(iter->first);
        if (destIter != managementObjects.end()) {
            // We have an objectId collision with an existing object.  If the old object
            // is deleted, move it to the deleted list.
            if (destIter->second->isDeleted()) {
                deletedManagementObjects.push_back(destIter->second);
                managementObjects.erase(destIter);
            } else {
                QPID_LOG(error, "ObjectId collision in moveNewObjects. class=" <<
                         iter->second->getClassName() << " key=" << iter->first.getV2Key());
                skip = true;
            }
        }

        if (!skip)
            managementObjects[iter->first] = iter->second;
    }
    newManagementObjects.clear();

    while (!newDeletedManagementObjects.empty()) {
        deletedManagementObjects.push_back(newDeletedManagementObjects.back());
        newDeletedManagementObjects.pop_back();
    }
}

void ManagementAgent::periodicProcessing (void)
{
#define BUFSIZE   65536
#define HEADROOM  4096
    QPID_LOG(trace, "Management agent periodic processing")
        Mutex::ScopedLock lock (userLock);
    char                msgChars[BUFSIZE];
    uint32_t            contentSize;
    string              routingKey;
    list<pair<ObjectId, ManagementObject*> > deleteList;

    uint64_t uptime = uint64_t(Duration(now())) - startTime;
    static_cast<_qmf::Broker*>(broker->GetManagementObject())->set_uptime(uptime);

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
        uint32_t pcount = 0;
        uint32_t scount = 0;

        //
        //  Skip until we find a base object requiring a sent message.
        //
        if (baseObject->getFlags() == 1 ||
            (!baseObject->getConfigChanged() &&
             !baseObject->getInstChanged() &&
             !baseObject->getForcePublish() &&
             !baseObject->isDeleted()))
            continue;

        Buffer msgBuffer(msgChars, BUFSIZE);
        for (ManagementObjectMap::iterator iter = baseIter;
             iter != managementObjects.end();
             iter++) {
            ManagementObject* object = iter->second;
            if (baseObject->isSameClass(*object) && object->getFlags() == 0) {
                object->setFlags(1);
                if (object->getConfigChanged() || object->getInstChanged())
                    object->setUpdateTime();

                if (object->getConfigChanged() || object->getForcePublish() || object->isDeleted()) {
                    encodeHeader(msgBuffer, 'c');
                    object->writeProperties(msgBuffer);
                    pcount++;
                }
        
                if (object->hasInst() && (object->getInstChanged() || object->getForcePublish())) {
                    encodeHeader(msgBuffer, 'i');
                    object->writeStatistics(msgBuffer);
                    scount++;
                }

                if (object->isDeleted())
                    deleteList.push_back(pair<ObjectId, ManagementObject*>(iter->first, object));
                object->setForcePublish(false);

                if (msgBuffer.available() < HEADROOM)
                    break;
            }
        }

        contentSize = BUFSIZE - msgBuffer.available();
        if (contentSize > 0) {
            msgBuffer.reset();
            stringstream key;
            key << "console.obj.1.0." << baseObject->getPackageName() << "." << baseObject->getClassName();
            sendBuffer(msgBuffer, contentSize, mExchange, key.str());
            QPID_LOG(trace, "SEND Multicast ContentInd to=" << key.str() << " props=" << pcount << " stats=" << scount);
        }
    }

    // Delete flagged objects
    for (list<pair<ObjectId, ManagementObject*> >::reverse_iterator iter = deleteList.rbegin();
         iter != deleteList.rend();
         iter++) {
        delete iter->second;
        managementObjects.erase(iter->first);
    }

    // Publish the deletion of objects created by insert-collision
    bool collisionDeletions = false;
    for (ManagementObjectVector::iterator cdIter = deletedManagementObjects.begin();
         cdIter != deletedManagementObjects.end(); cdIter++) {
        collisionDeletions = true;
        Buffer msgBuffer(msgChars, BUFSIZE);
        encodeHeader(msgBuffer, 'c');
        (*cdIter)->writeProperties(msgBuffer);
        contentSize = BUFSIZE - msgBuffer.available ();
        msgBuffer.reset ();
        stringstream key;
        key << "console.obj.1.0." << (*cdIter)->getPackageName() << "." << (*cdIter)->getClassName();
        sendBuffer (msgBuffer, contentSize, mExchange, key.str());
        QPID_LOG(trace, "SEND ContentInd for deleted object to=" << key.str());
    }

    if (!deleteList.empty() || collisionDeletions) {
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
        QPID_LOG(trace, "SEND HeartbeatInd to=" << routingKey);
    }
    QPID_LOG(debug, "periodic update " << debugSnapshot());
}

void ManagementAgent::deleteObjectNowLH(const ObjectId& oid)
{
    ManagementObjectMap::iterator iter = managementObjects.find(oid);
    if (iter == managementObjects.end())
        return;
    ManagementObject* object = iter->second;
    if (!object->isDeleted())
        return;

#define DNOW_BUFSIZE 2048
    char     msgChars[DNOW_BUFSIZE];
    uint32_t contentSize;
    Buffer   msgBuffer(msgChars, DNOW_BUFSIZE);

    encodeHeader(msgBuffer, 'c');
    object->writeProperties(msgBuffer);
    contentSize = msgBuffer.getPosition();
    msgBuffer.reset();
    stringstream key;
    key << "console.obj.1.0." << object->getPackageName() << "." << object->getClassName();
    sendBuffer(msgBuffer, contentSize, mExchange, key.str());
    QPID_LOG(trace, "SEND Immediate(delete) ContentInd to=" << key.str());

    managementObjects.erase(oid);
}

void ManagementAgent::sendCommandComplete (string replyToKey, uint32_t sequence,
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
    QPID_LOG(trace, "SEND CommandCompleteInd code=" << code << " text=" << text << " to=" <<
             replyToKey << " seq=" << sequence);
}

bool ManagementAgent::dispatchCommand (Deliverable&      deliverable,
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

void ManagementAgent::handleMethodRequestLH (Buffer& inBuffer, string replyToKey,
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

    QPID_LOG(trace, "RECV MethodRequest class=" << packageName << ":" << className << "(" << Uuid(hash) << ") method=" <<
             methodName << " replyTo=" << replyToKey);

    encodeHeader(outBuffer, 'm', sequence);

    DisallowedMethods::const_iterator i = disallowed.find(std::make_pair(className, methodName));
    if (i != disallowed.end()) {
        outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
        outBuffer.putMediumString(i->second);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        sendBuffer(outBuffer, outLen, dExchange, replyToKey);
        QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN text=" << i->second << " seq=" << sequence)
            return;
    }

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
            QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN" << " seq=" << sequence)
                return;
        }
    }

    ManagementObjectMap::iterator iter = numericFind(objId);
    if (iter == managementObjects.end() || iter->second->isDeleted()) {
        outBuffer.putLong        (Manageable::STATUS_UNKNOWN_OBJECT);
        outBuffer.putMediumString(Manageable::StatusText (Manageable::STATUS_UNKNOWN_OBJECT));
    } else {
        if ((iter->second->getPackageName() != packageName) ||
            (iter->second->getClassName()   != className)) {
            outBuffer.putLong        (Manageable::STATUS_PARAMETER_INVALID);
            outBuffer.putMediumString(Manageable::StatusText (Manageable::STATUS_PARAMETER_INVALID));
        }
        else
            try {
                outBuffer.record();
                Mutex::ScopedUnlock u(userLock);
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
    QPID_LOG(trace, "SEND MethodResponse to=" << replyToKey << " seq=" << sequence);
}

void ManagementAgent::handleBrokerRequestLH (Buffer&, string replyToKey, uint32_t sequence)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    QPID_LOG(trace, "RECV BrokerRequest replyTo=" << replyToKey);

    encodeHeader (outBuffer, 'b', sequence);
    uuid.encode  (outBuffer);

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBuffer (outBuffer, outLen, dExchange, replyToKey);
    QPID_LOG(trace, "SEND BrokerResponse to=" << replyToKey);
}

void ManagementAgent::handlePackageQueryLH (Buffer&, string replyToKey, uint32_t sequence)
{
    QPID_LOG(trace, "RECV PackageQuery replyTo=" << replyToKey);

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
        QPID_LOG(trace, "SEND PackageInd package=" << (*pIter).first << " to=" << replyToKey << " seq=" << sequence);
    }

    sendCommandComplete (replyToKey, sequence);
}

void ManagementAgent::handlePackageIndLH (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string packageName;

    inBuffer.getShortString(packageName);

    QPID_LOG(trace, "RECV PackageInd package=" << packageName << " replyTo=" << replyToKey << " seq=" << sequence);

    findOrAddPackageLH(packageName);
}

void ManagementAgent::handleClassQueryLH(Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string packageName;

    inBuffer.getShortString(packageName);

    QPID_LOG(trace, "RECV ClassQuery package=" << packageName << " replyTo=" << replyToKey << " seq=" << sequence);

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
                QPID_LOG(trace, "SEND ClassInd class=" << (*pIter).first << ":" << (*cIter).first.name <<
                         "(" << Uuid((*cIter).first.hash) << ") to=" << replyToKey << " seq=" << sequence);
            }
        }
    }
    sendCommandComplete(replyToKey, sequence);
}

void ManagementAgent::handleClassIndLH (Buffer& inBuffer, string replyToKey, uint32_t)
{
    string packageName;
    SchemaClassKey key;

    uint8_t kind = inBuffer.getOctet();
    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);

    QPID_LOG(trace, "RECV ClassInd class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) <<
             "), replyTo=" << replyToKey);

    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    ClassMap::iterator   cIter = pIter->second.find(key);
    if (cIter == pIter->second.end() || !cIter->second.hasSchema()) {
        Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;
        uint32_t sequence = nextRequestSequence++;

        encodeHeader (outBuffer, 'S', sequence);
        outBuffer.putShortString(packageName);
        key.encode(outBuffer);
        outLen = MA_BUFFER_SIZE - outBuffer.available ();
        outBuffer.reset ();
        sendBuffer (outBuffer, outLen, dExchange, replyToKey);
        QPID_LOG(trace, "SEND SchemaRequest class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) <<
                 "), to=" << replyToKey << " seq=" << sequence);

        if (cIter != pIter->second.end())
            pIter->second.erase(key);

        pIter->second.insert(pair<SchemaClassKey, SchemaClass>(key, SchemaClass(kind, sequence)));
    }
}

void ManagementAgent::SchemaClass::appendSchema(Buffer& buf)
{
    // If the management package is attached locally (embedded in the broker or
    // linked in via plug-in), call the schema handler directly.  If the package
    // is from a remote management agent, send the stored schema information.

    if (writeSchemaCall != 0)
        writeSchemaCall(buf);
    else
        buf.putRawData(reinterpret_cast<uint8_t*>(&data[0]), data.size());
}

void ManagementAgent::handleSchemaRequestLH(Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    string         packageName;
    SchemaClassKey key;

    inBuffer.getShortString (packageName);
    key.decode(inBuffer);

    QPID_LOG(trace, "RECV SchemaRequest class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) <<
             "), replyTo=" << replyToKey << " seq=" << sequence);

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
                QPID_LOG(trace, "SEND SchemaResponse to=" << replyToKey << " seq=" << sequence);
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

void ManagementAgent::handleSchemaResponseLH(Buffer& inBuffer, string /*replyToKey*/, uint32_t sequence)
{
    string         packageName;
    SchemaClassKey key;

    inBuffer.record();
    inBuffer.getOctet();
    inBuffer.getShortString(packageName);
    key.decode(inBuffer);
    inBuffer.restore();

    QPID_LOG(trace, "RECV SchemaResponse class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) << ")" << " seq=" << sequence);

    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end()) {
        ClassMap& cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find(key);
        if (cIter != cMap.end() && cIter->second.pendingSequence == sequence) {
            size_t length = validateSchema(inBuffer, cIter->second.kind);
            if (length == 0) {
                QPID_LOG(warning, "Management Agent received invalid schema response: " << packageName << "." << key.name);
                cMap.erase(key);
            } else {
                cIter->second.data.resize(length);
                inBuffer.getRawData(reinterpret_cast<uint8_t*>(&cIter->second.data[0]), length);

                // Publish a class-indication message
                Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
                uint32_t outLen;

                encodeHeader(outBuffer, 'q');
                encodeClassIndication(outBuffer, pIter, cIter);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                sendBuffer(outBuffer, outLen, mExchange, "schema.class");
                QPID_LOG(trace, "SEND ClassInd class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) << ")" <<
                         " to=schema.class");
            }
        }
    }
}

bool ManagementAgent::bankInUse (uint32_t bank)
{
    for (RemoteAgentMap::iterator aIter = remoteAgents.begin();
         aIter != remoteAgents.end();
         aIter++)
        if (aIter->second->agentBank == bank)
            return true;
    return false;
}

uint32_t ManagementAgent::allocateNewBank ()
{
    while (bankInUse (nextRemoteBank))
        nextRemoteBank++;

    uint32_t allocated = nextRemoteBank++;
    writeData ();
    return allocated;
}

uint32_t ManagementAgent::assignBankLH (uint32_t requestedBank)
{
    if (requestedBank == 0 || bankInUse (requestedBank))
        return allocateNewBank ();
    return requestedBank;
}

void ManagementAgent::deleteOrphanedAgentsLH()
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

void ManagementAgent::handleAttachRequestLH (Buffer& inBuffer, string replyToKey, uint32_t sequence, const ConnectionToken* connToken)
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

    QPID_LOG(trace, "RECV (Agent)AttachRequest label=" << label << " reqBrokerBank=" << requestedBrokerBank <<
             " reqAgentBank=" << requestedAgentBank << " replyTo=" << replyToKey << " seq=" << sequence);

    assignedBank = assignBankLH(requestedAgentBank);

    RemoteAgent* agent = new RemoteAgent(*this);
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
    addObject (agent->mgmtObject, 0);
    remoteAgents[connectionRef] = agent;

    QPID_LOG(trace, "Remote Agent registered bank=[" << brokerBank << "." << assignedBank << "] replyTo=" << replyToKey);

    // Send an Attach Response
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader (outBuffer, 'a', sequence);
    outBuffer.putLong (brokerBank);
    outBuffer.putLong (assignedBank);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBuffer (outBuffer, outLen, dExchange, replyToKey);
    QPID_LOG(trace, "SEND AttachResponse brokerBank=" << brokerBank << " agentBank=" << assignedBank <<
             " to=" << replyToKey << " seq=" << sequence);
}

void ManagementAgent::handleGetQueryLH (Buffer& inBuffer, string replyToKey, uint32_t sequence)
{
    FieldTable           ft;
    FieldTable::ValuePtr value;

    moveNewObjectsLH();

    ft.decode(inBuffer);

    QPID_LOG(trace, "RECV GetQuery query=" << ft << " seq=" << sequence);

    value = ft.get("_class");
    if (value.get() == 0 || !value->convertsTo<string>()) {
        value = ft.get("_objectid");
        if (value.get() == 0 || !value->convertsTo<string>())
            return;

        ObjectId selector(value->get<string>());
        ManagementObjectMap::iterator iter = numericFind(selector);
        if (iter != managementObjects.end()) {
            ManagementObject* object = iter->second;
            Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            if (object->getConfigChanged() || object->getInstChanged())
                object->setUpdateTime();

            if (!object->isDeleted()) {
                encodeHeader(outBuffer, 'g', sequence);
                object->writeProperties(outBuffer);
                object->writeStatistics(outBuffer, true);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                sendBuffer(outBuffer, outLen, dExchange, replyToKey);
                QPID_LOG(trace, "SEND GetResponse to=" << replyToKey << " seq=" << sequence);
            }
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

            if (object->getConfigChanged() || object->getInstChanged())
                object->setUpdateTime();

            if (!object->isDeleted()) {
                encodeHeader(outBuffer, 'g', sequence);
                object->writeProperties(outBuffer);
                object->writeStatistics(outBuffer, true);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                sendBuffer(outBuffer, outLen, dExchange, replyToKey);
                QPID_LOG(trace, "SEND GetResponse to=" << replyToKey << " seq=" << sequence);
            }
        }
    }

    sendCommandComplete(replyToKey, sequence);
}

bool ManagementAgent::authorizeAgentMessageLH(Message& msg)
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
            QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN" << " seq=" << sequence)
                }

        return false;
    }

    return true;
}

void ManagementAgent::dispatchAgentCommandLH(Message& msg)
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
        QPID_LOG(debug, "ManagementAgent::dispatchAgentCommandLH: Message too large: " <<
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

ManagementAgent::PackageMap::iterator ManagementAgent::findOrAddPackageLH(string name)
{
    PackageMap::iterator pIter = packages.find (name);
    if (pIter != packages.end ())
        return pIter;

    // No such package found, create a new map entry.
    pair<PackageMap::iterator, bool> result =
        packages.insert(pair<string, ClassMap>(name, ClassMap()));
    QPID_LOG (debug, "ManagementAgent added package " << name);

    // Publish a package-indication message
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader (outBuffer, 'p');
    encodePackageIndication (outBuffer, result.first);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBuffer (outBuffer, outLen, mExchange, "schema.package");
    QPID_LOG(trace, "SEND PackageInd package=" << name << " to=schema.package")

        return result.first;
}

void ManagementAgent::addClassLH(uint8_t               kind,
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
    QPID_LOG (debug, "ManagementAgent added class " << pIter->first << ":" <<
              key.name);

    cMap.insert(pair<SchemaClassKey, SchemaClass>(key, SchemaClass(kind, schemaCall)));
    cIter = cMap.find(key);
}

void ManagementAgent::encodePackageIndication(Buffer&              buf,
                                              PackageMap::iterator pIter)
{
    buf.putShortString((*pIter).first);
}

void ManagementAgent::encodeClassIndication(Buffer&              buf,
                                            PackageMap::iterator pIter,
                                            ClassMap::iterator   cIter)
{
    SchemaClassKey key = (*cIter).first;

    buf.putOctet((*cIter).second.kind);
    buf.putShortString((*pIter).first);
    key.encode(buf);
}

size_t ManagementAgent::validateSchema(Buffer& inBuffer, uint8_t kind)
{
    if      (kind == ManagementItem::CLASS_KIND_TABLE)
        return validateTableSchema(inBuffer);
    else if (kind == ManagementItem::CLASS_KIND_EVENT)
        return validateEventSchema(inBuffer);
    return 0;
}

size_t ManagementAgent::validateTableSchema(Buffer& inBuffer)
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

        uint8_t superType = 0; //inBuffer.getOctet();      

        uint16_t propCount = inBuffer.getShort();
        uint16_t statCount = inBuffer.getShort();
        uint16_t methCount = inBuffer.getShort();

        if (superType == 1) {
            inBuffer.getShortString(text);
            inBuffer.getShortString(text);
            inBuffer.getBin128(hash);
        }
        
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

size_t ManagementAgent::validateEventSchema(Buffer& inBuffer)
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
        
        uint8_t superType = inBuffer.getOctet();

        uint16_t argCount = inBuffer.getShort();

        if (superType == 1) {
            inBuffer.getShortString(text);
            inBuffer.getShortString(text);
            inBuffer.getBin128(hash);
        }
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

ManagementObjectMap::iterator ManagementAgent::numericFind(const ObjectId& oid)
{
    ManagementObjectMap::iterator iter = managementObjects.begin();
    for (; iter != managementObjects.end(); iter++) {
        if (oid.equalV1(iter->first))
            break;
    }

    return iter;
}


void ManagementAgent::setAllocator(std::auto_ptr<IdAllocator> a)
{
    Mutex::ScopedLock lock (userLock);
    allocator = a;
}

uint64_t ManagementAgent::allocateId(Manageable* object)
{
    Mutex::ScopedLock lock (userLock);
    if (allocator.get()) return allocator->getIdFor(object);
    return 0;
}

void ManagementAgent::disallow(const std::string& className, const std::string& methodName, const std::string& message) {
    disallowed[std::make_pair(className, methodName)] = message;
}

void ManagementAgent::SchemaClassKey::encode(qpid::framing::Buffer& buffer) const {
    buffer.checkAvailable(encodedSize());
    buffer.putShortString(name);
    buffer.putBin128(hash);
}

void ManagementAgent::SchemaClassKey::decode(qpid::framing::Buffer& buffer) {
    buffer.checkAvailable(encodedSize());
    buffer.getShortString(name);
    buffer.getBin128(hash);
}

uint32_t ManagementAgent::SchemaClassKey::encodedSize() const {
    return 1 + name.size() + 16 /* bin128 */;
}

void ManagementAgent::SchemaClass::encode(qpid::framing::Buffer& outBuf) const {
    outBuf.checkAvailable(encodedSize());
    outBuf.putOctet(kind);
    outBuf.putLong(pendingSequence);
    outBuf.putLongString(data);
}

void ManagementAgent::SchemaClass::decode(qpid::framing::Buffer& inBuf) {
    inBuf.checkAvailable(encodedSize());
    kind = inBuf.getOctet();
    pendingSequence = inBuf.getLong();
    inBuf.getLongString(data);
}

uint32_t ManagementAgent::SchemaClass::encodedSize() const {
    return sizeof(uint8_t) + sizeof(uint32_t) + sizeof(uint32_t) + data.size();
}

void ManagementAgent::exportSchemas(std::string& out) {
    out.clear();
    for (PackageMap::const_iterator i = packages.begin(); i != packages.end(); ++i) {
        string name = i->first;
        const ClassMap& classes = i ->second;
        for (ClassMap::const_iterator j = classes.begin(); j != classes.end(); ++j) {
            const SchemaClassKey& key = j->first;
            const SchemaClass& klass = j->second;
            if (klass.writeSchemaCall == 0) { // Ignore built-in schemas.
                // Encode name, schema-key, schema-class
                size_t encodedSize = 1+name.size()+key.encodedSize()+klass.encodedSize();
                size_t end = out.size();
                out.resize(end + encodedSize);
                framing::Buffer outBuf(&out[end], encodedSize);
                outBuf.putShortString(name);
                key.encode(outBuf);
                klass.encode(outBuf);
            }
        }
    }
}

void ManagementAgent::importSchemas(qpid::framing::Buffer& inBuf) {
    while (inBuf.available()) {
        string package;
        SchemaClassKey key;
        SchemaClass klass;
        inBuf.getShortString(package);
        key.decode(inBuf);
        klass.decode(inBuf);
        packages[package][key] = klass;
    }
}

void ManagementAgent::RemoteAgent::encode(qpid::framing::Buffer& outBuf) const {
    outBuf.checkAvailable(encodedSize());
    outBuf.putLong(brokerBank);
    outBuf.putLong(agentBank);
    outBuf.putShortString(routingKey);
    // TODO aconway 2010-03-04: we send the v2Key instead of the
    // ObjectId because that has the same meaning on different
    // brokers. ObjectId::encode doesn't currently encode the v2Key,
    // this can be cleaned up when it does.
    outBuf.putMediumString(connectionRef.getV2Key());
    mgmtObject->writeProperties(outBuf);
}

void ManagementAgent::RemoteAgent::decode(qpid::framing::Buffer& inBuf) {
    brokerBank = inBuf.getLong();
    agentBank = inBuf.getLong();
    inBuf.getShortString(routingKey);

    // TODO aconway 2010-03-04: see comment in encode()
    string connectionKey;
    inBuf.getMediumString(connectionKey);
    connectionRef = ObjectId(); // Clear out any existing value.
    connectionRef.setV2Key(connectionKey);

    mgmtObject = new _qmf::Agent(&agent, this);
    mgmtObject->readProperties(inBuf);
    // TODO aconway 2010-03-04: see comment in encode(), readProperties doesn't set v2key.
    mgmtObject->set_connectionRef(connectionRef);
}

uint32_t ManagementAgent::RemoteAgent::encodedSize() const {
    // TODO aconway 2010-03-04: see comment in encode()
    return sizeof(uint32_t) + sizeof(uint32_t) // 2 x Long
        + routingKey.size() + sizeof(uint8_t) // ShortString
        + connectionRef.getV2Key().size() + sizeof(uint16_t) // medium string
        + mgmtObject->writePropertiesSize();
}

void ManagementAgent::exportAgents(std::string& out) {
    out.clear();
    for (RemoteAgentMap::const_iterator i = remoteAgents.begin();
         i != remoteAgents.end();
         ++i)
    {
        // TODO aconway 2010-03-04: see comment in ManagementAgent::RemoteAgent::encode
        RemoteAgent* agent = i->second;
        size_t encodedSize = agent->encodedSize();
        size_t end = out.size();
        out.resize(end + encodedSize);
        framing::Buffer outBuf(&out[end], encodedSize);
        agent->encode(outBuf);
    }
}

void ManagementAgent::importAgents(qpid::framing::Buffer& inBuf) {
    while (inBuf.available()) {
        std::auto_ptr<RemoteAgent> agent(new RemoteAgent(*this));
        agent->decode(inBuf);
        addObject(agent->mgmtObject, 0);
        remoteAgents[agent->connectionRef] = agent.release();
    }
}

std::string ManagementAgent::debugSnapshot() {
    std::ostringstream msg;
    msg << " management snapshot:";
    for (RemoteAgentMap::const_iterator i=remoteAgents.begin();
         i != remoteAgents.end(); ++i)
        msg << " " << i->second->routingKey;
    msg << " packages: " << packages.size();
    msg << " objects: " << managementObjects.size();
    msg << " new objects: " << newManagementObjects.size();
    return msg.str();
}
