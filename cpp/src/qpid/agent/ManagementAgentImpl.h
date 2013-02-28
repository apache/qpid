#ifndef _qpid_agent_ManagementAgentImpl_
#define _qpid_agent_ManagementAgentImpl_

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

#include "qpid/agent/ManagementAgent.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/Session.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/client/Message.h"
#include "qpid/client/MessageListener.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/PipeHandle.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/Uuid.h"
#include <iostream>
#include <sstream>
#include <deque>

namespace qpid { 
namespace management {

class ManagementAgentImpl : public ManagementAgent, public client::MessageListener
{
  public:

    ManagementAgentImpl();
    virtual ~ManagementAgentImpl();

    //
    // Methods from ManagementAgent
    //
    int getMaxThreads() { return 1; }
    void setName(const std::string& vendor,
                 const std::string& product,
                 const std::string& instance="");
    void getName(std::string& vendor, std::string& product, std::string& instance);
    const std::string& getAddress();
    void init(const std::string& brokerHost = "localhost",
              uint16_t brokerPort = 5672,
              uint16_t intervalSeconds = 10,
              bool useExternalThread = false,
              const std::string& storeFile = "",
              const std::string& uid = "",
              const std::string& pwd = "",
              const std::string& mech = "PLAIN",
              const std::string& proto = "tcp");
    void init(const management::ConnectionSettings& settings,
              uint16_t intervalSeconds = 10,
              bool useExternalThread = false,
              const std::string& storeFile = "");
    bool isConnected() { return connected; }
    std::string& getLastFailure() { return lastFailure; }
    void registerClass(const std::string& packageName,
                       const std::string& className,
                       uint8_t*     md5Sum,
                       management::ManagementObject::writeSchemaCall_t schemaCall);
    void registerEvent(const std::string& packageName,
                       const std::string& eventName,
                       uint8_t*     md5Sum,
                       management::ManagementObject::writeSchemaCall_t schemaCall);
    ObjectId addObject(management::ManagementObject* objectPtr, uint64_t persistId = 0);
    ObjectId addObject(management::ManagementObject* objectPtr, const std::string& key,
                       bool persistent);
    void raiseEvent(const management::ManagementEvent& event, severity_t severity = SEV_DEFAULT);
    uint32_t pollCallbacks(uint32_t callLimit = 0);
    int getSignalFd();
    void setSignalCallback(cb_t callback, void* context);
    void setSignalCallback(Notifyable& n);

    uint16_t getInterval() { return interval; }
    void periodicProcessing();

    uint16_t getBootSequence(void) { return bootSequence; }
    void setBootSequence(uint16_t b) { bootSequence = b; }

  private:

    struct SchemaClassKey {
        std::string name;
        uint8_t     hash[16];
    };

    struct SchemaClassKeyComp {
        bool operator() (const SchemaClassKey& lhs, const SchemaClassKey& rhs) const
        {
            if (lhs.name != rhs.name)
                return lhs.name < rhs.name;
            else
                for (int i = 0; i < 16; i++)
                    if (lhs.hash[i] != rhs.hash[i])
                        return lhs.hash[i] < rhs.hash[i];
            return false;
        }
    };

    struct SchemaClass {
        management::ManagementObject::writeSchemaCall_t writeSchemaCall;
        uint8_t kind;

        SchemaClass(const management::ManagementObject::writeSchemaCall_t call,
                    const uint8_t _kind) : writeSchemaCall(call), kind(_kind) {}
    };

    struct QueuedMethod {
    QueuedMethod(const std::string& _cid, const std::string& _rte, const std::string& _rtk, const std::string& _body, const std::string& _uid) :
        cid(_cid), replyToExchange(_rte), replyToKey(_rtk), body(_body), userId(_uid) {}

        std::string cid;
        std::string replyToExchange;
        std::string replyToKey;
        std::string body;
        std::string userId;
    };

    typedef std::deque<QueuedMethod*> MethodQueue;
    typedef std::map<SchemaClassKey, SchemaClass, SchemaClassKeyComp> ClassMap;
    typedef std::map<std::string, ClassMap> PackageMap;

    PackageMap                       packages;
    AgentAttachment                  attachment;

    typedef std::map<ObjectId, boost::shared_ptr<ManagementObject> > ObjectMap;

    ObjectMap    managementObjects;
    ObjectMap    newManagementObjects;
    MethodQueue  methodQueue;

    void received (client::Message& msg);

    qpid::types::Variant::Map attrMap;
    std::string       name_address;
    std::string       vendorNameKey;    // vendor name with "." --> "_"
    std::string       productNameKey;   // product name with "." --> "_"
    std::string       instanceNameKey;  // agent instance with "." --> "_"
    uint16_t          interval;
    bool              extThread;
    sys::PipeHandle*  pipeHandle;
    uint64_t          nextObjectId;
    cb_t              notifyCallback;
    void*             notifyContext;
    Notifyable*       notifyable;
    bool              inCallback;
    std::string       storeFile;
    sys::Mutex        agentLock;
    sys::Mutex        addLock;
    framing::Uuid     systemId;
    client::ConnectionSettings connectionSettings;
    bool              initialized;
    bool              connected;
    bool              useMapMsg;
    std::string       lastFailure;
    std::string       topicExchange;
    std::string       directExchange;
    qpid::sys::Duration schemaTimestamp;

