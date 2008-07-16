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

#include "ManagementAgent.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Dispatcher.h"
#include "qpid/client/Session.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/client/Message.h"
#include "qpid/client/MessageListener.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Mutex.h"
#include "qpid/framing/Uuid.h"
#include <iostream>
#include <sstream>

namespace qpid { 
namespace management {

class ManagementAgentImpl : public ManagementAgent, public client::MessageListener
{
  public:

    ManagementAgentImpl();
    virtual ~ManagementAgentImpl();

    int getMaxThreads() { return 1; }
    void init(std::string brokerHost        = "localhost",
              uint16_t    brokerPort        = 5672,
              uint16_t    intervalSeconds   = 10,
              bool        useExternalThread = false);
    void RegisterClass(std::string packageName,
                       std::string className,
                       uint8_t*    md5Sum,
                       management::ManagementObject::writeSchemaCall_t schemaCall);
    uint64_t addObject     (management::ManagementObject* objectPtr,
                            uint32_t          persistId   = 0,
                            uint32_t          persistBank = 4);
    uint32_t pollCallbacks (uint32_t callLimit = 0);
    int      getSignalFd   (void);

    void PeriodicProcessing();

  private:

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
        management::ManagementObject::writeSchemaCall_t writeSchemaCall;

        SchemaClass () : writeSchemaCall(0) {}
    };

    typedef std::map<SchemaClassKey, SchemaClass, SchemaClassKeyComp> ClassMap;
    typedef std::map<std::string, ClassMap> PackageMap;

    PackageMap                       packages;
    management::ManagementObjectMap  managementObjects;
    management::ManagementObjectMap  newManagementObjects;

    void received (client::Message& msg);

    uint16_t          interval;
    bool              extThread;
    uint64_t          nextObjectId;
    sys::Mutex        agentLock;
    sys::Mutex        addLock;
    framing::Uuid     sessionId;
    framing::Uuid     systemId;

    int signalFdIn, signalFdOut;
    client::Connection   connection;
    client::Session      session;
    client::Dispatcher*  dispatcher;
    bool                 clientWasAdded;
    uint64_t    objIdPrefix;
    std::stringstream queueName;
#   define MA_BUFFER_SIZE 65536
    char outputBuffer[MA_BUFFER_SIZE];

    class BackgroundThread : public sys::Runnable
    {
        ManagementAgentImpl& agent;
        void run();
    public:
        BackgroundThread(ManagementAgentImpl& _agent) : agent(_agent) {}
    };

    BackgroundThread bgThread;
    sys::Thread      thread;

    PackageMap::iterator FindOrAddPackage (std::string name);
    void moveNewObjectsLH();
    void AddClassLocal (PackageMap::iterator  pIter,
                        std::string           className,
                        uint8_t*              md5Sum,
                        management::ManagementObject::writeSchemaCall_t schemaCall);
    void EncodePackageIndication (qpid::framing::Buffer& buf,
                                  PackageMap::iterator   pIter);
    void EncodeClassIndication (qpid::framing::Buffer& buf,
                                PackageMap::iterator   pIter,
                                ClassMap::iterator     cIter);
    void EncodeHeader (qpid::framing::Buffer& buf, uint8_t  opcode, uint32_t  seq = 0);
    bool CheckHeader  (qpid::framing::Buffer& buf, uint8_t *opcode, uint32_t *seq);
    void SendBuffer         (qpid::framing::Buffer&  buf,
                             uint32_t                length,
                             std::string             exchange,
                             std::string             routingKey);
    void handleAttachResponse (qpid::framing::Buffer& inBuffer);
    void handlePackageRequest (qpid::framing::Buffer& inBuffer);
    void handleClassQuery     (qpid::framing::Buffer& inBuffer);
    void handleSchemaRequest  (qpid::framing::Buffer& inBuffer, uint32_t sequence);
    void handleConsoleAddedIndication();
};

}}

#endif  /*!_qpid_agent_ManagementAgentImpl_*/
