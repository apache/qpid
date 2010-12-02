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
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/log/Statement.h"
#include <qpid/broker/Message.h>
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/sys/Time.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/broker/AclModule.h"
#include "qpid/types/Variant.h"
#include "qpid/types/Uuid.h"
#include "qpid/framing/List.h"
#include "qpid/amqp_0_10/Codecs.h"
#include <list>
#include <iostream>
#include <fstream>
#include <sstream>
#include <typeinfo>

using boost::intrusive_ptr;
using qpid::framing::Uuid;
using qpid::types::Variant;
using qpid::amqp_0_10::MapCodec;
using qpid::amqp_0_10::ListCodec;
using qpid::sys::Mutex;
using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::broker;
using namespace qpid;
using namespace std;
namespace _qmf = qmf::org::apache::qpid::broker;


namespace {
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


static Variant::Map mapEncodeSchemaId(const string& pname,
                                      const string& cname,
                                      const string& type,
                                      const uint8_t *md5Sum)
{
    Variant::Map map_;

    map_["_package_name"] = pname;
    map_["_class_name"] = cname;
    map_["_type"] = type;
    map_["_hash"] = qpid::types::Uuid(md5Sum);
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
    startTime(sys::now()),
    suppressed(false), disallowAllV1Methods(false),
    vendorNameKey(defaultVendorName), productNameKey(defaultProductName),
    qmf1Support(qmfV1), qmf2Support(qmfV2), maxV2ReplyObjs(100)
{
    nextObjectId   = 1;
    brokerBank     = 1;
    bootSequence   = 1;
    nextRemoteBank = 10;
    nextRequestSequence = 1;
    clientWasAdded = false;
    attrMap["_vendor"] = defaultVendorName;
    attrMap["_product"] = defaultProductName;
}

ManagementAgent::~ManagementAgent ()
{
    {
        sys::Mutex::ScopedLock lock (userLock);

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
            if (uuid.isNull()) {
                uuid.generate();
                QPID_LOG (info, "No stored broker ID found - ManagementAgent generated broker ID: " << uuid);
            } else
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
    if (vendor.find(':') != vendor.npos) {
        throw Exception("vendor string cannot contain a ':' character.");
    }
    if (product.find(':') != product.npos) {
        throw Exception("product string cannot contain a ':' character.");
    }
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

   vendorNameKey = keyifyNameStr(vendor);
   productNameKey = keyifyNameStr(product);
   instanceNameKey = keyifyNameStr(inst);
}


void ManagementAgent::getName(string& vendor, string& product, string& instance)
{
    vendor = std::string(attrMap["_vendor"]);
    product = std::string(attrMap["_product"]);
    instance = std::string(attrMap["_instance"]);
}


const std::string& ManagementAgent::getAddress()
{
    return name_address;
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
    sys::Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    addClassLH(ManagementItem::CLASS_KIND_TABLE, pIter, className, md5Sum, schemaCall);
}

void ManagementAgent::registerEvent (const string&  packageName,
                                     const string&  eventName,
                                     uint8_t* md5Sum,
                                     ManagementObject::writeSchemaCall_t schemaCall)
{
    sys::Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    addClassLH(ManagementItem::CLASS_KIND_EVENT, pIter, eventName, md5Sum, schemaCall);
}

// Deprecated:  V1 objects
ObjectId ManagementAgent::addObject(ManagementObject* object, uint64_t persistId, bool persistent)
{
    uint16_t sequence;
    uint64_t objectNum;

    sequence = persistent ? 0 : bootSequence;
    objectNum = persistId ? persistId : nextObjectId++;

    ObjectId objId(0 /*flags*/, sequence, brokerBank, objectNum);
    objId.setV2Key(*object);   // let object generate the v2 key

    object->setObjectId(objId);

    {
        sys::Mutex::ScopedLock lock(addLock);
        ManagementObjectMap::iterator destIter = newManagementObjects.find(objId);
        while (destIter != newManagementObjects.end()) {
            objId.disambiguate();
            destIter = newManagementObjects.find(objId);
        }
        newManagementObjects[objId] = object;
    }

    return objId;
}



ObjectId ManagementAgent::addObject(ManagementObject* object,
                                    const string& key,
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
        sys::Mutex::ScopedLock lock(addLock);
        ManagementObjectMap::iterator destIter = newManagementObjects.find(objId);
        while (destIter != newManagementObjects.end()) {
            objId.disambiguate();
            destIter = newManagementObjects.find(objId);
        }
        newManagementObjects[objId] = object;
    }

    return objId;
}

void ManagementAgent::raiseEvent(const ManagementEvent& event, severity_t severity)
{
    static const std::string severityStr[] = {
        "emerg", "alert", "crit", "error", "warn",
        "note", "info", "debug"
    };
    sys::Mutex::ScopedLock lock (userLock);
    uint8_t sev = (severity == SEV_DEFAULT) ? event.getSeverity() : (uint8_t) severity;

    if (qmf1Support) {
        Buffer outBuffer(eventBuffer, MA_BUFFER_SIZE);
        uint32_t outLen;

        encodeHeader(outBuffer, 'e');
        outBuffer.putShortString(event.getPackageName());
        outBuffer.putShortString(event.getEventName());
        outBuffer.putBin128(event.getMd5Sum());
        outBuffer.putLongLong(uint64_t(sys::Duration(sys::EPOCH, sys::now())));
        outBuffer.putOctet(sev);
        string sBuf;
        event.encode(sBuf);
        outBuffer.putRawData(sBuf);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        sendBufferLH(outBuffer, outLen, mExchange,
                   "console.event.1.0." + event.getPackageName() + "." + event.getEventName());
        QPID_LOG(trace, "SEND raiseEvent (v1) class=" << event.getPackageName() << "." << event.getEventName());
    }

    if (qmf2Support) {
        Variant::Map map_;
        Variant::Map schemaId;
        Variant::Map values;
        Variant::Map headers;

        map_["_schema_id"] = mapEncodeSchemaId(event.getPackageName(),
                                               event.getEventName(),
                                               "_event",
                                               event.getMd5Sum());
        event.mapEncode(values);
        map_["_values"] = values;
        map_["_timestamp"] = uint64_t(sys::Duration(sys::EPOCH, sys::now()));
        map_["_severity"] = sev;

        headers["method"] = "indication";
        headers["qmf.opcode"] = "_data_indication";
        headers["qmf.content"] = "_event";
        headers["qmf.agent"] = name_address;

        stringstream key;
        key << "agent.ind.event." << keyifyNameStr(event.getPackageName())
            << "." << keyifyNameStr(event.getEventName())
            << "." << severityStr[sev]
            << "." << vendorNameKey
            << "." << productNameKey;
        if (!instanceNameKey.empty())
            key << "." << instanceNameKey;


        string content;
        MapCodec::encode(map_, content);
        sendBufferLH(content, "", headers, "amqp/map", v2Topic, key.str());
        QPID_LOG(trace, "SEND raiseEvent (v2) class=" << event.getPackageName() << "." << event.getEventName());
    }

}

ManagementAgent::Periodic::Periodic (ManagementAgent& _agent, uint32_t _seconds)
    : TimerTask (sys::Duration((_seconds ? _seconds : 1) * sys::TIME_SEC),
                 "ManagementAgent::periodicProcessing"),
      agent(_agent) {}

ManagementAgent::Periodic::~Periodic () {}

void ManagementAgent::Periodic::fire ()
{
    agent.timer->add (new Periodic (agent, agent.interval));
    agent.periodicProcessing ();
}

void ManagementAgent::clientAdded (const string& routingKey)
{
    sys::Mutex::ScopedLock lock(userLock);

    //
    // If this routing key is not relevant to object updates, exit.
    //
    if ((routingKey.compare(0, 1,  "#") != 0) &&
        (routingKey.compare(0, 9,  "console.#") != 0) &&
        (routingKey.compare(0, 12, "console.obj.") != 0))
        return;

    //
    // Mark local objects for full-update.
    //
    clientWasAdded = true;

    //
    // If the routing key is relevant for local objects only, don't involve
    // any of the remote agents.
    //
    if (routingKey.compare(0, 39, "console.obj.*.*.org.apache.qpid.broker.") == 0)
        return;

    std::list<std::string> rkeys;

    for (RemoteAgentMap::iterator aIter = remoteAgents.begin();
         aIter != remoteAgents.end();
         aIter++) {
        rkeys.push_back(aIter->second->routingKey);
    }

    while (rkeys.size()) {
        char     localBuffer[16];
        Buffer   outBuffer(localBuffer, 16);
        uint32_t outLen;

        encodeHeader(outBuffer, 'x');
        outLen = outBuffer.getPosition();
        outBuffer.reset();
        sendBufferLH(outBuffer, outLen, dExchange, rkeys.front());
        QPID_LOG(trace, "SEND ConsoleAddedIndication to=" << rkeys.front());
        rkeys.pop_front();
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

// NOTE WELL: assumes userLock is held by caller (LH)
// NOTE EVEN WELLER: drops this lock when delivering the message!!!
void ManagementAgent::sendBufferLH(Buffer&  buf,
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

    {
        sys::Mutex::ScopedUnlock u(userLock);

        DeliverableMessage deliverable (msg);
        try {
            exchange->route(deliverable, routingKey, 0);
        } catch(exception&) {}
    }
    buf.reset();
}


// NOTE WELL: assumes userLock is held by caller (LH)
// NOTE EVEN WELLER: drops this lock when delivering the message!!!
void ManagementAgent::sendBufferLH(const string& data,
                                   const string& cid,
                                   const Variant::Map& headers,
                                   const string& content_type,
                                   qpid::broker::Exchange::shared_ptr exchange,
                                   const string& routingKey,
                                   uint64_t ttl_msec)
{
    Variant::Map::const_iterator i;

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
    props->setContentType(content_type);
    props->setAppId("qmf2");

    for (i = headers.begin(); i != headers.end(); ++i) {
        msg->getOrInsertHeaders().setString(i->first, i->second.asString());
    }

    DeliveryProperties* dp =
        msg->getFrames().getHeaders()->get<DeliveryProperties>(true);
    dp->setRoutingKey(routingKey);
    if (ttl_msec) {
        dp->setTtl(ttl_msec);
        msg->setTimestamp(broker->getExpiryPolicy());
    }
    msg->getFrames().append(content);

    {
        sys::Mutex::ScopedUnlock u(userLock);

        DeliverableMessage deliverable (msg);
        try {
            exchange->route(deliverable, routingKey, 0);
        } catch(exception&) {}
    }
}


void ManagementAgent::moveNewObjectsLH()
{
    sys::Mutex::ScopedLock lock (addLock);
    for (ManagementObjectMap::iterator iter = newManagementObjects.begin ();
         iter != newManagementObjects.end ();
         iter++) {
        ObjectId oid = iter->first;
        ManagementObjectMap::iterator destIter = managementObjects.find(oid);
        while (destIter != managementObjects.end()) {
            oid.disambiguate();
            destIter = managementObjects.find(oid);
        }

        managementObjects[oid] = iter->second;
    }
    newManagementObjects.clear();
}

void ManagementAgent::periodicProcessing (void)
{
#define BUFSIZE   65536
#define HEADROOM  4096
    QPID_LOG(trace, "Management agent periodic processing");
    sys::Mutex::ScopedLock lock (userLock);
    char                msgChars[BUFSIZE];
    uint32_t            contentSize;
    string              routingKey;
    list<pair<ObjectId, ManagementObject*> > deleteList;
    string sBuf;

    uint64_t uptime = sys::Duration(startTime, sys::now());
    static_cast<_qmf::Broker*>(broker->GetManagementObject())->set_uptime(uptime);

    moveNewObjectsLH();

    //
    //  Clear the been-here flag on all objects in the map.
    //
    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         iter++) {
        ManagementObject* object = iter->second;

        if (object->isDeleted()) {
            deleteList.push_back(pair<ObjectId, ManagementObject*>(iter->first, object));
        }

        object->setFlags(0);
        if (clientWasAdded) {
            object->setForcePublish(true);
        }
    }

    clientWasAdded = false;

    // Remove Deleted objects, and save for later publishing...
    //
    for (list<pair<ObjectId, ManagementObject*> >::reverse_iterator iter = deleteList.rbegin();
         iter != deleteList.rend();
         iter++) {

        ManagementObject* delObj = iter->second;
        DeletedObject::shared_ptr dptr(new DeletedObject());
        std::string classkey(delObj->getPackageName() + std::string(":") + delObj->getClassName());
        bool send_stats = (delObj->hasInst() && (delObj->getInstChanged() || delObj->getForcePublish()));

        dptr->packageName = delObj->getPackageName();
        dptr->className = delObj->getClassName();
        stringstream oid;
        oid << delObj->getObjectId();
        dptr->objectId = oid.str();

        if (qmf1Support) {
            delObj->writeProperties(dptr->encodedV1Config);
            if (send_stats) {
                delObj->writeStatistics(dptr->encodedV1Inst);
            }
        }

        if (qmf2Support) {
            Variant::Map map_;
            Variant::Map values;
            Variant::Map oid;

            delObj->getObjectId().mapEncode(oid);
            map_["_object_id"] = oid;
            map_["_schema_id"] = mapEncodeSchemaId(delObj->getPackageName(),
                                                   delObj->getClassName(),
                                                   "_data",
                                                   delObj->getMd5Sum());
            delObj->writeTimestamps(map_);
            delObj->mapEncodeValues(values, true, send_stats);
            map_["_values"] = values;

            dptr->encodedV2 = map_;
        }

        pendingDeletedObjs[classkey].push_back(dptr);

        delete iter->second;
        managementObjects.erase(iter->first);
    }

    //
    // Process the entire object map.  Remember: we drop the userLock each time we call
    // sendBuffer().  This allows the managementObjects map to be altered during the
    // sendBuffer() call, so always restart the search after a sendBuffer() call
    //
    while (1) {
        Buffer msgBuffer(msgChars, BUFSIZE);
        Variant::List list_;
        uint32_t pcount;
        uint32_t scount;
        uint32_t v2Objs;
        ManagementObjectMap::iterator baseIter;
        std::string packageName;
        std::string className;

        for (baseIter = managementObjects.begin();
             baseIter != managementObjects.end();
             baseIter++) {
            ManagementObject* baseObject = baseIter->second;
            //
            //  Skip until we find a base object requiring processing...
            //
            if (baseObject->getFlags() == 0) {
                packageName = baseObject->getPackageName();
                className = baseObject->getClassName();
                break;
            }
        }

        if (baseIter == managementObjects.end())
            break;  // done - all objects processed

        pcount = scount = 0;
        v2Objs = 0;
        list_.clear();
        msgBuffer.reset();

        for (ManagementObjectMap::iterator iter = baseIter;
             iter != managementObjects.end();
             iter++) {
            ManagementObject* baseObject = baseIter->second;
            ManagementObject* object = iter->second;
            bool send_stats, send_props;
            if (baseObject->isSameClass(*object) && object->getFlags() == 0) {
                object->setFlags(1);
                if (object->getConfigChanged() || object->getInstChanged())
                    object->setUpdateTime();

                // skip any objects marked deleted since our first pass.  Deal with them
                // on the next periodic cycle...
                if (object->isDeleted()) {
                    continue;
                }

                send_props = (object->getConfigChanged() || object->getForcePublish());
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
                    Variant::Map  map_;
                    Variant::Map values;
                    Variant::Map oid;

                    object->getObjectId().mapEncode(oid);
                    map_["_object_id"] = oid;
                    map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                           object->getClassName(),
                                                           "_data",
                                                           object->getMd5Sum());
                    object->writeTimestamps(map_);
                    object->mapEncodeValues(values, send_props, send_stats);
                    map_["_values"] = values;
                    list_.push_back(map_);
                    v2Objs++;
                }

                if (send_props) pcount++;
                if (send_stats) scount++;

                object->setForcePublish(false);

                if ((qmf1Support && (msgBuffer.available() < HEADROOM)) ||
                    (qmf2Support && (v2Objs >= maxV2ReplyObjs)))
                    break;  // have enough objects, send an indication...
            }
        }

        if (pcount || scount) {
            if (qmf1Support) {
                contentSize = BUFSIZE - msgBuffer.available();
                if (contentSize > 0) {
                    stringstream key;
                    key << "console.obj.1.0." << packageName << "." << className;
                    msgBuffer.reset();
                    sendBufferLH(msgBuffer, contentSize, mExchange, key.str());   // UNLOCKS USERLOCK
                    QPID_LOG(trace, "SEND V1 Multicast ContentInd to=" << key.str() << " props=" << pcount << " stats=" << scount);
                }
            }

            if (qmf2Support) {
                string content;
                ListCodec::encode(list_, content);
                if (content.length()) {
                    stringstream key;
                    Variant::Map  headers;
                    key << "agent.ind.data." << keyifyNameStr(packageName)
                        << "." << keyifyNameStr(className)
                        << "." << vendorNameKey
                        << "." << productNameKey;
                    if (!instanceNameKey.empty())
                        key << "." << instanceNameKey;

                    headers["method"] = "indication";
                    headers["qmf.opcode"] = "_data_indication";
                    headers["qmf.content"] = "_data";
                    headers["qmf.agent"] = name_address;

                    sendBufferLH(content, "", headers, "amqp/list", v2Topic, key.str());  // UNLOCKS USERLOCK
                    QPID_LOG(trace, "SEND Multicast ContentInd to=" << key.str() << " props=" << pcount << " stats=" << scount << " len=" << content.length());
                }
            }
        }
    }  // end processing updates for all objects


    // now send the pending deletes.  Make a temporary copy of the pending deletes so dropping the
    // lock when the buffer is sent is safe.
    //
    if (!pendingDeletedObjs.empty()) {
        PendingDeletedObjsMap tmp(pendingDeletedObjs);
        pendingDeletedObjs.clear();

        for (PendingDeletedObjsMap::iterator mIter = tmp.begin(); mIter != tmp.end(); mIter++) {
            std::string packageName;
            std::string className;
            Buffer msgBuffer(msgChars, BUFSIZE);
            uint32_t v1Objs = 0;
            uint32_t v2Objs = 0;
            Variant::List list_;

            size_t pos = mIter->first.find(":");
            packageName = mIter->first.substr(0, pos);
            className = mIter->first.substr(pos+1);

            for (DeletedObjectList::iterator lIter = mIter->second.begin();
                 lIter != mIter->second.end(); lIter++) {

                if (!(*lIter)->encodedV1Config.empty()) {
                    encodeHeader(msgBuffer, 'c');
                    msgBuffer.putRawData((*lIter)->encodedV1Config);
                    v1Objs++;
                }
                if (!(*lIter)->encodedV1Inst.empty()) {
                    encodeHeader(msgBuffer, 'i');
                    msgBuffer.putRawData((*lIter)->encodedV1Inst);
                    v1Objs++;
                }
                if (v1Objs && msgBuffer.available() < HEADROOM) {
                    v1Objs = 0;
                    contentSize = BUFSIZE - msgBuffer.available();
                    stringstream key;
                    key << "console.obj.1.0." << packageName << "." << className;
                    msgBuffer.reset();
                    sendBufferLH(msgBuffer, contentSize, mExchange, key.str());   // UNLOCKS USERLOCK
                    QPID_LOG(trace, "SEND V1 Multicast ContentInd V1 (delete) to=" << key.str());
                }

                if (!(*lIter)->encodedV2.empty()) {
                    list_.push_back((*lIter)->encodedV2);
                    if (++v2Objs >= maxV2ReplyObjs) {
                        v2Objs = 0;

                        string content;
                        ListCodec::encode(list_, content);
                        list_.clear();
                        if (content.length()) {
                            stringstream key;
                            Variant::Map  headers;
                            key << "agent.ind.data." << keyifyNameStr(packageName)
                                << "." << keyifyNameStr(className)
                                << "." << vendorNameKey
                                << "." << productNameKey;
                            if (!instanceNameKey.empty())
                                key << "." << instanceNameKey;

                            headers["method"] = "indication";
                            headers["qmf.opcode"] = "_data_indication";
                            headers["qmf.content"] = "_data";
                            headers["qmf.agent"] = name_address;

                            sendBufferLH(content, "", headers, "amqp/list", v2Topic, key.str());  // UNLOCKS USERLOCK
                            QPID_LOG(trace, "SEND Multicast ContentInd V2 (delete) to=" << key.str() << " len=" << content.length());
                        }
                    }
                }
            }  // end current list

            // send any remaining objects...

            if (v1Objs) {
                contentSize = BUFSIZE - msgBuffer.available();
                stringstream key;
                key << "console.obj.1.0." << packageName << "." << className;
                msgBuffer.reset();
                sendBufferLH(msgBuffer, contentSize, mExchange, key.str());   // UNLOCKS USERLOCK
                QPID_LOG(trace, "SEND V1 Multicast ContentInd V1 (delete) to=" << key.str());
            }

            if (!list_.empty()) {
                string content;
                ListCodec::encode(list_, content);
                list_.clear();
                if (content.length()) {
                    stringstream key;
                    Variant::Map  headers;
                    key << "agent.ind.data." << keyifyNameStr(packageName)
                        << "." << keyifyNameStr(className)
                        << "." << vendorNameKey
                        << "." << productNameKey;
                    if (!instanceNameKey.empty())
                        key << "." << instanceNameKey;

                    headers["method"] = "indication";
                    headers["qmf.opcode"] = "_data_indication";
                    headers["qmf.content"] = "_data";
                    headers["qmf.agent"] = name_address;

                    sendBufferLH(content, "", headers, "amqp/list", v2Topic, key.str());  // UNLOCKS USERLOCK
                    QPID_LOG(trace, "SEND Multicast ContentInd V2 (delete) to=" << key.str() << " len=" << content.length());
                }
            }
        }  // end map
    }

    if (!deleteList.empty()) {
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
        msgBuffer.putLongLong(uint64_t(sys::Duration(sys::EPOCH, sys::now())));

        contentSize = BUFSIZE - msgBuffer.available ();
        msgBuffer.reset ();
        routingKey = "console.heartbeat.1.0";
        sendBufferLH(msgBuffer, contentSize, mExchange, routingKey);
        QPID_LOG(trace, "SEND HeartbeatInd to=" << routingKey);
    }

    if (qmf2Support) {
        std::stringstream addr_key;

        addr_key << "agent.ind.heartbeat." << vendorNameKey << "." << productNameKey;
        if (!instanceNameKey.empty())
            addr_key << "." << instanceNameKey;

        Variant::Map map;
        Variant::Map headers;

        headers["method"] = "indication";
        headers["qmf.opcode"] = "_agent_heartbeat_indication";
        headers["qmf.agent"] = name_address;

        map["_values"] = attrMap;
        map["_values"].asMap()["timestamp"] = uint64_t(sys::Duration(sys::EPOCH, sys::now()));
        map["_values"].asMap()["heartbeat_interval"] = interval;
        map["_values"].asMap()["epoch"] = bootSequence;

        string content;
        MapCodec::encode(map, content);

        // Set TTL (in msecs) on outgoing heartbeat indications based on the interval
        // time to prevent stale heartbeats from getting to the consoles.
        sendBufferLH(content, "", headers, "amqp/map", v2Topic, addr_key.str(), interval * 2 * 1000);

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

    // since sendBufferLH drops the userLock, don't call it until we
    // are done manipulating the object.
#define DNOW_BUFSIZE 2048
    char     msgChars[DNOW_BUFSIZE];
    Buffer   msgBuffer(msgChars, DNOW_BUFSIZE);
    Variant::List list_;
    stringstream v1key, v2key;

    if (qmf1Support) {
        string sBuf;

        v1key << "console.obj.1.0." << object->getPackageName() << "." << object->getClassName();
        encodeHeader(msgBuffer, 'c');
        object->writeProperties(sBuf);
        msgBuffer.putRawData(sBuf);
    }

    if (qmf2Support) {
        Variant::Map  map_;
        Variant::Map  values;

        map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                               object->getClassName(),
                                               "_data",
                                               object->getMd5Sum());
        object->writeTimestamps(map_);
        object->mapEncodeValues(values, true, false);
        map_["_values"] = values;
        list_.push_back(map_);
        v2key << "agent.ind.data." << keyifyNameStr(object->getPackageName())
              << "." << keyifyNameStr(object->getClassName())
              << "." << vendorNameKey
              << "." << productNameKey;
        if (!instanceNameKey.empty())
            v2key << "." << instanceNameKey;
    }

    object = 0;
    managementObjects.erase(oid);

    // object deleted, ok to drop lock now.

    if (qmf1Support) {
        uint32_t contentSize = msgBuffer.getPosition();
        msgBuffer.reset();
        sendBufferLH(msgBuffer, contentSize, mExchange, v1key.str());
        QPID_LOG(trace, "SEND Immediate(delete) ContentInd to=" << v1key.str());
    }

    if (qmf2Support) {
        Variant::Map  headers;
        headers["method"] = "indication";
        headers["qmf.opcode"] = "_data_indication";
        headers["qmf.content"] = "_data";
        headers["qmf.agent"] = name_address;

        string content;
        ListCodec::encode(list_, content);
        sendBufferLH(content, "", headers, "amqp/list", v2Topic, v2key.str());
        QPID_LOG(trace, "SEND Immediate(delete) ContentInd to=" << v2key.str());
    }
}

void ManagementAgent::sendCommandCompleteLH(const string& replyToKey, uint32_t sequence,
                                            uint32_t code, const string& text)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    encodeHeader (outBuffer, 'z', sequence);
    outBuffer.putLong (code);
    outBuffer.putShortString (text);
    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
    QPID_LOG(trace, "SEND CommandCompleteInd code=" << code << " text=" << text << " to=" <<
             replyToKey << " seq=" << sequence);
}

