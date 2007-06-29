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

#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/HandlerUpdater.h"
#include "DirectExchange.h"
#include "TopicExchange.h"
#include "FanOutExchange.h"
#include "HeadersExchange.h"
#include "MessageStoreModule.h"
#include "NullMessageStore.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "RecoveryManagerImpl.h"
#include "Connection.h"
#include "qpid/sys/Acceptor.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/TimeoutHandler.h"
#include "qpid/Url.h"

#include <iostream>
#include <memory>

using qpid::sys::Acceptor;
using qpid::framing::HandlerUpdater;

namespace qpid {
namespace broker {

Broker::Options::Options(const std::string& name) :
    qpid::Options(name),
    port(TcpAddress::DEFAULT_PORT),
    workerThreads(5),
    maxConnections(500),
    connectionBacklog(10),
    store(),
    stagingThreshold(5000000)
{
    addOptions()
        ("port,p", optValue(port,"PORT"), "Use PORT for AMQP connections.")
        ("worker-threads", optValue(workerThreads, "N"),
         "Broker thread pool size")
        ("max-connections", optValue(maxConnections, "N"),
         "Maximum allowed connections")
        ("connection-backlog", optValue(connectionBacklog, "N"),
         "Connection backlog limit for server socket.")
        ("staging-threshold", optValue(stagingThreshold, "N"),
         "Messages over N bytes are staged to disk.")
        ("store,s", optValue(store,"LIBNAME"),
         "Name of message store shared library.");
}

const std::string empty;
const std::string amq_direct("amq.direct");
const std::string amq_topic("amq.topic");
const std::string amq_fanout("amq.fanout");
const std::string amq_match("amq.match");

Broker::Broker(const Broker::Options& conf) :
    config(conf),
    store(createStore(conf)),
    queues(store.get()),
    timeout(30000),
    stagingThreshold(0),
    cleaner(&queues, timeout/10),
    factory(*this),
    dtxManager(store.get())
{
    exchanges.declare(empty, DirectExchange::typeName); // Default exchange.
    exchanges.declare(amq_direct, DirectExchange::typeName);
    exchanges.declare(amq_topic, TopicExchange::typeName);
    exchanges.declare(amq_fanout, FanOutExchange::typeName);
    exchanges.declare(amq_match, HeadersExchange::typeName);

    if(store.get()) {
        RecoveryManagerImpl recoverer(queues, exchanges, dtxManager, 
                                      conf.stagingThreshold);
        store->recover(recoverer);
    }

    cleaner.start();
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
    cleaner.stop();
}

Broker::~Broker() {
    shutdown();
}

int16_t Broker::getPort() const  { return getAcceptor().getPort(); }

std::string Broker::getUrl() const {
    return Url(TcpAddress(getAcceptor().getHost(), getPort())).str();
}
               
Acceptor& Broker::getAcceptor() const {
    if (!acceptor) 
        const_cast<Acceptor::shared_ptr&>(acceptor) =
            Acceptor::create(config.port,
                             config.connectionBacklog,
                             config.workerThreads,
                             false);
    return *acceptor;
}

void Broker::use(const shared_ptr<Plugin>& plugin) {
    shared_ptr<HandlerUpdater> updater=
        dynamic_pointer_cast<HandlerUpdater>(plugin);
    if (updater) {
        QPID_LOG(critical, "HandlerUpdater plugins not implemented");
        // FIXME aconway 2007-06-28: hook into Connections.
    }
}

}} // namespace qpid::broker

