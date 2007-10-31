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

#include "Broker.h"
#include "Connection.h"
#include "DirectExchange.h"
#include "FanOutExchange.h"
#include "HeadersExchange.h"
#include "MessageStoreModule.h"
#include "NullMessageStore.h"
#include "RecoveryManagerImpl.h"
#include "TopicExchange.h"
#include "management/ManagementExchange.h"

#include "qpid/log/Statement.h"
#include "qpid/Url.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/HandlerUpdater.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/sys/Acceptor.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/TimeoutHandler.h"

#include <boost/bind.hpp>

#include <iostream>
#include <memory>

using qpid::sys::Acceptor;
using qpid::framing::HandlerUpdater;
using qpid::framing::FrameHandler;
using qpid::framing::ChannelId;

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
    enableMgmt(0),
    mgmtPubInterval(10),
    ack(100)
{
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
        ("store,s", optValue(store,"LIBNAME"),
         "Tells the broker to use the message store shared library LIBNAME for persistence")
        ("store-directory", optValue(storeDir,"DIR"),
         "Store directory location for persistence.")
        ("store-async", optValue(storeAsync,"yes|no"),
         "Use async persistence storage - if store supports it, enable AIO 0-DIRECT.")
        ("mgmt,m", optValue(enableMgmt,"yes|no"),
         "Enable Management")
        ("mgmt-pub-interval", optValue(mgmtPubInterval, "SECONDS"),
         "Management Publish Interval")
        ("ack", optValue(ack, "N"),
         "Send ack/solicit-ack at least every N frames. 0 disables voluntary acks/solitict-ack");
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
    stagingThreshold(0),
    factory(*this),
    dtxManager(store.get()),
    sessionManager(conf.ack)
{
    if(conf.enableMgmt){
        managementAgent = ManagementAgent::shared_ptr (new ManagementAgent (conf.mgmtPubInterval));
        queues.setManagementAgent(managementAgent);
    }

    exchanges.declare(empty, DirectExchange::typeName); // Default exchange.
    exchanges.declare(amq_direct, DirectExchange::typeName);
    exchanges.declare(amq_topic, TopicExchange::typeName);
    exchanges.declare(amq_fanout, FanOutExchange::typeName);
    exchanges.declare(amq_match, HeadersExchange::typeName);
    
    if(conf.enableMgmt) {
        QPID_LOG(info, "Management enabled");
        exchanges.declare(qpid_management, ManagementExchange::typeName);
        Exchange::shared_ptr mExchange = exchanges.get (qpid_management);
        managementAgent->setExchange (mExchange);
        dynamic_pointer_cast<ManagementExchange>(mExchange)->setManagmentAgent (managementAgent);

        mgmtObject = ManagementObjectBroker::shared_ptr (new ManagementObjectBroker (conf));
        managementAgent->addObject (dynamic_pointer_cast<ManagementObject>(mgmtObject));

        // Since there is currently no support for virtual hosts, a management object
        // representing the implied single virtual host is added here.
        mgmtVhostObject = ManagementObjectVhost::shared_ptr (new ManagementObjectVhost (conf));
        managementAgent->addObject (dynamic_pointer_cast<ManagementObject>(mgmtVhostObject));
    }
    else
        QPID_LOG(info, "Management not enabled");

    if(store.get()) {
        store->init(conf.storeDir, conf.storeAsync);
        RecoveryManagerImpl recoverer(queues, exchanges, dtxManager, 
                                      conf.stagingThreshold);
        store->recover(recoverer);
    }

    // Initialize plugins
    const Plugin::Plugins& plugins=Plugin::getPlugins();
    for (Plugin::Plugins::const_iterator i = plugins.begin();
         i != plugins.end();
         i++)
        (*i)->initialize(*this);
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
}

Broker::~Broker() {
    shutdown();
}

int16_t Broker::getPort() const  { return getAcceptor().getPort(); }

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

}} // namespace qpid::broker