void ManagementAgent::sendExceptionLH(const string& replyToKey, const string& cid,
                                      const string& text, uint32_t code, bool viaLocal)
{
    static const string addr_exchange("qmf.default.direct");

    Variant::Map map;
    Variant::Map headers;
    Variant::Map values;
    string content;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_exception";
    headers["qmf.agent"] = viaLocal ? "broker" : name_address;

    values["error_code"] = code;
    values["error_text"] = text;
    map["_values"] = values;

    MapCodec::encode(map, content);
    sendBufferLH(content, cid, headers, "amqp/map", v2Direct, replyToKey);

    QPID_LOG(trace, "SENT Exception code=" << code <<" text=" << text);
}

bool ManagementAgent::dispatchCommand (Deliverable&      deliverable,
                                       const string&     routingKey,
                                       const FieldTable* /*args*/,
                                       const bool topic,
                                       int qmfVersion)
{
    sys::Mutex::ScopedLock lock (userLock);
    Message&  msg = ((DeliverableMessage&) deliverable).getMessage ();

    if (topic && qmfVersion == 1) {

        // qmf1 is bound only to the topic management exchange.
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

        if (routingKey.length() > 6) {

            if (routingKey.compare(0, 9, "agent.1.0") == 0) {
                dispatchAgentCommandLH(msg);
                return false;
            }

            if (routingKey.compare(0, 8, "agent.1.") == 0) {
                return authorizeAgentMessageLH(msg);
            }

            if (routingKey.compare(0, 7, "schema.") == 0) {
                dispatchAgentCommandLH(msg);
                return true;
            }
        }
    }

    if (qmfVersion == 2) {

        if (topic) {
            // Intercept messages bound to:
            //  "console.ind.locate.# - process these messages, and also allow them to be forwarded.
            if (routingKey == "console.request.agent_locate") {
                dispatchAgentCommandLH(msg);
                return true;
            }

        } else { // direct exchange

            // Intercept messages bound to:
            //  "broker" - generic alias for the local broker
            //  "<name_address>" - the broker agent's proper name
            // and do not forward them futher
            if (routingKey == "broker" || routingKey == name_address) {
                dispatchAgentCommandLH(msg, routingKey == "broker");
                return false;
            }
        }
    }

    return true;
}

