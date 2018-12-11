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

#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/ConnectionFactory.h"
#include "qpid/broker/ConnectionToken.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/DtxManager.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/LinkRegistry.h"
#include "qpid/broker/SessionManager.h"
#include "qpid/broker/QueueCleaner.h"
#include "qpid/broker/QueueEvents.h"
#include "qpid/broker/Vhost.h"
#include "qpid/broker/System.h"
#include "qpid/broker/ExpiryPolicy.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/broker/Broker.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerConnect.h"
#include "qpid/Options.h"
#include "qpid/Plugin.h"
#include "qpid/DataDir.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/OutputHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Timer.h"
#include "qpid/types/Variant.h"
#include "qpid/RefCounted.h"
#include "qpid/broker/AclModule.h"
#include "qpid/sys/Mutex.h"

#include <boost/intrusive_ptr.hpp>
#include <string>
#include <vector>

namespace qpid {

namespace sys {
    class ProtocolFactory;
    class Poller;
}

struct Url;

namespace broker {

class ConnectionState;
class ExpiryPolicy;
class Message;
class Cluster;

static const  uint16_t DEFAULT_PORT=5672;

struct NoSuchTransportException : qpid::Exception
{
    NoSuchTransportException(const std::string& s) : Exception(s) {}
    virtual ~NoSuchTransportException() throw() {}
};

/**
 * A broker instance.
 */
class Broker : public sys::Runnable, public Plugin::Target,
               public management::Manageable,
               public RefCounted
{
public:

    struct Options : public qpid::Options {
        static const std::string DEFAULT_DATA_DIR_LOCATION;
        static const std::string DEFAULT_DATA_DIR_NAME;

        QPID_BROKER_EXTERN Options(const std::string& name="Broker Options");

        bool noDataDir;
        std::string dataDir;
        uint16_t port;
        int workerThreads;
        int maxConnections;
        int connectionBacklog;
        bool enableMgmt;
        uint16_t mgmtPubInterval;
        uint16_t queueCleanInterval;
        bool auth;
        std::string realm;
        size_t replayFlushLimit;
        size_t replayHardLimit;
        uint queueLimit;
        bool tcpNoDelay;
        bool requireEncrypted;
        std::string knownHosts;
        std::string saslConfigPath;
        uint32_t maxSessionRate;
        bool asyncQueueEvents;
        bool qmf2Support;
        bool qmf1Support;
        uint queueFlowStopRatio;    // producer flow control: on
        uint queueFlowResumeRatio;  // producer flow control: off
        uint16_t queueThresholdEventRatio;
        size_t outputPrefetch;

      private:
        std::string getHome();
    };

    class ConnectionCounter {
            int maxConnections;
            int connectionCount;
            sys::Mutex connectionCountLock;
        public:
            ConnectionCounter(int mc): maxConnections(mc),connectionCount(0) {};
            void inc_connectionCount() {
                sys::ScopedLock<sys::Mutex> l(connectionCountLock);
                connectionCount++;
            }
            void dec_connectionCount() {
                sys::ScopedLock<sys::Mutex> l(connectionCountLock);
                connectionCount--;
            }
            bool allowConnection() {
                sys::ScopedLock<sys::Mutex> l(connectionCountLock);
                return (maxConnections <= connectionCount);
            }
    };

  private:
    typedef std::map<std::string, boost::shared_ptr<sys::ProtocolFactory> > ProtocolFactoryMap;

    void declareStandardExchange(const std::string& name, const std::string& type);
    void setStore ();
    void setLogLevel(const std::string& level);
    std::string getLogLevel();
    void createObject(const std::string& type, const std::string& name,
                      const qpid::types::Variant::Map& properties, bool strict, const ConnectionState* context);
    void deleteObject(const std::string& type, const std::string& name,
                      const qpid::types::Variant::Map& options, const ConnectionState* context);

    boost::shared_ptr<sys::Poller> poller;
    sys::Timer timer;
    std::auto_ptr<sys::Timer> clusterTimer;
    Options config;
    std::auto_ptr<management::ManagementAgent> managementAgent;
    ProtocolFactoryMap protocolFactories;
    std::auto_ptr<MessageStore> store;
    std::auto_ptr<Cluster> cluster;
    AclModule* acl;
    DataDir dataDir;

    QueueRegistry queues;
    ExchangeRegistry exchanges;
    LinkRegistry links;
    boost::shared_ptr<sys::ConnectionCodec::Factory> factory;
    DtxManager dtxManager;
    SessionManager sessionManager;
    qmf::org::apache::qpid::broker::Broker* mgmtObject;
    Vhost::shared_ptr            vhostObject;
    System::shared_ptr           systemObject;
    QueueCleaner queueCleaner;
    QueueEvents queueEvents;
    std::vector<Url> knownBrokers;
    std::vector<Url> getKnownBrokersImpl();
    bool deferDeliveryImpl(const std::string& queue,
                           const boost::intrusive_ptr<Message>& msg);
    std::string federationTag;
    bool recovery;
    bool inCluster, clusterUpdatee;
    boost::intrusive_ptr<ExpiryPolicy> expiryPolicy;
    ConnectionCounter connectionCounter;

  public:
    virtual ~Broker();

    QPID_BROKER_EXTERN Broker(const Options& configuration);
    static QPID_BROKER_EXTERN boost::intrusive_ptr<Broker> create(const Options& configuration);
    static QPID_BROKER_EXTERN boost::intrusive_ptr<Broker> create(int16_t port = DEFAULT_PORT);

    /**
     * Return listening port. If called before bind this is
     * the configured port. If called after it is the actual
     * port, which will be different if the configured port is
     * 0.
     */
    virtual uint16_t getPort(const std::string& name) const;

    /**
     * Run the broker. Implements Runnable::run() so the broker
     * can be run in a separate thread.
     */
    virtual void run();

    /** Shut down the broker */
    virtual void shutdown();

    QPID_BROKER_EXTERN void setStore (boost::shared_ptr<MessageStore>& store);
    MessageStore& getStore() { return *store; }
    void setAcl (AclModule* _acl) {acl = _acl;}
    AclModule* getAcl() { return acl; }
    QueueRegistry& getQueues() { return queues; }
    ExchangeRegistry& getExchanges() { return exchanges; }
    LinkRegistry& getLinks() { return links; }
    DtxManager& getDtxManager() { return dtxManager; }
    DataDir& getDataDir() { return dataDir; }
    Options& getOptions() { return config; }
    QueueEvents& getQueueEvents() { return queueEvents; }

    void setExpiryPolicy(const boost::intrusive_ptr<ExpiryPolicy>& e) { expiryPolicy = e; }
    boost::intrusive_ptr<ExpiryPolicy> getExpiryPolicy() { return expiryPolicy; }

    SessionManager& getSessionManager() { return sessionManager; }
    const std::string& getFederationTag() const { return federationTag; }

    management::ManagementObject*     GetManagementObject (void) const;
    management::Manageable*           GetVhostObject      (void) const;
    management::Manageable::status_t  ManagementMethod (uint32_t methodId,
                                                        management::Args& args,
                                                        std::string& text);

    /** Add to the broker's protocolFactorys */
    void registerProtocolFactory(const std::string& name, boost::shared_ptr<sys::ProtocolFactory>);

    /** Accept connections */
    QPID_BROKER_EXTERN void accept();

    /** Create a connection to another broker. */
    void connect(const std::string& host, const std::string& port,
                 const std::string& transport,
                 boost::function2<void, int, std::string> failed,
                 sys::ConnectionCodec::Factory* =0);
    /** Create a connection to another broker. */
    void connect(const Url& url,
                 boost::function2<void, int, std::string> failed,
                 sys::ConnectionCodec::Factory* =0);

    /** Move messages from one queue to another.
        A zero quantity means to move all messages
    */
    uint32_t queueMoveMessages( const std::string& srcQueue,
			    const std::string& destQueue,
			    uint32_t  qty);

    boost::shared_ptr<sys::ProtocolFactory> getProtocolFactory(const std::string& name = TCP_TRANSPORT) const;

    /** Expose poller so plugins can register their descriptors. */
    boost::shared_ptr<sys::Poller> getPoller();

    boost::shared_ptr<sys::ConnectionCodec::Factory> getConnectionFactory() { return factory; }
    void setConnectionFactory(boost::shared_ptr<sys::ConnectionCodec::Factory> f) { factory = f; }

    /** Timer for local tasks affecting only this broker */
    sys::Timer& getTimer() { return timer; }

    /** Timer for tasks that must be synchronized if we are in a cluster */
    sys::Timer& getClusterTimer() { return clusterTimer.get() ? *clusterTimer : timer; }
    void setClusterTimer(std::auto_ptr<sys::Timer>);

    boost::function<std::vector<Url> ()> getKnownBrokers;

    static QPID_BROKER_EXTERN const std::string TCP_TRANSPORT;

    void setRecovery(bool set) { recovery = set; }
    bool getRecovery() const { return recovery; }

    /** True of this broker is part of a cluster.
     * Only valid after early initialization of plugins is complete.
     */
    bool isInCluster() const { return inCluster; }
    void setInCluster(bool set) { inCluster = set; }

    /** True if this broker is joining a cluster and in the process of
     * receiving a state update.
     */
    bool isClusterUpdatee() const { return clusterUpdatee; }
    void setClusterUpdatee(bool set) { clusterUpdatee = set; }

    QPID_BROKER_EXTERN void setCluster(std::auto_ptr<Cluster> c);
    QPID_BROKER_EXTERN Cluster& getCluster();

    management::ManagementAgent* getManagementAgent() { return managementAgent.get(); }

    ConnectionCounter& getConnectionCounter() {return connectionCounter;}

    /**
     * Never true in a stand-alone broker. In a cluster, return true
     * to defer delivery of messages deliveredg in a cluster-unsafe
     * context.
     *@return true if delivery of a message should be deferred.
     */
    boost::function<bool (const std::string& queue,
                          const boost::intrusive_ptr<Message>& msg)> deferDelivery;

    bool isAuthenticating ( ) { return config.auth; }

    typedef boost::function1<void, boost::shared_ptr<Queue> > QueueFunctor;

    std::pair<boost::shared_ptr<Queue>, bool> createQueue(
        const std::string& name,
        bool durable,
        bool autodelete,
        const OwnershipToken* owner,
        const std::string& alternateExchange,
        const qpid::framing::FieldTable& arguments,
        const std::string& userId,
        const std::string& connectionId);
    void deleteQueue(const std::string& name,
                     const std::string& userId,
                     const std::string& connectionId,
                     QueueFunctor check = QueueFunctor());
    std::pair<Exchange::shared_ptr, bool> createExchange(
        const std::string& name,
        const std::string& type,
        bool durable,
        const std::string& alternateExchange,
        const qpid::framing::FieldTable& args,
        const std::string& userId, const std::string& connectionId);
    void deleteExchange(const std::string& name, const std::string& userId,
                        const std::string& connectionId);
    void bind(const std::string& queue,
              const std::string& exchange,
              const std::string& key,
              const qpid::framing::FieldTable& arguments,
              const std::string& userId,
              const std::string& connectionId);
    void unbind(const std::string& queue,
                const std::string& exchange,
                const std::string& key,
                const std::string& userId,
                const std::string& connectionId);
};

}}

#endif  /*!_Broker_*/