    bool              publishAllData;
    uint32_t          requestedBrokerBank;
    uint32_t          requestedAgentBank;
    uint32_t          assignedBrokerBank;
    uint32_t          assignedAgentBank;
    uint16_t          bootSequence;

    // Maximum # of objects allowed in a single V2 response
    // message.
    uint32_t maxV2ReplyObjs;

    static const uint8_t DEBUG_OFF     = 0;
    static const uint8_t DEBUG_CONN    = 1;
    static const uint8_t DEBUG_PROTO   = 2;
    static const uint8_t DEBUG_PUBLISH = 3;

#   define MA_BUFFER_SIZE 65536
    char outputBuffer[MA_BUFFER_SIZE];
    char eventBuffer[MA_BUFFER_SIZE];

    friend class ConnectionThread;
    class ConnectionThread : public sys::Runnable
    {
        typedef boost::shared_ptr<client::SubscriptionManager> shared_ptr;

        bool operational;
        ManagementAgentImpl& agent;
        framing::Uuid        sessionId;
        client::Connection   connection;
        client::Session      session;
        ConnectionThread::shared_ptr subscriptions;
        std::stringstream queueName;
        mutable sys::Mutex   connLock;
        bool              shutdown;
        bool              sleeping;
        void run();
    public:
        ConnectionThread(ManagementAgentImpl& _agent) :
            operational(false), agent(_agent),
            shutdown(false), sleeping(false) {}
        ~ConnectionThread();
        void sendBuffer(qpid::framing::Buffer& buf,
                        uint32_t               length,
                        const std::string&     exchange,
                        const std::string&     routingKey);
        void sendBuffer(const std::string&     data,
                        const std::string&     cid,
                        const qpid::types::Variant::Map headers,
                        const std::string&     exchange,
                        const std::string&     routingKey,
                        const std::string&     contentType="amqp/map",
                        uint64_t               ttl_msec=0);
        void sendMessage(qpid::client::Message msg,
                         const std::string&     exchange,
                         const std::string&     routingKey);
        void bindToBank(uint32_t brokerBank, uint32_t agentBank);
        void close();
        bool isSleeping() const;
    };

    class PublishThread : public sys::Runnable
    {
        ManagementAgentImpl& agent;
        void run();
        bool shutdown;
    public:
        PublishThread(ManagementAgentImpl& _agent) :
            agent(_agent), shutdown(false) {}
        void close() { shutdown = true; }
    };

    ConnectionThread connThreadBody;
    sys::Thread      connThread;
    PublishThread    pubThreadBody;
    sys::Thread      pubThread;

    static const std::string storeMagicNumber;

    void startProtocol();
    void storeData(bool requested=false);
    void retrieveData(std::string& vendor, std::string& product, std::string& inst);
    PackageMap::iterator findOrAddPackage(const std::string& name);
    void moveNewObjectsLH(const sys::Mutex::ScopedLock& agentLock);
    void addClassLocal (uint8_t               classKind,
                        PackageMap::iterator  pIter,
                        const std::string&    className,
                        uint8_t*              md5Sum,
                        management::ManagementObject::writeSchemaCall_t schemaCall);
    void encodePackageIndication (framing::Buffer& buf,
                                  PackageMap::iterator   pIter);
    void encodeClassIndication (framing::Buffer& buf,
                                PackageMap::iterator   pIter,
                                ClassMap::iterator     cIter);
    void encodeHeader (framing::Buffer& buf, uint8_t  opcode, uint32_t  seq = 0);
    qpid::types::Variant::Map mapEncodeSchemaId(const std::string& pname,
                                                const std::string& cname,
                                                const uint8_t *md5Sum,
                                                uint8_t type=ManagementItem::CLASS_KIND_TABLE);
    bool checkHeader  (framing::Buffer& buf, uint8_t *opcode, uint32_t *seq);
    void sendHeartbeat();
    void sendException(const std::string& replyToExchange, const std::string& replyToKey, const std::string& cid,
                       const std::string& text, uint32_t code=1);
    void handlePackageRequest (qpid::framing::Buffer& inBuffer);
    void handleClassQuery     (qpid::framing::Buffer& inBuffer);
    void handleSchemaRequest  (qpid::framing::Buffer& inBuffer, uint32_t sequence, const std::string& rte, const std::string& rtk);
    void invokeMethodRequest  (const std::string& body, const std::string& cid, const std::string& rte, const std::string& rtk, const std::string& userId);

    void handleGetQuery       (const std::string& body, const std::string& cid, const std::string& rte, const std::string& rtk);
    void handleLocateRequest  (const std::string& body, const std::string& sequence, const std::string& rte, const std::string& rtk);
    void handleMethodRequest  (const std::string& body, const std::string& sequence, const std::string& rte, const std::string& rtk, const std::string& userId);
    void handleConsoleAddedIndication();
    void getHeartbeatContent  (qpid::types::Variant::Map& map);
};

}}

#endif  /*!_qpid_agent_ManagementAgentImpl_*/