void ManagementAgent::handleMethodRequestLH(Buffer& inBuffer, const string& replyToKey, uint32_t sequence, const ConnectionToken* connToken)
{
    moveNewObjectsLH();

    string   methodName;
    string   packageName;
    string   className;
    uint8_t  hash[16];
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;
    AclModule* acl = broker->getAcl();
    string inArgs;

    string sBuf;
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

    if (disallowAllV1Methods) {
        outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
        outBuffer.putMediumString("QMFv1 methods forbidden on this broker, use QMFv2");
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
        QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN reason='All QMFv1 Methods Forbidden' seq=" << sequence);
        return;
    }

    DisallowedMethods::const_iterator i = disallowed.find(make_pair(className, methodName));
    if (i != disallowed.end()) {
        outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
        outBuffer.putMediumString(i->second);
        outLen = MA_BUFFER_SIZE - outBuffer.available();
        outBuffer.reset();
        sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
        QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN text=" << i->second << " seq=" << sequence);
        return;
    }

    string userId = ((const qpid::broker::ConnectionState*) connToken)->getUserId();
    if (acl != 0) {
        map<acl::Property, string> params;
        params[acl::PROP_SCHEMAPACKAGE] = packageName;
        params[acl::PROP_SCHEMACLASS]   = className;

        if (!acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_METHOD, methodName, &params)) {
            outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
            outBuffer.putMediumString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
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
                sys::Mutex::ScopedUnlock u(userLock);
                string outBuf;
                iter->second->doMethod(methodName, inArgs, outBuf, userId);
                outBuffer.putRawData(outBuf);
            } catch(exception& e) {
                outBuffer.restore();
                outBuffer.putLong(Manageable::STATUS_EXCEPTION);
                outBuffer.putMediumString(e.what());
            }
    }

    outLen = MA_BUFFER_SIZE - outBuffer.available();
    outBuffer.reset();
    sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
    QPID_LOG(trace, "SEND MethodResponse (v1) to=" << replyToKey << " seq=" << sequence);
}


