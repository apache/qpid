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
    : connector(c), isClosed(false)
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

ConnectionImpl::~ConnectionImpl() { close(); }

void ConnectionImpl::addSession(const boost::shared_ptr<SessionCore>& session)
{
    Mutex::ScopedLock l(lock);
    boost::weak_ptr<SessionCore>& s = sessions[session->getChannel()];
    if (s.lock())
        throw ChannelBusyException();
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

    connector->connect(host, port);
    connector->init();
    handler.waitForOpen();
}

void ConnectionImpl::close()
{
    Mutex::ScopedLock l(lock);
    if (!isClosed)
        handler.close();
}

void ConnectionImpl::idleIn()
{
    connector->close();
}

void ConnectionImpl::idleOut()
{
    AMQFrame frame(in_place<AMQHeartbeatBody>());
    connector->send(frame);
}

template <class F>
void ConnectionImpl::forChannels(F functor) {
    for (SessionMap::iterator i = sessions.begin();
         i != sessions.end(); ++i) {
        try {
            boost::shared_ptr<SessionCore> s = i->second.lock();
            if (s) functor(*s);
        } catch (...) { assert(0); }
    }
}

void ConnectionImpl::shutdown() 
{
    Mutex::ScopedLock l(lock);
    if (isClosed) return;
    forChannels(boost::bind(&SessionCore::connectionBroke, _1,
                            INTERNAL_ERROR, "Unexpected socket closure."));
    sessions.clear();
    isClosed = true;
}

void ConnectionImpl::closed(uint16_t code, const std::string& text) 
{
    Mutex::ScopedLock l(lock);
    if (isClosed) return;
    forChannels(boost::bind(&SessionCore::connectionClosed, _1, code, text));
    sessions.clear();
    isClosed = true;
    connector->close();
}

void ConnectionImpl::erase(uint16_t ch) {
    Mutex::ScopedLock l(lock);
    sessions.erase(ch);
}
    
