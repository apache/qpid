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
#include "Connection.h"
#include "ConnectionSettings.h"
#include "Channel.h"
#include "Message.h"
#include "SessionImpl.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/log/Statement.h"
#include "qpid/shared_ptr.h"

#include <algorithm>
#include <iostream>
#include <sstream>
#include <functional>
#include <boost/format.hpp>
#include <boost/bind.hpp>

using namespace qpid::framing;
using namespace qpid::sys;


namespace qpid {
namespace client {

Connection::Connection(framing::ProtocolVersion _version) : 
    channelIdCounter(0), version(_version) {}

Connection::~Connection(){ }

void Connection::open(
    const std::string& host, int port,
    const std::string& uid, const std::string& pwd, 
    const std::string& vhost,
    uint16_t maxFrameSize)
{
    ConnectionSettings settings;
    settings.host = host;
    settings.port = port;
    settings.username = uid;
    settings.password = pwd;
    settings.virtualhost = vhost;
    settings.maxFrameSize = maxFrameSize;
    open(settings);
}

void Connection::open(const ConnectionSettings& settings)
{
    if (impl)
        throw Exception(QPID_MSG("Connection::open() was already called"));

    impl = shared_ptr<ConnectionImpl>(new ConnectionImpl(version, settings));
    impl->open(settings.host, settings.port);
    max_frame_size = impl->getNegotiatedSettings().maxFrameSize;
}

void Connection::openChannel(Channel& channel) 
{
    if (!impl)
        throw Exception(QPID_MSG("Connection has not yet been opened"));
    channel.open(newSession(ASYNC));
}

Session Connection::newSession(SynchronousMode sync,
                               uint32_t detachedLifetime)
{
    if (!impl)
        throw Exception(QPID_MSG("Connection has not yet been opened"));

    shared_ptr<SessionImpl> core(
        new SessionImpl(impl, ++channelIdCounter, max_frame_size));
    core->setSync(sync);
    impl->addSession(core);
    core->open(detachedLifetime);
    return Session(core);
}

void Connection::resume(Session& session) {
    if (!impl)
        throw Exception(QPID_MSG("Connection has not yet been opened"));

    session.impl->setChannel(++channelIdCounter);
    impl->addSession(session.impl);
    session.impl->resume(impl);
}

void Connection::close() {
    if (impl) {
        impl->close();
        impl.reset();
    }
}

}} // namespace qpid::client
