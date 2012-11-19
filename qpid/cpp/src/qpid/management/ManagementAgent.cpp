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


// NOTE on use of log levels: The criteria for using trace vs. debug
// is to use trace for log messages that are generated for each
// unbatched stats/props notification and debug for everything else.

#include "qpid/management/ManagementAgent.h"
#include "qpid/management/ManagementObject.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/log/Statement.h"
#include <qpid/broker/Message.h>
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/PollableQueue.h"
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

namespace qpid {
namespace management {

using boost::intrusive_ptr;
using qpid::framing::Uuid;
using qpid::types::Variant;
using qpid::amqp_0_10::MapCodec;
using qpid::amqp_0_10::ListCodec;
using namespace qpid::framing;
using namespace qpid::broker;
using namespace qpid;
using namespace std;
namespace _qmf = qmf::org::apache::qpid::broker;


namespace {
const size_t qmfV1BufferSize(65536);
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

struct ScopedManagementContext
{
    ScopedManagementContext(const qpid::broker::ConnectionState* context)
    {
        setManagementExecutionContext(context);
    }
    ~ScopedManagementContext()
    {
        setManagementExecutionContext(0);
    }
};
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
    QPID_LOG(debug, "Remote Agent removed bank=[" << brokerBank << "." << agentBank << "]");
    if (mgmtObject != 0) {
        mgmtObject->resourceDestroy();
        agent.deleteObjectNow(mgmtObject->getObjectId());
        mgmtObject.reset();
    }
}

ManagementAgent::ManagementAgent (const bool qmfV1, const bool qmfV2) :
    threadPoolSize(1), publish(true), interval(10), broker(0), timer(0),
    startTime(sys::now()),
    suppressed(false), disallowAllV1Methods(false),
    vendorNameKey(defaultVendorName), productNameKey(defaultProductName),
    qmf1Support(qmfV1), qmf2Support(qmfV2), maxReplyObjs(100)
{
    nextObjectId   = 1;
    brokerBank     = 1;
    bootSequence   = 1;
    nextRemoteBank = 10;
    nextRequestSequence = 1;
    clientWasAdded = false;
    attrMap["_vendor"] = defaultVendorName;
    attrMap["_product"] = defaultProductName;

    memstat = _qmf::Memory::shared_ptr(new qmf::org::apache::qpid::broker::Memory(this, 0, "amqp-broker"));
    addObject(memstat, "amqp-broker");
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

        remoteAgents.clear();
    }
}

void ManagementAgent::configure(const string& _dataDir, bool _publish, uint16_t _interval,
                                qpid::broker::Broker* _broker, int _threads)
{
    dataDir        = _dataDir;
    publish        = _publish;
    interval       = _interval;
    broker         = _broker;
    threadPoolSize = _threads;
    ManagementObject::maxThreads = threadPoolSize;
    sendQueue.reset(
        new EventQueue(boost::bind(&ManagementAgent::sendEvents, this, _1), broker->getPoller()));
    sendQueue->start();

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
                QPID_LOG (info, "ManagementAgent restored broker ID: " << uuid);

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
ObjectId ManagementAgent::addObject(ManagementObject::shared_ptr object, uint64_t persistId, bool persistent)
{
    uint16_t sequence;
    uint64_t objectNum;

    sys::Mutex::ScopedLock lock(addLock);
    sequence = persistent ? 0 : bootSequence;
    objectNum = persistId ? persistId : nextObjectId++;

    ObjectId objId(0 /*flags*/, sequence, brokerBank, objectNum);
    objId.setV2Key(*object);   // let object generate the v2 key

    object->setObjectId(objId);

    newManagementObjects.push_back(object);
    QPID_LOG(debug, "Management object (V1) added: " << objId.getV2Key());
    return objId;
}



ObjectId ManagementAgent::addObject(ManagementObject::shared_ptr object,
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
        newManagementObjects.push_back(object);
    }
    QPID_LOG(debug, "Management object added: " << objId.getV2Key());
    return objId;
}

void ManagementAgent::raiseEvent(const ManagementEvent& event, severity_t severity)
{
    static const std::string severityStr[] = {
        "emerg", "alert", "crit", "error", "warn",
        "note", "info", "debug"
    };
    uint8_t sev = (severity == SEV_DEFAULT) ? event.getSeverity() : (uint8_t) severity;

    if (qmf1Support) {
        char buffer[qmfV1BufferSize];
        Buffer outBuffer(buffer, qmfV1BufferSize);

        encodeHeader(outBuffer, 'e');
        outBuffer.putShortString(event.getPackageName());
        outBuffer.putShortString(event.getEventName());
        outBuffer.putBin128(event.getMd5Sum());
        outBuffer.putLongLong(uint64_t(sys::Duration(sys::EPOCH, sys::now())));
        outBuffer.putOctet(sev);
        string sBuf;
        event.encode(sBuf);
        outBuffer.putRawData(sBuf);
        sendBuffer(outBuffer, mExchange,
                   "console.event.1.0." + event.getPackageName() + "." + event.getEventName());
        QPID_LOG(debug, "SEND raiseEvent (v1) class=" << event.getPackageName() << "." << event.getEventName());
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
        Variant::List list_;
        list_.push_back(map_);
        ListCodec::encode(list_, content);
        sendBuffer(content, "", headers, "amqp/list", v2Topic, key.str());
        QPID_LOG(debug, "SEND raiseEvent (v2) class=" << event.getPackageName() << "." << event.getEventName());
    }
}

ManagementAgent::Periodic::Periodic (ManagementAgent& _agent, uint32_t _seconds)
    : TimerTask(sys::Duration((_seconds ? _seconds : 1) * sys::TIME_SEC),
                "ManagementAgent::periodicProcessing"),
      agent(_agent) {}

ManagementAgent::Periodic::~Periodic() {}

void ManagementAgent::Periodic::fire()
{
    setupNextFire();
    agent.timer->add(this);
    agent.periodicProcessing();
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

        encodeHeader(outBuffer, 'x');
        sendBuffer(outBuffer, dExchange, rkeys.front());
        QPID_LOG(debug, "SEND ConsoleAddedIndication to=" << rkeys.front());
        rkeys.pop_front();
    }
}

void ManagementAgent::clusterUpdate() {
    // Called on all cluster memebers when a new member joins a cluster.
    // Set clientWasAdded so that on the next periodicProcessing we will do 
    // a full update on all cluster members.
    sys::Mutex::ScopedLock l(userLock);
    moveNewObjects();         // keep lists consistent with updater/updatee.
    moveDeletedObjects();
    clientWasAdded = true;
    debugSnapshot("Cluster member joined");
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
                                 qpid::broker::Exchange::shared_ptr exchange,
                                 const string&  routingKey)
{
    if (suppressed) {
        QPID_LOG(debug, "Suppressing management message to " << routingKey);
        return;
    }
    if (exchange.get() == 0) return;

    intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> transfer(new qpid::broker::amqp_0_10::MessageTransfer());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), exchange->getName (), 0, 0)));
    AMQFrame header((AMQHeaderBody()));
    AMQFrame content((AMQContentBody()));

    size_t length = buf.getPosition();
    buf.reset();
    content.castBody<AMQContentBody>()->decode(buf, length);

    method.setEof(false);
    header.setBof(false);
    header.setEof(false);
    content.setBof(false);

    transfer->getFrames().append(method);
    transfer->getFrames().append(header);

    MessageProperties* props =
        transfer->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setContentLength(length);

    DeliveryProperties* dp =
        transfer->getFrames().getHeaders()->get<DeliveryProperties>(true);
    dp->setRoutingKey(routingKey);

    transfer->getFrames().append(content);
    Message msg(transfer, transfer);
    msg.setIsManagementMessage(true);
    sendQueue->push(make_pair(exchange, msg));
    buf.reset();
}


void ManagementAgent::sendBuffer(Buffer&  buf,
                                 const string& exchange,
                                 const string& routingKey)
{
    qpid::broker::Exchange::shared_ptr ex(broker->getExchanges().get(exchange));
    if (ex.get() != 0)
        sendBuffer(buf, ex, routingKey);
}


