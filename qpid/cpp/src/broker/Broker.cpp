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

#include "../framing/AMQFrame.h"
#include "DirectExchange.h"
#include "TopicExchange.h"
#include "FanOutExchange.h"
#include "HeadersExchange.h"
#include "MessageStoreModule.h"
#include "NullMessageStore.h"
#include "../framing/ProtocolInitiation.h"
#include "RecoveryManagerImpl.h"
#include "Connection.h"
#include "../sys/ConnectionInputHandler.h"
#include "../sys/ConnectionInputHandlerFactory.h"
#include "../sys/TimeoutHandler.h"

#include "Broker.h"

namespace qpid {
namespace broker {

const std::string empty;
const std::string amq_direct("amq.direct");
const std::string amq_topic("amq.topic");
const std::string amq_fanout("amq.fanout");
const std::string amq_match("amq.match");

Broker::Broker(const Configuration& conf) :
    config(conf),
    store(createStore(conf)),
    queues(store.get()),
    timeout(30000),
    stagingThreshold(0),
    cleaner(&queues, timeout/10),
    factory(*this)
{
    exchanges.declare(empty, DirectExchange::typeName); // Default exchange.
    exchanges.declare(amq_direct, DirectExchange::typeName);
    exchanges.declare(amq_topic, TopicExchange::typeName);
    exchanges.declare(amq_fanout, FanOutExchange::typeName);
    exchanges.declare(amq_match, HeadersExchange::typeName);

    if(store.get()) {
        RecoveryManagerImpl recoverer(queues, exchanges, conf.getStagingThreshold());
        store->recover(recoverer);
    }

    cleaner.start();
}


Broker::shared_ptr Broker::create(int16_t port) 
{
    Configuration config;
    config.setPort(port);
    return create(config);
}

Broker::shared_ptr Broker::create(const Configuration& config) {
    return Broker::shared_ptr(new Broker(config));
}    

MessageStore* Broker::createStore(const Configuration& config) {
    if (config.getStore().empty())
        return new NullMessageStore(config.isTrace());
    else
        return new MessageStoreModule(config.getStore());
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

Acceptor& Broker::getAcceptor() const {
    if (!acceptor) 
        const_cast<Acceptor::shared_ptr&>(acceptor) =
            Acceptor::create(config.getPort(),
                             config.getConnectionBacklog(),
                             config.getWorkerThreads(),
                             config.isTrace());
    return *acceptor;
}


const int16_t Broker::DEFAULT_PORT(5672);


}} // namespace qpid::broker

