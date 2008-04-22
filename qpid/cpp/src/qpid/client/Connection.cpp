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
#include "Channel.h"
#include "Message.h"
#include "SessionImpl.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/log/Statement.h"
#include "qpid/shared_ptr.h"
#include <iostream>
#include <sstream>
#include <functional>

using namespace qpid::framing;
using namespace qpid::sys;


namespace qpid {
namespace client {

Connection::Connection(bool _debug, uint32_t _max_frame_size, framing::ProtocolVersion _version) : 
    channelIdCounter(0), version(_version), 
    max_frame_size(_max_frame_size), 
    isOpen(false),
    impl(new ConnectionImpl(
             shared_ptr<Connector>(new Connector(_version, _debug))))
{}

Connection::Connection(shared_ptr<Connector> c) : 
    channelIdCounter(0), version(framing::highestProtocolVersion), 
    max_frame_size(65535), 
    isOpen(false),
    impl(new ConnectionImpl(c))
{}

Connection::~Connection(){ }

void Connection::open(
    const std::string& host, int port,
    const std::string& uid, const std::string& pwd, const std::string& vhost)
{
    if (isOpen)
        throw Exception(QPID_MSG("Channel object is already open"));

    impl->open(host, port, uid, pwd, vhost);
    isOpen = true;
}

void Connection::openChannel(Channel& channel) {
    channel.open(newSession(ASYNC));
}

Session Connection::newSession(SynchronousMode sync,
                                    uint32_t detachedLifetime)
{
    shared_ptr<SessionImpl> core(
        new SessionImpl(impl, ++channelIdCounter, max_frame_size));
    core->setSync(sync);
    impl->addSession(core);
    core->open(detachedLifetime);
    return Session(core);
}

void Connection::resume(Session& session) {
    session.impl->setChannel(++channelIdCounter);
    impl->addSession(session.impl);
    session.impl->resume(impl);
}

void Connection::close() {
    impl->close();
}

}} // namespace qpid::client
