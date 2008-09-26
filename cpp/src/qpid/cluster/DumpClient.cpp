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

// TODO aconway 2008-09-24: optimization: dump connections/sessions in parallel.

DumpClient::DumpClient(const Url& url, Cluster& c,
                       const boost::function<void()>& ok,
                       const boost::function<void(const std::exception&)>& fail)
    : receiver(url), donor(c), 
      connection(catchUpConnection()), shadowConnection(catchUpConnection()),
      done(ok), failed(fail)
{
    connection.open(url);
    session = connection.newSession("dump_shared");
}

DumpClient::~DumpClient() {}

// Catch-up exchange name: an illegal AMQP exchange name to avoid clashes.
static const char CATCH_UP_CHARS[] = "\000qpid-dump-exchange";
static const std::string CATCH_UP(CATCH_UP_CHARS, sizeof(CATCH_UP_CHARS)); 

void DumpClient::dump() {
    QPID_LOG(debug, donor.getSelf() << " starting dump to " << receiver);
    Broker& b = donor.getBroker();
    b.getExchanges().eachExchange(boost::bind(&DumpClient::dumpExchange, this, _1));
    // Catch-up exchange is used to route messages to the proper queue without modifying routing key.
    session.exchangeDeclare(arg::exchange=CATCH_UP, arg::type="fanout", arg::autoDelete=true);
    b.getQueues().eachQueue(boost::bind(&DumpClient::dumpQueue, this, _1));
    session.sync();
    session.close();
    donor.eachConnection(boost::bind(&DumpClient::dumpConnection, this, _1));
    // FIXME aconway 2008-09-18: inidicate successful end-of-dump.
    connection.close();
    QPID_LOG(debug,  donor.getSelf() << " dumped all state to " << receiver);
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
    shadowConnection = catchUpConnection();
    broker::Connection& bc = dumpConnection->getBrokerConnection();
    // FIXME aconway 2008-09-19: Open with identical settings to dumpConnection: password, vhost, frame size,
    // authentication etc. See ConnectionSettings.
    shadowConnection.open(receiver, bc.getUserId());
    dumpConnection->getBrokerConnection().eachSessionHandler(boost::bind(&DumpClient::dumpSession, this, _1));
    boost::shared_ptr<client::ConnectionImpl> impl = client::ConnectionAccess::getImpl(shadowConnection);
    AMQP_AllProxy::ClusterConnection proxy(*impl);
    proxy.shadowReady(dumpConnection->getId().getMember(),
                      reinterpret_cast<uint64_t>(dumpConnection->getId().getConnectionPtr()));
    shadowConnection.close();
    QPID_LOG(debug, donor.getId() << " dumped connection " << *dumpConnection);
}

// FIXME aconway 2008-09-26: REMOVE
void foo(broker::SemanticState::ConsumerImpl*) {}


void DumpClient::dumpSession(broker::SessionHandler& sh) {
    QPID_LOG(debug, donor.getId() << " dumping session " << &sh.getConnection()  << "[" << sh.getChannel() << "] = "
             << sh.getSession()->getId());
    broker::SessionState* s = sh.getSession();
    if (!s) return;         // no session.

    // Re-create the session.
    boost::shared_ptr<client::ConnectionImpl> cimpl = client::ConnectionAccess::getImpl(shadowConnection);
    size_t max_frame_size = cimpl->getNegotiatedSettings().maxFrameSize;
    boost::shared_ptr<client::SessionImpl> simpl(
        new client::SessionImpl(s->getId().getName(), cimpl, sh.getChannel(), max_frame_size));
    cimpl->addSession(simpl);
    simpl->open(sh.getSession()->getTimeout());
    client::SessionBase_0_10Access(shadowSession).set(simpl);
    AMQP_AllProxy::ClusterConnection proxy(simpl->out);

    // Re-create session state on remote connection.
    broker::SessionState* ss = sh.getSession();

    // For reasons unknown, boost::bind does not work here with boost 1.33.
    ss->eachConsumer(std::bind1st(std::mem_fun(&DumpClient::dumpConsumer),this));
    
    // FIXME aconway 2008-09-19: remaining session state.

    // Reset command-sequence state.
    proxy.sessionState(
        ss->senderGetReplayPoint().command,
        ss->senderGetCommandPoint().command,
        ss->senderGetIncomplete(),
        ss->receiverGetExpected().command,
        ss->receiverGetReceived().command,
        ss->receiverGetUnknownComplete(),
        ss->receiverGetIncomplete()
    );

    // FIXME aconway 2008-09-23: session replay list.

    QPID_LOG(debug, donor.getId() << " dumped session " << sh.getSession()->getId());
}

void DumpClient::dumpConsumer(broker::SemanticState::ConsumerImpl* ci) {
    using namespace message;
    shadowSession.messageSubscribe(
        arg::queue       = ci->getQueue()->getName(),
        arg::destination = ci->getName(),
        arg::acceptMode  = ci->isAckExpected() ? ACCEPT_MODE_EXPLICIT : ACCEPT_MODE_NONE,
        arg::acquireMode = ci->isAcquire() ? ACQUIRE_MODE_PRE_ACQUIRED : ACQUIRE_MODE_NOT_ACQUIRED,
        arg::exclusive   = false ,  // FIXME aconway 2008-09-23: how to read.

        // TODO aconway 2008-09-23: remaining args not used by current broker.
        // Update this code when they are.
        arg::resumeId=std::string(), 
        arg::resumeTtl=0,
        arg::arguments=FieldTable()
    );
    shadowSession.messageSetFlowMode(ci->getName(), ci->isWindowing() ? FLOW_MODE_WINDOW : FLOW_MODE_CREDIT);
    shadowSession.messageFlow(ci->getName(), CREDIT_UNIT_MESSAGE, ci->getMsgCredit());
    shadowSession.messageFlow(ci->getName(), CREDIT_UNIT_BYTE, ci->getByteCredit());
    // FIXME aconway 2008-09-23: need to replicate ConsumerImpl::blocked and notifyEnabled?
    QPID_LOG(debug, donor.getId() << " dumped consumer " << ci->getName() << " on " << shadowSession.getId());
}

}} // namespace qpid::cluster
