#ifndef _ManagementBroker_
#define _ManagementBroker_

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
#include "qpid/Options.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Timer.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/Mutex.h"
#include "qpid/broker/ConnectionToken.h"
#include "qpid/agent/ManagementAgent.h"
#include "ManagementObject.h"
#include "Manageable.h"
#include "qmf/org/apache/qpid/broker/Agent.h"
#include <qpid/framing/AMQFrame.h>

namespace qpid {
namespace management {

class ManagementBroker : public ManagementAgent
{
private:

    int threadPoolSize;

public:

    ManagementBroker ();
    virtual ~ManagementBroker ();

    void configure       (const std::string& dataDir, uint16_t interval,
                          qpid::broker::Broker* broker, int threadPoolSize);
    void setInterval     (uint16_t _interval) { interval = _interval; }
    void setExchange     (qpid::broker::Exchange::shared_ptr mgmtExchange,
                          qpid::broker::Exchange::shared_ptr directExchange);
    int  getMaxThreads   () { return threadPoolSize; }
    void registerClass   (const std::string& packageName,
                          const std::string& className,
                          uint8_t*    md5Sum,
                          ManagementObject::writeSchemaCall_t schemaCall);
    void registerEvent   (const std::string& packageName,
                          const std::string& eventName,
                          uint8_t*    md5Sum,
                          ManagementObject::writeSchemaCall_t schemaCall);
    ObjectId addObject   (ManagementObject* object,
                          uint64_t          persistId = 0);
    void raiseEvent(const ManagementEvent& event, severity_t severity = SEV_DEFAULT);
    void clientAdded     (const std::string& routingKey);
    bool dispatchCommand (qpid::broker::Deliverable&       msg,
                          const std::string&         routingKey,
                          const framing::FieldTable* args);
    const framing::Uuid& getUuid() const { return uuid; }

    // Stubs for remote management agent calls
    void init (const std::string&, uint16_t, uint16_t, bool,
               const std::string&, const std::string&, const std::string&,
               const std::string&, const std::string&) { assert(0); }
    uint32_t pollCallbacks (uint32_t) { assert(0); return 0; }
    int getSignalFd () { assert(0); return -1; }

private:
    friend class ManagementAgent;

    struct Periodic : public qpid::broker::TimerTask
    {
        ManagementBroker& broker;

        Periodic (ManagementBroker& broker, uint32_t seconds);
        virtual ~Periodic ();
        void fire ();
    };

    //  Storage for tracking remote management agents, attached via the client
    //  management agent API.
    //
    struct RemoteAgent : public Manageable
    {
        uint32_t          brokerBank;
        uint32_t          agentBank;
        std::string       routingKey;
        ObjectId          connectionRef;
        qmf::org::apache::qpid::broker::Agent*    mgmtObject;
        ManagementObject* GetManagementObject (void) const { return mgmtObject; }
        virtual ~RemoteAgent ();
    };

    // TODO: Eventually replace string with entire reply-to structure.  reply-to
    //       currently assumes that the exchange is "amq.direct" even though it could
    //       in theory be specified differently.
    typedef std::map<ObjectId, RemoteAgent*> RemoteAgentMap;
    typedef std::vector<std::string>         ReplyToVector;

    //  Storage for known schema classes:
    //
    //  SchemaClassKey     -- Key elements for map lookups
    //  SchemaClassKeyComp -- Comparison class for SchemaClassKey
    //  SchemaClass        -- Non-key elements for classes
    //
    struct SchemaClassKey
    {
        std::string name;
        uint8_t     hash[16];
    };

    struct SchemaClassKeyComp
    {
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

    struct SchemaClass
    {
        uint8_t  kind;
        ManagementObject::writeSchemaCall_t writeSchemaCall;
        uint32_t pendingSequence;
        size_t   bufferLen;
        uint8_t* buffer;

