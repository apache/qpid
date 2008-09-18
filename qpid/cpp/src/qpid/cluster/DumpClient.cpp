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
#include "DumpClient.h"
#include "qpid/client/SessionBase_0_10Access.h" 
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/enum.h"
#include "qpid/log/Statement.h"
#include "qpid/Url.h"
#include <boost/bind.hpp>

namespace qpid {
namespace cluster {

using broker::Broker;
using broker::Exchange;
using broker::Queue;
using broker::QueueBinding;
using broker::Message;
using namespace framing::message;

using namespace client;

DumpClient::DumpClient(const Url& url, Broker& b,
                       const boost::function<void()>& ok,
                       const boost::function<void(const std::exception&)>& fail)
    : donor(b), done(ok), failed(fail)
{
    // FIXME aconway 2008-09-16: Identify as DumpClient connection.
    connection.open(url);
    session = connection.newSession();
}

DumpClient::~DumpClient() {}

// Catch-up exchange name: an illegal AMQP exchange name to avoid clashes.
static const char CATCH_UP_CHARS[] = "\000qpid-dump-exchange";
static const std::string CATCH_UP(CATCH_UP_CHARS, sizeof(CATCH_UP_CHARS)); 

void DumpClient::dump() {
    donor.getExchanges().eachExchange(boost::bind(&DumpClient::dumpExchange, this, _1));
    // Catch-up exchange is used to route messages to the proper queue without modifying routing key.
    session.exchangeDeclare(arg::exchange=CATCH_UP, arg::type="fanout", arg::autoDelete=true);
    donor.getQueues().eachQueue(boost::bind(&DumpClient::dumpQueue, this, _1));
    session.sync();
    session.close();
    // FIXME aconway 2008-09-17: send dump complete indication.
    connection.close();
}

void DumpClient::run() {
    try {
        dump();
        done();
    } catch (const std::exception& e) {
        failed(e);
    }
    delete this;
}

void DumpClient::dumpExchange(const boost::shared_ptr<Exchange>& ex) {
    session.exchangeDeclare(
        ex->getName(), ex->getType(),
        ex->getAlternate() ? ex->getAlternate()->getName() : std::string(),
        arg::passive=false,
        arg::durable=ex->isDurable(),
        arg::autoDelete=false,
        arg::arguments=ex->getArgs());
}

void DumpClient::dumpQueue(const boost::shared_ptr<Queue>& q) {
    session.queueDeclare(
        q->getName(),
        q->getAlternateExchange() ? q->getAlternateExchange()->getName() : std::string(),
        arg::passive=false,
        arg::durable=q->isDurable(),
        arg::exclusive=q->hasExclusiveConsumer(),
        arg::autoDelete=q->isAutoDelete(),
        arg::arguments=q->getSettings());

    session.exchangeBind(q->getName(), CATCH_UP, std::string());
    q->eachMessage(boost::bind(&DumpClient::dumpMessage, this, _1));
    session.exchangeUnbind(q->getName(), CATCH_UP, std::string());
    q->eachBinding(boost::bind(&DumpClient::dumpBinding, this, q->getName(), _1));
}

void DumpClient::dumpMessage(const broker::QueuedMessage& message) {
    SessionBase_0_10Access sb(session);
    framing::MessageTransferBody transfer(framing::ProtocolVersion(), CATCH_UP, ACCEPT_MODE_NONE, ACQUIRE_MODE_PRE_ACQUIRED);
    sb.get()->send(transfer, message.payload->getFrames());
}

void DumpClient::dumpBinding(const std::string& queue, const QueueBinding& binding) {
    session.exchangeBind(queue, binding.exchange, binding.key, binding.args);
}


}} // namespace qpid::cluster