void ManagementAgent::sendBuffer(const string& data,
                                 const string& cid,
                                 const Variant::Map& headers,
                                 const string& content_type,
                                 qpid::broker::Exchange::shared_ptr exchange,
                                 const string& routingKey,
                                 uint64_t ttl_msec)
{
    Variant::Map::const_iterator i;

    if (suppressed) {
        QPID_LOG(debug, "Suppressing management message to " << routingKey);
        return;
    }
    if (exchange.get() == 0) return;

    intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> transfer(new qpid::broker::amqp_0_10::MessageTransfer);
    AMQFrame method((MessageTransferBody(ProtocolVersion(), exchange->getName (), 0, 0)));
    AMQFrame header((AMQHeaderBody()));
    AMQFrame content((AMQContentBody(data)));

    method.setEof(false);
    header.setBof(false);
    header.setEof(false);
    content.setBof(false);

    transfer->getFrames().append(method);
    transfer->getFrames().append(header);

    MessageProperties* props =
        transfer->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setContentLength(data.length());
    if (!cid.empty()) {
        props->setCorrelationId(cid);
    }
    props->setContentType(content_type);
    props->setAppId("qmf2");

    for (i = headers.begin(); i != headers.end(); ++i) {
        props->getApplicationHeaders().setString(i->first, i->second.asString());
    }

    DeliveryProperties* dp =
        transfer->getFrames().getHeaders()->get<DeliveryProperties>(true);
    dp->setRoutingKey(routingKey);
    if (ttl_msec) {
        dp->setTtl(ttl_msec);
    }
    transfer->getFrames().append(content);
    transfer->computeRequiredCredit();
    Message msg(transfer, transfer);
    msg.setIsManagementMessage(true);
    msg.computeExpiration(broker->getExpiryPolicy());

    sendQueue->push(make_pair(exchange, msg));
}


void ManagementAgent::sendBuffer(const string& data,
                                 const string& cid,
                                 const Variant::Map& headers,
                                 const string& content_type,
                                 const string& exchange,
                                 const string& routingKey,
                                 uint64_t ttl_msec)
{
    qpid::broker::Exchange::shared_ptr ex(broker->getExchanges().get(exchange));
    if (ex.get() != 0)
        sendBuffer(data, cid, headers, content_type, ex, routingKey, ttl_msec);
}


/** Objects that have been added since the last periodic poll are temporarily
 * saved in the newManagementObjects list.  This allows objects to be
 * added without needing to block on the userLock (objectLock is used instead).
 * These new objects need to be integrated into the object database
 * (managementObjects) *before* they can be properly managed.  This routine
 * performs the integration.
 *
 * Note well: objects on the newManagementObjects list may have been
 * marked as "deleted", and, possibly re-added.  This would result in
 * duplicate object ids.  To avoid clashes, don't put deleted objects
 * into the active object database.
 */
void ManagementAgent::moveNewObjects()
{
    sys::Mutex::ScopedLock lock(addLock);
    sys::Mutex::ScopedLock objLock (objectLock);
    while (!newManagementObjects.empty()) {
        ManagementObject::shared_ptr object = newManagementObjects.back();
        newManagementObjects.pop_back();

        if (object->isDeleted()) {
            DeletedObject::shared_ptr dptr(new DeletedObject(object, qmf1Support, qmf2Support));
            pendingDeletedObjs[dptr->getKey()].push_back(dptr);
        } else {    // add to active object list, check for duplicates.
            ObjectId oid = object->getObjectId();
            ManagementObjectMap::iterator destIter = managementObjects.find(oid);
            if (destIter != managementObjects.end()) {
                // duplicate found.  It is OK if the old object has been marked
                // deleted, just replace the old with the new.
                ManagementObject::shared_ptr oldObj = destIter->second;
                if (!oldObj->isDeleted()) {
                    // Duplicate non-deleted objects? This is a user error - oids must be unique.
                    // for now, leak the old object (safer than deleting - may still be referenced)
                    // and complain loudly...
                    QPID_LOG(error, "Detected two management objects with the same identifier: " << oid);
                    oldObj->resourceDestroy();
                }
                DeletedObject::shared_ptr dptr(new DeletedObject(oldObj, qmf1Support, qmf2Support));
                pendingDeletedObjs[dptr->getKey()].push_back(dptr);
                // QPID-3666: be sure to replace the -index- also, as non-key members of
                // the index object may be different for the new object!  So erase the
                // entry, rather than []= assign here:
                managementObjects.erase(destIter);
            }
            managementObjects[oid] = object;
        }
    }
}

