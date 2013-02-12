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

#include "qpid/broker/Broker.h"

#include "qpid/broker/AclModule.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/HeadersExchange.h"
#include "qpid/broker/MessageStoreModule.h"
#include "qpid/broker/NameGenerator.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/RecoveryManagerImpl.h"
#include "qpid/broker/SaslAuthenticator.h"
#include "qpid/broker/SecureConnectionFactory.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/ExpiryPolicy.h"
#include "qpid/broker/QueueFlowLimit.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/broker/MessageGroupManager.h"

#include "qmf/org/apache/qpid/broker/Package.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerConnect.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerCreate.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerDelete.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerQuery.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerEcho.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerGetLogLevel.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerQueueMoveMessages.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerSetLogLevel.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerGetLogHiresTimestamp.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerSetLogHiresTimestamp.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerSetTimestampConfig.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerGetTimestampConfig.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDeclare.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDelete.h"
#include "qmf/org/apache/qpid/broker/EventBind.h"
#include "qmf/org/apache/qpid/broker/EventUnbind.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/management/ManagementDirectExchange.h"
#include "qpid/management/ManagementTopicExchange.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/log/Statement.h"
#include "qpid/log/posix/SinkOptions.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/ProtocolFactory.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Timer.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/TimeoutHandler.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/Address.h"
#include "qpid/StringUtils.h"
#include "qpid/Url.h"
#include "qpid/Version.h"

#include <boost/bind.hpp>
#include <boost/format.hpp>

#include <iostream>
#include <memory>

