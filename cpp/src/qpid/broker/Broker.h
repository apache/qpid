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

#include "qpid/DataDir.h"
#include "qpid/Plugin.h"
#include "qpid/broker/DtxManager.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/Protocol.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/LinkRegistry.h"
#include "qpid/broker/SessionManager.h"
#include "qpid/broker/QueueCleaner.h"
#include "qpid/broker/Vhost.h"
#include "qpid/broker/System.h"
#include "qpid/broker/ConsumerFactory.h"
#include "qpid/broker/ConnectionObservers.h"
#include "qpid/broker/ConfigurationObservers.h"
#include "qpid/management/Manageable.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Runnable.h"

#include <boost/intrusive_ptr.hpp>

#include <string>
#include <vector>

namespace qpid {

namespace sys {
class TransportAcceptor;
class TransportConnector;
class Poller;
class Timer;
}

struct Url;

namespace broker {

class AclModule;
class ConnectionState;
class ExpiryPolicy;
class Message;
struct QueueSettings;
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
        std::vector<std::string> listenInterfaces;
        int workerThreads;
        int connectionBacklog;
        bool enableMgmt;
        bool mgmtPublish;
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
        bool qmf2Support;
        bool qmf1Support;
        uint queueFlowStopRatio;    // producer flow control: on
        uint queueFlowResumeRatio;  // producer flow control: off
        uint16_t queueThresholdEventRatio;
        std::string defaultMsgGroup;
        bool timestampRcvMsgs;
        double linkMaintenanceInterval; // FIXME aconway 2012-02-13: consistent parsing of SECONDS values.
        uint16_t linkHeartbeatInterval;
        uint32_t maxNegotiateTime;  // Max time in ms for connection with no negotiation
        std::string fedTag;

      private:
        std::string getHome();
    };

  private:
    struct TransportInfo {
        boost::shared_ptr<sys::TransportAcceptor> acceptor;
        boost::shared_ptr<sys::TransportConnector> connectorFactory;
        uint16_t port;

        TransportInfo() :
            port(0)
        {}

        TransportInfo(boost::shared_ptr<sys::TransportAcceptor> a, boost::shared_ptr<sys::TransportConnector> c, uint16_t p) :
            acceptor(a),
            connectorFactory(c),
            port(p)
        {}
    };
    typedef std::map<std::string, TransportInfo > TransportMap;
    
    void declareStandardExchange(const std::string& name, const std::string& type);
    void setStore ();
    void setLogLevel(const std::string& level);
    std::string getLogLevel();
    void setLogHiresTimestamp(bool enabled);
    bool getLogHiresTimestamp();
    void createObject(const std::string& type, const std::string& name,
                      const qpid::types::Variant::Map& properties, bool strict, const ConnectionState* context);
    void deleteObject(const std::string& type, const std::string& name,
                      const qpid::types::Variant::Map& options, const ConnectionState* context);
    Manageable::status_t queryObject(const std::string& type, const std::string& name,
                                     qpid::types::Variant::Map& results, const ConnectionState* context);
    Manageable::status_t queryQueue( const std::string& name,
                                     const std::string& userId,
                                     const std::string& connectionId,
                                     qpid::types::Variant::Map& results);
    Manageable::status_t getTimestampConfig(bool& receive,
                                            const ConnectionState* context);
    Manageable::status_t setTimestampConfig(const bool receive,
                                            const ConnectionState* context);
    boost::shared_ptr<sys::Poller> poller;
    std::auto_ptr<sys::Timer> timer;
    Options config;
    std::auto_ptr<management::ManagementAgent> managementAgent;
    TransportMap transportMap;
    std::auto_ptr<MessageStore> store;
    AclModule* acl;
    DataDir dataDir;
    ConnectionObservers connectionObservers;
    ConfigurationObservers configurationObservers;

    QueueRegistry queues;
    ExchangeRegistry exchanges;
    LinkRegistry links;
    boost::shared_ptr<sys::ConnectionCodec::Factory> factory;
    DtxManager dtxManager;
    SessionManager sessionManager;
    qmf::org::apache::qpid::broker::Broker::shared_ptr mgmtObject;
    Vhost::shared_ptr            vhostObject;
    System::shared_ptr           systemObject;
    QueueCleaner queueCleaner;
    std::vector<Url> knownBrokers;
    std::vector<Url> getKnownBrokersImpl();
    bool deferDeliveryImpl(const std::string& queue,
                           const Message& msg);
    std::string federationTag;
    bool recoveryInProgress;
    boost::intrusive_ptr<ExpiryPolicy> expiryPolicy;
    ConsumerFactories consumerFactories;
    ProtocolRegistry protocolRegistry;

    mutable sys::Mutex linkClientPropertiesLock;
    framing::FieldTable linkClientProperties;

  public:
    QPID_BROKER_EXTERN virtual ~Broker();

    QPID_BROKER_EXTERN Broker(const Options& configuration);
    static QPID_BROKER_EXTERN boost::intrusive_ptr<Broker> create(const Options& configuration);
    static QPID_BROKER_EXTERN boost::intrusive_ptr<Broker> create(int16_t port = DEFAULT_PORT);