void ManagementAgent::handleMethodRequestLH (const string& body, const string& replyTo,
                                             const string& cid, const ConnectionToken* connToken, bool viaLocal)
{
    moveNewObjectsLH();

    string   methodName;
    Variant::Map inMap;
    MapCodec::decode(body, inMap);
    Variant::Map::const_iterator oid, mid;
    string content;
    string error;
    uint32_t errorCode(0);

    Variant::Map outMap;
    Variant::Map headers;

    headers["method"] = "response";
    headers["qmf.opcode"] = "_method_response";
    headers["qmf.agent"] = viaLocal ? "broker" : name_address;

    if ((oid = inMap.find("_object_id")) == inMap.end() ||
        (mid = inMap.find("_method_name")) == inMap.end()) {
        sendExceptionLH(replyTo, cid, Manageable::StatusText(Manageable::STATUS_PARAMETER_INVALID),
                        Manageable::STATUS_PARAMETER_INVALID, viaLocal);
        return;
    }

    ObjectId objId;
    Variant::Map inArgs;
    Variant::Map callMap;

    try {
        // coversions will throw if input is invalid.
        objId = ObjectId(oid->second.asMap());
        methodName = mid->second.getString();

        mid = inMap.find("_arguments");
        if (mid != inMap.end()) {
            inArgs = (mid->second).asMap();
        }
    } catch(exception& e) {
        sendExceptionLH(replyTo, cid, e.what(), Manageable::STATUS_EXCEPTION, viaLocal);
        return;
    }

    ManagementObjectMap::iterator iter = managementObjects.find(objId);

    if (iter == managementObjects.end() || iter->second->isDeleted()) {
        stringstream estr;
        estr << "No object found with ID=" << objId;
        sendExceptionLH(replyTo, cid, estr.str(), 1, viaLocal);
        return;
    }

    // validate
    AclModule* acl = broker->getAcl();
    DisallowedMethods::const_iterator i;

    i = disallowed.find(make_pair(iter->second->getClassName(), methodName));
    if (i != disallowed.end()) {
        sendExceptionLH(replyTo, cid, i->second, Manageable::STATUS_FORBIDDEN, viaLocal);
        return;
    }

    string userId = ((const qpid::broker::ConnectionState*) connToken)->getUserId();
    if (acl != 0) {
        map<acl::Property, string> params;
        params[acl::PROP_SCHEMAPACKAGE] = iter->second->getPackageName();
        params[acl::PROP_SCHEMACLASS]   = iter->second->getClassName();

        if (!acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_METHOD, methodName, &params)) {
            sendExceptionLH(replyTo, cid, Manageable::StatusText(Manageable::STATUS_FORBIDDEN),
                            Manageable::STATUS_FORBIDDEN, viaLocal);
            return;
        }
    }

    // invoke the method

    QPID_LOG(trace, "RECV MethodRequest (v2) class=" << iter->second->getPackageName()
             << ":" << iter->second->getClassName() << " method=" <<
             methodName << " replyTo=" << replyTo << " objId=" << objId << " inArgs=" << inArgs);

    try {
        sys::Mutex::ScopedUnlock u(userLock);
        iter->second->doMethod(methodName, inArgs, callMap, userId);
        errorCode = callMap["_status_code"].asUint32();
        if (errorCode == 0) {
            outMap["_arguments"] = Variant::Map();
            for (Variant::Map::const_iterator iter = callMap.begin();
                 iter != callMap.end(); iter++)
                if (iter->first != "_status_code" && iter->first != "_status_text")
                    outMap["_arguments"].asMap()[iter->first] = iter->second;
        } else
            error = callMap["_status_text"].asString();
    } catch(exception& e) {
        sendExceptionLH(replyTo, cid, e.what(), Manageable::STATUS_EXCEPTION, viaLocal);
        return;
    }

    if (errorCode != 0) {
        sendExceptionLH(replyTo, cid, error, errorCode, viaLocal);
        return;
    }

    MapCodec::encode(outMap, content);
    sendBufferLH(content, cid, headers, "amqp/map", v2Direct, replyTo);
    QPID_LOG(trace, "SEND MethodResponse (v2) to=" << replyTo << " seq=" << cid << " map=" << outMap);
}


