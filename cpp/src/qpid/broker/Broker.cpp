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
#include <iostream>
#include <memory>

#include "qpid/framing/AMQFrame.h"
#include "DirectExchange.h"
#include "TopicExchange.h"
#include "FanOutExchange.h"
#include "HeadersExchange.h"
#include "MessageStoreModule.h"
#include "NullMessageStore.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "RecoveryManagerImpl.h"
#include "Connection.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/TimeoutHandler.h"

#include "Broker.h"

namespace qpid {
namespace broker {

Broker::Options::Options() :
    workerThreads(5),
    maxConnections(500),
    connectionBacklog(10),
    store(),
    stagingThreshold(5000000)
{}

void Broker::Options::addTo(po::options_description& desc)
{
    using namespace po;
    CommonOptions::addTo(desc);
    desc.add_options()
        ("worker-threads", optValue(workerThreads, "N"),
         "Broker thread pool size")
        ("max-connections", optValue(maxConnections, "N"),
         "Maximum allowed connections")
        ("connection-backlog", optValue(connectionBacklog, "N"),
         "Connection backlog limit for server socket.")
        ("staging-threshold", optValue(stagingThreshold, "N"),
         "Messages over N bytes are staged to disk.")
        ("store", optValue(store,"LIBNAME"),
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
        RecoveryManagerImpl recoverer(
            queues, exchanges, conf.stagingThreshold);
        store->recover(recoverer);
    }

    cleaner.start();
}


Broker::shared_ptr Broker::create(int16_t port) 
{
    Options config;
    config.port=port;
    return create(config);
}

Broker::shared_ptr Broker::create(const Options& config) {
    return Broker::shared_ptr(new Broker(config));
}    

MessageStore* Broker::createStore(const Options& config) {
    if (config.store.empty())
        return new NullMessageStore(config.trace);
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

Acceptor& Broker::getAcceptor() const {
    if (!acceptor) 
        const_cast<Acceptor::shared_ptr&>(acceptor) =
            Acceptor::create(config.port,
                             config.connectionBacklog,
                             config.workerThreads,
                             config.trace);
    return *acceptor;
}


}} // namespace qpid::broker