    /**
     * Return listening port. If called before bind this is
     * the configured port. If called after it is the actual
     * port, which will be different if the configured port is
     * 0.
     */
    QPID_BROKER_EXTERN virtual uint16_t getPort(const std::string& name) const;

    /**
     * Run the broker. Implements Runnable::run() so the broker
     * can be run in a separate thread.
     */
    QPID_BROKER_EXTERN virtual void run();

    /** Shut down the broker */
    QPID_BROKER_EXTERN virtual void shutdown();

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
    ProtocolRegistry& getProtocolRegistry() { return protocolRegistry; }

    void setExpiryPolicy(const boost::intrusive_ptr<ExpiryPolicy>& e) { expiryPolicy = e; }
    boost::intrusive_ptr<ExpiryPolicy> getExpiryPolicy() { return expiryPolicy; }

    SessionManager& getSessionManager() { return sessionManager; }
    const std::string& getFederationTag() const { return federationTag; }

    QPID_BROKER_EXTERN management::ManagementObject::shared_ptr GetManagementObject() const;
    QPID_BROKER_EXTERN management::Manageable* GetVhostObject() const;
    QPID_BROKER_EXTERN management::Manageable::status_t ManagementMethod(
        uint32_t methodId, management::Args& args, std::string& text);

    /** Add to the broker's protocolFactorys */
    QPID_BROKER_EXTERN void registerTransport(
        const std::string& name,
        boost::shared_ptr<sys::TransportAcceptor>, boost::shared_ptr<sys::TransportConnector>,
        uint16_t port);

    /** Accept connections */
    QPID_BROKER_EXTERN void accept();

    /** Create a connection to another broker. */
    void connect(const std::string& name,
                 const std::string& host, const std::string& port,
                 const std::string& transport,
                 boost::function2<void, int, std::string> failed);

    /** Move messages from one queue to another.
        A zero quantity means to move all messages
        Return -1 if one of the queues does not exist, otherwise
               the number of messages moved.
    */
    QPID_BROKER_EXTERN int32_t queueMoveMessages(
        const std::string& srcQueue,
        const std::string& destQueue,
        uint32_t  qty,
        const qpid::types::Variant::Map& filter);

    QPID_BROKER_EXTERN const TransportInfo& getTransportInfo(
        const std::string& name = TCP_TRANSPORT) const;

    /** Expose poller so plugins can register their descriptors. */
    QPID_BROKER_EXTERN boost::shared_ptr<sys::Poller> getPoller();

    /** Timer for local tasks affecting only this broker */
    sys::Timer& getTimer() { return *timer; }

    boost::function<std::vector<Url> ()> getKnownBrokers;

    static QPID_BROKER_EXTERN const std::string TCP_TRANSPORT;

    bool inRecovery() const { return recoveryInProgress; }

    management::ManagementAgent* getManagementAgent() { return managementAgent.get(); }

    bool isAuthenticating ( ) { return config.auth; }
    bool isTimestamping() { return config.timestampRcvMsgs; }

    typedef boost::function1<void, boost::shared_ptr<Queue> > QueueFunctor;

    QPID_BROKER_EXTERN std::pair<boost::shared_ptr<Queue>, bool> createQueue(
        const std::string& name,
        const QueueSettings& settings,
        const OwnershipToken* owner,
        const std::string& alternateExchange,
        const std::string& userId,
        const std::string& connectionId);

    QPID_BROKER_EXTERN void deleteQueue(
        const std::string& name,
        const std::string& userId,
        const std::string& connectionId,
        QueueFunctor check = QueueFunctor());

    QPID_BROKER_EXTERN std::pair<Exchange::shared_ptr, bool> createExchange(
        const std::string& name,
        const std::string& type,
        bool durable,
        const std::string& alternateExchange,
        const qpid::framing::FieldTable& args,
        const std::string& userId, const std::string& connectionId);

    QPID_BROKER_EXTERN void deleteExchange(
        const std::string& name, const std::string& userId,
        const std::string& connectionId);

    QPID_BROKER_EXTERN void bind(
        const std::string& queue,
        const std::string& exchange,
        const std::string& key,
        const qpid::framing::FieldTable& arguments,
        const std::string& userId,
        const std::string& connectionId);

    QPID_BROKER_EXTERN void unbind(
        const std::string& queue,
        const std::string& exchange,
        const std::string& key,
        const std::string& userId,
        const std::string& connectionId);

    ConsumerFactories&  getConsumerFactories() { return consumerFactories; }
    ConnectionObservers& getConnectionObservers() { return connectionObservers; }
    ConfigurationObservers& getConfigurationObservers() { return configurationObservers; }

    /** Properties to be set on outgoing link connections */
    QPID_BROKER_EXTERN framing::FieldTable getLinkClientProperties() const;
    QPID_BROKER_EXTERN void setLinkClientProperties(const framing::FieldTable&);

    QPID_BROKER_EXTERN uint16_t getLinkHearbeatInterval() { return config.linkHeartbeatInterval; }
    /** Information identifying this system */
    boost::shared_ptr<const System> getSystem() const { return systemObject; }
  friend class StatusCheckThread;
};

}}

#endif  /*!_Broker_*/
