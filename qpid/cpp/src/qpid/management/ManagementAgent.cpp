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
#include "qpid/messaging/Variant.h"
#include "qpid/messaging/Uuid.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/ListContent.h"
#include "qpid/messaging/ListView.h"
#include "qpid/framing/List.h"
#include <list>
#include <iostream>
#include <fstream>
#include <sstream>
#include <typeinfo>

using boost::intrusive_ptr;
using qpid::framing::Uuid;
using qpid::messaging::Variant;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::broker;
using namespace qpid::sys;
using namespace qpid;
using namespace std;
namespace _qmf = qmf::org::apache::qpid::broker;



static qpid::messaging::Variant::Map mapEncodeSchemaId(const std::string& pname,
                                                       const std::string& cname,
                                                       const std::string& type,
                                                       const uint8_t *md5Sum)
{
    qpid::messaging::Variant::Map map_;

    map_["_package_name"] = pname;
    map_["_class_name"] = cname;
    map_["_type"] = type;
    map_["_hash"] = qpid::messaging::Uuid(md5Sum);
    return map_;
}


ManagementAgent::RemoteAgent::~RemoteAgent ()
{
    QPID_LOG(trace, "Remote Agent removed bank=[" << brokerBank << "." << agentBank << "]");
    if (mgmtObject != 0) {
        mgmtObject->resourceDestroy();
        agent.deleteObjectNowLH(mgmtObject->getObjectId());
    }
}

ManagementAgent::ManagementAgent (const bool qmfV1, const bool qmfV2) :
    threadPoolSize(1), interval(10), broker(0), timer(0),
    startTime(uint64_t(Duration(now()))),
    suppressed(false),
    qmf1Support(qmfV1), qmf2Support(qmfV2)
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


