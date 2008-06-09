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
    host("localhost"), 
    port(TcpAddress::DEFAULT_PORT),
    username("guest"), 
    password("guest"),
    mechanism("PLAIN"),
    locale("en_US"),
    heartbeat(0),
    maxChannels(32767),
    maxFrameSize(65535),
    bounds(2),
    tcpNoDelay(false)
{}

ConnectionSettings::~ConnectionSettings() {}

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
