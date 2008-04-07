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
#include <sys/errno.h>
#include <netinet/in.h>
#include <netdb.h>

#include <boost/format.hpp>

#include <QpidError.h>
#include <posix/check.h>
#include <sys/Socket.h>

using namespace qpid::sys;

Socket Socket::createTcp() 
{
    int s = ::socket (PF_INET, SOCK_STREAM, 0);
    if (s < 0) throw QPID_POSIX_ERROR(errno);
    return s;
}

Socket::Socket(int descriptor) : socket(descriptor) {}

void Socket::setTimeout(Time interval)
{
    struct timeval tv;
    tv.tv_sec = interval/TIME_SEC;
    tv.tv_usec = (interval%TIME_SEC)/TIME_USEC;
    setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
}

void Socket::connect(const std::string& host, int port)
{
    struct sockaddr_in name;
    name.sin_family = AF_INET;
    name.sin_port = htons(port);
    struct hostent* hp = gethostbyname ( host.c_str() );
    if (hp == 0) throw QPID_POSIX_ERROR(errno);
    memcpy(&name.sin_addr.s_addr, hp->h_addr_list[0], hp->h_length);
    if (::connect(socket, (struct sockaddr*)(&name), sizeof(name)) < 0)
        throw QPID_POSIX_ERROR(errno);
}

void
Socket::close()
{
    if (socket == 0) return;
    if (::close(socket) < 0) throw QPID_POSIX_ERROR(errno);
    socket = 0;
}

ssize_t
Socket::send(const void* data, size_t size)
{
    ssize_t sent = ::send(socket, data, size, 0);
    if (sent < 0) {
        if (errno == ECONNRESET) return SOCKET_EOF;
        if (errno == ETIMEDOUT) return SOCKET_TIMEOUT;
        throw QPID_POSIX_ERROR(errno);
    }
    return sent;
}

ssize_t
Socket::recv(void* data, size_t size)
{
    ssize_t received = ::recv(socket, data, size, 0);
    if (received < 0) {
        if (errno == ETIMEDOUT) return SOCKET_TIMEOUT;
        throw QPID_POSIX_ERROR(errno);
    }
    return received;
}

int Socket::listen(int port, int backlog) 
{
    struct sockaddr_in name;
    name.sin_family = AF_INET;
    name.sin_port = htons(port);
    name.sin_addr.s_addr = 0;
    if (::bind(socket, (struct sockaddr*)&name, sizeof(name)) < 0)
        throw QPID_POSIX_ERROR(errno);
    if (::listen(socket, backlog) < 0)
        throw QPID_POSIX_ERROR(errno);
    
    socklen_t namelen = sizeof(name);
    if (::getsockname(socket, (struct sockaddr*)&name, &namelen) < 0)
        throw QPID_POSIX_ERROR(errno);

    return ntohs(name.sin_port);
}


int Socket::fd() 
{
    return socket;
}

void Socket::setTcpNoDelay(bool) {} //not yet implemented