void ManagementAgent::setName(const string& vendor, const string& product, const string& instance)
{
    attrMap["_vendor"] = vendor;
    attrMap["_product"] = product;
    string inst;
    if (instance.empty()) {
        if (uuid.isNull())
        {
            throw Exception("ManagementAgent::configure() must be called if default name is used.");
        }
        inst = uuid.str();
    } else
        inst = instance;

   name_address = vendor + ":" + product + ":" + inst;
   attrMap["_instance"] = inst;
   attrMap["_name"] = name_address;
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

// Deprecated:  V1 objects
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

    ObjectId objId(0 /*flags*/, sequence, brokerBank, objectNum);
    objId.setV2Key(*object);   // let object generate the v2 key

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



ObjectId ManagementAgent::addObject(ManagementObject* object,
                                    const std::string& key,
                                    bool persistent)
{
    uint16_t sequence;

    sequence = persistent ? 0 : bootSequence;

    ObjectId objId(0 /*flags*/, sequence, brokerBank);
    if (key.empty()) {
        objId.setV2Key(*object);   // let object generate the key
    } else {
        objId.setV2Key(key);
    }

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
    uint8_t sev = (severity == SEV_DEFAULT) ? event.getSeverity() : (uint8_t) severity;

    if (qmf1Support) {
        Buffer outBuffer(eventBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        encodeHeader(outBuffer, 'e');
        outBuffer.putShortString(event.getPackageName());
        outBuffer.putShortString(event.getEventName());
        outBuffer.putBin128(event.getMd5Sum());
        outBuffer.putLongLong(uint64_t(Duration(now())));
        outBuffer.putOctet(sev);
        std::string sBuf;
        event.encode(sBuf);
        outBuffer.putRawData(sBuf);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        sendBuffer(outBuffer, outLen, mExchange,
                   "console.event.1.0." + event.getPackageName() + "." + event.getEventName());
        QPID_LOG(trace, "SEND raiseEvent (v1) class=" << event.getPackageName() << "." << event.getEventName());
    }

    if (qmf2Support) {
        ::qpid::messaging::Message msg;
        ::qpid::messaging::MapContent content(msg);
        ::qpid::messaging::VariantMap &map_ = content.asMap();
        ::qpid::messaging::VariantMap schemaId;
        ::qpid::messaging::VariantMap values;
        ::qpid::messaging::VariantMap headers;

        map_["_schema_id"] = mapEncodeSchemaId(event.getPackageName(),
                                               event.getEventName(),
                                               "_event",
                                               event.getMd5Sum());
        event.mapEncode(values);
        map_["_values"] = values;
        map_["_timestamp"] = uint64_t(Duration(now()));
        map_["_severity"] = sev;

        headers["method"] = "indication";
        headers["qmf.opcode"] = "_data_indication";
        headers["qmf.content"] = "_event";
        headers["qmf.agent"] = name_address;

        stringstream key;
        key << "agent.ind.event." << sev << "." << name_address << "." << event.getEventName();

        content.encode();
        sendBuffer(msg.getContent(), "", headers, v2Topic, key.str());
        QPID_LOG(trace, "SEND raiseEvent (v2) class=" << event.getPackageName() << "." << event.getEventName());
    }

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


void ManagementAgent::sendBuffer(const std::string& data,
                                 const std::string& cid,
                                 const qpid::messaging::VariantMap& headers,
                                 qpid::broker::Exchange::shared_ptr exchange,
                                 const std::string& routingKey)
{
    qpid::messaging::VariantMap::const_iterator i;

    if (suppressed) {
        QPID_LOG(trace, "Suppressed management message to " << routingKey);
        return;
    }
    if (exchange.get() == 0) return;

    intrusive_ptr<Message> msg(new Message());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), exchange->getName (), 0, 0)));
    AMQFrame header((AMQHeaderBody()));
    AMQFrame content((AMQContentBody(data)));

    method.setEof(false);
    header.setBof(false);
    header.setEof(false);
    content.setBof(false);

    msg->getFrames().append(method);
    msg->getFrames().append(header);

    MessageProperties* props =
        msg->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setContentLength(data.length());
    if (!cid.empty()) {
        props->setCorrelationId(cid);
    }

    for (i = headers.begin(); i != headers.end(); ++i) {
        msg->getOrInsertHeaders().setString(i->first, i->second.asString());
    }
    msg->getOrInsertHeaders().setString("app_id", "qmf2");

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
    QPID_LOG(trace, "Management agent periodic processing");
    Mutex::ScopedLock lock (userLock);
    char                msgChars[BUFSIZE];
    uint32_t            contentSize;
    string              routingKey;
    list<pair<ObjectId, ManagementObject*> > deleteList;
    std::string sBuf;

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

                if (send_props && qmf1Support) {
                    encodeHeader(msgBuffer, 'c');
                    sBuf.clear();
                    object->writeProperties(sBuf);
                    msgBuffer.putRawData(sBuf);
                }

                if (send_stats && qmf1Support) {
                    encodeHeader(msgBuffer, 'i');
                    sBuf.clear();
                    object->writeStatistics(sBuf);
                    msgBuffer.putRawData(sBuf);
                }

                if ((send_stats || send_props) && qmf2Support) {
                    ::qpid::messaging::Variant::Map  map_;
                    ::qpid::messaging::Variant::Map values;

                    map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                           object->getClassName(),
                                                           "_data",
                                                           object->getMd5Sum());
                    object->mapEncodeValues(values, send_props, send_stats);
                    map_["_values"] = values;
                    list_.push_back(map_);

                }

                if (send_props) pcount++;
                if (send_stats) scount++;

                if (object->isDeleted())
                    deleteList.push_back(pair<ObjectId, ManagementObject*>(iter->first, object));
                object->setForcePublish(false);

                if (qmf1Support && (msgBuffer.available() < HEADROOM))
                    break;
            }
        }

        if (pcount || scount) {
            if (qmf1Support) {
                contentSize = BUFSIZE - msgBuffer.available();
                if (contentSize > 0) {
                    msgBuffer.reset();
                    stringstream key;
                    key << "console.obj.1.0." << baseObject->getPackageName() << "." << baseObject->getClassName();
                    sendBuffer(msgBuffer, contentSize, mExchange, key.str());
                    QPID_LOG(trace, "SEND Multicast ContentInd to=" << key.str() << " props=" << pcount << " stats=" << scount);
                }
            }

            if (qmf2Support) {
                content.encode();
                const std::string &body = m.getContent();
                if (body.length()) {
                    stringstream key;
                    ::qpid::messaging::Variant::Map  headers;
                    key << "agent.ind.data." << baseObject->getPackageName() << "." << baseObject->getClassName();
                    // key << "console.obj.1.0." << baseObject->getPackageName() << "." << baseObject->getClassName();
                    headers["method"] = "indication";
                    headers["qmf.opcode"] = "_data_indication";
                    headers["qmf.content"] = "_data";
                    headers["qmf.agent"] = name_address;

                    sendBuffer(body, "", headers, v2Topic, key.str());
                    QPID_LOG(trace, "SEND Multicast ContentInd to=" << key.str() << " props=" << pcount << " stats=" << scount);
                }
            }
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
        {
            if (qmf1Support) {
                Buffer msgBuffer(msgChars, BUFSIZE);
                encodeHeader(msgBuffer, 'c');
                sBuf.clear();
                (*cdIter)->writeProperties(sBuf);
                msgBuffer.putRawData(sBuf);
                contentSize = BUFSIZE - msgBuffer.available ();
                msgBuffer.reset ();
                stringstream key;
                key << "console.obj.1.0." << (*cdIter)->getPackageName() << "." << (*cdIter)->getClassName();
                sendBuffer (msgBuffer, contentSize, mExchange, key.str());
                QPID_LOG(trace, "SEND ContentInd for deleted object to=" << key.str());
            }

            if (qmf2Support) {
                ::qpid::messaging::Message m;
                ::qpid::messaging::ListContent content(m);
                ::qpid::messaging::Variant::List &list_ = content.asList();
                ::qpid::messaging::Variant::Map  map_;
                ::qpid::messaging::Variant::Map values;
                ::qpid::messaging::Variant::Map  headers;

                map_["_schema_id"] = mapEncodeSchemaId((*cdIter)->getPackageName(),
                                                       (*cdIter)->getClassName(),
                                                       "_data",
                                                       (*cdIter)->getMd5Sum());
                (*cdIter)->mapEncodeValues(values, true, false);
                map_["_values"] = values;
                list_.push_back(map_);

                headers["method"] = "indication";
                headers["qmf.opcode"] = "_data_indication";
                headers["qmf.content"] = "_data";
                headers["qmf.agent"] = name_address;

                stringstream key;
                key << "agent.ind.data." << (*cdIter)->getPackageName() << "." << (*cdIter)->getClassName();

                content.encode();
                sendBuffer(m.getContent(), "", headers, v2Topic, key.str());
                QPID_LOG(trace, "SEND ContentInd for deleted object to=" << key.str());
            }
        }
    }

    if (!deleteList.empty() || collisionDeletions) {
        deleteList.clear();
        deleteOrphanedAgentsLH();
    }

    // heartbeat generation

    if (qmf1Support) {
#define BUFSIZE   65536
        uint32_t            contentSize;
        char                msgChars[BUFSIZE];
        Buffer msgBuffer(msgChars, BUFSIZE);
        encodeHeader(msgBuffer, 'h');
        msgBuffer.putLongLong(uint64_t(Duration(now())));

        contentSize = BUFSIZE - msgBuffer.available ();
        msgBuffer.reset ();
        routingKey = "console.heartbeat.1.0";
        sendBuffer (msgBuffer, contentSize, mExchange, routingKey);
        QPID_LOG(trace, "SEND HeartbeatInd to=" << routingKey);
    }

    if (qmf2Support) {
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
        sendBuffer(msg.getContent(), "", headers, v2Topic, addr_key);

        QPID_LOG(trace, "SENT AgentHeartbeat name=" << name_address);
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

    if (qmf1Support) {
#define DNOW_BUFSIZE 2048
        char     msgChars[DNOW_BUFSIZE];
        uint32_t contentSize;
        Buffer   msgBuffer(msgChars, DNOW_BUFSIZE);
        std::string sBuf;

        encodeHeader(msgBuffer, 'c');
        object->writeProperties(sBuf);
        msgBuffer.putRawData(sBuf);
        contentSize = msgBuffer.getPosition();
        msgBuffer.reset();
        stringstream key;
        key << "console.obj.1.0." << object->getPackageName() << "." << object->getClassName();
        sendBuffer(msgBuffer, contentSize, mExchange, key.str());
        QPID_LOG(trace, "SEND Immediate(delete) ContentInd to=" << key.str());
    }

    if (qmf2Support) {
        ::qpid::messaging::Message m;
        ::qpid::messaging::ListContent content(m);
        ::qpid::messaging::Variant::List &list_ = content.asList();
        ::qpid::messaging::Variant::Map  map_;
        ::qpid::messaging::Variant::Map values;

        map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                               object->getClassName(),
                                               "_data",
                                               object->getMd5Sum());
        object->mapEncodeValues(values, true, false);
        map_["_values"] = values;
        list_.push_back(map_);

        stringstream key;
        key << "agent.ind.data." << object->getPackageName() << "." << object->getClassName();

        ::qpid::messaging::Variant::Map  headers;
        headers["method"] = "indication";
        headers["qmf.opcode"] = "_data_indication";
        headers["qmf.content"] = "_data";
        headers["qmf.agent"] = name_address;

        content.encode();
        sendBuffer(m.getContent(), "", headers, v2Topic, key.str());
        QPID_LOG(trace, "SEND Immediate(delete) ContentInd to=" << key.str());
    }

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
    std::string inArgs;

    std::string sBuf;
    inBuffer.getRawData(sBuf, 16);
    ObjectId objId;
    objId.decode(sBuf);
    inBuffer.getShortString(packageName);
    inBuffer.getShortString(className);
    inBuffer.getBin128(hash);
    inBuffer.getShortString(methodName);
    inBuffer.getRawData(inArgs, inBuffer.available());

    QPID_LOG(trace, "RECV MethodRequest (v1) class=" << packageName << ":" << className << "(" << Uuid(hash) << ") method=" <<
             methodName << " replyTo=" << replyToKey);

    encodeHeader(outBuffer, 'm', sequence);

    DisallowedMethods::const_iterator i = disallowed.find(std::make_pair(className, methodName));
    if (i != disallowed.end()) {
        outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
        outBuffer.putMediumString(i->second);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        sendBuffer(outBuffer, outLen, dExchange, replyToKey);
        QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN text=" << i->second << " seq=" << sequence);
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
            QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN" << " seq=" << sequence);
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
                std::string outBuf;
                iter->second->doMethod(methodName, inArgs, outBuf);
                outBuffer.putRawData(outBuf);
            } catch(exception& e) {
                outBuffer.restore();
                outBuffer.putLong(Manageable::STATUS_EXCEPTION);
                outBuffer.putMediumString(e.what());
            }
    }

    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    sendBuffer(outBuffer, outLen, dExchange, replyToKey);
    QPID_LOG(trace, "SEND MethodResponse (v1) to=" << replyToKey << " seq=" << sequence);
}


