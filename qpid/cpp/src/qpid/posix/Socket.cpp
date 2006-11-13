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

#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>

#include <boost/format.hpp>

#include <qpid/QpidError.h>
#include <qpid/posix/check.h>
#include <qpid/sys/Socket.h>

using namespace qpid::sys;

Socket::Socket() : socket(::socket (PF_INET, SOCK_STREAM, 0))
{
    CHECKNN(socket == 0);
}

void
Socket::setTimeout(long msecs)
{
    struct timeval tv;
    tv.tv_sec = msecs / 1000;
    tv.tv_usec = (msecs % 1000)*1000;
    setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
}

void
Socket::connect(const std::string& host, int port)
{
    struct sockaddr_in name;
    name.sin_family = AF_INET;
    name.sin_port = htons(port);
    struct hostent* hp = gethostbyname ( host.c_str() );
    if (hp == 0) CHECK0(-1);     // TODO aconway 2006-11-09: error message?
    memcpy(&name.sin_addr.s_addr, hp->h_addr_list[0], hp->h_length);
    CHECK0(::connect(socket, (struct sockaddr*)(&name), sizeof(name)));
}

void
Socket::close()
{
    if (socket == 0) return;
    CHECK0(::close(socket));
    socket = 0;
}

ssize_t
Socket::send(const char* data, size_t size)
{
    ssize_t sent = ::send(socket, data, size, 0);
    if (sent < 0) {
        if (errno == ECONNRESET) return SOCKET_EOF;
        if (errno == ETIMEDOUT) return SOCKET_TIMEOUT;
        CHECK0(sent);
    }
    return sent;
}

ssize_t
Socket::recv(char* data, size_t size)
{
    ssize_t received = ::recv(socket, data, size, 0);
    if (received < 0) {
        if (errno == ETIMEDOUT) return SOCKET_TIMEOUT;
        CHECK0(received);
    }
    return received;
}
