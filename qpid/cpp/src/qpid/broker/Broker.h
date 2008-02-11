#ifndef _Broker_
#define _Broker_

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

#include "ConnectionFactory.h"
#include "ConnectionToken.h"
#include "DirectExchange.h"
#include "DtxManager.h"
#include "ExchangeRegistry.h"
#include "MessageStore.h"
#include "QueueRegistry.h"
#include "SessionManager.h"
#include "Vhost.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/management/Broker.h"
#include "qpid/management/ArgsBrokerConnect.h"
#include "qpid/Options.h"
#include "qpid/Plugin.h"
#include "qpid/DataDir.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/OutputHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/sys/Acceptor.h"
#include "qpid/sys/Runnable.h"

#include <vector>

namespace qpid { 
namespace broker {

static const  uint16_t DEFAULT_PORT=5672;

/**
 * A broker instance. 
 */
class Broker : public sys::Runnable, public Plugin::Target, public management::Manageable
{
  public:

    struct Options : public qpid::Options {
        Options(const std::string& name="Broker Options");

        std::string dataDir;
        uint16_t port;
        int workerThreads;
        int maxConnections;
        int connectionBacklog;
        uint64_t stagingThreshold;
        bool enableMgmt;
        uint16_t mgmtPubInterval;
        uint32_t ack;
    };
    
    virtual ~Broker();

    Broker(const Options& configuration);
    static shared_ptr<Broker> create(const Options& configuration);
    static shared_ptr<Broker> create(int16_t port = DEFAULT_PORT);

    /**
     * Return listening port. If called before bind this is
     * the configured port. If called after it is the actual
     * port, which will be different if the configured port is
     * 0.
     */
    virtual uint16_t getPort() const;

    /**
     * Run the broker. Implements Runnable::run() so the broker
     * can be run in a separate thread.
     */
    virtual void run();

    /** Shut down the broker */
    virtual void shutdown();

    void setStore (MessageStore*);
    MessageStore& getStore() { return *store; }
    QueueRegistry& getQueues() { return queues; }
    ExchangeRegistry& getExchanges() { return exchanges; }
    uint64_t getStagingThreshold() { return config.stagingThreshold; }
    DtxManager& getDtxManager() { return dtxManager; }
    DataDir& getDataDir() { return dataDir; }

    SessionManager& getSessionManager() { return sessionManager; }

    management::ManagementObject::shared_ptr GetManagementObject (void) const;
    management::Manageable*                  GetVhostObject      (void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args);
    
  private:
    sys::Acceptor& getAcceptor() const;

    Options config;
    sys::Acceptor::shared_ptr acceptor;
    MessageStore* store;
    DataDir dataDir;

    QueueRegistry queues;
    ExchangeRegistry exchanges;
    ConnectionFactory factory;
    DtxManager dtxManager;
    SessionManager sessionManager;
    management::ManagementAgent::shared_ptr managementAgent;
    management::Broker::shared_ptr mgmtObject;
    Vhost::shared_ptr              vhostObject;

    void declareStandardExchange(const std::string& name, const std::string& type);
    void connect(management::ArgsBrokerConnect& args);
};

}}
            


#endif  /*!_Broker_*/