void ManagementAgent::handleBrokerRequestLH (Buffer&, const string& replyToKey, uint32_t sequence)
{
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    QPID_LOG(trace, "RECV BrokerRequest replyTo=" << replyToKey);

    encodeHeader (outBuffer, 'b', sequence);
    uuid.encode  (outBuffer);

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    outBuffer.reset ();
    sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
    QPID_LOG(trace, "SEND BrokerResponse to=" << replyToKey);
}

void ManagementAgent::handlePackageQueryLH (Buffer&, const string& replyToKey, uint32_t sequence)
{
    QPID_LOG(trace, "RECV PackageQuery replyTo=" << replyToKey);
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;

    for (PackageMap::iterator pIter = packages.begin ();
         pIter != packages.end ();
         pIter++)
    {
        encodeHeader (outBuffer, 'p', sequence);
        encodePackageIndication (outBuffer, pIter);
    }

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    if (outLen) {
        outBuffer.reset ();
        sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
        QPID_LOG(trace, "SEND PackageInd to=" << replyToKey << " seq=" << sequence);
    }

    sendCommandCompleteLH(replyToKey, sequence);
}

void ManagementAgent::handlePackageIndLH (Buffer& inBuffer, const string& replyToKey, uint32_t sequence)
{
    string packageName;

    inBuffer.getShortString(packageName);

    QPID_LOG(trace, "RECV PackageInd package=" << packageName << " replyTo=" << replyToKey << " seq=" << sequence);

    findOrAddPackageLH(packageName);
}

void ManagementAgent::handleClassQueryLH(Buffer& inBuffer, const string& replyToKey, uint32_t sequence)
{
    string packageName;

    inBuffer.getShortString(packageName);

    QPID_LOG(trace, "RECV ClassQuery package=" << packageName << " replyTo=" << replyToKey << " seq=" << sequence);

    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end())
    {
        typedef std::pair<SchemaClassKey, uint8_t> _ckeyType;
        std::list<_ckeyType> classes;
        ClassMap &cMap = pIter->second;
        for (ClassMap::iterator cIter = cMap.begin();
             cIter != cMap.end();
             cIter++) {
            if (cIter->second.hasSchema()) {
                classes.push_back(make_pair(cIter->first, cIter->second.kind));
            }
        }

        while (classes.size()) {
            Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
            uint32_t outLen;

            encodeHeader(outBuffer, 'q', sequence);
            encodeClassIndication(outBuffer, packageName, classes.front().first, classes.front().second);

            outLen = MA_BUFFER_SIZE - outBuffer.available();
            outBuffer.reset();
            sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
            QPID_LOG(trace, "SEND ClassInd class=" << packageName << ":" << classes.front().first.name <<
                     "(" << Uuid(classes.front().first.hash) << ") to=" << replyToKey << " seq=" << sequence);
            classes.pop_front();
        }

    }
    sendCommandCompleteLH(replyToKey, sequence);
}

void ManagementAgent::handleClassIndLH (Buffer& inBuffer, const string& replyToKey, uint32_t)
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
        sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
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
        string schema;
        writeSchemaCall(schema);
        buf.putRawData(schema);
    } else
        buf.putRawData(reinterpret_cast<uint8_t*>(&data[0]), data.size());
}

void ManagementAgent::handleSchemaRequestLH(Buffer& inBuffer, const string& replyToKey, uint32_t sequence)
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
                sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
                QPID_LOG(trace, "SEND SchemaResponse to=" << replyToKey << " seq=" << sequence);
            }
            else
                sendCommandCompleteLH(replyToKey, sequence, 1, "Schema not available");
        }
        else
            sendCommandCompleteLH(replyToKey, sequence, 1, "Class key not found");
    }
    else
        sendCommandCompleteLH(replyToKey, sequence, 1, "Package not found");
}

void ManagementAgent::handleSchemaResponseLH(Buffer& inBuffer, const string& /*replyToKey*/, uint32_t sequence)
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
                encodeClassIndication(outBuffer, pIter->first, cIter->first, cIter->second.kind);
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                sendBufferLH(outBuffer, outLen, mExchange, "schema.class");
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
    list<ObjectId> deleteList;

    for (RemoteAgentMap::const_iterator aIter = remoteAgents.begin(); aIter != remoteAgents.end(); aIter++) {
        bool found = false;

        for (ManagementObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++) {
            if (iter->first == aIter->first && !iter->second->isDeleted()) {
                found = true;
                break;
            }
        }

        if (!found)
            deleteList.push_back(aIter->first);
    }

    for (list<ObjectId>::const_iterator dIter = deleteList.begin(); dIter != deleteList.end(); dIter++)
        remoteAgents.erase(*dIter);
}

void ManagementAgent::handleAttachRequestLH (Buffer& inBuffer, const string& replyToKey, uint32_t sequence, const ConnectionToken* connToken)
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
        sendCommandCompleteLH(replyToKey, sequence, 1, "Connection already has remote agent");
        return;
    }

    inBuffer.getShortString(label);
    systemId.decode(inBuffer);
    requestedBrokerBank = inBuffer.getLong();
    requestedAgentBank  = inBuffer.getLong();

    QPID_LOG(trace, "RECV (Agent)AttachRequest label=" << label << " reqBrokerBank=" << requestedBrokerBank <<
             " reqAgentBank=" << requestedAgentBank << " replyTo=" << replyToKey << " seq=" << sequence);

    assignedBank = assignBankLH(requestedAgentBank);

    boost::shared_ptr<RemoteAgent> agent(new RemoteAgent(*this));
    agent->brokerBank = brokerBank;
    agent->agentBank  = assignedBank;
    agent->routingKey = replyToKey;
    agent->connectionRef = connectionRef;
    agent->mgmtObject = new _qmf::Agent (this, agent.get());
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
    sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
    QPID_LOG(trace, "SEND AttachResponse brokerBank=" << brokerBank << " agentBank=" << assignedBank <<
             " to=" << replyToKey << " seq=" << sequence);
}

