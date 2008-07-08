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
#include "qpid/agent/ManagementAgent.h"
#include "ManagementObject.h"
#include "Manageable.h"
#include "qpid/management/Agent.h"
#include <qpid/framing/AMQFrame.h>

namespace qpid {
namespace management {

class ManagementBroker : public ManagementAgent
{
  private:

    ManagementBroker (std::string dataDir, uint16_t interval, Manageable* broker, int threadPoolSize);
    int threadPoolSize;

  public:

    virtual ~ManagementBroker ();

    static void enableManagement (std::string dataDir, uint16_t interval, Manageable* broker, int threadPoolSize);
    static ManagementAgent* getAgent (void);
    static void shutdown (void);

    void setInterval     (uint16_t _interval) { interval = _interval; }
    void setExchange     (broker::Exchange::shared_ptr mgmtExchange,
                          broker::Exchange::shared_ptr directExchange);
    int  getMaxThreads   () { return threadPoolSize; }
    void RegisterClass   (std::string packageName,
                          std::string className,
                          uint8_t*    md5Sum,
                          ManagementObject::writeSchemaCall_t schemaCall);
    uint64_t addObject   (ManagementObject* object,
                          uint32_t          persistId   = 0,
                          uint32_t          persistBank = 4);
    void clientAdded     (void);
    void dispatchCommand (broker::Deliverable&       msg,
                          const std::string&         routingKey,
                          const framing::FieldTable* args);

    // Stubs for remote management agent calls
    void init (std::string, uint16_t, uint16_t, bool) { assert(0); }
    uint32_t pollCallbacks (uint32_t) { assert(0); return 0; }
    int getSignalFd () { assert(0); return -1; }

  private:
    friend class ManagementAgent;

    struct Periodic : public broker::TimerTask
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
        uint32_t          objIdBank;
        Agent*            mgmtObject;
        ManagementObject* GetManagementObject (void) const { return mgmtObject; }
        virtual ~RemoteAgent ();
    };

    // TODO: Eventually replace string with entire reply-to structure.  reply-to
    //       currently assumes that the exchange is "amq.direct" even though it could
    //       in theory be specified differently.
    typedef std::map<framing::Uuid, RemoteAgent*> RemoteAgentMap;
    typedef std::vector<std::string>              ReplyToVector;

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
        ManagementObject::writeSchemaCall_t writeSchemaCall;
        ReplyToVector                       remoteAgents;
        size_t                              bufferLen;
        uint8_t*                            buffer;

        SchemaClass () : writeSchemaCall(0), bufferLen(0), buffer(0) {}
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
    broker::Timer                timer;
    broker::Exchange::shared_ptr mExchange;
    broker::Exchange::shared_ptr dExchange;
    std::string                  dataDir;
    uint16_t                     interval;
    Manageable*                  broker;
    uint16_t                     bootSequence;
    uint32_t                     localBank;
    uint32_t                     nextObjectId;
    uint32_t                     nextRemoteBank;
    bool                         clientWasAdded;

#   define MA_BUFFER_SIZE 65536
    char inputBuffer[MA_BUFFER_SIZE];
    char outputBuffer[MA_BUFFER_SIZE];

    void writeData ();
    void PeriodicProcessing (void);
    void EncodeHeader       (framing::Buffer& buf, uint8_t  opcode, uint32_t  seq = 0);
    bool CheckHeader        (framing::Buffer& buf, uint8_t *opcode, uint32_t *seq);
    void SendBuffer         (framing::Buffer&             buf,
                             uint32_t                     length,
                             broker::Exchange::shared_ptr exchange,
                             std::string                  routingKey);
    void moveNewObjectsLH();

    void dispatchMethodLH (broker::Message&   msg,
                           const std::string& routingKey,
                           size_t             first);
    void dispatchAgentCommandLH (broker::Message& msg);

    PackageMap::iterator FindOrAddPackage (std::string name);
    void AddClassLocal (PackageMap::iterator         pIter,
                        std::string                  className,
                        uint8_t*                     md5Sum,
                        ManagementObject::writeSchemaCall_t schemaCall);
    void EncodePackageIndication (framing::Buffer&     buf,
                                  PackageMap::iterator pIter);
    void EncodeClassIndication (framing::Buffer&     buf,
                                PackageMap::iterator pIter,
                                ClassMap::iterator   cIter);
    bool     bankInUse (uint32_t bank);
    uint32_t allocateNewBank ();
    uint32_t assignBankLH (uint32_t requestedPrefix);
    void sendCommandComplete (std::string replyToKey, uint32_t sequence,
                              uint32_t code = 0, std::string text = std::string("OK"));
    void handleBrokerRequestLH (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageQueryLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageIndLH    (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleClassQueryLH    (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleSchemaRequestLH (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleAttachRequestLH (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleGetQueryLH      (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
};

}}
            
#endif  /*!_ManagementBroker_*/