void ManagementAgent::handleMethodRequestLH (const std::string& body, string replyTo,
                                             const std::string& cid, const ConnectionToken* connToken)
{
    string   methodName;
    qpid::messaging::Message inMsg(body);
    qpid::messaging::MapView inMap(inMsg);
    qpid::messaging::MapView::const_iterator oid, mid;

    qpid::messaging::Message outMsg;
    qpid::messaging::MapContent outMap(outMsg);
    qpid::messaging::Variant::Map headers;

    headers["method"] = "response";
    headers["qmf.opcode"] = "_method_response";
    headers["qmf.agent"] = name_address;

    if ((oid = inMap.find("_object_id")) == inMap.end() ||
        (mid = inMap.find("_method_name")) == inMap.end())
    {
        headers["qmf.opcode"] = "_exception";
        (outMap["_values"].asMap())["_status"] = Manageable::STATUS_PARAMETER_INVALID;
        (outMap["_values"].asMap())["_status_text"] = Manageable::StatusText(Manageable::STATUS_PARAMETER_INVALID);
        outMap.encode();
        sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
        QPID_LOG(trace, "SEND MethodResponse (invalid param) to=" << replyTo << " seq=" << cid);
        return;
    }

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
    } catch(exception& e) {
        headers["qmf.opcode"] = "_exception";
        (outMap["_values"].asMap())["_status"] = Manageable::STATUS_EXCEPTION;
        (outMap["_values"].asMap())["_status_text"] = e.what();
        outMap.encode();
        sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
        QPID_LOG(trace, "SEND MethodResponse (invalid format) to=" << replyTo << " seq=" << cid);
        return;
    }

    ManagementObjectMap::iterator iter = managementObjects.find(objId);

    if (iter == managementObjects.end() || iter->second->isDeleted()) {
        headers["qmf.opcode"] = "_exception";
        (outMap["_values"].asMap())["_status"] = Manageable::STATUS_UNKNOWN_OBJECT;
        (outMap["_values"].asMap())["_status_text"] = Manageable::StatusText(Manageable::STATUS_UNKNOWN_OBJECT);
        outMap.encode();
        sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
        QPID_LOG(trace, "SEND MethodResponse (unknown object) to=" << replyTo << " seq=" << cid);
        return;
    }

    // validate
    AclModule* acl = broker->getAcl();
    DisallowedMethods::const_iterator i;

    i = disallowed.find(std::make_pair(iter->second->getClassName(), methodName));
    if (i != disallowed.end()) {
        headers["qmf.opcode"] = "_exception";
        (outMap["_values"].asMap())["_status"] = Manageable::STATUS_FORBIDDEN;
        (outMap["_values"].asMap())["_status_text"] = i->second;
        outMap.encode();
        sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
        QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN text=" << i->second << " seq=" << cid);
        return;
    }

    if (acl != 0) {
        string userId = ((const qpid::broker::ConnectionState*) connToken)->getUserId();
        map<acl::Property, string> params;
        params[acl::PROP_SCHEMAPACKAGE] = iter->second->getPackageName();
        params[acl::PROP_SCHEMACLASS]   = iter->second->getClassName();

        if (!acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_METHOD, methodName, &params)) {
            headers["qmf.opcode"] = "_exception";
            (outMap["_values"].asMap())["_status"] = Manageable::STATUS_FORBIDDEN;
            (outMap["_values"].asMap())["_status_text"] = Manageable::StatusText(Manageable::STATUS_FORBIDDEN);
            outMap.encode();
            sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
            QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN" << " seq=" << cid);
            return;
        }
    }

    // invoke the method

    QPID_LOG(trace, "RECV MethodRequest (v2) class=" << iter->second->getPackageName()
             << ":" << iter->second->getClassName() << " method=" <<
             methodName << " replyTo=" << replyTo);

    try {
        iter->second->doMethod(methodName, inArgs, outMap.asMap());
    } catch(exception& e) {
        outMap.clear();
        headers["qmf.opcode"] = "_exception";
        (outMap["_values"].asMap())["_status"] = Manageable::STATUS_EXCEPTION;
        (outMap["_values"].asMap())["_status_text"] = e.what();
        outMap.encode();
        sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
        QPID_LOG(trace, "SEND MethodResponse (exception) to=" << replyTo << " seq=" << cid);
        return;
    }

    outMap.encode();
    sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
    QPID_LOG(trace, "SEND MethodResponse (v2) to=" << replyTo << " seq=" << cid);
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

        // Schema Request
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

    if (writeSchemaCall != 0) {
        std::string schema;
        writeSchemaCall(schema);
        buf.putRawData(schema);
    } else
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
    agent->mgmtObject->set_systemId     ((const unsigned char*)systemId.data());
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

    QPID_LOG(trace, "RECV GetQuery (v1) query=" << ft << " seq=" << sequence);

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
                std::string sBuf;
                encodeHeader(outBuffer, 'g', sequence);
                object->writeProperties(sBuf);
                outBuffer.putRawData(sBuf);
                sBuf.clear();
                object->writeStatistics(sBuf, true);
                outBuffer.putRawData(sBuf);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                sendBuffer(outBuffer, outLen, dExchange, replyToKey);
                QPID_LOG(trace, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
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
                std::string sBuf;
                encodeHeader(outBuffer, 'g', sequence);
                object->writeProperties(sBuf);
                outBuffer.putRawData(sBuf);
                sBuf.clear();
                object->writeStatistics(sBuf, true);
                outBuffer.putRawData(sBuf);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                sendBuffer(outBuffer, outLen, dExchange, replyToKey);
                QPID_LOG(trace, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
            }
        }
    }

    sendCommandComplete(replyToKey, sequence);
}


