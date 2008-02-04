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
#include "qpid/framing/AMQFrame.h"
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
    port(DEFAULT_PORT),
    workerThreads(5),
    maxConnections(500),
    connectionBacklog(10),
    stagingThreshold(5000000),
    enableMgmt(1),
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
    store(0),
    factory(*this),
    sessionManager(conf.ack)
{
    // Early-Initialize plugins
    const Plugin::Plugins& plugins=Plugin::getPlugins();
    for (Plugin::Plugins::const_iterator i = plugins.begin();
         i != plugins.end();
         i++)
        (*i)->earlyInitialize(*this);

    // If no plugin store module registered itself, set up the null store.
    if (store == 0)
        setStore (new NullMessageStore (false));

    queues.setStore     (store);
    dtxManager.setStore (store);

    if(conf.enableMgmt){
        ManagementAgent::enableManagement ();
        managementAgent = ManagementAgent::getAgent ();
        managementAgent->setInterval (conf.mgmtPubInterval);

        mgmtObject = management::Broker::shared_ptr (new management::Broker (this, 0, 0, conf.port));
        mgmtObject->set_workerThreads    (conf.workerThreads);
        mgmtObject->set_maxConns         (conf.maxConnections);
        mgmtObject->set_connBacklog      (conf.connectionBacklog);
        mgmtObject->set_stagingThreshold (conf.stagingThreshold);
        mgmtObject->set_mgmtPubInterval  (conf.mgmtPubInterval);
        mgmtObject->set_version          (PACKAGE_VERSION);
        
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
    
    if (store != 0) {
        RecoveryManagerImpl recoverer(queues, exchanges, dtxManager, 
                                      conf.stagingThreshold);
        store->recover(recoverer);
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
    for (Plugin::Plugins::const_iterator i = plugins.begin();
         i != plugins.end();
         i++)
        (*i)->initialize(*this);
}

void Broker::declareStandardExchange(const std::string& name, const std::string& type)
{
    bool storeEnabled = store != NULL;
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

void Broker::setStore (MessageStore* _store)
{
    assert (store == 0 && _store != 0);
    if (store == 0 && _store != 0)
        store = new MessageStoreModule (_store);
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
    delete store;    
}

uint16_t Broker::getPort() const  { return getAcceptor().getPort(); }

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

ManagementObject::shared_ptr Broker::GetManagementObject(void) const
{
    return dynamic_pointer_cast<ManagementObject> (mgmtObject);
}

Manageable* Broker::GetVhostObject(void) const
{
    return vhostObject.get();
}

Manageable::status_t Broker::ManagementMethod (uint32_t methodId,
                                               Args&    args)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Broker::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case management::Broker::METHOD_ECHO :
        status = Manageable::STATUS_OK;
        break;
    case management::Broker::METHOD_CONNECT :
        connect(dynamic_cast<management::ArgsBrokerConnect&>(args));
        status = Manageable::STATUS_OK;
        break;

    case management::Broker::METHOD_JOINCLUSTER :
    case management::Broker::METHOD_LEAVECLUSTER :
        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}

void Broker::connect(management::ArgsBrokerConnect& args)
{
    getAcceptor().connect(args.i_host, args.i_port, &factory);
}

}} // namespace qpid::broker

