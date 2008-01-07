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

#include "config.h"
#include "Broker.h"
#include "Connection.h"
#include "DirectExchange.h"
#include "FanOutExchange.h"
#include "HeadersExchange.h"
#include "MessageStoreModule.h"
#include "NullMessageStore.h"
#include "RecoveryManagerImpl.h"
#include "TopicExchange.h"
#include "qpid/management/ManagementExchange.h"
#include "qpid/management/ArgsBrokerEcho.h"

#include "qpid/log/Statement.h"
#include "qpid/Url.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/HandlerUpdater.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/sys/Acceptor.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/TimeoutHandler.h"
#include "qpid/sys/SystemInfo.h"

#include <boost/bind.hpp>

#include <iostream>
#include <memory>

using qpid::sys::Acceptor;
using qpid::framing::HandlerUpdater;
using qpid::framing::FrameHandler;
using qpid::framing::ChannelId;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
using qpid::management::ArgsBrokerEcho;

namespace qpid {
namespace broker {

Broker::Options::Options(const std::string& name) :
    qpid::Options(name),
    port(TcpAddress::DEFAULT_PORT),
    workerThreads(5),
    maxConnections(500),
    connectionBacklog(10),
    store(),
    stagingThreshold(5000000),
    storeDir("/var"),
    storeAsync(false),
    storeForce(false),
    numJrnlFiles(8),
    jrnlFsizePgs(24),
    enableMgmt(0),
    mgmtPubInterval(10),
    ack(0)
{
    int c = sys::SystemInfo::concurrency();
    workerThreads=std::max(2,c);
    addOptions()
        ("port,p", optValue(port,"PORT"),
         "Tells the broker to listen on PORT")
        ("worker-threads", optValue(workerThreads, "N"),
         "Sets the broker thread pool size")
        ("max-connections", optValue(maxConnections, "N"),
         "Sets the maximum allowed connections")
        ("connection-backlog", optValue(connectionBacklog, "N"),
         "Sets the connection backlog limit for the server socket")
        ("staging-threshold", optValue(stagingThreshold, "N"),
         "Stages messages over N bytes to disk")
// TODO: These options need to come from within the store module
        ("store-lib,s", optValue(store,"LIBNAME"),
         "Tells the broker to use the message store shared library LIBNAME for persistence")
        ("store-directory", optValue(storeDir,"DIR"),
         "Store directory location for persistence.")
        ("store-async", optValue(storeAsync,"yes|no"),
         "Use async persistence storage - if store supports it, enables AIO O_DIRECT.")
        ("store-force", optValue(storeForce,"yes|no"),
         "Force changing modes of store, will delete all existing data if mode is changed. Be SURE you want to do this!")
        ("num-jfiles", qpid::optValue(numJrnlFiles, "N"),
         "Number of files in persistence journal")
        ("jfile-size-pgs", qpid::optValue(jrnlFsizePgs, "N"),
         "Size of each journal file in multiples of read pages (1 read page = 64kiB)")
// End of store module options
        ("mgmt,m", optValue(enableMgmt,"yes|no"),
         "Enable Management")
        ("mgmt-pub-interval", optValue(mgmtPubInterval, "SECONDS"),
         "Management Publish Interval")
        ("ack", optValue(ack, "N"),
         "Send session.ack/solicit-ack at least every N frames. 0 disables voluntary ack/solitict-ack");
}

const std::string empty;
const std::string amq_direct("amq.direct");
const std::string amq_topic("amq.topic");
const std::string amq_fanout("amq.fanout");
const std::string amq_match("amq.match");
const std::string qpid_management("qpid.management");

Broker::Broker(const Broker::Options& conf) :
    config(conf),
    store(createStore(conf)),
    queues(store.get()),
    factory(*this),
    dtxManager(store.get()),
    sessionManager(conf.ack)
{
    if(conf.enableMgmt){
        ManagementAgent::enableManagement ();
        managementAgent = ManagementAgent::getAgent ();
        managementAgent->setInterval (conf.mgmtPubInterval);

        mgmtObject = management::Broker::shared_ptr (new management::Broker (this, 0, 0, conf.port));
        mgmtObject->set_workerThreads        (conf.workerThreads);
        mgmtObject->set_maxConns             (conf.maxConnections);
        mgmtObject->set_connBacklog          (conf.connectionBacklog);
        mgmtObject->set_stagingThreshold     (conf.stagingThreshold);
        mgmtObject->set_storeLib             (conf.store);
        mgmtObject->set_asyncStore           (conf.storeAsync);
        mgmtObject->set_mgmtPubInterval      (conf.mgmtPubInterval);
        mgmtObject->set_initialDiskPageSize  (0);
        mgmtObject->set_initialPagesPerQueue (0);
        mgmtObject->set_clusterName          ("");
        mgmtObject->set_version              (PACKAGE_VERSION);
        
        managementAgent->addObject (mgmtObject, 1, 0);

        // Since there is currently no support for virtual hosts, a placeholder object
        // representing the implied single virtual host is added here to keep the
        // management schema correct.
        Vhost* vhost = new Vhost (this);
        vhostObject = Vhost::shared_ptr (vhost);

        queues.setParent    (vhost);
        exchanges.setParent (vhost);
    }

    exchanges.declare(empty, DirectExchange::typeName); // Default exchange.
    
    if(store.get()) {
        if (!store->init(&conf)){
              throw Exception( "Existing Journal in different mode, backup/move existing data \
			  before changing modes. Or use --store-force yes to blow existing data away.");
		}else{
             RecoveryManagerImpl recoverer(queues, exchanges, dtxManager, 
                                      conf.stagingThreshold);
             store->recover(recoverer);
        }
    }
    //ensure standard exchanges exist (done after recovery from store)
    declareStandardExchange(amq_direct, DirectExchange::typeName);
    declareStandardExchange(amq_topic, TopicExchange::typeName);
    declareStandardExchange(amq_fanout, FanOutExchange::typeName);
    declareStandardExchange(amq_match, HeadersExchange::typeName);

    if(conf.enableMgmt) {
        QPID_LOG(info, "Management enabled");
        exchanges.declare(qpid_management, ManagementExchange::typeName);
        Exchange::shared_ptr mExchange = exchanges.get (qpid_management);
        Exchange::shared_ptr dExchange = exchanges.get (amq_direct);
        managementAgent->setExchange (mExchange, dExchange);
        dynamic_pointer_cast<ManagementExchange>(mExchange)->setManagmentAgent (managementAgent);
    }
    else
        QPID_LOG(info, "Management not enabled");

    // Initialize plugins
    const Plugin::Plugins& plugins=Plugin::getPlugins();
    for (Plugin::Plugins::const_iterator i = plugins.begin();
         i != plugins.end();
         i++)
        (*i)->initialize(*this);
}

void Broker::declareStandardExchange(const std::string& name, const std::string& type)
{
    bool storeEnabled = store.get();
    std::pair<Exchange::shared_ptr, bool> status = exchanges.declare(name, type, storeEnabled);
    if (status.second && storeEnabled) {
        store->create(*status.first);
    }
}


shared_ptr<Broker> Broker::create(int16_t port) 
{
    Options config;
    config.port=port;
    return create(config);
}

shared_ptr<Broker> Broker::create(const Options& opts) 
{
    return shared_ptr<Broker>(new Broker(opts));
}

MessageStore* Broker::createStore(const Options& config) {
    if (config.store.empty())
        return new NullMessageStore(false);
    else
        return new MessageStoreModule(config.store);
}
        
void Broker::run() {
    getAcceptor().run(&factory);
}

void Broker::shutdown() {
    if (acceptor)
        acceptor->shutdown();
    ManagementAgent::shutdown ();
}

Broker::~Broker() {
    shutdown();
}

uint16_t Broker::getPort() const  { return getAcceptor().getPort(); }

std::string Broker::getUrl() const {
    return Url(TcpAddress(getAcceptor().getHost(), getPort())).str();
}
               
Acceptor& Broker::getAcceptor() const {
    if (!acceptor) {
        const_cast<Acceptor::shared_ptr&>(acceptor) =
            Acceptor::create(config.port,
                             config.connectionBacklog,
                             config.workerThreads);
        QPID_LOG(info, "Listening on port " << getPort());
    }
    return *acceptor;
}

void Broker::add(const shared_ptr<HandlerUpdater>& updater) {
    QPID_LOG(debug, "Broker added HandlerUpdater");
    handlerUpdaters.push_back(updater);
}

void Broker::update(ChannelId channel, FrameHandler::Chains& chains) {
    for_each(handlerUpdaters.begin(), handlerUpdaters.end(),
             boost::bind(&HandlerUpdater::update, _1,
                         channel, boost::ref(chains)));
}

ManagementObject::shared_ptr Broker::GetManagementObject(void) const
{
    return dynamic_pointer_cast<ManagementObject> (mgmtObject);
}

Manageable* Broker::GetVhostObject(void) const
{
    return vhostObject.get();
}

Manageable::status_t Broker::ManagementMethod (uint32_t methodId,
                                               Args&    /*_args*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Broker::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case management::Broker::METHOD_ECHO :
        status = Manageable::STATUS_OK;
        break;

    case management::Broker::METHOD_JOINCLUSTER :
    case management::Broker::METHOD_LEAVECLUSTER :
        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}

}} // namespace qpid::broker