        SchemaClass(uint8_t _kind, uint32_t seq) :
            kind(_kind), writeSchemaCall(0), pendingSequence(seq), bufferLen(0), buffer(0) {}
        SchemaClass(uint8_t _kind, ManagementObject::writeSchemaCall_t call) :
            kind(_kind), writeSchemaCall(call), pendingSequence(0), bufferLen(0), buffer(0) {}
        bool hasSchema () { return (writeSchemaCall != 0) || (buffer != 0); }
        void appendSchema (framing::Buffer& buf);
    };

    typedef std::map<SchemaClassKey, SchemaClass, SchemaClassKeyComp> ClassMap;
    typedef std::map<std::string, ClassMap> PackageMap;

    RemoteAgentMap               remoteAgents;
    PackageMap                   packages;
    ManagementObjectMap          managementObjects;
    ManagementObjectMap          newManagementObjects;

    static ManagementAgent*      agent;
    static bool                  enabled;

    framing::Uuid                uuid;
    sys::Mutex                   addLock;
    sys::Mutex                   userLock;
    qpid::broker::Timer          timer;
    qpid::broker::Exchange::shared_ptr mExchange;
    qpid::broker::Exchange::shared_ptr dExchange;
    std::string                  dataDir;
    uint16_t                     interval;
    qpid::broker::Broker*        broker;
    uint16_t                     bootSequence;
    uint32_t                     nextObjectId;
    uint32_t                     brokerBank;
    uint32_t                     nextRemoteBank;
    uint32_t                     nextRequestSequence;
    bool                         clientWasAdded;

#   define MA_BUFFER_SIZE 65536
    char inputBuffer[MA_BUFFER_SIZE];
    char outputBuffer[MA_BUFFER_SIZE];
    char eventBuffer[MA_BUFFER_SIZE];

    void writeData ();
    void periodicProcessing (void);
    void encodeHeader       (framing::Buffer& buf, uint8_t  opcode, uint32_t  seq = 0);
    bool checkHeader        (framing::Buffer& buf, uint8_t *opcode, uint32_t *seq);
    void sendBuffer         (framing::Buffer&             buf,
                             uint32_t                     length,
                             qpid::broker::Exchange::shared_ptr exchange,
                             std::string                  routingKey);
    void moveNewObjectsLH();

    bool authorizeAgentMessageLH(qpid::broker::Message& msg);
    void dispatchAgentCommandLH(qpid::broker::Message& msg);

    PackageMap::iterator findOrAddPackageLH(std::string name);
    void addClassLH(uint8_t                      kind,
                    PackageMap::iterator         pIter,
                    const std::string&           className,
                    uint8_t*                     md5Sum,
                    ManagementObject::writeSchemaCall_t schemaCall);
    void encodePackageIndication (framing::Buffer&     buf,
                                  PackageMap::iterator pIter);
    void encodeClassIndication (framing::Buffer&     buf,
                                PackageMap::iterator pIter,
                                ClassMap::iterator   cIter);
    bool     bankInUse (uint32_t bank);
    uint32_t allocateNewBank ();
    uint32_t assignBankLH (uint32_t requestedPrefix);
    void deleteOrphanedAgentsLH();
    void sendCommandComplete (std::string replyToKey, uint32_t sequence,
                              uint32_t code = 0, std::string text = std::string("OK"));
    void handleBrokerRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageQueryLH   (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageIndLH     (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleClassQueryLH     (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleClassIndLH       (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleSchemaRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleSchemaResponseLH (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleAttachRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence, const qpid::broker::ConnectionToken* connToken);
    void handleGetQueryLH       (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleMethodRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence, const qpid::broker::ConnectionToken* connToken);

    size_t validateSchema(framing::Buffer&, uint8_t kind);
    size_t validateTableSchema(framing::Buffer&);
    size_t validateEventSchema(framing::Buffer&);
};

}}
            
#endif  /*!_ManagementBroker_*/