void ManagementAgent::periodicProcessing (void)
{
#define HEADROOM  4096
    debugSnapshot("Management agent periodic processing");
    sys::Mutex::ScopedLock lock (userLock);
    string              routingKey;
    string sBuf;

    moveNewObjects();

    //
    //  If we're publishing updates, get the latest memory statistics and uptime now
    //
    if (publish) {
        uint64_t uptime = sys::Duration(startTime, sys::now());
        boost::dynamic_pointer_cast<_qmf::Broker>(broker->GetManagementObject())->set_uptime(uptime);
        qpid::sys::MemStat::loadMemInfo(memstat.get());
    }

    //
    //  Use a copy of the management object map to avoid holding the objectLock
    //
    ManagementObjectVector localManagementObjects;
    {
        sys::Mutex::ScopedLock objLock(objectLock);
        std::transform(managementObjects.begin(), managementObjects.end(),
                       std::back_inserter(localManagementObjects),
                       boost::bind(&ManagementObjectMap::value_type::second, _1));
    }

    //
    //  Clear the been-here flag on all objects in the map.
    //
    for (ManagementObjectVector::iterator iter = localManagementObjects.begin();
         iter != localManagementObjects.end();
         iter++) {
        ManagementObject::shared_ptr object = *iter;
        object->setFlags(0);
        if (clientWasAdded) {
            object->setForcePublish(true);
        }
    }

    clientWasAdded = false;

    // first send the pending deletes before sending updates.  This prevents a
    // "false delete" scenario: if an object was deleted then re-added during
    // the last poll cycle, it will have a delete entry and an active entry.
    // if we sent the active update first, _then_ the delete update, clients
    // would incorrectly think the object was deleted.  See QPID-2997
    //
    bool objectsDeleted = moveDeletedObjects();
    PendingDeletedObjsMap localPendingDeletedObjs;
    {
        sys::Mutex::ScopedLock objLock(objectLock);
        localPendingDeletedObjs.swap(pendingDeletedObjs);
    }

    //
    // If we are not publishing updates, just clear the pending deletes.  There's no
    // need to tell anybody.
    //
    if (!publish)
        localPendingDeletedObjs.clear();

    ResizableBuffer msgBuffer(qmfV1BufferSize);
    if (!localPendingDeletedObjs.empty()) {
        for (PendingDeletedObjsMap::iterator mIter = localPendingDeletedObjs.begin();
             mIter != localPendingDeletedObjs.end();
             mIter++) {
            std::string packageName;
            std::string className;
            msgBuffer.reset();
            uint32_t v1Objs = 0;
            uint32_t v2Objs = 0;
            Variant::List list_;

            size_t pos = mIter->first.find(":");
            packageName = mIter->first.substr(0, pos);
            className = mIter->first.substr(pos+1);

            for (DeletedObjectList::iterator lIter = mIter->second.begin();
                 lIter != mIter->second.end(); lIter++) {
                msgBuffer.makeAvailable(HEADROOM); // Make sure there's buffer space.
                std::string oid = (*lIter)->objectId;
                if (!(*lIter)->encodedV1Config.empty()) {
                    encodeHeader(msgBuffer, 'c');
                    msgBuffer.putRawData((*lIter)->encodedV1Config);
                    QPID_LOG(trace, "Deleting V1 properties " << oid
                             << " len=" << (*lIter)->encodedV1Config.size());
                    v1Objs++;
                }
                if (!(*lIter)->encodedV1Inst.empty()) {
                    encodeHeader(msgBuffer, 'i');
                    msgBuffer.putRawData((*lIter)->encodedV1Inst);
                    QPID_LOG(trace, "Deleting V1 statistics " << oid
                             << " len=" <<  (*lIter)->encodedV1Inst.size());
                    v1Objs++;
                }
                if (v1Objs >= maxReplyObjs) {
                    v1Objs = 0;
                    stringstream key;
                    key << "console.obj.1.0." << packageName << "." << className;
                    size_t contentSize = msgBuffer.getPosition();
                    sendBuffer(msgBuffer, mExchange, key.str());
                    QPID_LOG(debug, "SEND V1 Multicast ContentInd V1 (delete) to="
                             << key.str() << " len=" << contentSize);
                }

                if (!(*lIter)->encodedV2.empty()) {
                    QPID_LOG(trace, "Deleting V2 " << "map=" << (*lIter)->encodedV2);
                    list_.push_back((*lIter)->encodedV2);
                    if (++v2Objs >= maxReplyObjs) {
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

                            sendBuffer(content, "", headers, "amqp/list", v2Topic, key.str(), 0);
                            QPID_LOG(debug, "SEND Multicast ContentInd V2 (delete) to=" << key.str() << " len=" << content.length());
                        }
                    }
                }
            }  // end current list

            // send any remaining objects...

            if (v1Objs) {
                stringstream key;
                key << "console.obj.1.0." << packageName << "." << className;
                size_t contentSize = msgBuffer.getPosition();
                sendBuffer(msgBuffer, mExchange, key.str());
                QPID_LOG(debug, "SEND V1 Multicast ContentInd V1 (delete) to=" << key.str() << " len=" << contentSize);
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

                    sendBuffer(content, "", headers, "amqp/list", v2Topic, key.str(), 0);
                    QPID_LOG(debug, "SEND Multicast ContentInd V2 (delete) to=" << key.str() << " len=" << content.length());
                }
            }
        }  // end map
    }

    //
    // Process the entire object map.
    //
    // If publish is disabled, don't send any updates.
    //
    while (publish) {
        msgBuffer.reset();
        Variant::List list_;
        uint32_t pcount;
        uint32_t scount;
        uint32_t v1Objs, v2Objs;
        ManagementObjectVector::iterator baseIter;
        std::string packageName;
        std::string className;

        for (baseIter = localManagementObjects.begin();
             baseIter != localManagementObjects.end();
             baseIter++) {
            ManagementObject::shared_ptr baseObject = *baseIter;
            //
            //  Skip until we find a base object requiring processing...
            //
            if (baseObject->getFlags() == 0) {
                packageName = baseObject->getPackageName();
                className = baseObject->getClassName();
                break;
            }
        }

        if (baseIter == localManagementObjects.end())
            break;  // done - all objects processed

        pcount = scount = 0;
        v1Objs = 0;
        v2Objs = 0;
        list_.clear();
        msgBuffer.reset();

        for (ManagementObjectVector::iterator iter = baseIter;
             iter != localManagementObjects.end();
             iter++) {
            msgBuffer.makeAvailable(HEADROOM); // Make sure there's buffer space
            ManagementObject::shared_ptr baseObject = *baseIter;
            ManagementObject::shared_ptr object = *iter;
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
                    size_t pos = msgBuffer.getPosition();
                    encodeHeader(msgBuffer, 'c');
                    sBuf.clear();
                    object->writeProperties(sBuf);
                    msgBuffer.putRawData(sBuf);
                    QPID_LOG(trace, "Changed V1 properties "
                             << object->getObjectId().getV2Key()
                             << " len=" << msgBuffer.getPosition()-pos);
                    ++v1Objs;
                }

                if (send_stats && qmf1Support) {
                    size_t pos = msgBuffer.getPosition();
                    encodeHeader(msgBuffer, 'i');
                    sBuf.clear();
                    object->writeStatistics(sBuf);
                    msgBuffer.putRawData(sBuf);
                    QPID_LOG(trace, "Changed V1 statistics "
                             << object->getObjectId().getV2Key()
                             << " len=" << msgBuffer.getPosition()-pos);
                    ++v1Objs;
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
                    QPID_LOG(trace, "Changed V2"
                             << (send_stats? " statistics":"")
                             << (send_props? " properties":"")
                             << " map=" << map_);
                }

                if (send_props) pcount++;
                if (send_stats) scount++;

                object->setForcePublish(false);

                if ((qmf1Support && (v1Objs >= maxReplyObjs)) ||
                    (qmf2Support && (v2Objs >= maxReplyObjs)))
                    break;  // have enough objects, send an indication...
            }
        }

        if (pcount || scount) {
            if (qmf1Support) {
                if (msgBuffer.getPosition() > 0) {
                    stringstream key;
                    key << "console.obj.1.0." << packageName << "." << className;
                    size_t contentSize = msgBuffer.getPosition();
                    sendBuffer(msgBuffer, mExchange, key.str());
                    QPID_LOG(debug, "SEND V1 Multicast ContentInd to=" << key.str()
                             << " props=" << pcount
                             << " stats=" << scount
                             << " len=" << contentSize);
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

                    sendBuffer(content, "", headers, "amqp/list", v2Topic, key.str(), 0);
                    QPID_LOG(debug, "SEND Multicast ContentInd to=" << key.str()
                             << " props=" << pcount
                             << " stats=" << scount
                             << " len=" << content.length());
                }
            }
        }
    }  // end processing updates for all objects

    if (objectsDeleted) {
        sys::Mutex::ScopedLock lock (userLock);
        deleteOrphanedAgentsLH();
    }

    // heartbeat generation.  Note that heartbeats need to be sent even if publish is disabled.

    if (qmf1Support) {
        char                msgChars[qmfV1BufferSize];
        Buffer msgBuffer(msgChars, qmfV1BufferSize);
        encodeHeader(msgBuffer, 'h');
        msgBuffer.putLongLong(uint64_t(sys::Duration(sys::EPOCH, sys::now())));

        routingKey = "console.heartbeat.1.0";
        sendBuffer(msgBuffer, mExchange, routingKey);
        QPID_LOG(debug, "SEND HeartbeatInd to=" << routingKey);
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
        map["_values"].asMap()["_timestamp"] = uint64_t(sys::Duration(sys::EPOCH, sys::now()));
        map["_values"].asMap()["_heartbeat_interval"] = interval;
        map["_values"].asMap()["_epoch"] = bootSequence;

        string content;
        MapCodec::encode(map, content);

        // Set TTL (in msecs) on outgoing heartbeat indications based on the interval
        // time to prevent stale heartbeats from getting to the consoles.
        sendBuffer(content, "", headers, "amqp/map", v2Topic, addr_key.str(), interval * 2 * 1000);

        QPID_LOG(debug, "SENT AgentHeartbeat name=" << name_address);
    }
}

