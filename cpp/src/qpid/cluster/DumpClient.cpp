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
#include "Cluster.h"
#include "Connection.h"
#include "qpid/client/SessionBase_0_10Access.h" 
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/SessionState.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/ClusterConnectionDumpCompleteBody.h"
#include "qpid/framing/ClusterConnectionShadowReadyBody.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/log/Statement.h"
#include "qpid/Url.h"
#include <boost/bind.hpp>

namespace qpid {

namespace client {
struct ConnectionAccess {
    static void setVersion(Connection& c, const framing::ProtocolVersion& v) { c.version = v; }
    static boost::shared_ptr<ConnectionImpl>  getImpl(Connection& c) { return c.impl; }
};
} // namespace client

namespace cluster {

using broker::Broker;
using broker::Exchange;
using broker::Queue;
using broker::QueueBinding;
using broker::Message;
using namespace framing;
namespace arg=client::arg;
using client::SessionBase_0_10Access;

// Create a connection with special version that marks it as a catch-up connection.
client::Connection catchUpConnection() {
    client::Connection c;
    client::ConnectionAccess::setVersion(c, ProtocolVersion(0x80 , 0x80 + 10));
    return c;
}

// Send a control body directly to the session.
void send(client::Session& s, const AMQBody& body) {
    client::SessionBase_0_10Access sb(s);
    sb.get()->send(body);
}

DumpClient::DumpClient(const Url& url, Cluster& c,
                       const boost::function<void()>& ok,
                       const boost::function<void(const std::exception&)>& fail)
    : receiver(url), donor(c), 
      connection(catchUpConnection()), shadowConnection(catchUpConnection()),
      done(ok), failed(fail)
{
    QPID_LOG(debug, "DumpClient from " << c.getSelf() << " to " << url);
    connection.open(url);
    session = connection.newSession();
}

DumpClient::~DumpClient() {}

// Catch-up exchange name: an illegal AMQP exchange name to avoid clashes.
static const char CATCH_UP_CHARS[] = "\000qpid-dump-exchange";
static const std::string CATCH_UP(CATCH_UP_CHARS, sizeof(CATCH_UP_CHARS)); 

void DumpClient::dump() {
    Broker& b = donor.getBroker();
    b.getExchanges().eachExchange(boost::bind(&DumpClient::dumpExchange, this, _1));
    // Catch-up exchange is used to route messages to the proper queue without modifying routing key.
    session.exchangeDeclare(arg::exchange=CATCH_UP, arg::type="fanout", arg::autoDelete=true);
    b.getQueues().eachQueue(boost::bind(&DumpClient::dumpQueue, this, _1));
    session.sync();
    session.close();
    donor.eachConnection(boost::bind(&DumpClient::dumpConnection, this, _1));
    QPID_LOG(debug, "Dump sent, closing catch_up connection.");
    // FIXME aconway 2008-09-18: inidicate successful end-of-dump.
    connection.close();
    QPID_LOG(debug, "Dump sent.");
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
    framing::MessageTransferBody transfer(
        framing::ProtocolVersion(), CATCH_UP, message::ACCEPT_MODE_NONE, message::ACQUIRE_MODE_PRE_ACQUIRED);
    sb.get()->send(transfer, message.payload->getFrames());
}

void DumpClient::dumpBinding(const std::string& queue, const QueueBinding& binding) {
    session.exchangeBind(queue, binding.exchange, binding.key, binding.args);
}

void DumpClient::dumpConnection(const boost::intrusive_ptr<Connection>& dumpConnection) {
    QPID_LOG(debug, "Dump connection " << *dumpConnection);

    shadowConnection = catchUpConnection();
    // FIXME aconway 2008-09-19: Open with settings from dumpConnection - userid etc.
    shadowConnection.open(receiver);
    dumpConnection->getBrokerConnection().eachSessionHandler(boost::bind(&DumpClient::dumpSession, this, _1));
    boost::shared_ptr<client::ConnectionImpl> impl = client::ConnectionAccess::getImpl(shadowConnection);
    // FIXME aconway 2008-09-19: use proxy for cluster commands? 
    AMQFrame ready(in_place<ClusterConnectionShadowReadyBody>(ProtocolVersion(),
                       dumpConnection->getId().getMember(),
                       reinterpret_cast<uint64_t>(dumpConnection->getId().getConnectionPtr())));
    impl->handle(ready);
    // Will be closed from the other end.
    QPID_LOG(debug, "Dump done, connection " << *dumpConnection);
}

void DumpClient::dumpSession(broker::SessionHandler& sh) {
    QPID_LOG(debug, "Dump session " << &sh.getConnection()  << "[" << sh.getChannel() << "] "
             << sh.getSession()->getId());

    broker::SessionState* s = sh.getSession();
    if (!s) return;         // no session.
    // Re-create the session.
    boost::shared_ptr<client::ConnectionImpl> cimpl = client::ConnectionAccess::getImpl(shadowConnection);
    size_t max_frame_size = cimpl->getNegotiatedSettings().maxFrameSize;
    // FIXME aconway 2008-09-19: verify matching ID.
    boost::shared_ptr<client::SessionImpl> simpl(
        new client::SessionImpl(s->getId().getName(), cimpl, sh.getChannel(), max_frame_size));
    cimpl->addSession(simpl);
    simpl->open(0);
    client::Session cs;
    client::SessionBase_0_10Access(cs).set(simpl);
    cs.sync();

    broker::SessionState* ss = sh.getSession();
    ss->eachConsumer(boost::bind(&DumpClient::dumpConsumer, this, _1));
    
    // FIXME aconway 2008-09-19: remaining session state.
    QPID_LOG(debug, "Dump done, session " << sh.getSession()->getId());
}

void DumpClient::dumpConsumer(broker::SemanticState::ConsumerImpl* ci) {
    QPID_LOG(critical, "DEBUG: dump consumer: " << ci->getName());
}

}} // namespace qpid::cluster