void ManagementAgent::handleGetQueryLH(const std::string& body, std::string replyTo, const std::string& cid, const std::string& contentType)
{
    FieldTable           ft;
    FieldTable::ValuePtr value;

    moveNewObjectsLH();

    if (contentType != "_query_v1") {
        QPID_LOG(warning, "Support for QMF V2 Query format TBD!!!");
        return;
    }

    qpid::messaging::Message inMsg(body);
    qpid::messaging::MapView inMap(inMsg);
    qpid::messaging::MapView::const_iterator i;
    ::qpid::messaging::Variant::Map headers;

    QPID_LOG(trace, "RECV GetQuery (v2): map=" << inMap << " seq=" << cid);

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

                if (!object->isDeleted()) {
                    object->mapEncodeValues(values, true, true); // write both stats and properties
                    map_["_values"] = values;
                    list_.push_back(map_);

                    content.encode();
                    sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
                }
            }
        }
    } else {
        for (ManagementObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++) {
            ManagementObject* object = iter->second;
            if (object->getClassName () == className) {

                // @todo: support multiple objects per message reply
                values.clear();
                list_.clear();
                if (object->getConfigChanged() || object->getInstChanged())
                    object->setUpdateTime();

                if (!object->isDeleted()) {
                    object->mapEncodeValues(values, true, true); // write both stats and properties
                map_["_values"] = values;
                list_.push_back(map_);

                content.encode();
                sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
                }
            }
        }
    }

    // end empty "non-partial" message to indicate CommandComplete
    list_.clear();
    headers.erase("partial");
    content.encode();
    sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyTo);
    QPID_LOG(trace, "SEND GetResponse (v2) to=" << replyTo << " seq=" << cid);
}


