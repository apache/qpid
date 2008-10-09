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
#include "ConnectionImpl.h"
#include "Connector.h"
#include "ConnectionSettings.h"
#include "SessionImpl.h"
#include "FailoverListener.h"

#include "qpid/log/Statement.h"
#include "qpid/Url.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/reply_exceptions.h"

#include <boost/bind.hpp>
#include <boost/format.hpp>

using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::framing::connection;
using namespace qpid::sys;

using namespace qpid::framing::connection;//for connection error codes

ConnectionImpl::ConnectionImpl(framing::ProtocolVersion v, const ConnectionSettings& settings)
    : Bounds(settings.maxFrameSize * settings.bounds),
      handler(settings, v),
      failover(new FailoverListener()),
      version(v),
      nextChannel(1)
{
    QPID_LOG(debug, "ConnectionImpl created for " << version);
    handler.in = boost::bind(&ConnectionImpl::incoming, this, _1);
    handler.out = boost::bind(&Connector::send, boost::ref(connector), _1);
    handler.onClose = boost::bind(&ConnectionImpl::closed, this,
                                  CLOSE_CODE_NORMAL, std::string());

    //only set error handler once  open
    handler.onError = boost::bind(&ConnectionImpl::closed, this, _1, _2);
}

ConnectionImpl::~ConnectionImpl() {
    // Important to close the connector first, to ensure the
    // connector thread does not call on us while the destructor
    // is running.
    failover.reset();
    if (connector) connector->close();
}

void ConnectionImpl::addSession(const boost::shared_ptr<SessionImpl>& session, uint16_t channel)
{
    Mutex::ScopedLock l(lock);
    session->setChannel(channel ? channel : nextChannel++);
    boost::weak_ptr<SessionImpl>& s = sessions[session->getChannel()];
    if (s.lock()) throw SessionBusyException();
    s = session;
}

void ConnectionImpl::handle(framing::AMQFrame& frame)
{
    handler.outgoing(frame);
}

void ConnectionImpl::incoming(framing::AMQFrame& frame)
{
    boost::shared_ptr<SessionImpl> s;
    {
        Mutex::ScopedLock l(lock);
        s = sessions[frame.getChannel()].lock();
    }
    if (!s)
        throw NotAttachedException(QPID_MSG("Invalid channel: " << frame.getChannel()));
    s->in(frame);
}

bool ConnectionImpl::isOpen() const 
{ 
    return handler.isOpen();
}


void ConnectionImpl::open()
{
    const std::string& protocol = handler.protocol;
    const std::string& host = handler.host;
    int port = handler.port;
    QPID_LOG(info, "Connecting to " << protocol << ":" << host << ":" << port);

    connector.reset(Connector::create(protocol, version, handler, this)); 
    connector->setInputHandler(&handler);
    connector->setShutdownHandler(this);
    connector->connect(host, port);
    connector->init();
    handler.waitForOpen();

    if (failover.get()) failover->start(shared_from_this());
}

void ConnectionImpl::idleIn()
{
    close();
}

void ConnectionImpl::idleOut()
{
    AMQFrame frame(in_place<AMQHeartbeatBody>());
    connector->send(frame);
}

void ConnectionImpl::close()
{
    if (!handler.isOpen()) return;
    handler.close();
    closed(CLOSE_CODE_NORMAL, "Closed by client");
}


template <class F> void ConnectionImpl::closeInternal(const F& f) {
    connector->close();
    for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        boost::shared_ptr<SessionImpl> s = i->second.lock();
        if (s) f(s);
    }
    sessions.clear();
}

void ConnectionImpl::closed(uint16_t code, const std::string& text) { 
    Mutex::ScopedLock l(lock);
    setException(new ConnectionException(code, text));
    closeInternal(boost::bind(&SessionImpl::connectionClosed, _1, code, text));
}

static const std::string CONN_CLOSED("Connection closed by broker");

void ConnectionImpl::shutdown() {
    Mutex::ScopedLock l(lock);
    if (handler.isClosed()) return;
    // FIXME aconway 2008-06-06: exception use, amqp0-10 does not seem to have
    // an appropriate close-code. connection-forced is not right.
    if (!handler.isClosing()) 
        closeInternal(boost::bind(&SessionImpl::connectionBroke, _1, CLOSE_CODE_CONNECTION_FORCED, CONN_CLOSED));
    setException(new ConnectionException(CLOSE_CODE_CONNECTION_FORCED, CONN_CLOSED));
    handler.fail(CONN_CLOSED);
}

void ConnectionImpl::erase(uint16_t ch) {
    Mutex::ScopedLock l(lock);
    sessions.erase(ch);
}

const ConnectionSettings& ConnectionImpl::getNegotiatedSettings()
{
    return handler;
}
    
std::vector<qpid::Url> ConnectionImpl::getKnownBrokers() {
    // FIXME aconway 2008-10-08: initialize failover list from openOk or settings
    return failover ? failover->getKnownBrokers() : std::vector<qpid::Url>();
}

boost::shared_ptr<SessionImpl>  ConnectionImpl::newSession(const std::string& name, uint32_t timeout, uint16_t channel) {
    boost::shared_ptr<SessionImpl> simpl(new SessionImpl(name, shared_from_this()));
    addSession(simpl, channel);
    simpl->open(timeout);
    return simpl;
}

void ConnectionImpl::stopFailoverListener() { failover.reset(); }