void ManagementAgent::deleteObjectNow(const ObjectId& oid)
{
    ManagementObject::shared_ptr object;
    {
        sys::Mutex::ScopedLock lock(objectLock);
        ManagementObjectMap::iterator iter = managementObjects.find(oid);
        if (iter == managementObjects.end())
            return;
        object = iter->second;
        if (!object->isDeleted())
            return;
        managementObjects.erase(oid);
    }

#define DNOW_BUFSIZE 2048
    char     msgChars[DNOW_BUFSIZE];
    Buffer   msgBuffer(msgChars, DNOW_BUFSIZE);
    Variant::List list_;
    stringstream v1key, v2key;

    if (publish && qmf1Support) {
        string sBuf;

        v1key << "console.obj.1.0." << object->getPackageName() << "." << object->getClassName();
        encodeHeader(msgBuffer, 'c');
        object->writeProperties(sBuf);
        msgBuffer.putRawData(sBuf);
    }

    if (publish && qmf2Support) {
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

    object.reset();

    // object deleted, ok to drop lock now.

    if (publish && qmf1Support) {
        sendBuffer(msgBuffer, mExchange, v1key.str());
        QPID_LOG(debug, "SEND Immediate(delete) ContentInd to=" << v1key.str());
    }

    if (publish && qmf2Support) {
        Variant::Map  headers;
        headers["method"] = "indication";
        headers["qmf.opcode"] = "_data_indication";
        headers["qmf.content"] = "_data";
        headers["qmf.agent"] = name_address;

        string content;
        ListCodec::encode(list_, content);
        sendBuffer(content, "", headers, "amqp/list", v2Topic, v2key.str(), 0);
        QPID_LOG(debug, "SEND Immediate(delete) ContentInd to=" << v2key.str());
    }
}

void ManagementAgent::sendCommandComplete(const string& replyToKey, uint32_t sequence,
                                          uint32_t code, const string& text)
{
    ResizableBuffer   outBuffer (qmfV1BufferSize);

    encodeHeader (outBuffer, 'z', sequence);
    outBuffer.putLong (code);
    outBuffer.putShortString (text);
    sendBuffer(outBuffer, dExchange, replyToKey);
    QPID_LOG(debug, "SEND CommandCompleteInd code=" << code << " text=" << text << " to=" <<
             replyToKey << " seq=" << sequence);
}

void ManagementAgent::sendException(const string& rte, const string& rtk, const string& cid,
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
    sendBuffer(content, cid, headers, "amqp/map", rte, rtk);

    QPID_LOG(debug, "SENT Exception code=" << code <<" text=" << text);
}

bool ManagementAgent::dispatchCommand (Deliverable&      deliverable,
                                       const string&     routingKey,
                                       const FieldTable* /*args*/,
                                       const bool topic,
                                       int qmfVersion)
{
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
            dispatchAgentCommand(msg);
            return false;
        }

        if (routingKey.length() > 6) {

            if (routingKey.compare(0, 9, "agent.1.0") == 0) {
                dispatchAgentCommand(msg);
                return false;
            }

            if (routingKey.compare(0, 8, "agent.1.") == 0) {
                return authorizeAgentMessage(msg);
            }

            if (routingKey.compare(0, 7, "schema.") == 0) {
                dispatchAgentCommand(msg);
                return true;
            }
        }
    }

    if (qmfVersion == 2) {

        if (topic) {
            // Intercept messages bound to:
            //  "console.ind.locate.# - process these messages, and also allow them to be forwarded.
            if (routingKey == "console.request.agent_locate") {
                dispatchAgentCommand(msg);
                return true;
            }

        } else { // direct exchange

            // Intercept messages bound to:
            //  "broker" - generic alias for the local broker
            //  "<name_address>" - the broker agent's proper name
            // and do not forward them futher
            if (routingKey == "broker" || routingKey == name_address) {
                dispatchAgentCommand(msg, routingKey == "broker");
                return false;
            }
        }
    }

    return true;
}

void ManagementAgent::handleMethodRequest(Buffer& inBuffer, const string& replyToKey, uint32_t sequence, const ConnectionToken* connToken)
{
    moveNewObjects();

    string   methodName;
    string   packageName;
    string   className;
    uint8_t  hash[16];
    ResizableBuffer   outBuffer (qmfV1BufferSize);
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

    QPID_LOG(debug, "RECV MethodRequest (v1) class=" << packageName << ":" << className << "(" << Uuid(hash) << ") method=" <<
             methodName << " replyTo=" << replyToKey);

    encodeHeader(outBuffer, 'm', sequence);

    if (disallowAllV1Methods) {
        outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
        outBuffer.putMediumString("QMFv1 methods forbidden on this broker, use QMFv2");
        sendBuffer(outBuffer, dExchange, replyToKey);
        QPID_LOG(debug, "SEND MethodResponse status=FORBIDDEN reason='All QMFv1 Methods Forbidden' seq=" << sequence);
        return;
    }

    DisallowedMethods::const_iterator i = disallowed.find(make_pair(className, methodName));
    if (i != disallowed.end()) {
        outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
        outBuffer.putMediumString(i->second);
        sendBuffer(outBuffer, dExchange, replyToKey);
        QPID_LOG(debug, "SEND MethodResponse status=FORBIDDEN text=" << i->second << " seq=" << sequence);
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
            sendBuffer(outBuffer, dExchange, replyToKey);
            QPID_LOG(debug, "SEND MethodResponse status=FORBIDDEN" << " seq=" << sequence);
            return;
        }
    }

    ManagementObject::shared_ptr object;
    {
        sys::Mutex::ScopedLock lock(objectLock);
        ManagementObjectMap::iterator iter = numericFind(objId);
        if (iter != managementObjects.end())
            object = iter->second;
    }

    if (!object || object->isDeleted()) {
        outBuffer.putLong        (Manageable::STATUS_UNKNOWN_OBJECT);
        outBuffer.putMediumString(Manageable::StatusText (Manageable::STATUS_UNKNOWN_OBJECT));
    } else {
        if ((object->getPackageName() != packageName) ||
            (object->getClassName()   != className)) {
            outBuffer.putLong        (Manageable::STATUS_PARAMETER_INVALID);
            outBuffer.putMediumString(Manageable::StatusText (Manageable::STATUS_PARAMETER_INVALID));
        }
        else {
            uint32_t pos = outBuffer.getPosition();
            try {
                string outBuf;
                object->doMethod(methodName, inArgs, outBuf, userId);
                outBuffer.putRawData(outBuf);
            } catch(exception& e) {
                outBuffer.setPosition(pos);;
                outBuffer.putLong(Manageable::STATUS_EXCEPTION);
                outBuffer.putMediumString(e.what());
            }
        }
    }

    sendBuffer(outBuffer, dExchange, replyToKey);
    QPID_LOG(debug, "SEND MethodResponse (v1) to=" << replyToKey << " seq=" << sequence);
}


void ManagementAgent::handleMethodRequest (const string& body, const string& rte, const string& rtk,
                                           const string& cid, const ConnectionToken* connToken, bool viaLocal)
{
    moveNewObjects();

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
        sendException(rte, rtk, cid, Manageable::StatusText(Manageable::STATUS_PARAMETER_INVALID),
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
        sendException(rte, rtk, cid, e.what(), Manageable::STATUS_EXCEPTION, viaLocal);
        return;
    }

    ManagementObject::shared_ptr object;
    {
        sys::Mutex::ScopedLock lock(objectLock);
        ManagementObjectMap::iterator iter = managementObjects.find(objId);
        if (iter != managementObjects.end())
            object = iter->second;
    }

    if (!object || object->isDeleted()) {
        stringstream estr;
        estr << "No object found with ID=" << objId;
        sendException(rte, rtk, cid, estr.str(), 1, viaLocal);
        return;
    }

    // validate
    AclModule* acl = broker->getAcl();
    DisallowedMethods::const_iterator i;

    i = disallowed.find(make_pair(object->getClassName(), methodName));
    if (i != disallowed.end()) {
        sendException(rte, rtk, cid, i->second, Manageable::STATUS_FORBIDDEN, viaLocal);
        return;
    }

    string userId = ((const qpid::broker::ConnectionState*) connToken)->getUserId();
    if (acl != 0) {
        map<acl::Property, string> params;
        params[acl::PROP_SCHEMAPACKAGE] = object->getPackageName();
        params[acl::PROP_SCHEMACLASS]   = object->getClassName();

        if (!acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_METHOD, methodName, &params)) {
            sendException(rte, rtk, cid, Manageable::StatusText(Manageable::STATUS_FORBIDDEN),
                          Manageable::STATUS_FORBIDDEN, viaLocal);
            return;
        }
    }

    // invoke the method

    QPID_LOG(debug, "RECV MethodRequest (v2) class=" << object->getPackageName()
             << ":" << object->getClassName() << " method=" <<
             methodName << " replyTo=" << rte << "/" << rtk << " objId=" << objId << " inArgs=" << inArgs);

    try {
        object->doMethod(methodName, inArgs, callMap, userId);
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
        sendException(rte, rtk, cid, e.what(), Manageable::STATUS_EXCEPTION, viaLocal);
        return;
    }

    if (errorCode != 0) {
        sendException(rte, rtk, cid, error, errorCode, viaLocal);
        return;
    }

    MapCodec::encode(outMap, content);
    sendBuffer(content, cid, headers, "amqp/map", rte, rtk);
    QPID_LOG(debug, "SEND MethodResponse (v2) to=" << rte << "/" << rtk << " seq=" << cid << " map=" << outMap);
}