void ManagementAgent::handleLocateRequestLH(const string&, const string& replyTo,
                                            const string& cid)
{
    QPID_LOG(trace, "RCVD AgentLocateRequest");

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
    sendBuffer(msg.getContent(), cid, headers, v2Direct, replyTo);

    QPID_LOG(trace, "SENT AgentLocateResponse replyTo=" << replyTo);
}


bool ManagementAgent::authorizeAgentMessageLH(Message& msg)
{
    Buffer   inBuffer (inputBuffer, MA_BUFFER_SIZE);
    uint32_t sequence = 0;
    bool methodReq = false;
    bool mapMsg = false;
    string  packageName;
    string  className;
    string  methodName;
    std::string cid;

    if (msg.encodedSize() > MA_BUFFER_SIZE)
        return false;

    msg.encodeContent(inBuffer);
    uint32_t bufferLen = inBuffer.getPosition();
    inBuffer.reset();

    const framing::MessageProperties* p =
      msg.getFrames().getHeaders()->get<framing::MessageProperties>();

    const framing::FieldTable *headers = msg.getApplicationHeaders();

    if (headers && headers->getAsString("app_id") == "qmf2")
    {
        mapMsg = true;

        if (p && p->hasCorrelationId()) {
            cid = p->getCorrelationId();
        }

        if (headers->getAsString("qmf.opcode") == "_method_request")
        {
            methodReq = true;

            // extract object id and method name

            std::string body;
            inBuffer.getRawData(body, bufferLen);
            qpid::messaging::Message inMsg(body);
            qpid::messaging::MapView inMap(inMsg);
            qpid::messaging::MapView::const_iterator oid, mid;

            ObjectId objId;

            if ((oid = inMap.find("_object_id")) == inMap.end() ||
                (mid = inMap.find("_method_name")) == inMap.end()) {
                QPID_LOG(warning,
                         "Missing fields in QMF authorize req received.");
                return false;
            }

            try {
                // coversions will throw if input is invalid.
                objId = ObjectId(oid->second.asMap());
                methodName = mid->second.getString();
            } catch(exception& e) {
                QPID_LOG(warning,
                         "Badly formatted QMF authorize req received.");
                return false;
            }

            // look up schema for object to get package and class name

            ManagementObjectMap::iterator iter = managementObjects.find(objId);

            if (iter == managementObjects.end() || iter->second->isDeleted()) {
                QPID_LOG(debug, "ManagementAgent::authorizeAgentMessageLH: stale object id " <<
                         objId);
                return false;
            }

            packageName = iter->second->getPackageName();
            className = iter->second->getClassName();
        }
    } else {    // old style binary message format

        uint8_t  opcode;

        if (!checkHeader(inBuffer, &opcode, &sequence))
            return false;

        if (opcode == 'M') {
            methodReq = true;

            // extract method name & schema package and class name

            uint8_t hash[16];
            inBuffer.getLongLong(); // skip over object id
            inBuffer.getLongLong();
            inBuffer.getShortString(packageName);
            inBuffer.getShortString(className);
            inBuffer.getBin128(hash);
            inBuffer.getShortString(methodName);

        }
    }

    if (methodReq) {
        // TODO: check method call against ACL list.
        map<acl::Property, string> params;
        AclModule* acl = broker->getAcl();
        if (acl == 0)
            return true;

        string  userId = ((const qpid::broker::ConnectionState*) msg.getPublisher())->getUserId();
        params[acl::PROP_SCHEMAPACKAGE] = packageName;
        params[acl::PROP_SCHEMACLASS]   = className;

        if (acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_METHOD, methodName, &params))
            return true;

        // authorization failed, send reply if replyTo present

        const framing::MessageProperties* p =
            msg.getFrames().getHeaders()->get<framing::MessageProperties>();
        if (p && p->hasReplyTo()) {
            const framing::ReplyTo& rt = p->getReplyTo();
            string replyToKey = rt.getRoutingKey();

            if (mapMsg) {

                qpid::messaging::Message outMsg;
                qpid::messaging::MapContent outMap(outMsg);
                qpid::messaging::Variant::Map headers;

                headers["method"] = "response";
                headers["qmf.opcode"] = "_method_response";
                headers["qmf.agent"] = name_address;

                ((outMap["_error"].asMap())["_values"].asMap())["_status"] = Manageable::STATUS_FORBIDDEN;
                ((outMap["_error"].asMap())["_values"].asMap())["_status_text"] = Manageable::StatusText(Manageable::STATUS_FORBIDDEN);
                outMap.encode();
                sendBuffer(outMsg.getContent(), cid, headers, v2Direct, replyToKey);

            } else {

                Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
                uint32_t outLen;

                encodeHeader(outBuffer, 'm', sequence);
                outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
                outBuffer.putMediumString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                sendBuffer(outBuffer, outLen, dExchange, replyToKey);
            }

            QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN" << " seq=" << sequence);
        }

        return false;
    }

    return true;
}