void ManagementAgent::handleGetQueryLH(Buffer& inBuffer, const string& replyToKey, uint32_t sequence)
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
                string sBuf;
                encodeHeader(outBuffer, 'g', sequence);
                object->writeProperties(sBuf);
                outBuffer.putRawData(sBuf);
                sBuf.clear();
                object->writeStatistics(sBuf, true);
                outBuffer.putRawData(sBuf);
                outLen = MA_BUFFER_SIZE - outBuffer.available ();
                outBuffer.reset ();
                sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
                QPID_LOG(trace, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
            }
        }
        sendCommandCompleteLH(replyToKey, sequence);
        return;
    }

    string className (value->get<string>());
    std::list<ObjectId>matches;

    // build up a set of all objects to be dumped
    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         iter++) {
        ManagementObject* object = iter->second;
        if (object->getClassName () == className) {
            matches.push_back(object->getObjectId());
        }
    }

    // send them (as sendBufferLH drops the userLock)
    Buffer   outBuffer (outputBuffer, MA_BUFFER_SIZE);
    uint32_t outLen;
    while (matches.size()) {
        ObjectId objId = matches.front();
        ManagementObjectMap::iterator oIter = managementObjects.find( objId );
        if (oIter != managementObjects.end()) {
            ManagementObject* object = oIter->second;

            if (object->getConfigChanged() || object->getInstChanged())
                object->setUpdateTime();

            if (!object->isDeleted()) {
                string sProps, sStats;
                object->writeProperties(sProps);
                object->writeStatistics(sStats, true);

                size_t len = 8 + sProps.length() + sStats.length();   // 8 == size of header in bytes.
                if (len > MA_BUFFER_SIZE) {
                    QPID_LOG(error, "Object " << objId << " too large for output buffer - discarded!");
                } else {
                    if (outBuffer.available() < len) {  // not enough room in current buffer, send it.
                        outLen = MA_BUFFER_SIZE - outBuffer.available ();
                        outBuffer.reset ();
                        sendBufferLH(outBuffer, outLen, dExchange, replyToKey);   // drops lock
                        QPID_LOG(trace, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
                        continue;  // lock dropped, need to re-find _SAME_ objid as it may have been deleted.
                    }
                    encodeHeader(outBuffer, 'g', sequence);
                    outBuffer.putRawData(sProps);
                    outBuffer.putRawData(sStats);
                }
            }
        }
        matches.pop_front();
    }

    outLen = MA_BUFFER_SIZE - outBuffer.available ();
    if (outLen) {
        outBuffer.reset ();
        sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
        QPID_LOG(trace, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
    }

    sendCommandCompleteLH(replyToKey, sequence);
}


void ManagementAgent::handleGetQueryLH(const string& body, const string& replyTo, const string& cid, bool viaLocal)
{
    moveNewObjectsLH();

    Variant::Map inMap;
    Variant::Map::const_iterator i;
    Variant::Map headers;

    MapCodec::decode(body, inMap);
    QPID_LOG(trace, "RECV GetQuery (v2): map=" << inMap << " seq=" << cid);

    headers["method"] = "response";
    headers["qmf.opcode"] = "_query_response";
    headers["qmf.content"] = "_data";
    headers["qmf.agent"] = viaLocal ? "broker" : name_address;

    /*
     * Unpack the _what element of the query.  Currently we only support OBJECT queries.
     */
    i = inMap.find("_what");
    if (i == inMap.end()) {
        sendExceptionLH(replyTo, cid, "_what element missing in Query");
        return;
    }

    if (i->second.getType() != qpid::types::VAR_STRING) {
        sendExceptionLH(replyTo, cid, "_what element is not a string");
        return;
    }

    if (i->second.asString() != "OBJECT") {
        sendExceptionLH(replyTo, cid, "Query for _what => '" + i->second.asString() + "' not supported");
        return;
    }

    string className;
    string packageName;

    /*
     * Handle the _schema_id element, if supplied.
     */
    i = inMap.find("_schema_id");
    if (i != inMap.end() && i->second.getType() == qpid::types::VAR_MAP) {
        const Variant::Map& schemaIdMap(i->second.asMap());

        Variant::Map::const_iterator s_iter = schemaIdMap.find("_class_name");
        if (s_iter != schemaIdMap.end() && s_iter->second.getType() == qpid::types::VAR_STRING)
            className = s_iter->second.asString();

        s_iter = schemaIdMap.find("_package_name");
        if (s_iter != schemaIdMap.end() && s_iter->second.getType() == qpid::types::VAR_STRING)
            packageName = s_iter->second.asString();
    }


    /*
     * Unpack the _object_id element of the query if it is present.  If it is present, find that one
     * object and return it.  If it is not present, send a class-based result.
     */
    i = inMap.find("_object_id");
    if (i != inMap.end() && i->second.getType() == qpid::types::VAR_MAP) {
        Variant::List list_;
        ObjectId objId(i->second.asMap());

        ManagementObjectMap::iterator iter = managementObjects.find(objId);
        if (iter != managementObjects.end()) {
            ManagementObject* object = iter->second;

            if (object->getConfigChanged() || object->getInstChanged())
                object->setUpdateTime();

            if (!object->isDeleted()) {
                Variant::Map  map_;
                Variant::Map values;
                Variant::Map oidMap;

                object->mapEncodeValues(values, true, true); // write both stats and properties
                objId.mapEncode(oidMap);
                map_["_values"] = values;
                map_["_object_id"] = oidMap;
                map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                       object->getClassName(),
                                                       "_data",
                                                       object->getMd5Sum());
                list_.push_back(map_);
            }

            string content;

            ListCodec::encode(list_, content);
            sendBufferLH(content, cid, headers, "amqp/list", v2Direct, replyTo);
            QPID_LOG(trace, "SENT QueryResponse (query by object_id) to=" << replyTo);
            return;
        }
    } else {
        // send class-based result.
        Variant::List _list;
        Variant::List _subList;
        unsigned int objCount = 0;

        for (ManagementObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++) {
            ManagementObject* object = iter->second;
            if (object->getClassName() == className &&
                (packageName.empty() || object->getPackageName() == packageName)) {


                if (!object->isDeleted()) {
                    Variant::Map  map_;
                    Variant::Map values;
                    Variant::Map oidMap;

                    if (object->getConfigChanged() || object->getInstChanged())
                        object->setUpdateTime();

                    object->writeTimestamps(map_);
                    object->mapEncodeValues(values, true, true); // write both stats and properties
                    iter->first.mapEncode(oidMap);

                    map_["_values"] = values;
                    map_["_object_id"] = oidMap;
                    map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                           object->getClassName(),
                                                           "_data",
                                                           object->getMd5Sum());
                    _subList.push_back(map_);
                    if (++objCount >= maxV2ReplyObjs) {
                        objCount = 0;
                        _list.push_back(_subList);
                        _subList.clear();
                    }
                }
            }
        }

        if (_subList.size())
            _list.push_back(_subList);

        headers["partial"] = Variant();
        string content;
        while (_list.size() > 1) {
            ListCodec::encode(_list.front().asList(), content);
            sendBufferLH(content, cid, headers, "amqp/list", v2Direct, replyTo);
            _list.pop_front();
            QPID_LOG(trace, "SENT QueryResponse (partial, query by schema_id) to=" << replyTo << " size=" << content.length());
        }
        headers.erase("partial");
        ListCodec::encode(_list.size() ? _list.front().asList() : Variant::List(), content);
        sendBufferLH(content, cid, headers, "amqp/list", v2Direct, replyTo);
        QPID_LOG(trace, "SENT QueryResponse (query by schema_id) to=" << replyTo << " size=" << content.length());
        return;
    }

    // Unrecognized query - Send empty message to indicate CommandComplete
    string content;
    ListCodec::encode(Variant::List(), content);
    sendBufferLH(content, cid, headers, "amqp/list", v2Direct, replyTo);
    QPID_LOG(trace, "SENT QueryResponse (empty) to=" << replyTo);
}


void ManagementAgent::handleLocateRequestLH(const string&, const string& replyTo,
                                            const string& cid)
{
    QPID_LOG(trace, "RCVD AgentLocateRequest");

    Variant::Map map;
    Variant::Map headers;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_locate_response";
    headers["qmf.agent"] = name_address;

    map["_values"] = attrMap;
    map["_values"].asMap()["timestamp"] = uint64_t(sys::Duration(sys::EPOCH, sys::now()));
    map["_values"].asMap()["heartbeat_interval"] = interval;
    map["_values"].asMap()["epoch"] = bootSequence;

    string content;
    MapCodec::encode(map, content);
    sendBufferLH(content, cid, headers, "amqp/map", v2Direct, replyTo);
    clientWasAdded = true;

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
    string cid;

    if (msg.encodedSize() > MA_BUFFER_SIZE)
        return false;

    msg.encodeContent(inBuffer);
    uint32_t bufferLen = inBuffer.getPosition();
    inBuffer.reset();

    const framing::MessageProperties* p =
      msg.getFrames().getHeaders()->get<framing::MessageProperties>();

    const framing::FieldTable *headers = msg.getApplicationHeaders();

    if (headers && msg.getAppId() == "qmf2")
    {
        mapMsg = true;

        if (p && p->hasCorrelationId()) {
            cid = p->getCorrelationId();
        }

        if (headers->getAsString("qmf.opcode") == "_method_request")
        {
            methodReq = true;

            // extract object id and method name

            string body;
            inBuffer.getRawData(body, bufferLen);
            Variant::Map inMap;
            MapCodec::decode(body, inMap);
            Variant::Map::const_iterator oid, mid;

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
            } catch(exception& /*e*/) {
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
            string cid;
            if (p && p->hasCorrelationId())
                cid = p->getCorrelationId();

            if (mapMsg) {
                sendExceptionLH(replyToKey, cid, Manageable::StatusText(Manageable::STATUS_FORBIDDEN),
                                Manageable::STATUS_FORBIDDEN, false);
            } else {

                Buffer   outBuffer(outputBuffer, MA_BUFFER_SIZE);
                uint32_t outLen;

                encodeHeader(outBuffer, 'm', sequence);
                outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
                outBuffer.putMediumString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
                outLen = MA_BUFFER_SIZE - outBuffer.available();
                outBuffer.reset();
                sendBufferLH(outBuffer, outLen, dExchange, replyToKey);
            }

            QPID_LOG(trace, "SEND MethodResponse status=FORBIDDEN" << " seq=" << sequence);
        }

        return false;
    }

    return true;
}