void ManagementAgent::handleBrokerRequest (Buffer&, const string& replyToKey, uint32_t sequence)
{
    ResizableBuffer   outBuffer (qmfV1BufferSize);

    QPID_LOG(debug, "RECV BrokerRequest replyTo=" << replyToKey);

    encodeHeader (outBuffer, 'b', sequence);
    uuid.encode  (outBuffer);

    sendBuffer(outBuffer, dExchange, replyToKey);
    QPID_LOG(debug, "SEND BrokerResponse to=" << replyToKey);
}

void ManagementAgent::handlePackageQuery (Buffer&, const string& replyToKey, uint32_t sequence)
{
    QPID_LOG(debug, "RECV PackageQuery replyTo=" << replyToKey);
    ResizableBuffer   outBuffer (qmfV1BufferSize);

    {
        sys::Mutex::ScopedLock lock(userLock);
        for (PackageMap::iterator pIter = packages.begin ();
             pIter != packages.end ();
             pIter++)
        {
            encodeHeader (outBuffer, 'p', sequence);
            encodePackageIndication (outBuffer, pIter);
        }
    }

    if (outBuffer.getPosition() > 0) {
        sendBuffer(outBuffer, dExchange, replyToKey);
        QPID_LOG(debug, "SEND PackageInd to=" << replyToKey << " seq=" << sequence);
    }

    sendCommandComplete(replyToKey, sequence);
}

void ManagementAgent::handlePackageInd (Buffer& inBuffer, const string& replyToKey, uint32_t sequence)
{
    string packageName;

    inBuffer.getShortString(packageName);

    QPID_LOG(debug, "RECV PackageInd package=" << packageName << " replyTo=" << replyToKey << " seq=" << sequence);

    sys::Mutex::ScopedLock lock(userLock);
    findOrAddPackageLH(packageName);
}

void ManagementAgent::handleClassQuery(Buffer& inBuffer, const string& replyToKey, uint32_t sequence)
{
    string packageName;

    inBuffer.getShortString(packageName);

    QPID_LOG(debug, "RECV ClassQuery package=" << packageName << " replyTo=" << replyToKey << " seq=" << sequence);

    typedef std::pair<SchemaClassKey, uint8_t> _ckeyType;
    std::list<_ckeyType> classes;
    {
        sys::Mutex::ScopedLock lock(userLock);
        PackageMap::iterator pIter = packages.find(packageName);
        if (pIter != packages.end())
        {
            ClassMap &cMap = pIter->second;
            for (ClassMap::iterator cIter = cMap.begin();
                 cIter != cMap.end();
                 cIter++) {
                if (cIter->second.hasSchema()) {
                    classes.push_back(make_pair(cIter->first, cIter->second.kind));
                }
            }
        }
    }

    while (classes.size()) {
        ResizableBuffer   outBuffer(qmfV1BufferSize);

        encodeHeader(outBuffer, 'q', sequence);
        encodeClassIndication(outBuffer, packageName, classes.front().first, classes.front().second);

        sendBuffer(outBuffer, dExchange, replyToKey);
        QPID_LOG(debug, "SEND ClassInd class=" << packageName << ":" << classes.front().first.name <<
                 "(" << Uuid(classes.front().first.hash) << ") to=" << replyToKey << " seq=" << sequence);
        classes.pop_front();
    }
    sendCommandComplete(replyToKey, sequence);
}