void ManagementAgent::dispatchAgentCommandLH(Message& msg)
{
    string   replyToKey;

    const framing::MessageProperties* p =
        msg.getFrames().getHeaders()->get<framing::MessageProperties>();
    if (p && p->hasReplyTo()) {
        const framing::ReplyTo& rt = p->getReplyTo();
        replyToKey = rt.getRoutingKey();
    }
    else
        return;

    Buffer   inBuffer(inputBuffer, MA_BUFFER_SIZE);
    uint8_t  opcode;

    if (msg.encodedSize() > MA_BUFFER_SIZE) {
        QPID_LOG(debug, "ManagementAgent::dispatchAgentCommandLH: Message too large: " <<
                 msg.encodedSize());
        return;
    }

    msg.encodeContent(inBuffer);
    uint32_t bufferLen = inBuffer.getPosition();
    inBuffer.reset();

    const framing::FieldTable *headers = msg.getApplicationHeaders();

    if (headers && headers->getAsString("app_id") == "qmf2")
    {
        std::string opcode = headers->getAsString("qmf.opcode");
        std::string contentType = headers->getAsString("qmf.content");
        std::string body;
        std::string cid;

        inBuffer.getRawData(body, bufferLen);

        if (p && p->hasCorrelationId()) {
            cid = p->getCorrelationId();
        }

        if (opcode == "_method_request")
            return handleMethodRequestLH(body, replyToKey, cid, msg.getPublisher());
        else if (opcode == "_query_request")
            return handleGetQueryLH(body, replyToKey, cid, contentType);
        else if (opcode == "_agent_locate_request")
            return handleLocateRequestLH(body, replyToKey, cid);

        QPID_LOG(warning, "Support for QMF Opcode [" << opcode << "] TBD!!!");
        return;
    }

    // old preV2 binary messages

    while (inBuffer.getPosition() < bufferLen) {
        uint32_t sequence;
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

void ManagementAgent::SchemaClassKey::mapEncode(qpid::messaging::Variant::Map& _map) const {
    _map["_cname"] = name;
    _map["_hash"] = qpid::messaging::Uuid(hash);
}

void ManagementAgent::SchemaClassKey::mapDecode(const qpid::messaging::Variant::Map& _map) {
    qpid::messaging::Variant::Map::const_iterator i;

    if ((i = _map.find("_cname")) != _map.end()) {
        name = i->second.asString();
    }

    if ((i = _map.find("_hash")) != _map.end()) {
        const qpid::messaging::Uuid& uuid = i->second.asUuid();
        memcpy(hash, uuid.data(), uuid.size());
    }
}

void ManagementAgent::SchemaClassKey::encode(qpid::framing::Buffer& buffer) const {
    buffer.checkAvailable(encodedBufSize());
    buffer.putShortString(name);
    buffer.putBin128(hash);
}

void ManagementAgent::SchemaClassKey::decode(qpid::framing::Buffer& buffer) {
    buffer.checkAvailable(encodedBufSize());
    buffer.getShortString(name);
    buffer.getBin128(hash);
}

uint32_t ManagementAgent::SchemaClassKey::encodedBufSize() const {
    return 1 + name.size() + 16 /* bin128 */;
}

void ManagementAgent::SchemaClass::mapEncode(qpid::messaging::Variant::Map& _map) const {
    _map["_type"] = kind;
    _map["_pending_sequence"] = pendingSequence;
    _map["_data"] = data;
}

void ManagementAgent::SchemaClass::mapDecode(const qpid::messaging::Variant::Map& _map) {
    qpid::messaging::Variant::Map::const_iterator i;

    if ((i = _map.find("_type")) != _map.end()) {
        kind = i->second;
    }
    if ((i = _map.find("_pending_sequence")) != _map.end()) {
        pendingSequence = i->second;
    }
    if ((i = _map.find("_data")) != _map.end()) {
        data = i->second.asString();
    }
}

void ManagementAgent::exportSchemas(std::string& out) {
    ::qpid::messaging::Message m;
    ::qpid::messaging::ListContent content(m);
    ::qpid::messaging::Variant::List &list_ = content.asList();
    ::qpid::messaging::Variant::Map map_, kmap, cmap;

    for (PackageMap::const_iterator i = packages.begin(); i != packages.end(); ++i) {
        string name = i->first;
        const ClassMap& classes = i ->second;
        for (ClassMap::const_iterator j = classes.begin(); j != classes.end(); ++j) {
            const SchemaClassKey& key = j->first;
            const SchemaClass& klass = j->second;
            if (klass.writeSchemaCall == 0) { // Ignore built-in schemas.
                // Encode name, schema-key, schema-class

                map_.clear();
                kmap.clear();
                cmap.clear();

                key.mapEncode(kmap);
                klass.mapEncode(cmap);

                map_["_pname"] = name;
                map_["_key"] = kmap;
                map_["_class"] = cmap;
                list_.push_back(map_);
            }
        }
    }

    content.encode();
    out = m.getContent();
}

void ManagementAgent::importSchemas(qpid::framing::Buffer& inBuf) {

    ::qpid::messaging::Message m(inBuf.getPointer(), inBuf.available());
    ::qpid::messaging::ListView content(m);
    ::qpid::messaging::ListView::const_iterator l;


    for (l = content.begin(); l != content.end(); l++) {
        string package;
        SchemaClassKey key;
        SchemaClass klass;
        ::qpid::messaging::VariantMap map_, kmap, cmap;
        qpid::messaging::MapView::const_iterator i;
        
        map_ = l->asMap();

        if ((i = map_.find("_pname")) != map_.end()) {
            package = i->second.asString();

            if ((i = map_.find("_key")) != map_.end()) {
                key.mapDecode(i->second.asMap());

                if ((i = map_.find("_class")) != map_.end()) {
                    klass.mapDecode(i->second.asMap());

                    packages[package][key] = klass;
                }
            }
        }
    }
}

void ManagementAgent::RemoteAgent::mapEncode(qpid::messaging::Variant::Map& map_) const {
    ::qpid::messaging::VariantMap _objId, _values;

    map_["_brokerBank"] = brokerBank;
    map_["_agentBank"] = agentBank;
    map_["_routingKey"] = routingKey;

    connectionRef.mapEncode(_objId);
    map_["_object_id"] = _objId;

    mgmtObject->mapEncodeValues(_values, true, false);
    map_["_values"] = _values;
}

void ManagementAgent::RemoteAgent::mapDecode(const qpid::messaging::Variant::Map& map_) {
    qpid::messaging::MapView::const_iterator i;

    if ((i = map_.find("_brokerBank")) != map_.end()) {
        brokerBank = i->second;
    }

    if ((i = map_.find("_agentBank")) != map_.end()) {
        agentBank = i->second;
    }

    if ((i = map_.find("_routingKey")) != map_.end()) {
        routingKey = i->second.getString();
    }

    if ((i = map_.find("_object_id")) != map_.end()) {
        connectionRef.mapDecode(i->second.asMap());
    }

    mgmtObject = new _qmf::Agent(&agent, this);

    if ((i = map_.find("_values")) != map_.end()) {
        mgmtObject->mapDecodeValues(i->second.asMap());
    }

    // TODO aconway 2010-03-04: see comment in encode(), readProperties doesn't set v2key.
    mgmtObject->set_connectionRef(connectionRef);
}

void ManagementAgent::exportAgents(std::string& out) {
    ::qpid::messaging::Message m;
    ::qpid::messaging::ListContent content(m);
    ::qpid::messaging::Variant::List &list_ = content.asList();
    ::qpid::messaging::VariantMap map_, omap, amap;

    for (RemoteAgentMap::const_iterator i = remoteAgents.begin();
         i != remoteAgents.end();
         ++i)
    {
        // TODO aconway 2010-03-04: see comment in ManagementAgent::RemoteAgent::encode
        RemoteAgent* agent = i->second;

        map_.clear();
        amap.clear();

        agent->mapEncode(amap);
        map_["_remote_agent"] = amap;
        list_.push_back(map_);
    }

    content.encode();
    out = m.getContent();
}

void ManagementAgent::importAgents(qpid::framing::Buffer& inBuf) {

    ::qpid::messaging::Message m(inBuf.getPointer(), inBuf.available());
    ::qpid::messaging::ListView content(m);
    ::qpid::messaging::ListView::const_iterator l;

    for (l = content.begin(); l != content.end(); l++) {
        std::auto_ptr<RemoteAgent> agent(new RemoteAgent(*this));
        ::qpid::messaging::VariantMap map_;
        qpid::messaging::MapView::const_iterator i;

        map_ = l->asMap();

        if ((i = map_.find("_remote_agent")) != map_.end()) {

            agent->mapDecode(i->second.asMap());

            addObject (agent->mgmtObject, 0, false);
            remoteAgents[agent->connectionRef] = agent.release();
        }
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

qpid::messaging::Variant::Map ManagementAgent::toMap(const FieldTable& from)
{
    qpid::messaging::Variant::Map map;

    for (FieldTable::const_iterator iter = from.begin(); iter != from.end(); iter++) {
        const string& key(iter->first);
        const FieldTable::ValuePtr& val(iter->second);

        map[key] = toVariant(val);
    }

    return map;
}

qpid::messaging::Variant::List ManagementAgent::toList(const List& from)
{
    qpid::messaging::Variant::List _list;

    for (List::const_iterator iter = from.begin(); iter != from.end(); iter++) {
        const List::ValuePtr& val(*iter);

        _list.push_back(toVariant(val));
    }

    return _list;
}

qpid::framing::FieldTable ManagementAgent::fromMap(const qpid::messaging::Variant::Map& from)
{
    qpid::framing::FieldTable ft;

    for (qpid::messaging::Variant::Map::const_iterator iter = from.begin();
         iter != from.end();
         iter++) {
        const string& key(iter->first);
        const qpid::messaging::Variant& val(iter->second);

        ft.set(key, toFieldValue(val));
    }

    return ft;
}


List ManagementAgent::fromList(const qpid::messaging::Variant::List& from)
{
    List fa;

    for (qpid::messaging::Variant::List::const_iterator iter = from.begin();
         iter != from.end();
         iter++) {
        const qpid::messaging::Variant& val(*iter);

        fa.push_back(toFieldValue(val));
    }

    return fa;
}


boost::shared_ptr<FieldValue> ManagementAgent::toFieldValue(const Variant& in)
{

    switch(in.getType()) {

    case messaging::VAR_VOID:   return boost::shared_ptr<FieldValue>(new VoidValue());
    case messaging::VAR_BOOL:   return boost::shared_ptr<FieldValue>(new BoolValue(in.asBool()));
    case messaging::VAR_UINT8:  return boost::shared_ptr<FieldValue>(new Unsigned8Value(in.asUint8()));
    case messaging::VAR_UINT16: return boost::shared_ptr<FieldValue>(new Unsigned16Value(in.asUint16()));
    case messaging::VAR_UINT32: return boost::shared_ptr<FieldValue>(new Unsigned32Value(in.asUint32()));
    case messaging::VAR_UINT64: return boost::shared_ptr<FieldValue>(new Unsigned64Value(in.asUint64()));
    case messaging::VAR_INT8:   return boost::shared_ptr<FieldValue>(new Integer8Value(in.asInt8()));
    case messaging::VAR_INT16:  return boost::shared_ptr<FieldValue>(new Integer16Value(in.asInt16()));
    case messaging::VAR_INT32:  return boost::shared_ptr<FieldValue>(new Integer32Value(in.asInt32()));
    case messaging::VAR_INT64:  return boost::shared_ptr<FieldValue>(new Integer64Value(in.asInt64()));
    case messaging::VAR_FLOAT:  return boost::shared_ptr<FieldValue>(new FloatValue(in.asFloat()));
    case messaging::VAR_DOUBLE: return boost::shared_ptr<FieldValue>(new DoubleValue(in.asDouble()));
    case messaging::VAR_STRING: return boost::shared_ptr<FieldValue>(new Str16Value(in.asString()));
    case messaging::VAR_UUID:   return boost::shared_ptr<FieldValue>(new UuidValue(in.asUuid().data()));
    case messaging::VAR_MAP:    return boost::shared_ptr<FieldValue>(new FieldTableValue(ManagementAgent::fromMap(in.asMap())));
    case messaging::VAR_LIST:   return boost::shared_ptr<FieldValue>(new ListValue(ManagementAgent::fromList(in.asList())));
    }

    QPID_LOG(error, "Unknown Variant type - not converted: [" << in.getType() << "]");
    return boost::shared_ptr<FieldValue>(new VoidValue());
}

// stolen from qpid/client/amqp0_10/Codecs.cpp - TODO: make Codecs public, and remove this dup.
qpid::messaging::Variant ManagementAgent::toVariant(const boost::shared_ptr<FieldValue>& in)
{
    const std::string iso885915("iso-8859-15");
    const std::string utf8("utf8");
    const std::string utf16("utf16");
    //const std::string binary("binary");
    const std::string amqp0_10_binary("amqp0-10:binary");
    //const std::string amqp0_10_bit("amqp0-10:bit");
    const std::string amqp0_10_datetime("amqp0-10:datetime");
    const std::string amqp0_10_struct("amqp0-10:struct");
    Variant out;

    //based on AMQP 0-10 typecode, pick most appropriate variant type
    switch (in->getType()) {
        //Fixed Width types:
    case 0x00: //bin8
    case 0x01: out.setEncoding(amqp0_10_binary); // int8
    case 0x02: out = in->getIntegerValue<int8_t, 1>(); break;  //uint8
    case 0x03: out = in->getIntegerValue<uint8_t, 1>(); break;  // 
        // case 0x04: break; //TODO: iso-8859-15 char  // char
    case 0x08: out = static_cast<bool>(in->getIntegerValue<uint8_t, 1>()); break;  // bool int8

    case 0x10: out.setEncoding(amqp0_10_binary);  // bin16
    case 0x11: out = in->getIntegerValue<int16_t, 2>(); break;  // int16
    case 0x12: out = in->getIntegerValue<uint16_t, 2>(); break;  //uint16

    case 0x20: out.setEncoding(amqp0_10_binary);   // bin32
    case 0x21: out = in->getIntegerValue<int32_t, 4>(); break;  // int32
    case 0x22: out = in->getIntegerValue<uint32_t, 4>(); break; // uint32

    case 0x23: out = in->get<float>(); break;  // float(32)

        // case 0x27: break; //TODO: utf-32 char

    case 0x30: out.setEncoding(amqp0_10_binary); // bin64
    case 0x31: out = in->getIntegerValue<int64_t, 8>(); break; //int64

    case 0x38: out.setEncoding(amqp0_10_datetime); //treat datetime as uint64_t, but set encoding
    case 0x32: out = in->getIntegerValue<uint64_t, 8>(); break;  //uint64
    case 0x33: out = in->get<double>(); break;  // double

    case 0x48: // uuid
        {
            unsigned char data[16];
            in->getFixedWidthValue<16>(data);
            out = qpid::messaging::Uuid(data);
        } break;

        //TODO: figure out whether and how to map values with codes 0x40-0xd8

    case 0xf0: break;//void, which is the default value for Variant
        // case 0xf1: out.setEncoding(amqp0_10_bit); break;//treat 'bit' as void, which is the default value for Variant

        //Variable Width types:
        //strings:
    case 0x80: // str8
    case 0x90: // str16
    case 0xa0: // str32
        out = in->get<std::string>();
        out.setEncoding(amqp0_10_binary);
        break;

    case 0x84: // str8
    case 0x94: // str16
        out = in->get<std::string>();
        out.setEncoding(iso885915);
        break;

    case 0x85: // str8
    case 0x95: // str16
        out = in->get<std::string>();
        out.setEncoding(utf8);
        break;

    case 0x86: // str8
    case 0x96: // str16
        out = in->get<std::string>();
        out.setEncoding(utf16);
        break;

    case 0xab:  // str32
        out = in->get<std::string>();
        out.setEncoding(amqp0_10_struct);
        break;

    case 0xa8:  // map
        out = ManagementAgent::toMap(in->get<FieldTable>());
        break;

    case 0xa9: // list of variant types
        out = ManagementAgent::toList(in->get<List>());
        break;
        //case 0xaa: //convert amqp0-10 array (uniform type) into variant list
        // out = Variant::List();
        // translate<Array>(in, out.asList(), &toVariant);
        // break;

      default:
          //error?
          QPID_LOG(error, "Unknown FieldValue type - not converted: [" << (unsigned int)(in->getType()) << "]");
          break;
    }

    return out;
}

