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
#include <boost/bind.hpp>
#include <boost/format.hpp>

using namespace qpid::client;
using namespace qpid::framing;

ConnectionImpl::ConnectionImpl(boost::shared_ptr<Connector> c) : connector(c)
{
    handler.in = boost::bind(&ConnectionImpl::incoming, this, _1);
    handler.out = boost::bind(&Connector::send, connector, _1);
    handler.onClose = boost::bind(&ConnectionImpl::closed, this);
    connector->setInputHandler(&handler);
    connector->setTimeoutHandler(this);
    connector->setShutdownHandler(this);
}

void ConnectionImpl::allocated(SessionCore::shared_ptr session)
{
    if (sessions.find(session->getId()) != sessions.end()) {
        throw Exception("Id already in use.");
    }
    sessions[session->getId()] = session;
}

void ConnectionImpl::released(SessionCore::shared_ptr session)
{
    SessionMap::iterator i = sessions.find(session->getId());
    if (i == sessions.end()) {
        throw Exception("Id not in use.");
    }
    sessions.erase(i);
}

void ConnectionImpl::handle(framing::AMQFrame& frame)
{
    handler.outgoing(frame);
}

void ConnectionImpl::incoming(framing::AMQFrame& frame)
{
    uint16_t id = frame.getChannel();
    SessionCore::shared_ptr session = sessions[id];
    if (!session) {
        throw ConnectionException(504, (boost::format("Invalid channel number %g") % id).str());
    }
    session->handle(frame);
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
    handler.close();
}

void ConnectionImpl::closed()
{
    closed(200, "OK");
}

void ConnectionImpl::closed(uint16_t code, const std::string& text)
{
    for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); i++) {
        i->second->closed(code, text);
    }
    sessions.clear();
    connector->close();
}

void ConnectionImpl::idleIn()
{
    connector->close();
}

void ConnectionImpl::idleOut()
{
    AMQFrame frame(version, 0, new AMQHeartbeatBody());
    connector->send(frame);
}

void ConnectionImpl::shutdown() {
    //this indicates that the socket to the server has closed
    for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); i++) {
        i->second->closed(0, "Unexpected scoket closure.");
    }
    sessions.clear();
}