using qpid::sys::ProtocolFactory;
using qpid::sys::Poller;
using qpid::sys::Dispatcher;
using qpid::sys::Thread;
using qpid::framing::FrameHandler;
using qpid::framing::ChannelId;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
using qpid::management::getManagementExecutionContext;
using qpid::types::Variant;
using std::string;
using std::make_pair;

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {

const std::string empty;
const std::string amq_direct("amq.direct");
const std::string amq_topic("amq.topic");
const std::string amq_fanout("amq.fanout");
const std::string amq_match("amq.match");
const std::string qpid_management("qpid.management");
const std::string knownHostsNone("none");

Broker::Options::Options(const std::string& name) :
    qpid::Options(name),
    noDataDir(0),
    port(DEFAULT_PORT),
    workerThreads(5),
    connectionBacklog(10),
    enableMgmt(1),
    mgmtPublish(1),
    mgmtPubInterval(10),
    queueCleanInterval(60*10),//10 minutes
    auth(SaslAuthenticator::available()),
    realm("QPID"),
    replayFlushLimit(0),
    replayHardLimit(0),
    queueLimit(100*1048576/*100M default limit*/),
    tcpNoDelay(false),
    requireEncrypted(false),
    knownHosts(knownHostsNone),
    qmf2Support(true),
    qmf1Support(true),
    queueFlowStopRatio(80),
    queueFlowResumeRatio(70),
    queueThresholdEventRatio(80),
    defaultMsgGroup("qpid.no-group"),
    timestampRcvMsgs(false),    // set the 0.10 timestamp delivery property
    linkMaintenanceInterval(2),
    linkHeartbeatInterval(120),
    maxNegotiateTime(10000)     // 10s
{
    int c = sys::SystemInfo::concurrency();
    workerThreads=c+1;
    std::string home = getHome();

    if (home.length() == 0)
        dataDir += DEFAULT_DATA_DIR_LOCATION;
    else
        dataDir += home;
    dataDir += DEFAULT_DATA_DIR_NAME;

    addOptions()
        ("data-dir", optValue(dataDir,"DIR"), "Directory to contain persistent data generated by the broker")
        ("no-data-dir", optValue(noDataDir), "Don't use a data directory.  No persistent configuration will be loaded or stored")
        ("port,p", optValue(port,"PORT"), "Tells the broker to listen on PORT")
        ("interface", optValue(listenInterfaces, "<interface name>|<interface address>"), "Which network interfaces to use to listen for incoming connections")
        ("worker-threads", optValue(workerThreads, "N"), "Sets the broker thread pool size")
        ("connection-backlog", optValue(connectionBacklog, "N"), "Sets the connection backlog limit for the server socket")
        ("mgmt-enable,m", optValue(enableMgmt,"yes|no"), "Enable Management")
        ("mgmt-publish", optValue(mgmtPublish,"yes|no"), "Enable Publish of Management Data ('no' implies query-only)")
        ("mgmt-qmf2", optValue(qmf2Support,"yes|no"), "Enable broadcast of management information over QMF v2")
        ("mgmt-qmf1", optValue(qmf1Support,"yes|no"), "Enable broadcast of management information over QMF v1")
        // FIXME aconway 2012-02-13: consistent treatment of values in SECONDS
        // allow sub-second intervals.
        ("mgmt-pub-interval", optValue(mgmtPubInterval, "SECONDS"), "Management Publish Interval")
        ("queue-purge-interval", optValue(queueCleanInterval, "SECONDS"),
         "Interval between attempts to purge any expired messages from queues")
        ("auth", optValue(auth, "yes|no"), "Enable authentication, if disabled all incoming connections will be trusted")
        ("realm", optValue(realm, "REALM"), "Use the given realm when performing authentication")
        ("default-queue-limit", optValue(queueLimit, "BYTES"), "Default maximum size for queues (in bytes)")
        ("tcp-nodelay", optValue(tcpNoDelay), "Set TCP_NODELAY on TCP connections")
        ("require-encryption", optValue(requireEncrypted), "Only accept connections that are encrypted")
        ("known-hosts-url", optValue(knownHosts, "URL or 'none'"), "URL to send as 'known-hosts' to clients ('none' implies empty list)")
        ("sasl-config", optValue(saslConfigPath, "DIR"), "gets sasl config info from nonstandard location")
        ("default-flow-stop-threshold", optValue(queueFlowStopRatio, "PERCENT"), "Percent of queue's maximum capacity at which flow control is activated.")
        ("default-flow-resume-threshold", optValue(queueFlowResumeRatio, "PERCENT"), "Percent of queue's maximum capacity at which flow control is de-activated.")
        ("default-event-threshold-ratio", optValue(queueThresholdEventRatio, "%age of limit"), "The ratio of any specified queue limit at which an event will be raised")
        ("default-message-group", optValue(defaultMsgGroup, "GROUP-IDENTIFER"), "Group identifier to assign to messages delivered to a message group queue that do not contain an identifier.")
        ("enable-timestamp", optValue(timestampRcvMsgs, "yes|no"), "Add current time to each received message.")
        ("link-maintenance-interval", optValue(linkMaintenanceInterval, "SECONDS"),
         "Interval to check link health and re-connect  if need be")
        ("link-heartbeat-interval", optValue(linkHeartbeatInterval, "SECONDS"),
         "Heartbeat interval for a federation link")
        ("max-negotiate-time", optValue(maxNegotiateTime, "MILLISECONDS"), "Maximum time a connection can take to send the initial protocol negotiation")
        ("federation-tag", optValue(fedTag, "NAME"), "Override the federation tag")
        ;
}

namespace {
// Arguments to declare a non-replicated exchange.
framing::FieldTable noReplicateArgs() {
    framing::FieldTable args;
    args.setString("qpid.replicate", "none");
    return args;
}
}

Broker::Broker(const Broker::Options& conf) :
    poller(new Poller),
    timer(new qpid::sys::Timer),
    config(conf),
    managementAgent(conf.enableMgmt ? new ManagementAgent(conf.qmf1Support,
                                                          conf.qmf2Support)
                                    : 0),
    store(new NullMessageStore),
    acl(0),
    dataDir(conf.noDataDir ? std::string() : conf.dataDir),
    queues(this),
    exchanges(this),
    links(this),
    factory(new SecureConnectionFactory(*this)),
    dtxManager(*timer.get()),
    sessionManager(
        qpid::SessionState::Configuration(
            conf.replayFlushLimit*1024, // convert kb to bytes.
            conf.replayHardLimit*1024),
        *this),
    queueCleaner(queues, timer.get()),
    recoveryInProgress(false),
    expiryPolicy(new ExpiryPolicy),
    getKnownBrokers(boost::bind(&Broker::getKnownBrokersImpl, this))
{
    try {
    if (conf.enableMgmt) {
        QPID_LOG(info, "Management enabled");
        managementAgent->configure(dataDir.isEnabled() ? dataDir.getPath() : string(), conf.mgmtPublish,
                                   conf.mgmtPubInterval, this, conf.workerThreads + 3);
        managementAgent->setName("apache.org", "qpidd");
        _qmf::Package packageInitializer(managementAgent.get());

        System* system = new System (dataDir.isEnabled() ? dataDir.getPath() : string(), this);
        systemObject = System::shared_ptr(system);

        mgmtObject = _qmf::Broker::shared_ptr(new _qmf::Broker(managementAgent.get(), this, system, "amqp-broker"));
        mgmtObject->set_systemRef(system->GetManagementObject()->getObjectId());
        mgmtObject->set_port(conf.port);
        mgmtObject->set_workerThreads(conf.workerThreads);
        mgmtObject->set_connBacklog(conf.connectionBacklog);
        mgmtObject->set_mgmtPubInterval(conf.mgmtPubInterval);
        mgmtObject->set_mgmtPublish(conf.mgmtPublish);
        mgmtObject->set_version(qpid::version);
        if (dataDir.isEnabled())
            mgmtObject->set_dataDir(dataDir.getPath());
        else
            mgmtObject->clr_dataDir();

        managementAgent->addObject(mgmtObject, 0, true);

        // Since there is currently no support for virtual hosts, a placeholder object
        // representing the implied single virtual host is added here to keep the
        // management schema correct.
        Vhost* vhost = new Vhost(this, this);
        vhostObject = Vhost::shared_ptr(vhost);
        if (conf.fedTag.empty()) {
            framing::Uuid uuid(managementAgent->getUuid());
            federationTag = uuid.str();
        } else
            federationTag = conf.fedTag;
        vhostObject->setFederationTag(federationTag);

        queues.setParent(vhost);
        exchanges.setParent(vhost);
        links.setParent(vhost);
    } else {
        // Management is disabled so there is no broker management ID.
        // Create a unique uuid to use as the federation tag.
        if (conf.fedTag.empty()) {
            framing::Uuid uuid(true);
            federationTag = uuid.str();
        } else
            federationTag = conf.fedTag;
    }

    // Early-Initialize plugins
    Plugin::earlyInitAll(*this);

    QueueFlowLimit::setDefaults(conf.queueLimit, conf.queueFlowStopRatio, conf.queueFlowResumeRatio);
    MessageGroupManager::setDefaults(conf.defaultMsgGroup);

    // If no plugin store module registered itself, set up the null store.
    if (NullMessageStore::isNullStore(store.get()))
        setStore();

    framing::FieldTable args;

    // Default exchnge is not replicated.
    exchanges.declare(empty, DirectExchange::typeName, false, noReplicateArgs());

    if (store.get() != 0) {
        RecoveryManagerImpl recoverer(
            queues, exchanges, links, dtxManager, protocolRegistry);
        recoveryInProgress = true;
        store->recover(recoverer);
        recoveryInProgress = false;
    }

    //ensure standard exchanges exist (done after recovery from store)
    declareStandardExchange(amq_direct, DirectExchange::typeName);
    declareStandardExchange(amq_topic, TopicExchange::typeName);
    declareStandardExchange(amq_fanout, FanOutExchange::typeName);
    declareStandardExchange(amq_match, HeadersExchange::typeName);

    if(conf.enableMgmt) {
        exchanges.declare(qpid_management, ManagementTopicExchange::typeName, false, noReplicateArgs());
        Exchange::shared_ptr mExchange = exchanges.get(qpid_management);
        Exchange::shared_ptr dExchange = exchanges.get(amq_direct);
        managementAgent->setExchange(mExchange, dExchange);
        boost::dynamic_pointer_cast<ManagementTopicExchange>(mExchange)->setManagmentAgent(managementAgent.get(), 1);

        std::string qmfTopic("qmf.default.topic");
        std::string qmfDirect("qmf.default.direct");

        std::pair<Exchange::shared_ptr, bool> topicPair(
            exchanges.declare(qmfTopic, ManagementTopicExchange::typeName, false, noReplicateArgs()));
        std::pair<Exchange::shared_ptr, bool> directPair(
            exchanges.declare(qmfDirect, ManagementDirectExchange::typeName, false, noReplicateArgs()));

        boost::dynamic_pointer_cast<ManagementDirectExchange>(directPair.first)->setManagmentAgent(managementAgent.get(), 2);
        boost::dynamic_pointer_cast<ManagementTopicExchange>(topicPair.first)->setManagmentAgent(managementAgent.get(), 2);

        managementAgent->setExchangeV2(topicPair.first, directPair.first);
    }
    else
        QPID_LOG(info, "Management not enabled");

    // this feature affects performance, so let's be sure that gets logged!
    if (conf.timestampRcvMsgs) {
        QPID_LOG(notice, "Receive message timestamping is ENABLED.");
    }

    /**
     * SASL setup, can fail and terminate startup
     */
    if (conf.auth) {
        SaslAuthenticator::init(qpid::saslName, conf.saslConfigPath);
        QPID_LOG(info, "SASL enabled");
    } else {
        QPID_LOG(notice, "SASL disabled: No Authentication Performed");
    }

    // Initialize plugins
    Plugin::initializeAll(*this);

    if(conf.enableMgmt) {
        if (getAcl()) {
            mgmtObject->set_maxConns(getAcl()->getMaxConnectTotal());
        }
    }

    if (conf.queueCleanInterval) {
        queueCleaner.start(conf.queueCleanInterval * qpid::sys::TIME_SEC);
    }

    if (!conf.knownHosts.empty() && conf.knownHosts != knownHostsNone) {
        knownBrokers.push_back(Url(conf.knownHosts));
    }

    } catch (const std::exception&) {
        finalize();
        throw;
    }
}

void Broker::declareStandardExchange(const std::string& name, const std::string& type)
{
    bool storeEnabled = store.get() != NULL;
    framing::FieldTable args;
    // Standard exchanges are not replicated.
    std::pair<Exchange::shared_ptr, bool> status =
        exchanges.declare(name, type, storeEnabled, noReplicateArgs());
    if (status.second && storeEnabled) {
        store->create(*status.first, framing::FieldTable ());
    }
}


boost::intrusive_ptr<Broker> Broker::create(int16_t port)
{
    Options config;
    config.port=port;
    return create(config);
}

boost::intrusive_ptr<Broker> Broker::create(const Options& opts)
{
    return boost::intrusive_ptr<Broker>(new Broker(opts));
}

void Broker::setStore (boost::shared_ptr<MessageStore>& _store)
{
    store.reset(new MessageStoreModule (_store));
    setStore();
}

void Broker::setStore () {
    queues.setStore     (store.get());
    dtxManager.setStore (store.get());
    links.setStore      (store.get());
}

void Broker::run() {
    if (config.workerThreads > 0) {
        QPID_LOG(notice, "Broker running");
        Dispatcher d(poller);
        int numIOThreads = config.workerThreads;
        std::vector<Thread> t(numIOThreads-1);

        // Run n-1 io threads
        for (int i=0; i<numIOThreads-1; ++i)
            t[i] = Thread(d);

        // Run final thread
        d.run();

        // Now wait for n-1 io threads to exit
        for (int i=0; i<numIOThreads-1; ++i) {
            t[i].join();
        }
    } else {
        throw Exception((boost::format("Invalid value for worker-threads: %1%") % config.workerThreads).str());
    }
}

void Broker::shutdown() {
    // NB: this function must be async-signal safe, it must not
    // call any function that is not async-signal safe.
    // Any unsafe shutdown actions should be done in the destructor.
    poller->shutdown();
}

Broker::~Broker() {
    shutdown();
    finalize();                 // Finalize any plugins.
    if (config.auth)
        SaslAuthenticator::fini();
    timer->stop();
    QPID_LOG(notice, "Shut down");
}

ManagementObject::shared_ptr Broker::GetManagementObject(void) const
{
    return mgmtObject;
}

Manageable* Broker::GetVhostObject(void) const
{
    return vhostObject.get();
}

Manageable::status_t Broker::ManagementMethod (uint32_t methodId,
                                               Args&    args,
                                               string&  text)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    switch (methodId)
    {
    case _qmf::Broker::METHOD_ECHO :
        QPID_LOG (debug, "Broker::echo("
                  << dynamic_cast<_qmf::ArgsBrokerEcho&>(args).io_sequence
                  << ", "
                  << dynamic_cast<_qmf::ArgsBrokerEcho&>(args).io_body
                  << ")");
        status = Manageable::STATUS_OK;
        break;
    case _qmf::Broker::METHOD_CONNECT : {
        /** Management is creating a Link to a remote broker using the host and port of
         * the remote.  This (old) interface does not allow management to specify a name
         * for the link, nor does it allow multiple Links to the same remote.  Use the
         * "create()" broker method if these features are needed.
         * TBD: deprecate this interface.
         */
        QPID_LOG(info, "The Broker::connect() method will be removed in a future release of QPID."
                 " Please use the Broker::create() method with type='link' instead.");
        _qmf::ArgsBrokerConnect& hp=
            dynamic_cast<_qmf::ArgsBrokerConnect&>(args);

        string transport = hp.i_transport.empty() ? TCP_TRANSPORT : hp.i_transport;
        QPID_LOG (debug, "Broker::connect() " << hp.i_host << ":" << hp.i_port << "; transport=" << transport <<
                        "; durable=" << (hp.i_durable?"T":"F") << "; authMech=\"" << hp.i_authMechanism << "\"");
        if (!getProtocolFactory(transport)) {
            QPID_LOG(error, "Transport '" << transport << "' not supported");
            text = "transport type not supported";
            return  Manageable::STATUS_NOT_IMPLEMENTED;
        }

        // Does a link to the remote already exist?  If so, re-use the existing link
        // - this behavior is backward compatible with previous releases.
        if (!links.getLink(hp.i_host, hp.i_port, transport)) {
            // new link, need to generate a unique name for it
            std::pair<Link::shared_ptr, bool> response =
              links.declare(Link::createName(transport, hp.i_host, hp.i_port),
                            hp.i_host, hp.i_port, transport,
                            hp.i_durable, hp.i_authMechanism, hp.i_username, hp.i_password);
            if (!response.first) {
                text = "Unable to create Link";
                status = Manageable::STATUS_PARAMETER_INVALID;
                break;
            }
        }
        status = Manageable::STATUS_OK;
        break;
      }
    case _qmf::Broker::METHOD_QUEUEMOVEMESSAGES : {
        _qmf::ArgsBrokerQueueMoveMessages& moveArgs=
            dynamic_cast<_qmf::ArgsBrokerQueueMoveMessages&>(args);
        QPID_LOG (debug, "Broker::queueMoveMessages()");
        if (queueMoveMessages(moveArgs.i_srcQueue, moveArgs.i_destQueue, moveArgs.i_qty, moveArgs.i_filter) >= 0)
            status = Manageable::STATUS_OK;
        else
            return Manageable::STATUS_PARAMETER_INVALID;
        break;
      }
    case _qmf::Broker::METHOD_SETLOGLEVEL :
        setLogLevel(dynamic_cast<_qmf::ArgsBrokerSetLogLevel&>(args).i_level);
        QPID_LOG (debug, "Broker::setLogLevel()");
        status = Manageable::STATUS_OK;
        break;
    case _qmf::Broker::METHOD_GETLOGLEVEL :
        dynamic_cast<_qmf::ArgsBrokerGetLogLevel&>(args).o_level = getLogLevel();
        QPID_LOG (debug, "Broker::getLogLevel()");
        status = Manageable::STATUS_OK;
        break;
    case _qmf::Broker::METHOD_CREATE :
      {
          _qmf::ArgsBrokerCreate& a = dynamic_cast<_qmf::ArgsBrokerCreate&>(args);
          createObject(a.i_type, a.i_name, a.i_properties, a.i_strict, getManagementExecutionContext());
          status = Manageable::STATUS_OK;
          break;
      }
    case _qmf::Broker::METHOD_DELETE :
      {
          _qmf::ArgsBrokerDelete& a = dynamic_cast<_qmf::ArgsBrokerDelete&>(args);
          deleteObject(a.i_type, a.i_name, a.i_options, getManagementExecutionContext());
          status = Manageable::STATUS_OK;
          break;
      }
    case _qmf::Broker::METHOD_QUERY :
      {
          _qmf::ArgsBrokerQuery& a = dynamic_cast<_qmf::ArgsBrokerQuery&>(args);
          status = queryObject(a.i_type, a.i_name, a.o_results, getManagementExecutionContext());
          break;
      }
    case _qmf::Broker::METHOD_GETTIMESTAMPCONFIG:
        {
          _qmf::ArgsBrokerGetTimestampConfig& a = dynamic_cast<_qmf::ArgsBrokerGetTimestampConfig&>(args);
          status = getTimestampConfig(a.o_receive, getManagementExecutionContext());
          break;
        }
    case _qmf::Broker::METHOD_SETTIMESTAMPCONFIG:
        {
          _qmf::ArgsBrokerSetTimestampConfig& a = dynamic_cast<_qmf::ArgsBrokerSetTimestampConfig&>(args);
          status = setTimestampConfig(a.i_receive, getManagementExecutionContext());
          break;
        }

    case _qmf::Broker::METHOD_GETLOGHIRESTIMESTAMP:
    {
        dynamic_cast<_qmf::ArgsBrokerGetLogHiresTimestamp&>(args).o_logHires = getLogHiresTimestamp();
        QPID_LOG (debug, "Broker::getLogHiresTimestamp()");
        status = Manageable::STATUS_OK;
        break;
    }
    case _qmf::Broker::METHOD_SETLOGHIRESTIMESTAMP:
    {
        setLogHiresTimestamp(dynamic_cast<_qmf::ArgsBrokerSetLogHiresTimestamp&>(args).i_logHires);
        QPID_LOG (debug, "Broker::setLogHiresTimestamp()");
        status = Manageable::STATUS_OK;
        break;
    }
    default:
        QPID_LOG (debug, "Broker ManagementMethod not implemented: id=" << methodId << "]");
        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}

namespace
{
const std::string TYPE_QUEUE("queue");
const std::string TYPE_EXCHANGE("exchange");
const std::string TYPE_TOPIC("topic");
const std::string TYPE_BINDING("binding");
const std::string TYPE_LINK("link");
const std::string TYPE_BRIDGE("bridge");
const std::string DURABLE("durable");
const std::string AUTO_DELETE("auto-delete");
const std::string ALTERNATE_EXCHANGE("alternate-exchange");
const std::string EXCHANGE_TYPE("exchange-type");
const std::string QUEUE_NAME("queue");
const std::string EXCHANGE_NAME("exchange");

const std::string ATTRIBUTE_TIMESTAMP_0_10("timestamp-0.10");

const std::string _TRUE("true");
const std::string _FALSE("false");

// parameters for creating a Link object, see mgmt schema
const std::string HOST("host");
const std::string PORT("port");
const std::string TRANSPORT("transport");
const std::string AUTH_MECHANISM("authMechanism");
const std::string USERNAME("username");
const std::string PASSWORD("password");

// parameters for creating a Bridge object, see mgmt schema
const std::string LINK("link");
const std::string SRC("src");
const std::string DEST("dest");
const std::string KEY("key");
const std::string TAG("tag");
const std::string EXCLUDES("excludes");
const std::string SRC_IS_QUEUE("srcIsQueue");
const std::string SRC_IS_LOCAL("srcIsLocal");
const std::string DYNAMIC("dynamic");
const std::string SYNC("sync");
}

struct InvalidBindingIdentifier : public qpid::Exception
{
    InvalidBindingIdentifier(const std::string& name) : qpid::Exception(name) {}
    std::string getPrefix() const { return "invalid binding"; }
};

struct BindingIdentifier
{
    std::string exchange;
    std::string queue;
    std::string key;

    BindingIdentifier(const std::string& name)
    {
        std::vector<std::string> path;
        split(path, name, "/");
        switch (path.size()) {
          case 1:
            queue = path[0];
            break;
          case 2:
            exchange = path[0];
            queue = path[1];
            break;
          case 3:
            exchange = path[0];
            queue = path[1];
            key = path[2];
            break;
          default:
            throw InvalidBindingIdentifier(name);
        }
    }
};

struct ObjectAlreadyExists : public qpid::Exception
{
    ObjectAlreadyExists(const std::string& name) : qpid::Exception(name) {}
    std::string getPrefix() const { return "object already exists"; }
};

struct UnknownObjectType : public qpid::Exception
{
    UnknownObjectType(const std::string& type) : qpid::Exception(type) {}
    std::string getPrefix() const { return "unknown object type"; }
};

struct ReservedObjectName : public qpid::Exception
{
    ReservedObjectName(const std::string& type) : qpid::Exception(type) {}
    std::string getPrefix() const { return std::string("names prefixed with '")
          + QPID_NAME_PREFIX + std::string("' are reserved"); }
};

struct UnsupportedTransport : public qpid::Exception
{
    UnsupportedTransport(const std::string& type) : qpid::Exception(type) {}
    std::string getPrefix() const { return "transport is not supported"; }
};

struct InvalidParameter : public qpid::Exception
{
    InvalidParameter(const std::string& type) : qpid::Exception(type) {}
    std::string getPrefix() const { return "invalid parameter to method call"; }
};

void Broker::createObject(const std::string& type, const std::string& name,
                          const Variant::Map& properties, bool /*strict*/, const ConnectionState* context)
{
    std::string userId;
    std::string connectionId;
    if (context) {
        userId = context->getUserId();
        connectionId = context->getUrl();
    }
    //TODO: implement 'strict' option (check there are no unrecognised properties)
    QPID_LOG (debug, "Broker::create(" << type << ", " << name << "," << properties << ")");
    if (type == TYPE_QUEUE) {
        bool durable(false);
        bool autodelete(false);
        std::string alternateExchange;
        Variant::Map extensions;
        for (Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            // extract durable, auto-delete and alternate-exchange properties
            if (i->first == DURABLE) durable = i->second;
            else if (i->first == AUTO_DELETE) autodelete = i->second;
            else if (i->first == ALTERNATE_EXCHANGE) alternateExchange = i->second.asString();
            //treat everything else as extension properties
            else extensions[i->first] = i->second;
        }
        QueueSettings settings(durable, autodelete);
        Variant::Map unused;
        settings.populate(extensions, unused);
        qpid::amqp_0_10::translate(unused, settings.storeSettings);
        //TODO: unused doesn't take store settings into account... so can't yet implement strict
        QPID_LOG(debug, "Broker did not use the following settings (store module may): " << unused);

        std::pair<boost::shared_ptr<Queue>, bool> result =
            createQueue(name, settings, 0, alternateExchange, userId, connectionId);
        if (!result.second) {
            throw ObjectAlreadyExists(name);
        }
    } else if (type == TYPE_EXCHANGE || type == TYPE_TOPIC) {
        bool durable(false);
        std::string exchangeType("topic");
        std::string alternateExchange;
        Variant::Map extensions;
        for (Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            // extract durable, auto-delete and alternate-exchange properties
            if (i->first == DURABLE) durable = i->second;
            else if (i->first == EXCHANGE_TYPE) exchangeType = i->second.asString();
            else if (i->first == ALTERNATE_EXCHANGE) alternateExchange = i->second.asString();
            //treat everything else as extension properties
            else extensions[i->first] = i->second;
        }
        framing::FieldTable arguments;
        qpid::amqp_0_10::translate(extensions, arguments);

        try {
            std::pair<boost::shared_ptr<Exchange>, bool> result =
                createExchange(name, exchangeType, durable, alternateExchange, arguments, userId, connectionId);
            if (!result.second) {
                throw ObjectAlreadyExists(name);
            }
        } catch (const UnknownExchangeTypeException&) {
            throw Exception(QPID_MSG("Invalid exchange type: " << exchangeType));
        }
    } else if (type == TYPE_BINDING) {
        BindingIdentifier binding(name);
        std::string exchangeType("topic");
        Variant::Map extensions;
        for (Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            // extract durable, auto-delete and alternate-exchange properties
            if (i->first == EXCHANGE_TYPE) exchangeType = i->second.asString();
            //treat everything else as extension properties
            else extensions[i->first] = i->second;
        }
        framing::FieldTable arguments;
        qpid::amqp_0_10::translate(extensions, arguments);

        bind(binding.queue, binding.exchange, binding.key, arguments, userId, connectionId);

    } else if (type == TYPE_LINK) {

        QPID_LOG (debug, "createObject: Link; name=" << name << "; args=" << properties );

        if (name.compare(0, QPID_NAME_PREFIX.length(), QPID_NAME_PREFIX) == 0) {
            QPID_LOG(error, "Link name='" << name << "' cannot use the reserved prefix '" << QPID_NAME_PREFIX << "'");
            throw ReservedObjectName(name);
        }

        std::string host;
        uint16_t port = 0;
        std::string transport = TCP_TRANSPORT;
        bool durable = false;
        std::string authMech, username, password;

        for (Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            if (i->first == HOST) host = i->second.asString();
            else if (i->first == PORT) port = i->second.asUint16();
            else if (i->first == TRANSPORT) transport = i->second.asString();
            else if (i->first == DURABLE) durable = bool(i->second);
            else if (i->first == AUTH_MECHANISM) authMech = i->second.asString();
            else if (i->first == USERNAME) username = i->second.asString();
            else if (i->first == PASSWORD) password = i->second.asString();
            else {
                // TODO: strict checking here
            }
        }

        if (!getProtocolFactory(transport)) {
            QPID_LOG(error, "Transport '" << transport << "' not supported.");
            throw UnsupportedTransport(transport);
        }

        std::pair<boost::shared_ptr<Link>, bool> rc;
        rc = links.declare(name, host, port, transport, durable, authMech, username, password);
        if (!rc.first) {
            QPID_LOG (error, "Failed to create Link object, name=" << name << " remote=" << host << ":" << port <<
                      "; transport=" << transport << "; durable=" << (durable?"T":"F") << "; authMech=\"" << authMech << "\"");
            throw InvalidParameter(name);
        }
        if (!rc.second) {
            QPID_LOG (error, "Failed to create a new Link object, name=" << name << " already exists.");
            throw ObjectAlreadyExists(name);
        }

    } else if (type == TYPE_BRIDGE) {

        QPID_LOG (debug, "createObject: Bridge; name=" << name << "; args=" << properties );

        if (name.compare(0, QPID_NAME_PREFIX.length(), QPID_NAME_PREFIX) == 0) {
            QPID_LOG(error, "Bridge name='" << name << "' cannot use the reserved prefix '" << QPID_NAME_PREFIX << "'");
            throw ReservedObjectName(name);
        }

        std::string linkName;
        std::string src;
        std::string dest;
        std::string key;
        std::string id;
        std::string excludes;
        std::string queueName;
        bool durable = false;
        bool srcIsQueue = false;
        bool srcIsLocal = false;
        bool dynamic = false;
        uint16_t sync = 0;

        for (Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {

            if (i->first == LINK) linkName = i->second.asString();
            else if (i->first == SRC) src = i->second.asString();
            else if (i->first == DEST) dest = i->second.asString();
            else if (i->first == KEY) key = i->second.asString();
            else if (i->first == TAG) id = i->second.asString();
            else if (i->first == EXCLUDES) excludes = i->second.asString();
            else if (i->first == SRC_IS_QUEUE) srcIsQueue = bool(i->second);
            else if (i->first == SRC_IS_LOCAL) srcIsLocal = bool(i->second);
            else if (i->first == DYNAMIC) dynamic = bool(i->second);
            else if (i->first == SYNC) sync = i->second.asUint16();
            else if (i->first == DURABLE) durable = bool(i->second);
            else if (i->first == QUEUE_NAME) queueName = i->second.asString();
            else {
                // TODO: strict checking here
            }
        }

        boost::shared_ptr<Link> link;
        if (linkName.empty() || !(link = links.getLink(linkName))) {
            QPID_LOG(error, "Link '" << linkName << "' not found; bridge create failed.");
            throw InvalidParameter(name);
        }
        std::pair<Bridge::shared_ptr, bool> rc =
          links.declare(name, *link, durable, src, dest, key, srcIsQueue, srcIsLocal, id, excludes,
                        dynamic, sync,
                        0,
                        queueName);

        if (!rc.first) {
            QPID_LOG (error, "Failed to create Bridge object, name=" << name << " link=" << linkName <<
                      "; src=" << src << "; dest=" << dest << "; key=" << key);
            throw InvalidParameter(name);
        }
        if (!rc.second) {
            QPID_LOG (error, "Failed to create a new Bridge object, name=" << name << " already exists.");
            throw ObjectAlreadyExists(name);
        }
    } else {
        throw UnknownObjectType(type);
    }
}

void Broker::deleteObject(const std::string& type, const std::string& name,
                          const Variant::Map& options, const ConnectionState* context)
{
    std::string userId;
    std::string connectionId;
    if (context) {
        userId = context->getUserId();
        connectionId = context->getUrl();
    }
    QPID_LOG (debug, "Broker::delete(" << type << ", " << name << "," << options << ")");
    if (type == TYPE_QUEUE) {
        deleteQueue(name, userId, connectionId);
    } else if (type == TYPE_EXCHANGE || type == TYPE_TOPIC) {
        deleteExchange(name, userId, connectionId);
    } else if (type == TYPE_BINDING) {
        BindingIdentifier binding(name);
        unbind(binding.queue, binding.exchange, binding.key, userId, connectionId);
    } else if (type == TYPE_LINK) {
        boost::shared_ptr<Link> link = links.getLink(name);
        if (link) {
            link->close();
        }
    } else if (type == TYPE_BRIDGE) {
        boost::shared_ptr<Bridge> bridge = links.getBridge(name);
        if (bridge) {
            bridge->close();
        }
    } else {
        throw UnknownObjectType(type);
    }

}

Manageable::status_t Broker::queryObject(const std::string& type,
                                         const std::string& name,
                                         Variant::Map& results,
                                         const ConnectionState* context)
{
    std::string userId;
    std::string connectionId;
    if (context) {
        userId = context->getUserId();
        connectionId = context->getUrl();
    }
    QPID_LOG (debug, "Broker::query(" << type << ", " << name << ")");

    if (type == TYPE_QUEUE)
        return queryQueue( name, userId, connectionId, results );

    if (type == TYPE_EXCHANGE ||
        type == TYPE_TOPIC ||
        type == TYPE_BINDING)
        return Manageable::STATUS_NOT_IMPLEMENTED;

    throw UnknownObjectType(type);
}

Manageable::status_t Broker::queryQueue( const std::string& name,
                                         const std::string& userId,
                                         const std::string& /*connectionId*/,
                                         Variant::Map& results )
{
    (void) results;
    if (acl) {
        if (!acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_QUEUE, name, NULL) )
            throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied queue query request from " << userId));
    }

    boost::shared_ptr<Queue> q(queues.find(name));
    if (!q) {
        QPID_LOG(error, "Query failed: queue not found, name=" << name);
        return Manageable::STATUS_UNKNOWN_OBJECT;
    }
    q->query( results );
    return Manageable::STATUS_OK;;
}

Manageable::status_t Broker::getTimestampConfig(bool& receive,
                                                const ConnectionState* context)
{
    std::string name;   // none needed for broker
    std::string userId = context->getUserId();
    if (acl && !acl->authorise(userId, acl::ACT_ACCESS, acl::OBJ_BROKER, name, NULL))  {
        throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied broker timestamp get request from " << userId));
    }
    receive = config.timestampRcvMsgs;
    return Manageable::STATUS_OK;
}

Manageable::status_t Broker::setTimestampConfig(const bool receive,
                                                const ConnectionState* context)
{
    std::string name;   // none needed for broker
    std::string userId = context->getUserId();
    if (acl && !acl->authorise(userId, acl::ACT_UPDATE, acl::OBJ_BROKER, name, NULL)) {
        throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied broker timestamp set request from " << userId));
    }
    config.timestampRcvMsgs = receive;
    QPID_LOG(notice, "Receive message timestamping is " << ((config.timestampRcvMsgs) ? "ENABLED." : "DISABLED."));
    return Manageable::STATUS_OK;
}

void Broker::setLogLevel(const std::string& level)
{
    QPID_LOG(notice, "Changing log level to " << level);
    std::vector<std::string> selectors;
    split(selectors, level, ", ");
    qpid::log::Logger::instance().reconfigure(selectors);
}

std::string Broker::getLogLevel()
{
    std::string level;
    const std::vector<std::string>& selectors = qpid::log::Logger::instance().getOptions().selectors;
    for (std::vector<std::string>::const_iterator i = selectors.begin(); i != selectors.end(); ++i) {
        if (i != selectors.begin()) level += std::string(",");
        level += *i;
    }
    return level;
}

void Broker::setLogHiresTimestamp(bool enabled)
{
    QPID_LOG(notice, "Changing log hires timestamp to " << enabled);
    qpid::log::Logger::instance().setHiresTimestamp(enabled);
}

bool Broker::getLogHiresTimestamp()
{
    return qpid::log::Logger::instance().getHiresTimestamp();
}


boost::shared_ptr<ProtocolFactory> Broker::getProtocolFactory(const std::string& name) const {
    ProtocolFactoryMap::const_iterator i
        = name.empty() ? protocolFactories.begin() : protocolFactories.find(name);
    if (i == protocolFactories.end()) return boost::shared_ptr<ProtocolFactory>();
    else return i->second;
}

uint16_t Broker::getPort(const std::string& name) const  {
    boost::shared_ptr<ProtocolFactory> factory = getProtocolFactory(name);
    if (factory) {
        return factory->getPort();
    } else {
        throw NoSuchTransportException(QPID_MSG("No such transport: '" << name << "'"));
    }
}

void Broker::registerProtocolFactory(const std::string& name, ProtocolFactory::shared_ptr protocolFactory) {
    protocolFactories[name] = protocolFactory;
    Url::addProtocol(name);
}

void Broker::accept() {
    for (ProtocolFactoryMap::const_iterator i = protocolFactories.begin(); i != protocolFactories.end(); i++) {
        i->second->accept(poller, factory.get());
    }
}

void Broker::connect(
    const std::string& name,
    const std::string& host, const std::string& port, const std::string& transport,
    boost::function2<void, int, std::string> failed)
{
    boost::shared_ptr<ProtocolFactory> pf = getProtocolFactory(transport);
    if (pf) pf->connect(poller, name, host, port, factory.get(), failed);
    else throw NoSuchTransportException(QPID_MSG("Unsupported transport type: " << transport));
}

int32_t Broker::queueMoveMessages(
     const std::string& srcQueue,
     const std::string& destQueue,
     uint32_t  qty,
     const Variant::Map& filter)
{
    Queue::shared_ptr src_queue = queues.find(srcQueue);
    if (!src_queue)
        return -1;
    Queue::shared_ptr dest_queue = queues.find(destQueue);
    if (!dest_queue)
        return -1;

    return (int32_t) src_queue->move(dest_queue, qty, &filter);
}


boost::shared_ptr<sys::Poller> Broker::getPoller() { return poller; }

std::vector<Url>
Broker::getKnownBrokersImpl()
{
    return knownBrokers;
}

bool Broker::deferDeliveryImpl(const std::string&, const Message&)
{ return false; }

const std::string Broker::TCP_TRANSPORT("tcp");


std::pair<boost::shared_ptr<Queue>, bool> Broker::createQueue(
    const std::string& name,
    const QueueSettings& settings,
    const OwnershipToken* owner,
    const std::string& alternateExchange,
    const std::string& userId,
    const std::string& connectionId)
{
    if (acl) {
        std::map<acl::Property, std::string> params;
        params.insert(make_pair(acl::PROP_ALTERNATE, alternateExchange));
        params.insert(make_pair(acl::PROP_DURABLE, settings.durable ? _TRUE : _FALSE));
        params.insert(make_pair(acl::PROP_EXCLUSIVE, owner ? _TRUE : _FALSE));
        params.insert(make_pair(acl::PROP_AUTODELETE, settings.autodelete ? _TRUE : _FALSE));
        params.insert(make_pair(acl::PROP_POLICYTYPE, settings.dropMessagesAtLimit ? "ring" : "reject"));
        params.insert(make_pair(acl::PROP_MAXQUEUECOUNT, boost::lexical_cast<string>(settings.maxDepth.getCount())));
        params.insert(make_pair(acl::PROP_MAXQUEUESIZE, boost::lexical_cast<string>(settings.maxDepth.getSize())));
        params.insert(make_pair(acl::PROP_MAXFILECOUNT, boost::lexical_cast<string>(settings.maxFileCount)));
        params.insert(make_pair(acl::PROP_MAXFILESIZE, boost::lexical_cast<string>(settings.maxFileSize)));

        if (!acl->authorise(userId,acl::ACT_CREATE,acl::OBJ_QUEUE,name,&params) )
            throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied queue create request from " << userId));

        if (!acl->approveCreateQueue(userId,name) )
            throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied queue create request from " << userId));
    }

    Exchange::shared_ptr alternate;
    if (!alternateExchange.empty()) {
        alternate = exchanges.get(alternateExchange);
        if (!alternate) throw framing::NotFoundException(QPID_MSG("Alternate exchange does not exist: " << alternateExchange));
    }

    std::pair<Queue::shared_ptr, bool> result =
        queues.declare(name, settings, alternate, false/*recovering*/,
                       owner, connectionId, userId);
    if (result.second) {
        //add default binding:
        result.first->bind(exchanges.getDefault(), name);
        QPID_LOG_CAT(debug, model, "Create queue. name:" << name
            << " user:" << userId
            << " rhost:" << connectionId
            << " durable:" << (settings.durable ? "T" : "F")
            << " owner:" << owner
            << " autodelete:" << (settings.autodelete ? "T" : "F")
            << " alternateExchange:" << alternateExchange );
    }
    return result;
}

void Broker::deleteQueue(const std::string& name, const std::string& userId,
                         const std::string& connectionId, QueueFunctor check)
{
    QPID_LOG_CAT(debug, model, "Deleting queue. name:" << name
                 << " user:" << userId
                 << " rhost:" << connectionId
    );
    if (acl && !acl->authorise(userId,acl::ACT_DELETE,acl::OBJ_QUEUE,name,NULL)) {
        throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied queue delete request from " << userId));
    }

    Queue::shared_ptr queue = queues.find(name);
    if (queue) {
        if (check) check(queue);
        if (acl)
            acl->recordDestroyQueue(name);
        queues.destroy(name, connectionId, userId);
        queue->destroyed();
    } else {
        throw framing::NotFoundException(QPID_MSG("Delete failed. No such queue: " << name));
    }
}

std::pair<Exchange::shared_ptr, bool> Broker::createExchange(
    const std::string& name,
    const std::string& type,
    bool durable,
    const std::string& alternateExchange,
    const qpid::framing::FieldTable& arguments,
    const std::string& userId,
    const std::string& connectionId)
{
    if (acl) {
        std::map<acl::Property, std::string> params;
        params.insert(make_pair(acl::PROP_TYPE, type));
        params.insert(make_pair(acl::PROP_ALTERNATE, alternateExchange));
        params.insert(make_pair(acl::PROP_DURABLE, durable ? _TRUE : _FALSE));
        if (!acl->authorise(userId,acl::ACT_CREATE,acl::OBJ_EXCHANGE,name,&params) )
            throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied exchange create request from " << userId));
    }

    Exchange::shared_ptr alternate;
    if (!alternateExchange.empty()) {
        alternate = exchanges.get(alternateExchange);
        if (!alternate) throw framing::NotFoundException(QPID_MSG("Alternate exchange does not exist: " << alternateExchange));
    }

    std::pair<Exchange::shared_ptr, bool> result;
    result = exchanges.declare(
        name, type, durable, arguments, alternate, connectionId, userId);
    if (result.second) {
        if (durable) {
            store->create(*result.first, arguments);
        }
        QPID_LOG_CAT(debug, model, "Create exchange. name:" << name
            << " user:" << userId
            << " rhost:" << connectionId
            << " type:" << type
            << " alternateExchange:" << alternateExchange
            << " durable:" << (durable ? "T" : "F"));
    }
    return result;
}

void Broker::deleteExchange(const std::string& name, const std::string& userId,
                           const std::string& connectionId)
{
    QPID_LOG_CAT(debug, model, "Deleting exchange. name:" << name
        << " user:" << userId
        << " rhost:" << connectionId);
    if (acl) {
        if (!acl->authorise(userId,acl::ACT_DELETE,acl::OBJ_EXCHANGE,name,NULL) )
            throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied exchange delete request from " << userId));
    }

    if (name.empty()) {
        throw framing::InvalidArgumentException(QPID_MSG("Delete not allowed for default exchange"));
    }
    Exchange::shared_ptr exchange(exchanges.get(name));
    if (!exchange) throw framing::NotFoundException(QPID_MSG("Delete failed. No such exchange: " << name));
    if (exchange->inUseAsAlternate()) throw framing::NotAllowedException(QPID_MSG("Cannot delete " << name <<", in use as alternate-exchange."));
    if (exchange->isDurable()) store->destroy(*exchange);
    if (exchange->getAlternate()) exchange->getAlternate()->decAlternateUsers();
    exchanges.destroy(name, connectionId,  userId);
}

void Broker::bind(const std::string& queueName,
                  const std::string& exchangeName,
                  const std::string& key,
                  const qpid::framing::FieldTable& arguments,
                  const std::string& userId,
                  const std::string& connectionId)
{
    if (acl) {
        std::map<acl::Property, std::string> params;
        params.insert(make_pair(acl::PROP_QUEUENAME, queueName));
        params.insert(make_pair(acl::PROP_ROUTINGKEY, key));

        if (!acl->authorise(userId,acl::ACT_BIND,acl::OBJ_EXCHANGE,exchangeName,&params))
            throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied exchange bind request from " << userId));
    }
    if (exchangeName.empty()) {
        throw framing::InvalidArgumentException(QPID_MSG("Bind not allowed for default exchange"));
    }

    Queue::shared_ptr queue = queues.find(queueName);
    Exchange::shared_ptr exchange = exchanges.get(exchangeName);
    if (!queue) {
        throw framing::NotFoundException(QPID_MSG("Bind failed. No such queue: " << queueName));
    } else if (!exchange) {
        throw framing::NotFoundException(QPID_MSG("Bind failed. No such exchange: " << exchangeName));
    } else {
        if (queue->bind(exchange, key, arguments)) {
            getConfigurationObservers().bind(exchange, queue, key, arguments);
            if (managementAgent.get()) {
                managementAgent->raiseEvent(_qmf::EventBind(connectionId, userId, exchangeName,
                                                  queueName, key, ManagementAgent::toMap(arguments)));
            }
            QPID_LOG_CAT(debug, model, "Create binding. exchange:" << exchangeName
                << " queue:" << queueName
                << " key:" << key
                << " arguments:" << arguments
                << " user:" << userId
                << " rhost:" << connectionId);
        }
    }
}

void Broker::unbind(const std::string& queueName,
                    const std::string& exchangeName,
                    const std::string& key,
                    const std::string& userId,
                    const std::string& connectionId)
{
    if (acl) {
        std::map<acl::Property, std::string> params;
        params.insert(make_pair(acl::PROP_QUEUENAME, queueName));
        params.insert(make_pair(acl::PROP_ROUTINGKEY, key));
        if (!acl->authorise(userId,acl::ACT_UNBIND,acl::OBJ_EXCHANGE,exchangeName,&params) )
            throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied exchange unbind request from " << userId));
    }
    if (exchangeName.empty()) {
        throw framing::InvalidArgumentException(QPID_MSG("Unbind not allowed for default exchange"));
    }
    Queue::shared_ptr queue = queues.find(queueName);
    Exchange::shared_ptr exchange = exchanges.get(exchangeName);
    if (!queue) {
        throw framing::NotFoundException(QPID_MSG("Unbind failed. No such queue: " << queueName));
    } else if (!exchange) {
        throw framing::NotFoundException(QPID_MSG("Unbind failed. No such exchange: " << exchangeName));
    } else {
        if (exchange->unbind(queue, key, 0)) {
            if (exchange->isDurable() && queue->isDurable()) {
                store->unbind(*exchange, *queue, key, qpid::framing::FieldTable());
            }
            getConfigurationObservers().unbind(
                exchange, queue, key, framing::FieldTable());
            if (managementAgent.get()) {
                managementAgent->raiseEvent(_qmf::EventUnbind(connectionId, userId, exchangeName, queueName, key));
            }
            QPID_LOG_CAT(debug, model, "Delete binding. exchange:" << exchangeName
                << " queue:" << queueName
                << " key:" << key
                << " user:" << userId
                << " rhost:" << connectionId);
        }
    }
}

// FIXME aconway 2012-04-27: access to linkClientProperties is
// not properly thread safe, you could lose fields if 2 threads
// attempt to add a field concurrently.

framing::FieldTable Broker::getLinkClientProperties() const {
    sys::Mutex::ScopedLock l(linkClientPropertiesLock);
    return linkClientProperties;
}

void Broker::setLinkClientProperties(const framing::FieldTable& ft) {
    sys::Mutex::ScopedLock l(linkClientPropertiesLock);
    linkClientProperties = ft;
}

}} // namespace qpid::broker

