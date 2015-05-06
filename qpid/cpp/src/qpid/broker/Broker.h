#ifndef QPID_BROKER_BROKER_H
#define QPID_BROKER_BROKER_H

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
#include "qpid/Options.h"
#include "qpid/Plugin.h"
#include "qpid/broker/DtxManager.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/ObjectFactory.h"
#include "qpid/broker/Protocol.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/LinkRegistry.h"
#include "qpid/broker/SessionManager.h"
#include "qpid/broker/QueueCleaner.h"
#include "qpid/broker/Vhost.h"
#include "qpid/broker/System.h"
#include "qpid/broker/ConsumerFactory.h"
#include "qpid/broker/ConnectionObservers.h"
#include "qpid/broker/SessionHandlerObserver.h"
#include "qpid/broker/BrokerObservers.h"
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
struct BrokerOptions;
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
                      const qpid::types::Variant::Map& properties, bool strict, const Connection* context);
    void deleteObject(const std::string& type, const std::string& name,
                      const qpid::types::Variant::Map& options, const Connection* context);
    void checkDeleteQueue(boost::shared_ptr<Queue> queue, bool ifUnused, bool ifEmpty);
    Manageable::status_t queryObject(const std::string& type, const std::string& name,
                                     qpid::types::Variant::Map& results, const Connection* context);
    Manageable::status_t queryQueue( const std::string& name,
                                     const std::string& userId,
                                     const std::string& connectionId,
                                     qpid::types::Variant::Map& results);
    Manageable::status_t getTimestampConfig(bool& receive,
                                            const Connection* context);
    Manageable::status_t setTimestampConfig(const bool receive,
                                            const Connection* context);
    Manageable::status_t queueRedirect(const std::string& srcQueue, const std::string& tgtQueue, const Connection* context);
    void queueRedirectDestroy(boost::shared_ptr<Queue> srcQ, boost::shared_ptr<Queue> tgtQ, bool moveMsgs);

    // This must be the first member of Broker. It logs a start-up message
    // at the start of Broker construction and a shut-down message at the
    // end of destruction.
    struct LogPrefix : public std::string {
        LogPrefix();
        ~LogPrefix();
    } logPrefix;

    boost::shared_ptr<sys::Poller> poller;
    std::auto_ptr<sys::Timer> timer;
    const BrokerOptions& config;
    std::auto_ptr<management::ManagementAgent> managementAgent;
    std::set<std::string> disabledListeningTransports;
    TransportMap transportMap;
    std::auto_ptr<MessageStore> store;
    AclModule* acl;
    DataDir dataDir;
    DataDir pagingDir;
    ConnectionObservers connectionObservers;
    SessionHandlerObservers sessionHandlerObservers;
    BrokerObservers brokerObservers;

    QueueRegistry queues;
    ExchangeRegistry exchanges;
    LinkRegistry links;
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
    ConsumerFactories consumerFactories;
    ProtocolRegistry protocolRegistry;
    ObjectFactoryRegistry objectFactory;

    mutable sys::Mutex linkClientPropertiesLock;
    framing::FieldTable linkClientProperties;
    bool timestampRcvMsgs;

  public:
    QPID_BROKER_EXTERN virtual ~Broker();

    QPID_BROKER_EXTERN Broker(const BrokerOptions& configuration);
    static QPID_BROKER_EXTERN boost::intrusive_ptr<Broker> create(const BrokerOptions& configuration);
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

    QPID_BROKER_EXTERN void setStore (const boost::shared_ptr<MessageStore>& store);
    bool hasStore() const { return store.get(); }
    MessageStore& getStore() { return *store; }
    void setAcl (AclModule* _acl) {acl = _acl;}
    AclModule* getAcl() { return acl; }
    QueueRegistry& getQueues() { return queues; }
    ExchangeRegistry& getExchanges() { return exchanges; }
    LinkRegistry& getLinks() { return links; }
    DtxManager& getDtxManager() { return dtxManager; }
    const DataDir& getDataDir() { return dataDir; }
    const DataDir& getPagingDir() { return pagingDir; }
    ProtocolRegistry& getProtocolRegistry() { return protocolRegistry; }
    ObjectFactoryRegistry& getObjectFactoryRegistry() { return objectFactory; }

    SessionManager& getSessionManager() { return sessionManager; }
    const std::string& getFederationTag() const { return federationTag; }

    QPID_BROKER_EXTERN management::ManagementObject::shared_ptr GetManagementObject() const;
    QPID_BROKER_EXTERN management::Manageable* GetVhostObject() const;
    QPID_BROKER_EXTERN management::Manageable::status_t ManagementMethod(
        uint32_t methodId, management::Args& args, std::string& text);

    // Should we listen using this protocol or not?
    QPID_BROKER_EXTERN bool shouldListen(std::string transport);

    // Turn off listening for a protocol
    QPID_BROKER_EXTERN void disableListening(std::string transport);

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
    QPID_BROKER_EXTERN void connect(const std::string& name,
                                    const std::string& host, const std::string& port,
                                    const std::string& transport,
                                    sys::ConnectionCodec::Factory*,
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
        const qpid::types::Variant::Map& filter,
        const Connection* context);

    QPID_BROKER_EXTERN const TransportInfo& getTransportInfo(
        const std::string& name = TCP_TRANSPORT) const;

    /** Expose poller so plugins can register their descriptors. */
    QPID_BROKER_EXTERN boost::shared_ptr<sys::Poller> getPoller();

    /** Timer for local tasks affecting only this broker */
    sys::Timer& getTimer() { return *timer; }

    boost::function<std::vector<Url> ()> getKnownBrokers;

    static QPID_BROKER_EXTERN const std::string TCP_TRANSPORT;

    management::ManagementAgent* getManagementAgent() { return managementAgent.get(); }

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
        bool autodelete,
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
        const OwnershipToken* owner,
        const std::string& userId,
        const std::string& connectionId);

    QPID_BROKER_EXTERN void unbind(
        const std::string& queue,
        const std::string& exchange,
        const std::string& key,
        const OwnershipToken* owner,
        const std::string& userId,
        const std::string& connectionId);

    ConsumerFactories&  getConsumerFactories() { return consumerFactories; }
    ConnectionObservers& getConnectionObservers() { return connectionObservers; }
    SessionHandlerObservers& getSessionHandlerObservers() { return sessionHandlerObservers; }
    BrokerObservers& getBrokerObservers() { return brokerObservers; }

    /** Properties to be set on outgoing link connections */
    QPID_BROKER_EXTERN framing::FieldTable getLinkClientProperties() const;
    QPID_BROKER_EXTERN void setLinkClientProperties(const framing::FieldTable&);

    bool inRecovery() const { return recoveryInProgress; }
    bool isTimestamping() const { return timestampRcvMsgs; }
    QPID_BROKER_EXTERN bool isAuthenticating() const;
    QPID_BROKER_EXTERN bool requireEncrypted() const;
    QPID_BROKER_EXTERN std::string getRealm() const;
    QPID_BROKER_EXTERN std::string getSaslServiceName() const;
    QPID_BROKER_EXTERN bool getTcpNoDelay() const;
    QPID_BROKER_EXTERN uint16_t getPortOption() const;
    QPID_BROKER_EXTERN const std::vector<std::string>& getListenInterfaces() const;
    QPID_BROKER_EXTERN int getConnectionBacklog() const;
    uint32_t getMaxNegotiateTime() const;
    sys::Duration getLinkMaintenanceInterval() const;
    QPID_BROKER_EXTERN sys::Duration getLinkHeartbeatInterval() const;
    uint32_t getDtxMaxTimeout() const;
    uint16_t getQueueThresholdEventRatio() const;
    uint getQueueLimit() const;

    /** Information identifying this system */
    boost::shared_ptr<const System> getSystem() const { return systemObject; }
};

}}

#endif
