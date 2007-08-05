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
#include <algorithm>
#include <boost/format.hpp>
#include <boost/bind.hpp>

#include "Connection.h"
#include "ClientChannel.h"
#include "ClientMessage.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/log/Statement.h"
#include "qpid/QpidError.h"
#include <iostream>
#include <sstream>
#include "MethodBodyInstances.h"
#include <functional>

using namespace qpid::framing;
using namespace qpid::sys;


namespace qpid {
namespace client {

Connection::Connection(bool _debug, uint32_t _max_frame_size, framing::ProtocolVersion _version) : 
    channelIdCounter(0), version(_version), 
    max_frame_size(_max_frame_size), 
    impl(new ConnectionImpl(boost::shared_ptr<Connector>(new Connector(_version, _debug)))),
    isOpen(false) {}

Connection::Connection(boost::shared_ptr<Connector> c) : 
    channelIdCounter(0), version(framing::highestProtocolVersion), 
    max_frame_size(65536), 
    impl(new ConnectionImpl(c)),
    isOpen(false) {}

Connection::~Connection(){}

void Connection::open(
    const std::string& host, int port,
    const std::string& uid, const std::string& pwd, const std::string& vhost)
{
    if (isOpen)
        THROW_QPID_ERROR(INTERNAL_ERROR, "Channel object is already open");

    impl->open(host, port, uid, pwd, vhost);
    isOpen = true;
}

void Connection::openChannel(Channel& channel) {
    ChannelId id = ++channelIdCounter;
    SessionCore::shared_ptr session(new SessionCore(id, impl, max_frame_size));
    impl->allocated(session);
    channel.open(impl, session);
    session->open();
}

Session Connection::newSession() {
    ChannelId id = ++channelIdCounter;
    SessionCore::shared_ptr session(new SessionCore(id, impl, max_frame_size));
    impl->allocated(session);
    return Session(impl, session);
}

void Connection::close()
{
    impl->close();
}

}} // namespace qpid::client
