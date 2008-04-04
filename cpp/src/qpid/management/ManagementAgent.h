#ifndef _ManagementAgent_
#define _ManagementAgent_

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
#include "ManagementObject.h"
#include <qpid/framing/AMQFrame.h>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace management {

class ManagementAgent
{
  private:

    ManagementAgent (std::string dataDir, uint16_t interval);

  public:

    virtual ~ManagementAgent ();

    typedef boost::shared_ptr<ManagementAgent> shared_ptr;

    static void       enableManagement (std::string dataDir, uint16_t interval);
    static shared_ptr getAgent (void);
    static void       shutdown (void);

    void setInterval     (uint16_t _interval) { interval = _interval; }
    void setExchange     (broker::Exchange::shared_ptr mgmtExchange,
                          broker::Exchange::shared_ptr directExchange);
    void RegisterClass   (std::string packageName,
                          std::string className,
                          uint8_t*    md5Sum,
                          ManagementObject::writeSchemaCall_t schemaCall);
    void addObject       (ManagementObject::shared_ptr object,
                          uint64_t                     persistenceId = 0,
                          uint64_t                     idOffset      = 10);
    void clientAdded     (void);
    void dispatchCommand (broker::Deliverable&             msg,
                          const std::string&               routingKey,
                          const qpid::framing::FieldTable* args);
    
  private:

    struct Periodic : public broker::TimerTask
    {
        ManagementAgent& agent;

        Periodic (ManagementAgent& agent, uint32_t seconds);
        virtual ~Periodic ();
        void fire ();
    };

    //  Storage for tracking remote management agents, attached via the client
    //  management agent API.
    //
    struct RemoteAgent
    {
        std::string name;
        uint64_t    objIdBase;
    };

    // TODO: Eventually replace string with entire reply-to structure.  reply-to
    //       currently assumes that the exchange is "amq.direct" even though it could
    //       in theory be specified differently.
    typedef std::map<std::string, RemoteAgent> RemoteAgentMap;
    typedef std::vector<std::string>           ReplyToVector;

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

        SchemaClass () : writeSchemaCall(0) {}
    };

    typedef std::map<SchemaClassKey, SchemaClass, SchemaClassKeyComp> ClassMap;
    typedef std::map<std::string, ClassMap> PackageMap;

    RemoteAgentMap               remoteAgents;
    PackageMap                   packages;
    ManagementObjectMap          managementObjects;

    static shared_ptr            agent;
    static bool                  enabled;

    qpid::framing::Uuid          uuid;
    qpid::sys::RWlock            userLock;
    broker::Timer                timer;
    broker::Exchange::shared_ptr mExchange;
    broker::Exchange::shared_ptr dExchange;
    std::string                  dataDir;
    uint16_t                     interval;
    uint64_t                     nextObjectId;
    uint32_t                     nextRemotePrefix;

#   define MA_BUFFER_SIZE 65536
    char inputBuffer[MA_BUFFER_SIZE];
    char outputBuffer[MA_BUFFER_SIZE];

    void PeriodicProcessing (void);
    void EncodeHeader       (qpid::framing::Buffer& buf, uint8_t  opcode, uint32_t  seq = 0);
    bool CheckHeader        (qpid::framing::Buffer& buf, uint8_t *opcode, uint32_t *seq);
    void SendBuffer         (qpid::framing::Buffer&       buf,
                             uint32_t                     length,
                             broker::Exchange::shared_ptr exchange,
                             std::string                  routingKey);

    void dispatchMethod (broker::Message&   msg,
                         const std::string& routingKey,
                         size_t             first);
    void dispatchAgentCommand (broker::Message& msg);

    PackageMap::iterator FindOrAddPackage (std::string name);
    void AddClassLocal (PackageMap::iterator         pIter,
                        std::string                  className,
                        uint8_t*                     md5Sum,
                        ManagementObject::writeSchemaCall_t schemaCall);
    void EncodePackageIndication (qpid::framing::Buffer& buf,
                                  PackageMap::iterator   pIter);
    void EncodeClassIndication (qpid::framing::Buffer& buf,
                                PackageMap::iterator   pIter,
                                ClassMap::iterator     cIter);
    uint32_t assignPrefix (uint32_t requestedPrefix);
    void sendCommandComplete (std::string replyToKey, uint32_t sequence,
                              uint32_t code = 0, std::string text = std::string("OK"));
    void handleBrokerRequest (qpid::framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageQuery  (qpid::framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageInd    (qpid::framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleClassQuery    (qpid::framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleSchemaQuery   (qpid::framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleAttachRequest (qpid::framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleGetRequest    (qpid::framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
};

}}
            
#endif  /*!_ManagementAgent_*/
