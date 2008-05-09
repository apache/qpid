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
#include "ConnectionSettings.h"

#include "qpid/log/Logger.h"
#include "qpid/sys/posix/check.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

namespace qpid {
namespace client {

ConnectionSettings::ConnectionSettings() :
    Options("Connection Settings"),
    host("localhost"), 
    port(TcpAddress::DEFAULT_PORT),
    clientid("cpp"), 
    username("guest"), 
    password("guest"),
    mechanism("ANONYMOUS"),
    locale("en_US"),
    heartbeat(0),
    maxChannels(32767),
    maxFrameSize(65535),
    bounds(2),
    tcpNoDelay(false)
{
    addOptions()
        ("broker,b", optValue(host, "HOST"), "Broker host to connect to") 
        ("port,p", optValue(port, "PORT"), "Broker port to connect to")
        ("virtualhost,v", optValue(virtualhost, "VHOST"), "virtual host")
        ("clientname,n", optValue(clientid, "ID"), "unique client identifier")
        ("username", optValue(username, "USER"), "user name for broker log in.")
        ("password", optValue(password, "PASSWORD"), "password for broker log in.")
        ("mechanism", optValue(mechanism, "MECH"), "SASL mechanism to use when authenticating.")
        ("locale", optValue(locale, "LOCALE"), "locale to use.")
        ("max-channels", optValue(maxChannels, "N"), "the maximum number of channels the client requires.")
        ("max-frame-size", optValue(maxFrameSize, "N"), "the maximum frame size to request.")
        ("bounds-multiplier", optValue(bounds, "N"), 
         "restricts the total size of outgoing frames queued up for writing (as a multiple of the max frame size).");
    add(log);
}

ConnectionSettings::~ConnectionSettings() {}

void ConnectionSettings::parse(int argc, char** argv) 
{
    qpid::Options::parse(argc, argv);
    qpid::log::Logger::instance().configure(log, argv[0]);
}


void ConnectionSettings::configurePosixTcpSocket(int fd) const
{
    if (tcpNoDelay) {
        int flag = 1;
        int result = setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, (char *)&flag, sizeof(flag));
        QPID_POSIX_CHECK(result);
        QPID_LOG(debug, "Set TCP_NODELAY");
    }
}

}} // namespace qpid::client