void ManagementAgent::handleClassInd (Buffer& inBuffer, const string& replyToKey, uint32_t)
{
    string packageName;
    SchemaClassKey key;

    uint8_t kind = inBuffer.getOctet();
    inBuffer.getShortString(packageName);
    inBuffer.getShortString(key.name);
    inBuffer.getBin128(key.hash);

    QPID_LOG(debug, "RECV ClassInd class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) <<
             "), replyTo=" << replyToKey);

    sys::Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = findOrAddPackageLH(packageName);
    ClassMap::iterator   cIter = pIter->second.find(key);
    if (cIter == pIter->second.end() || !cIter->second.hasSchema()) {
        ResizableBuffer   outBuffer (qmfV1BufferSize);
        uint32_t sequence = nextRequestSequence++;

        // Schema Request
        encodeHeader (outBuffer, 'S', sequence);
        outBuffer.putShortString(packageName);
        key.encode(outBuffer);
        sendBuffer(outBuffer, dExchange, replyToKey);
        QPID_LOG(debug, "SEND SchemaRequest class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) <<
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

void ManagementAgent::handleSchemaRequest(Buffer& inBuffer, const string& rte, const string& rtk, uint32_t sequence)
{
    string         packageName;
    SchemaClassKey key;

    inBuffer.getShortString (packageName);
    key.decode(inBuffer);

    QPID_LOG(debug, "RECV SchemaRequest class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) <<
             "), replyTo=" << rte << "/" << rtk << " seq=" << sequence);

    sys::Mutex::ScopedLock lock(userLock);
    PackageMap::iterator pIter = packages.find(packageName);
    if (pIter != packages.end()) {
        ClassMap& cMap = pIter->second;
        ClassMap::iterator cIter = cMap.find(key);
        if (cIter != cMap.end()) {
            ResizableBuffer   outBuffer(qmfV1BufferSize);
            SchemaClass& classInfo = cIter->second;

            if (classInfo.hasSchema()) {
                encodeHeader(outBuffer, 's', sequence);
                classInfo.appendSchema(outBuffer);
                sendBuffer(outBuffer, rte, rtk);
                QPID_LOG(debug, "SEND SchemaResponse to=" << rte << "/" << rtk << " seq=" << sequence);
            }
            else
                sendCommandComplete(rtk, sequence, 1, "Schema not available");
        }
        else
            sendCommandComplete(rtk, sequence, 1, "Class key not found");
    }
    else
        sendCommandComplete(rtk, sequence, 1, "Package not found");
}

void ManagementAgent::handleSchemaResponse(Buffer& inBuffer, const string& /*replyToKey*/, uint32_t sequence)
{
    string         packageName;
    SchemaClassKey key;

    uint32_t pos = inBuffer.getPosition();
    inBuffer.getOctet();
    inBuffer.getShortString(packageName);
    key.decode(inBuffer);
    inBuffer.setPosition(pos);;

    QPID_LOG(debug, "RECV SchemaResponse class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) << ")" << " seq=" << sequence);

    sys::Mutex::ScopedLock lock(userLock);
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
                ResizableBuffer outBuffer(qmfV1BufferSize);

                encodeHeader(outBuffer, 'q');
                encodeClassIndication(outBuffer, pIter->first, cIter->first, cIter->second.kind);
                sendBuffer(outBuffer, mExchange, "schema.class");
                QPID_LOG(debug, "SEND ClassInd class=" << packageName << ":" << key.name << "(" << Uuid(key.hash) << ")" <<
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

void ManagementAgent::handleAttachRequest (Buffer& inBuffer, const string& replyToKey, uint32_t sequence, const ConnectionToken* connToken)
{
    string   label;
    uint32_t requestedBrokerBank, requestedAgentBank;
    uint32_t assignedBank;
    ObjectId connectionRef = ((const ConnectionState*) connToken)->GetManagementObject()->getObjectId();
    Uuid     systemId;

    moveNewObjects();

    sys::Mutex::ScopedLock lock(userLock);
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

    QPID_LOG(debug, "RECV (Agent)AttachRequest label=" << label << " reqBrokerBank=" << requestedBrokerBank <<
             " reqAgentBank=" << requestedAgentBank << " replyTo=" << replyToKey << " seq=" << sequence);

    assignedBank = assignBankLH(requestedAgentBank);

    boost::shared_ptr<RemoteAgent> agent(new RemoteAgent(*this));
    agent->brokerBank = brokerBank;
    agent->agentBank  = assignedBank;
    agent->routingKey = replyToKey;
    agent->connectionRef = connectionRef;
    agent->mgmtObject = _qmf::Agent::shared_ptr(new _qmf::Agent (this, agent.get()));
    agent->mgmtObject->set_connectionRef(agent->connectionRef);
    agent->mgmtObject->set_label        (label);
    agent->mgmtObject->set_registeredTo (broker->GetManagementObject()->getObjectId());
    agent->mgmtObject->set_systemId     ((const unsigned char*)systemId.data());
    agent->mgmtObject->set_brokerBank   (brokerBank);
    agent->mgmtObject->set_agentBank    (assignedBank);
    addObject (agent->mgmtObject, 0);
    remoteAgents[connectionRef] = agent;

    QPID_LOG(debug, "Remote Agent registered bank=[" << brokerBank << "." << assignedBank << "] replyTo=" << replyToKey);

    // Send an Attach Response
    ResizableBuffer outBuffer (qmfV1BufferSize);

    encodeHeader (outBuffer, 'a', sequence);
    outBuffer.putLong (brokerBank);
    outBuffer.putLong (assignedBank);
    sendBuffer(outBuffer, dExchange, replyToKey);
    QPID_LOG(debug, "SEND AttachResponse brokerBank=" << brokerBank << " agentBank=" << assignedBank <<
             " to=" << replyToKey << " seq=" << sequence);
}

void ManagementAgent::handleGetQuery(Buffer& inBuffer, const string& replyToKey, uint32_t sequence)
{
    FieldTable           ft;
    FieldTable::ValuePtr value;

    moveNewObjects();

    ft.decode(inBuffer);

    QPID_LOG(debug, "RECV GetQuery (v1) query=" << ft << " seq=" << sequence);

    value = ft.get("_class");
    if (value.get() == 0 || !value->convertsTo<string>()) {
        value = ft.get("_objectid");
        if (value.get() == 0 || !value->convertsTo<string>())
            return;

        ObjectId selector(value->get<string>());

        ManagementObject::shared_ptr object;
        {
            sys::Mutex::ScopedLock lock(objectLock);
            ManagementObjectMap::iterator iter = numericFind(selector);
            if (iter != managementObjects.end())
                object = iter->second;
        }

        if (object) {
            ResizableBuffer outBuffer (qmfV1BufferSize);

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
                sendBuffer(outBuffer, dExchange, replyToKey);
                QPID_LOG(debug, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
            }
        }
        sendCommandComplete(replyToKey, sequence);
        return;
    }

    string className (value->get<string>());
    std::list<ManagementObject::shared_ptr> matches;

    if (className == "memory")
        qpid::sys::MemStat::loadMemInfo(memstat.get());

    if (className == "broker") {
        uint64_t uptime = sys::Duration(startTime, sys::now());
        boost::dynamic_pointer_cast<_qmf::Broker>(broker->GetManagementObject())->set_uptime(uptime);
    }


    // build up a set of all objects to be dumped
    {
        sys::Mutex::ScopedLock lock(objectLock);
        for (ManagementObjectMap::iterator iter = managementObjects.begin();
             iter != managementObjects.end();
             iter++) {
            ManagementObject::shared_ptr object = iter->second;
            if (object->getClassName () == className) {
                matches.push_back(object);
            }
        }
    }

    // send them
    ResizableBuffer outBuffer (qmfV1BufferSize);
    while (matches.size()) {
        ManagementObject::shared_ptr object = matches.front();
        if (object->getConfigChanged() || object->getInstChanged())
            object->setUpdateTime();

        if (!object->isDeleted()) {
            string sProps, sStats;
            object->writeProperties(sProps);
            object->writeStatistics(sStats, true);

            size_t len = 8 + sProps.length() + sStats.length();   // 8 == size of header in bytes.
            if (len > qmfV1BufferSize) {
                QPID_LOG(error, "Object " << object->getObjectId() << " too large for output buffer - discarded!");
            } else {
                if (outBuffer.available() < len) {  // not enough room in current buffer, send it.
                    sendBuffer(outBuffer, dExchange, replyToKey);
                    QPID_LOG(debug, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
                    continue;  // lock dropped, need to re-find _SAME_ objid as it may have been deleted.
                }
                encodeHeader(outBuffer, 'g', sequence);
                outBuffer.putRawData(sProps);
                outBuffer.putRawData(sStats);
            }
        }
        matches.pop_front();
    }

    if (outBuffer.getPosition() > 0) {
        sendBuffer(outBuffer, dExchange, replyToKey);
        QPID_LOG(debug, "SEND GetResponse (v1) to=" << replyToKey << " seq=" << sequence);
    }

    sendCommandComplete(replyToKey, sequence);
}


void ManagementAgent::handleGetQuery(const string& body, const string& rte, const string& rtk, const string& cid, bool viaLocal)
{
    moveNewObjects();

    Variant::Map inMap;
    Variant::Map::const_iterator i;
    Variant::Map headers;

    MapCodec::decode(body, inMap);
    QPID_LOG(debug, "RECV GetQuery (v2): map=" << inMap << " seq=" << cid);

    headers["method"] = "response";
    headers["qmf.opcode"] = "_query_response";
    headers["qmf.content"] = "_data";
    headers["qmf.agent"] = viaLocal ? "broker" : name_address;

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

    if (i->second.asString() != "OBJECT") {
        sendException(rte, rtk, cid, "Query for _what => '" + i->second.asString() + "' not supported");
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

    if (className == "memory")
        qpid::sys::MemStat::loadMemInfo(memstat.get());

    if (className == "broker") {
        uint64_t uptime = sys::Duration(startTime, sys::now());
        boost::dynamic_pointer_cast<_qmf::Broker>(broker->GetManagementObject())->set_uptime(uptime);
    }

    /*
     * Unpack the _object_id element of the query if it is present.  If it is present, find that one
     * object and return it.  If it is not present, send a class-based result.
     */
    i = inMap.find("_object_id");
    if (i != inMap.end() && i->second.getType() == qpid::types::VAR_MAP) {
        Variant::List list_;
        ObjectId objId(i->second.asMap());

        ManagementObject::shared_ptr object;
        {
            sys::Mutex::ScopedLock lock (objectLock);
            ManagementObjectMap::iterator iter = managementObjects.find(objId);
            if (iter != managementObjects.end())
                object = iter->second;
        }
        if (object) {
            if (object->getConfigChanged() || object->getInstChanged())
                object->setUpdateTime();

            if (!object->isDeleted()) {
                Variant::Map  map_;
                Variant::Map values;
                Variant::Map oidMap;

                object->writeTimestamps(map_);
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
            sendBuffer(content, cid, headers, "amqp/list", rte, rtk);
            QPID_LOG(debug, "SENT QueryResponse (query by object_id) to=" << rte << "/" << rtk);
            return;
        }
    } else {
        // send class-based result.
        Variant::List _list;
        Variant::List _subList;
        unsigned int objCount = 0;

        ManagementObjectVector localManagementObjects;
        {
            sys::Mutex::ScopedLock objLock(objectLock);
            std::transform(managementObjects.begin(), managementObjects.end(),
                           std::back_inserter(localManagementObjects),
                           boost::bind(&ManagementObjectMap::value_type::second, _1));
        }

        for (ManagementObjectVector::iterator iter = localManagementObjects.begin();
             iter != localManagementObjects.end();
             iter++) {
            ManagementObject::shared_ptr object = *iter;
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
                    object->getObjectId().mapEncode(oidMap);

                    map_["_values"] = values;
                    map_["_object_id"] = oidMap;
                    map_["_schema_id"] = mapEncodeSchemaId(object->getPackageName(),
                                                           object->getClassName(),
                                                           "_data",
                                                           object->getMd5Sum());
                    _subList.push_back(map_);
                    if (++objCount >= maxReplyObjs) {
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
            sendBuffer(content, cid, headers, "amqp/list", rte, rtk);
            _list.pop_front();
            QPID_LOG(debug, "SENT QueryResponse (partial, query by schema_id) to=" << rte << "/" << rtk << " len=" << content.length());
        }
        headers.erase("partial");
        ListCodec::encode(_list.size() ? _list.front().asList() : Variant::List(), content);
        sendBuffer(content, cid, headers, "amqp/list", rte, rtk);
        QPID_LOG(debug, "SENT QueryResponse (query by schema_id) to=" << rte << "/" << rtk << " len=" << content.length());
        return;
    }

    // Unrecognized query - Send empty message to indicate CommandComplete
    string content;
    ListCodec::encode(Variant::List(), content);
    sendBuffer(content, cid, headers, "amqp/list", rte, rtk);
    QPID_LOG(debug, "SENT QueryResponse (empty) to=" << rte << "/" << rtk);
}


void ManagementAgent::handleLocateRequest(const string&, const string& rte, const string& rtk, const string& cid)
{
    QPID_LOG(debug, "RCVD AgentLocateRequest");

    Variant::Map map;
    Variant::Map headers;

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_locate_response";
    headers["qmf.agent"] = name_address;

    map["_values"] = attrMap;
    map["_values"].asMap()["_timestamp"] = uint64_t(sys::Duration(sys::EPOCH, sys::now()));
    map["_values"].asMap()["_heartbeat_interval"] = interval;
    map["_values"].asMap()["_epoch"] = bootSequence;

    string content;
    MapCodec::encode(map, content);
    sendBuffer(content, cid, headers, "amqp/map", rte, rtk);
    clientWasAdded = true;

    QPID_LOG(debug, "SENT AgentLocateResponse replyTo=" << rte << "/" << rtk);
}


bool ManagementAgent::authorizeAgentMessage(Message& msg)
{
    sys::Mutex::ScopedLock lock(userLock);
    ResizableBuffer inBuffer (qmfV1BufferSize);
    uint32_t sequence = 0;
    bool methodReq = false;
    bool mapMsg = false;
    string  packageName;
    string  className;
    string  methodName;
    string cid;

    //
    // If the message is larger than our working buffer size, we can't determine if it's
    // authorized or not.  In this case, return true (authorized) if there is no ACL in place,
    // otherwise return false;
    //
    if (msg.getContentSize() > qmfV1BufferSize)
        return broker->getAcl() == 0;

    inBuffer.putRawData(msg.getContent());
    uint32_t bufferLen = inBuffer.getPosition();
    inBuffer.reset();

    qpid::broker::amqp_0_10::MessageTransfer& transfer(qpid::broker::amqp_0_10::MessageTransfer::get(msg));
    const framing::MessageProperties* p =
        transfer.getFrames().getHeaders()->get<framing::MessageProperties>();

    const framing::FieldTable *headers = p ? &p->getApplicationHeaders() : 0;

    if (headers && p->getAppId() == "qmf2")
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
            sys::Mutex::ScopedLock lock(objectLock);
            ManagementObjectMap::iterator iter = managementObjects.find(objId);

            if (iter == managementObjects.end() || iter->second->isDeleted()) {
                QPID_LOG(debug, "ManagementAgent::authorizeAgentMessage: stale object id " <<
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

        qpid::broker::amqp_0_10::MessageTransfer& transfer(qpid::broker::amqp_0_10::MessageTransfer::get(msg));
        const framing::MessageProperties* p =
            transfer.getFrames().getHeaders()->get<framing::MessageProperties>();
        if (p && p->hasReplyTo()) {
            const framing::ReplyTo& rt = p->getReplyTo();
            string rte = rt.getExchange();
            string rtk = rt.getRoutingKey();
            string cid;
            if (p && p->hasCorrelationId())
                cid = p->getCorrelationId();

            if (mapMsg) {
                sendException(rte, rtk, cid, Manageable::StatusText(Manageable::STATUS_FORBIDDEN),
                              Manageable::STATUS_FORBIDDEN, false);
            } else {

                ResizableBuffer outBuffer(qmfV1BufferSize);

                encodeHeader(outBuffer, 'm', sequence);
                outBuffer.putLong(Manageable::STATUS_FORBIDDEN);
                outBuffer.putMediumString(Manageable::StatusText(Manageable::STATUS_FORBIDDEN));
                sendBuffer(outBuffer, rte, rtk);
            }

            QPID_LOG(debug, "SEND MethodResponse status=FORBIDDEN" << " seq=" << sequence);
        }

        return false;
    }

    return true;
}

void ManagementAgent::dispatchAgentCommand(Message& msg, bool viaLocal)
{
    string   rte;
    string   rtk;
    qpid::broker::amqp_0_10::MessageTransfer& transfer(qpid::broker::amqp_0_10::MessageTransfer::get(msg));
    const framing::MessageProperties* p =
        transfer.getFrames().getHeaders()->get<framing::MessageProperties>();
    if (p && p->hasReplyTo()) {
        const framing::ReplyTo& rt = p->getReplyTo();
        rte = rt.getExchange();
        rtk = rt.getRoutingKey();
    }
    else
        return;

    ResizableBuffer inBuffer(qmfV1BufferSize);
    uint8_t  opcode;

    if (msg.getContentSize() > qmfV1BufferSize) {
        QPID_LOG(debug, "ManagementAgent::dispatchAgentCommandLH: Message too large: " <<
                 msg.getContentSize());
        return;
    }

    inBuffer.putRawData(msg.getContent());
    uint32_t bufferLen = inBuffer.getPosition();
    inBuffer.reset();

    ScopedManagementContext context((const qpid::broker::ConnectionState*) msg.getPublisher());
    const framing::FieldTable *headers = p ? &p->getApplicationHeaders() : 0;
    if (headers && p->getAppId() == "qmf2")
    {
        string opcode = headers->getAsString("qmf.opcode");
        string contentType = headers->getAsString("qmf.content");
        string body;
        string cid;
        inBuffer.getRawData(body, bufferLen);
        {
            if (p && p->hasCorrelationId()) {
                cid = p->getCorrelationId();
            }

            if (opcode == "_method_request")
                return handleMethodRequest(body, rte, rtk, cid, msg.getPublisher(), viaLocal);
            else if (opcode == "_query_request")
                return handleGetQuery(body, rte, rtk, cid, viaLocal);
            else if (opcode == "_agent_locate_request")
                return handleLocateRequest(body, rte, rtk, cid);
        }
        QPID_LOG(warning, "Support for QMF Opcode [" << opcode << "] TBD!!!");
        return;
    }

    // old preV2 binary messages
    while (inBuffer.getPosition() < bufferLen) {
        uint32_t sequence;
        if (!checkHeader(inBuffer, &opcode, &sequence))
            return;

        if      (opcode == 'B') handleBrokerRequest  (inBuffer, rtk, sequence);
        else if (opcode == 'P') handlePackageQuery   (inBuffer, rtk, sequence);
        else if (opcode == 'p') handlePackageInd     (inBuffer, rtk, sequence);
        else if (opcode == 'Q') handleClassQuery     (inBuffer, rtk, sequence);
        else if (opcode == 'q') handleClassInd       (inBuffer, rtk, sequence);
        else if (opcode == 'S') handleSchemaRequest  (inBuffer, rte, rtk, sequence);
        else if (opcode == 's') handleSchemaResponse (inBuffer, rtk, sequence);
        else if (opcode == 'A') handleAttachRequest  (inBuffer, rtk, sequence, msg.getPublisher());
        else if (opcode == 'G') handleGetQuery       (inBuffer, rtk, sequence);
        else if (opcode == 'M') handleMethodRequest  (inBuffer, rtk, sequence, msg.getPublisher());
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
    ResizableBuffer outBuffer (qmfV1BufferSize);

    encodeHeader (outBuffer, 'p');
    encodePackageIndication (outBuffer, result.first);
    sendBuffer(outBuffer, mExchange, "schema.package");
    QPID_LOG(debug, "SEND PackageInd package=" << name << " to=schema.package");

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
    inBuffer.setPosition(start); // restore original position
    return end - start;
}

size_t ManagementAgent::validateEventSchema(Buffer& inBuffer)
{
    uint32_t start = inBuffer.getPosition();
    uint32_t end;
    string   text;
    uint8_t  hash[16];

    try {
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
    inBuffer.setPosition(start); // restore original position
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

    mgmtObject = _qmf::Agent::shared_ptr(new _qmf::Agent(&agent, this));

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
bool isDeletedMap(const ManagementObjectMap::value_type& value) {
    return value.second->isDeleted();
}

bool isDeletedVector(const ManagementObjectVector::value_type& value) {
    return value->isDeleted();
}

string summarizeMap(const char* name, const ManagementObjectMap& map) {
    ostringstream o;
    size_t deleted = std::count_if(map.begin(), map.end(), isDeletedMap);
    o << map.size() << " " << name << " (" << deleted << " deleted), ";
    return o.str();
}

string summarizeVector(const char* name, const ManagementObjectVector& map) {
    ostringstream o;
    size_t deleted = std::count_if(map.begin(), map.end(), isDeletedVector);
    o << map.size() << " " << name << " (" << deleted << " deleted), ";
    return o.str();
}

string dumpMap(const ManagementObjectMap& map) {
    ostringstream o;
    for (ManagementObjectMap::const_iterator i = map.begin(); i != map.end(); ++i) {
        o << endl << "   " << i->second->getObjectId().getV2Key()
          << (i->second->isDeleted() ? " (deleted)" : "");
    }
    return o.str();
}

string dumpVector(const ManagementObjectVector& map) {
    ostringstream o;
    for (ManagementObjectVector::const_iterator i = map.begin(); i != map.end(); ++i) {
        o << endl << "   " << (*i)->getObjectId().getV2Key()
          << ((*i)->isDeleted() ? " (deleted)" : "");
    }
    return o.str();
}

} // namespace

string ManagementAgent::summarizeAgents() {
    ostringstream msg;
    if (!remoteAgents.empty()) {
        msg <<  remoteAgents.size() << " agents(";
        for (RemoteAgentMap::const_iterator i=remoteAgents.begin();
             i != remoteAgents.end(); ++i)
            msg << " " << i->second->routingKey;
        msg << "), ";
    }
    return msg.str();
}


void ManagementAgent::debugSnapshot(const char* title) {
    QPID_LOG(debug, title << ": management snapshot: "
             << packages.size() << " packages, "
             << summarizeMap("objects", managementObjects)
             << summarizeVector("new objects ", newManagementObjects)
             << pendingDeletedObjs.size() << " pending deletes"
             << summarizeAgents());

    QPID_LOG_IF(trace, managementObjects.size(),
                title << ": objects" << dumpMap(managementObjects));
    QPID_LOG_IF(trace, newManagementObjects.size(),
                title << ": new objects" << dumpVector(newManagementObjects));
}


Variant::Map ManagementAgent::toMap(const FieldTable& from)
{
    Variant::Map map;
    qpid::amqp_0_10::translate(from, map);
    return map;
}


// Build up a list of the current set of deleted objects that are pending their
// next (last) publish-ment.
void ManagementAgent::exportDeletedObjects(DeletedObjectList& outList)
{
    outList.clear();

    sys::Mutex::ScopedLock lock (userLock);

    moveNewObjects();
    moveDeletedObjects();

    // now copy the pending deletes into the outList
    for (PendingDeletedObjsMap::iterator mIter = pendingDeletedObjs.begin();
         mIter != pendingDeletedObjs.end(); mIter++) {
        for (DeletedObjectList::iterator lIter = mIter->second.begin();
             lIter != mIter->second.end(); lIter++) {
            outList.push_back(*lIter);
        }
    }
}

// Called by cluster to reset the management agent's list of deleted
// objects to match the rest of the cluster.
void ManagementAgent::importDeletedObjects(const DeletedObjectList& inList)
{
    sys::Mutex::ScopedLock lock (userLock);
    sys::Mutex::ScopedLock objLock(objectLock);
    // Clear out any existing deleted objects
    moveNewObjects();
    pendingDeletedObjs.clear();
    ManagementObjectMap::iterator i = managementObjects.begin();
    // Silently drop any deleted objects left over from receiving the update.
    while (i != managementObjects.end()) {
        ManagementObject::shared_ptr object = i->second;
        if (object->isDeleted()) {
            managementObjects.erase(i++);
        }
        else ++i;
    }
    for (DeletedObjectList::const_iterator lIter = inList.begin(); lIter != inList.end(); lIter++) {

        std::string classkey((*lIter)->packageName + std::string(":") + (*lIter)->className);
        pendingDeletedObjs[classkey].push_back(*lIter);
    }
}


// construct a DeletedObject from a management object.
ManagementAgent::DeletedObject::DeletedObject(ManagementObject::shared_ptr src, bool v1, bool v2)
    : packageName(src->getPackageName()),
      className(src->getClassName())
{
    bool send_stats = (src->hasInst() && (src->getInstChanged() || src->getForcePublish()));

    stringstream oid;
    oid << src->getObjectId();
    objectId = oid.str();

    if (v1) {
        src->writeProperties(encodedV1Config);
        if (send_stats) {
            src->writeStatistics(encodedV1Inst);
        }
    }

    if (v2) {
        Variant::Map map_;
        Variant::Map values;
        Variant::Map oid;

        src->getObjectId().mapEncode(oid);
        map_["_object_id"] = oid;
        map_["_schema_id"] = mapEncodeSchemaId(src->getPackageName(),
                                               src->getClassName(),
                                               "_data",
                                               src->getMd5Sum());
        src->writeTimestamps(map_);
        src->mapEncodeValues(values, true, send_stats);
        map_["_values"] = values;

        encodedV2 = map_;
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

// Remove Deleted objects, and save for later publishing...
bool ManagementAgent::moveDeletedObjects() {
    typedef vector<pair<ObjectId, ManagementObject::shared_ptr> > DeleteList;

    sys::Mutex::ScopedLock lock (objectLock);

    DeleteList deleteList;
    for (ManagementObjectMap::iterator iter = managementObjects.begin();
         iter != managementObjects.end();
         ++iter)
    {
        ManagementObject::shared_ptr object = iter->second;
        if (object->isDeleted()) deleteList.push_back(*iter);
    }

    // Iterate in reverse over deleted object list
    for (DeleteList::reverse_iterator iter = deleteList.rbegin();
         iter != deleteList.rend();
         iter++)
    {
        ManagementObject::shared_ptr delObj = iter->second;
        assert(delObj->isDeleted());
        DeletedObject::shared_ptr dptr(new DeletedObject(delObj, qmf1Support, qmf2Support));

        pendingDeletedObjs[dptr->getKey()].push_back(dptr);
        managementObjects.erase(iter->first);
    }
    return !deleteList.empty();
}

ManagementAgent::EventQueue::Batch::const_iterator ManagementAgent::sendEvents(
    const EventQueue::Batch& batch)
{
    EventQueue::Batch::const_iterator i;
    for (i = batch.begin(); i != batch.end(); ++i) {
        DeliverableMessage deliverable (i->second, 0);
        try {
            i->first->route(deliverable);
        } catch(exception& e) {
            QPID_LOG(warning, "ManagementAgent failed to route event: " << e.what());
        }
    }
    return i;
}

namespace {
QPID_TSS const qpid::broker::ConnectionState* executionContext = 0;
}

void setManagementExecutionContext(const qpid::broker::ConnectionState* ctxt)
{
    executionContext = ctxt;
}
const qpid::broker::ConnectionState* getManagementExecutionContext()
{
    return executionContext;
}

}}