void ManagementAgent::dispatchAgentCommandLH(Message& msg, bool viaLocal)
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
    if (headers && msg.getAppId() == "qmf2")
    {
        string opcode = headers->getAsString("qmf.opcode");
        string contentType = headers->getAsString("qmf.content");
        string body;
        string cid;
        inBuffer.getRawData(body, bufferLen);

        if (p && p->hasCorrelationId()) {
            cid = p->getCorrelationId();
        }

        if (opcode == "_method_request")
            return handleMethodRequestLH(body, replyToKey, cid, msg.getPublisher(), viaLocal);
        else if (opcode == "_query_request")
            return handleGetQueryLH(body, replyToKey, cid, viaLocal);
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
    sendBufferLH(outBuffer, outLen, mExchange, "schema.package");
    QPID_LOG(trace, "SEND PackageInd package=" << name << " to=schema.package");

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
                                            const std::string packageName,
                                            const SchemaClassKey key,
                                            uint8_t kind)
{
    buf.putOctet(kind);
    buf.putShortString(packageName);
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
        
        uint8_t superType = 0; //inBuffer.getOctet();

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

void ManagementAgent::disallow(const string& className, const string& methodName, const string& message) {
    disallowed[make_pair(className, methodName)] = message;
}

void ManagementAgent::SchemaClassKey::mapEncode(Variant::Map& _map) const {
    _map["_cname"] = name;
    _map["_hash"] = qpid::types::Uuid(hash);
}

void ManagementAgent::SchemaClassKey::mapDecode(const Variant::Map& _map) {
    Variant::Map::const_iterator i;

    if ((i = _map.find("_cname")) != _map.end()) {
        name = i->second.asString();
    }

    if ((i = _map.find("_hash")) != _map.end()) {
        const qpid::types::Uuid& uuid = i->second.asUuid();
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

void ManagementAgent::SchemaClass::mapEncode(Variant::Map& _map) const {
    _map["_type"] = kind;
    _map["_pending_sequence"] = pendingSequence;
    _map["_data"] = data;
}

void ManagementAgent::SchemaClass::mapDecode(const Variant::Map& _map) {
    Variant::Map::const_iterator i;

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

void ManagementAgent::exportSchemas(string& out) {
    Variant::List list_;
    Variant::Map map_, kmap, cmap;

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

    ListCodec::encode(list_, out);
}

void ManagementAgent::importSchemas(qpid::framing::Buffer& inBuf) {

    string buf(inBuf.getPointer(), inBuf.available());
    Variant::List content;
    ListCodec::decode(buf, content);
    Variant::List::const_iterator l;


    for (l = content.begin(); l != content.end(); l++) {
        string package;
        SchemaClassKey key;
        SchemaClass klass;
        Variant::Map map_, kmap, cmap;
        Variant::Map::const_iterator i;
        
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

void ManagementAgent::RemoteAgent::mapEncode(Variant::Map& map_) const {
    Variant::Map _objId, _values;

    map_["_brokerBank"] = brokerBank;
    map_["_agentBank"] = agentBank;
    map_["_routingKey"] = routingKey;

    connectionRef.mapEncode(_objId);
    map_["_object_id"] = _objId;

    mgmtObject->mapEncodeValues(_values, true, false);
    map_["_values"] = _values;
}

void ManagementAgent::RemoteAgent::mapDecode(const Variant::Map& map_) {
    Variant::Map::const_iterator i;

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

void ManagementAgent::exportAgents(string& out) {
    Variant::List list_;
    Variant::Map map_, omap, amap;

    for (RemoteAgentMap::const_iterator i = remoteAgents.begin();
         i != remoteAgents.end();
         ++i)
    {
        // TODO aconway 2010-03-04: see comment in ManagementAgent::RemoteAgent::encode
        boost::shared_ptr<RemoteAgent> agent(i->second);

        map_.clear();
        amap.clear();

        agent->mapEncode(amap);
        map_["_remote_agent"] = amap;
        list_.push_back(map_);
    }

    ListCodec::encode(list_, out);
}

void ManagementAgent::importAgents(qpid::framing::Buffer& inBuf) {
    string buf(inBuf.getPointer(), inBuf.available());
    Variant::List content;
    ListCodec::decode(buf, content);
    Variant::List::const_iterator l;
    sys::Mutex::ScopedLock lock(userLock);

    for (l = content.begin(); l != content.end(); l++) {
        boost::shared_ptr<RemoteAgent> agent(new RemoteAgent(*this));
        Variant::Map map_;
        Variant::Map::const_iterator i;

        map_ = l->asMap();

        if ((i = map_.find("_remote_agent")) != map_.end()) {

            agent->mapDecode(i->second.asMap());

            addObject (agent->mgmtObject, 0, false);
            remoteAgents[agent->connectionRef] = agent;
        }
    }
}

namespace {
bool isNotDeleted(const ManagementObjectMap::value_type& value) {
    return !value.second->isDeleted();
}

size_t countNotDeleted(const ManagementObjectMap& map) {
    return std::count_if(map.begin(), map.end(), isNotDeleted);
}

void dumpMap(std::ostream& o, const ManagementObjectMap& map) {
    for (ManagementObjectMap::const_iterator i = map.begin(); i != map.end(); ++i) {
        if (!i->second->isDeleted())
            o << endl << "   " << i->second->getObjectId().getV2Key();
    }
}
} // namespace

string ManagementAgent::debugSnapshot() {
    ostringstream msg;
    msg << " management snapshot:";
    for (RemoteAgentMap::const_iterator i=remoteAgents.begin();
         i != remoteAgents.end(); ++i)
        msg << " " << i->second->routingKey;
    msg << " packages: " << packages.size();
    msg << " objects: " << countNotDeleted(managementObjects);
    msg << " new objects: " << countNotDeleted(newManagementObjects);
    return msg.str();
}

Variant::Map ManagementAgent::toMap(const FieldTable& from)
{
    Variant::Map map;

    for (FieldTable::const_iterator iter = from.begin(); iter != from.end(); iter++) {
        const string& key(iter->first);
        const FieldTable::ValuePtr& val(iter->second);

        map[key] = toVariant(val);
    }

    return map;
}

Variant::List ManagementAgent::toList(const List& from)
{
    Variant::List _list;

    for (List::const_iterator iter = from.begin(); iter != from.end(); iter++) {
        const List::ValuePtr& val(*iter);

        _list.push_back(toVariant(val));
    }

    return _list;
}

qpid::framing::FieldTable ManagementAgent::fromMap(const Variant::Map& from)
{
    qpid::framing::FieldTable ft;

    for (Variant::Map::const_iterator iter = from.begin();
         iter != from.end();
         iter++) {
        const string& key(iter->first);
        const Variant& val(iter->second);

        ft.set(key, toFieldValue(val));
    }

    return ft;
}


List ManagementAgent::fromList(const Variant::List& from)
{
    List fa;

    for (Variant::List::const_iterator iter = from.begin();
         iter != from.end();
         iter++) {
        const Variant& val(*iter);

        fa.push_back(toFieldValue(val));
    }

    return fa;
}


boost::shared_ptr<FieldValue> ManagementAgent::toFieldValue(const Variant& in)
{

    switch(in.getType()) {

    case types::VAR_VOID:   return boost::shared_ptr<FieldValue>(new VoidValue());
    case types::VAR_BOOL:   return boost::shared_ptr<FieldValue>(new BoolValue(in.asBool()));
    case types::VAR_UINT8:  return boost::shared_ptr<FieldValue>(new Unsigned8Value(in.asUint8()));
    case types::VAR_UINT16: return boost::shared_ptr<FieldValue>(new Unsigned16Value(in.asUint16()));
    case types::VAR_UINT32: return boost::shared_ptr<FieldValue>(new Unsigned32Value(in.asUint32()));
    case types::VAR_UINT64: return boost::shared_ptr<FieldValue>(new Unsigned64Value(in.asUint64()));
    case types::VAR_INT8:   return boost::shared_ptr<FieldValue>(new Integer8Value(in.asInt8()));
    case types::VAR_INT16:  return boost::shared_ptr<FieldValue>(new Integer16Value(in.asInt16()));
    case types::VAR_INT32:  return boost::shared_ptr<FieldValue>(new Integer32Value(in.asInt32()));
    case types::VAR_INT64:  return boost::shared_ptr<FieldValue>(new Integer64Value(in.asInt64()));
    case types::VAR_FLOAT:  return boost::shared_ptr<FieldValue>(new FloatValue(in.asFloat()));
    case types::VAR_DOUBLE: return boost::shared_ptr<FieldValue>(new DoubleValue(in.asDouble()));
    case types::VAR_STRING: return boost::shared_ptr<FieldValue>(new Str16Value(in.asString()));
    case types::VAR_UUID:   return boost::shared_ptr<FieldValue>(new UuidValue(in.asUuid().data()));
    case types::VAR_MAP:    return boost::shared_ptr<FieldValue>(new FieldTableValue(ManagementAgent::fromMap(in.asMap())));
    case types::VAR_LIST:   return boost::shared_ptr<FieldValue>(new ListValue(ManagementAgent::fromList(in.asList())));
    }

    QPID_LOG(error, "Unknown Variant type - not converted: [" << in.getType() << "]");
    return boost::shared_ptr<FieldValue>(new VoidValue());
}

// stolen from qpid/client/amqp0_10/Codecs.cpp - TODO: make Codecs public, and remove this dup.
Variant ManagementAgent::toVariant(const boost::shared_ptr<FieldValue>& in)
{
    const string iso885915("iso-8859-15");
    const string utf8("utf8");
    const string utf16("utf16");
    //const string binary("binary");
    const string amqp0_10_binary("amqp0-10:binary");
    //const string amqp0_10_bit("amqp0-10:bit");
    const string amqp0_10_datetime("amqp0-10:datetime");
    const string amqp0_10_struct("amqp0-10:struct");
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
            out = qpid::types::Uuid(data);
        } break;

        //TODO: figure out whether and how to map values with codes 0x40-0xd8

    case 0xf0: break;//void, which is the default value for Variant
        // case 0xf1: out.setEncoding(amqp0_10_bit); break;//treat 'bit' as void, which is the default value for Variant

        //Variable Width types:
        //strings:
    case 0x80: // str8
    case 0x90: // str16
    case 0xa0: // str32
        out = in->get<string>();
        out.setEncoding(amqp0_10_binary);
        break;

    case 0x84: // str8
    case 0x94: // str16
        out = in->get<string>();
        out.setEncoding(iso885915);
        break;

    case 0x85: // str8
    case 0x95: // str16
        out = in->get<string>();
        out.setEncoding(utf8);
        break;

    case 0x86: // str8
    case 0x96: // str16
        out = in->get<string>();
        out.setEncoding(utf16);
        break;

    case 0xab:  // str32
        out = in->get<string>();
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


// Build up a list of the current set of deleted objects that are pending their
// next (last) publish-ment.
void ManagementAgent::exportDeletedObjects(DeletedObjectList& outList)
{
    outList.clear();

    sys::Mutex::ScopedLock lock (userLock);
    list<pair<ObjectId, ManagementObject*> > deleteList;

    moveNewObjectsLH();

    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         iter++) {
        ManagementObject* object = iter->second;

        if (object->isDeleted()) {
            deleteList.push_back(pair<ObjectId, ManagementObject*>(iter->first, object));
        }
    }

    // Remove Deleted objects, and save for later publishing...
    //
    for (list<pair<ObjectId, ManagementObject*> >::reverse_iterator iter = deleteList.rbegin();
         iter != deleteList.rend();
         iter++) {

        ManagementObject* delObj = iter->second;
        DeletedObject::shared_ptr dptr(new DeletedObject());
        std::string classkey(delObj->getPackageName() + std::string(":") + delObj->getClassName());
        bool send_stats = (delObj->hasInst() && (delObj->getInstChanged() || delObj->getForcePublish()));

        dptr->packageName = delObj->getPackageName();
        dptr->className = delObj->getClassName();
        stringstream oid;
        oid << delObj->getObjectId();
        dptr->objectId = oid.str();

        if (qmf1Support) {
            delObj->writeProperties(dptr->encodedV1Config);
            if (send_stats) {
                delObj->writeStatistics(dptr->encodedV1Inst);
            }
        }

        if (qmf2Support) {
            Variant::Map map_;
            Variant::Map values;
            Variant::Map oid;

            delObj->getObjectId().mapEncode(oid);
            map_["_object_id"] = oid;
            map_["_schema_id"] = mapEncodeSchemaId(delObj->getPackageName(),
                                                   delObj->getClassName(),
                                                   "_data",
                                                   delObj->getMd5Sum());
            delObj->writeTimestamps(map_);
            delObj->mapEncodeValues(values, true, send_stats);
            map_["_values"] = values;

            dptr->encodedV2 = map_;
        }

        pendingDeletedObjs[classkey].push_back(dptr);

        delete iter->second;
        managementObjects.erase(iter->first);
    }

    // now copy the pending deletes into the outList

    for (PendingDeletedObjsMap::iterator mIter = pendingDeletedObjs.begin();
         mIter != pendingDeletedObjs.end(); mIter++) {
        for (DeletedObjectList::iterator lIter = mIter->second.begin();
             lIter != mIter->second.end(); lIter++) {
            outList.push_back(*lIter);
        }
    }
}


// Merge this list's deleted objects to the management Agent's list of deleted
// objects waiting for next (last) publish-ment.
void ManagementAgent::importDeletedObjects(const DeletedObjectList& inList)
{
    sys::Mutex::ScopedLock lock (userLock);

    for (DeletedObjectList::const_iterator lIter = inList.begin(); lIter != inList.end(); lIter++) {

        std::string classkey((*lIter)->packageName + std::string(":") + (*lIter)->className);
        DeletedObjectList& dList = pendingDeletedObjs[classkey];

        // not sure if this is necessary - merge by objectid....
        bool found = false;
        for (DeletedObjectList::iterator dIter = dList.begin(); dIter != dList.end(); dIter++) {
            if ((*dIter)->objectId == (*lIter)->objectId) {
                found = true;
                break;
            }
        }
        if (!found) {
            dList.push_back(*lIter);
        }
    }
}


// construct a DeletedObject from an encoded representation. Used by
// clustering to move deleted objects between clustered brokers.  See
// DeletedObject::encode() for the reverse.
ManagementAgent::DeletedObject::DeletedObject(const std::string& encoded)
{
    qpid::types::Variant::Map map_;
    MapCodec::decode(encoded, map_);

    packageName = map_["_package_name"].getString();
    className = map_["_class_name"].getString();
    objectId = map_["_object_id"].getString();

    encodedV1Config = map_["_v1_config"].getString();
    encodedV1Inst = map_["_v1_inst"].getString();
    encodedV2 = map_["_v2_data"].asMap();
}


// encode a DeletedObject to a string buffer. Used by
// clustering to move deleted objects between clustered brokers.  See
// DeletedObject(const std::string&) for the reverse.
void ManagementAgent::DeletedObject::encode(std::string& toBuffer)
{
    qpid::types::Variant::Map map_;


    map_["_package_name"] = packageName;
    map_["_class_name"] = className;
    map_["_object_id"] = objectId;

    map_["_v1_config"] = encodedV1Config;
    map_["_v1_inst"] = encodedV1Inst;
    map_["_v2_data"] = encodedV2;

    MapCodec::encode(map_, toBuffer);
}
