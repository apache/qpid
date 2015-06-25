/*
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

#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/SessionState.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/ConnectionOutputHandler.h"

#include <boost/bind.hpp>

namespace qpid {
namespace broker {
using namespace framing;
using namespace std;
using namespace qpid::sys;

namespace {
class DefaultErrorListener : public SessionHandler::ErrorListener {
  public:
    void connectionException(framing::connection::CloseCode code, const std::string& msg) {
        QPID_LOG(error, "Connection exception: " << framing::createConnectionException(code, msg).what());
    }
    void channelException(framing::session::DetachCode code, const std::string& msg) {
        QPID_LOG(error, "Channel exception: " << framing::createChannelException(code, msg).what());
    }
    void executionException(framing::execution::ErrorCode code, const std::string& msg) {
        QPID_LOG(error, "Execution exception: " << framing::createSessionException(code, msg).what());
    }
    void incomingExecutionException(framing::execution::ErrorCode code, const std::string& msg) {
        QPID_LOG(debug, "Incoming execution exception: " << framing::createSessionException(code, msg).what());
    }
    void detach() {}

  private:
};
}

SessionHandler::SessionHandler(amqp_0_10::Connection& c, ChannelId ch)
    : qpid::amqp_0_10::SessionHandler(&c.getOutput(), ch),
      connection(c),
      proxy(out),
      errorListener(boost::shared_ptr<ErrorListener>(new DefaultErrorListener()))
{
    c.getBroker().getSessionHandlerObservers().newSessionHandler(*this);
}

SessionHandler::~SessionHandler()
{
    if (session.get())
        connection.getBroker().getSessionManager().forget(session->getId());
}

void SessionHandler::connectionException(
    framing::connection::CloseCode code, const std::string& msg)
{
    // NOTE: must tell the error listener _before_ calling connection.close()
    if (errorListener)
        errorListener->connectionException(code, msg);
    connection.close(code, msg);
}

void SessionHandler::channelException(
    framing::session::DetachCode code, const std::string& msg)
{
    if (errorListener)
        errorListener->channelException(code, msg);
}

void SessionHandler::executionException(
    framing::execution::ErrorCode code, const std::string& msg)
{
    if (errorListener)
        errorListener->executionException(code, msg);
}

void SessionHandler::incomingExecutionException(
    framing::execution::ErrorCode code, const std::string& msg)
{
    if (errorListener)
        errorListener->incomingExecutionException(code, msg);
}

amqp_0_10::Connection& SessionHandler::getConnection() { return connection; }

const amqp_0_10::Connection& SessionHandler::getConnection() const { return connection; }

void SessionHandler::handleDetach() {
    qpid::amqp_0_10::SessionHandler::handleDetach();
    assert(&connection.getChannel(channel.get()) == this);
    if (session.get())
        connection.getBroker().getSessionManager().detach(session);
    assert(!session.get());
    if (errorListener) errorListener->detach();
    connection.closeChannel(channel.get());
}

void SessionHandler::setState(const std::string& name, bool force) {
    assert(!session.get());
    SessionId id(connection.getUserId(), name);
    session = connection.getBroker().getSessionManager().attach(*this, id, force);
}

void SessionHandler::detaching()
{
    assert(session.get());
    session->disableOutput();
}

FrameHandler* SessionHandler::getInHandler() { return session.get() ? &session->in : 0; }
qpid::SessionState* SessionHandler::getState() { return session.get(); }

void SessionHandler::readyToSend() {
    if (session.get()) session->readyToSend();
}

/**
 * Used by inter-broker bridges to set up session id and attach
 */
void SessionHandler::attachAs(const std::string& name)
{
    SessionId id(connection.getUserId(), name);
    SessionState::Configuration config = connection.getBroker().getSessionManager().getSessionConfig();
    session.reset(new SessionState(connection.getBroker(), *this, id, config));
    sendAttach(false);
}

/**
 * TODO: this is a little ugly, fix it; its currently still relied on
 * for 'push' bridges
 */
void SessionHandler::attached(const std::string& name)
{
    if (session.get()) {
        session->addManagementObject(); // Delayed from attachAs()
        qpid::amqp_0_10::SessionHandler::attached(name);
    } else {
        SessionId id(connection.getUserId(), name);
        SessionState::Configuration config = connection.getBroker().getSessionManager().getSessionConfig();
        session.reset(new SessionState(connection.getBroker(), *this, id, config));
        markReadyToSend();
    }
}

}} // namespace qpid::broker
