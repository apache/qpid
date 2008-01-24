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
#include "qpid/log/Statement.h"
#include "qpid/framing/constants.h"
#include "qpid/framing/reply_exceptions.h"

#include "ConnectionImpl.h"
#include "SessionCore.h"

#include <boost/bind.hpp>
#include <boost/format.hpp>

using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;

ConnectionImpl::ConnectionImpl(boost::shared_ptr<Connector> c)
    : connector(c), isClosed(false), isClosing(false)
{
    handler.in = boost::bind(&ConnectionImpl::incoming, this, _1);
    handler.out = boost::bind(&Connector::send, connector, _1);
    handler.onClose = boost::bind(&ConnectionImpl::closed, this,
                                  REPLY_SUCCESS, std::string());
    handler.onError = boost::bind(&ConnectionImpl::closed, this, _1, _2);
    connector->setInputHandler(&handler);
    connector->setTimeoutHandler(this);
    connector->setShutdownHandler(this);
}

ConnectionImpl::~ConnectionImpl() {
    // Important to close the connector first, to ensure the
    // connector thread does not call on us while the destructor
    // is running.
    connector->close(); 
}

void ConnectionImpl::addSession(const boost::shared_ptr<SessionCore>& session)
{
    Mutex::ScopedLock l(lock);
    boost::weak_ptr<SessionCore>& s = sessions[session->getChannel()];
    if (s.lock()) throw ChannelBusyException();
    s = session;
}

void ConnectionImpl::handle(framing::AMQFrame& frame)
{
    handler.outgoing(frame);
}

void ConnectionImpl::incoming(framing::AMQFrame& frame)
{
    boost::shared_ptr<SessionCore> s;
    {
        Mutex::ScopedLock l(lock);
        s = sessions[frame.getChannel()].lock();
    }
    if (!s)
        throw ChannelErrorException(QPID_MSG("Invalid channel: " << frame.getChannel()));
    s->in(frame);
}

void ConnectionImpl::open(const std::string& host, int port,
                          const std::string& uid, const std::string& pwd, 
                          const std::string& vhost)
{
    //TODO: better management of connection properties
    handler.uid = uid;
    handler.pwd = pwd;
    handler.vhost = vhost;

    QPID_LOG(info, "Connecting to " << host << ":" << port);
    connector->connect(host, port);
    connector->init();
    handler.waitForOpen();
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
    Mutex::ScopedLock l(lock);
    if (isClosing || isClosed) return;
    isClosing = true;
    {
        Mutex::ScopedUnlock u(lock);
        handler.close();
    }
    closed(REPLY_SUCCESS, "Closed by client");
}

// Set closed flags and erase the sessions map, but keep the contents
// so sessions can be updated outside the lock.
ConnectionImpl::SessionVector ConnectionImpl::closeInternal(const Mutex::ScopedLock&) {
    isClosed = true;
    connector->close();
    SessionVector save;
    for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        boost::shared_ptr<SessionCore> s = i->second.lock();
        if (s) save.push_back(s);
    }
    sessions.clear();
    return save;
}

void ConnectionImpl::closed(uint16_t code, const std::string& text) 
{
    Mutex::ScopedLock l(lock);
    if (isClosed) return;
    SessionVector save(closeInternal(l));
    Mutex::ScopedUnlock u(lock);
    std::for_each(save.begin(), save.end(), boost::bind(&SessionCore::connectionClosed, _1, code, text));
}

static const std::string CONN_CLOSED("Connection closed by broker");

void ConnectionImpl::shutdown() 
{
    Mutex::ScopedLock l(lock);
    if (isClosed) return;
    SessionVector save(closeInternal(l));
    handler.fail(CONN_CLOSED);
    Mutex::ScopedUnlock u(lock);
    std::for_each(save.begin(), save.end(),
                  boost::bind(&SessionCore::connectionBroke, _1, INTERNAL_ERROR, CONN_CLOSED));
}

void ConnectionImpl::erase(uint16_t ch) {
    Mutex::ScopedLock l(lock);
    sessions.erase(ch);
}
    
