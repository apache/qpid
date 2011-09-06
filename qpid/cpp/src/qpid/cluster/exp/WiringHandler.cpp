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

#include "Core.h"
#include "WiringHandler.h"
#include "EventHandler.h"
#include "BrokerHandler.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace cluster {
using namespace broker;
using framing::FieldTable;

WiringHandler::WiringHandler(EventHandler& e) :
    HandlerBase(e),
    broker(e.getCore().getBroker()),
    recovery(broker.getQueues(), broker.getExchanges(),
             broker.getLinks(), broker.getDtxManager())
{}

bool WiringHandler::invoke(const framing::AMQBody& body) {
    return framing::invoke(*this, body).wasHandled();
}

void WiringHandler::createQueue(const std::string& data) {
    if (sender() == self()) return;
    BrokerHandler::ScopedSuppressReplication ssr;
    framing::Buffer buf(const_cast<char*>(&data[0]), data.size());
    // TODO aconway 2011-02-21: asymetric - RecoveryManager vs Broker::create*()
    RecoverableQueue::shared_ptr queue = recovery.recoverQueue(buf);
    QPID_LOG(debug, "cluster: create queue " << queue->getName());
}

void WiringHandler::destroyQueue(const std::string& name) {
    if (sender() == self()) return;
    QPID_LOG(debug, "cluster: destroy queue " << name);
    BrokerHandler::ScopedSuppressReplication ssr;
    broker.deleteQueue(name, std::string(), std::string());
}

void WiringHandler::createExchange(const std::string& data) {
    if (sender() == self()) return;
    BrokerHandler::ScopedSuppressReplication ssr;
    framing::Buffer buf(const_cast<char*>(&data[0]), data.size());
    // TODO aconway 2011-02-21: asymetric - RecoveryManager vs Broker::create*()
    RecoverableExchange::shared_ptr exchange = recovery.recoverExchange(buf);
    QPID_LOG(debug, "cluster: create exchange " << exchange->getName());
}

void WiringHandler::destroyExchange(const std::string& name) {
    if (sender() == self()) return;
    QPID_LOG(debug, "cluster: destroy exchange " << name);
    BrokerHandler::ScopedSuppressReplication ssr;
    broker.getExchanges().destroy(name);
}

void WiringHandler::bind(
    const std::string& queueName, const std::string& exchangeName,
    const std::string& routingKey, const FieldTable& arguments)
{
    if (sender() == self()) return;
    QPID_LOG(debug, "cluster: bind queue=" << queueName
             << " exchange=" << exchangeName
             << " key=" << routingKey
             << " arguments=" << arguments);
    BrokerHandler::ScopedSuppressReplication ssr;
    broker.bind(queueName, exchangeName, routingKey, arguments, std::string(), std::string());
}

void WiringHandler::unbind(
    const std::string& queueName, const std::string& exchangeName,
    const std::string& routingKey, const FieldTable& arguments)
{
    if (sender() == self()) return;
    QPID_LOG(debug, "cluster: unbind queue=" << queueName
             << " exchange=" << exchangeName
             << " key=" << routingKey
             << " arguments=" << arguments);
    BrokerHandler::ScopedSuppressReplication ssr;
    broker.unbind(queueName, exchangeName, routingKey, std::string(), std::string());
}

}} // namespace qpid::cluster
