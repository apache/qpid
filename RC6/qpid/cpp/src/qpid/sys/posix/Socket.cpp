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

#include "qpid/sys/Socket.h"

#include "check.h"
#include "PrivatePosix.h"

#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/errno.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <netdb.h>
#include <cstdlib>
#include <string.h>
#include <iostream>

#include <boost/format.hpp>

namespace qpid {
namespace sys {

namespace {
std::string getName(int fd, bool local, bool includeService = false)
{
    ::sockaddr_storage name; // big enough for any socket address    
    ::socklen_t namelen = sizeof(name);
    
    int result = -1;
    if (local) {
        result = ::getsockname(fd, (::sockaddr*)&name, &namelen);
    } else {
        result = ::getpeername(fd, (::sockaddr*)&name, &namelen);
    }

    QPID_POSIX_CHECK(result);

    char servName[NI_MAXSERV];
    char dispName[NI_MAXHOST];
    if (includeService) {
        if (int rc=::getnameinfo((::sockaddr*)&name, namelen, dispName, sizeof(dispName), 
                                 servName, sizeof(servName), 
                                 NI_NUMERICHOST | NI_NUMERICSERV) != 0)
            throw QPID_POSIX_ERROR(rc);
        return std::string(dispName) + ":" + std::string(servName);

    } else {
        if (int rc=::getnameinfo((::sockaddr*)&name, namelen, dispName, sizeof(dispName), 0, 0, NI_NUMERICHOST) != 0)
            throw QPID_POSIX_ERROR(rc);
        return dispName;
    }
}

std::string getService(int fd, bool local)
{
    ::sockaddr_storage name; // big enough for any socket address    
    ::socklen_t namelen = sizeof(name);
    
    int result = -1;
    if (local) {
        result = ::getsockname(fd, (::sockaddr*)&name, &namelen);
    } else {
        result = ::getpeername(fd, (::sockaddr*)&name, &namelen);
    }

    QPID_POSIX_CHECK(result);

    char servName[NI_MAXSERV];
    if (int rc=::getnameinfo((::sockaddr*)&name, namelen, 0, 0, 
                                 servName, sizeof(servName), 
                                 NI_NUMERICHOST | NI_NUMERICSERV) != 0)
        throw QPID_POSIX_ERROR(rc);
    return servName;
}
}

Socket::Socket() :
	IOHandle(new IOHandlePrivate)
{
	createTcp();
}

Socket::Socket(IOHandlePrivate* h) :
	IOHandle(h)
{}

void Socket::createTcp() const
{
    int& socket = impl->fd;
    if (socket != -1) Socket::close();
    int s = ::socket (PF_INET, SOCK_STREAM, 0);
    if (s < 0) throw QPID_POSIX_ERROR(errno);
    socket = s;
}

void Socket::setTimeout(const Duration& interval) const
{
    const int& socket = impl->fd;
    struct timeval tv;
    toTimeval(tv, interval);
    setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
}

void Socket::setNonblocking() const {
    QPID_POSIX_CHECK(::fcntl(impl->fd, F_SETFL, O_NONBLOCK));
}

namespace {
const char* h_errstr(int e) {
    switch (e) {
      case HOST_NOT_FOUND: return "Host not found";
      case NO_ADDRESS: return "Name does not have an IP address";
      case TRY_AGAIN: return "A temporary error occurred on an authoritative name server.";
      case NO_RECOVERY: return "Non-recoverable name server error";
      default: return "Unknown error";
    }
}
}

void Socket::connect(const std::string& host, uint16_t port) const
{
    std::stringstream namestream;
    namestream << host << ":" << port;
    connectname = namestream.str();

    const int& socket = impl->fd;
    struct sockaddr_in name;
    name.sin_family = AF_INET;
    name.sin_port = htons(port);
    // TODO: Be good to make this work for IPv6 as well as IPv4
    // Use more modern lookup functions
    struct hostent* hp = gethostbyname ( host.c_str() );
    if (hp == 0)
        throw Exception(QPID_MSG("Cannot resolve " << host << ": " << h_errstr(h_errno)));
    ::memcpy(&name.sin_addr.s_addr, hp->h_addr_list[0], hp->h_length);
    if ((::connect(socket, (struct sockaddr*)(&name), sizeof(name)) < 0) &&
        (errno != EINPROGRESS))
        throw qpid::Exception(QPID_MSG(strError(errno) << ": " << host << ":" << port));
}

void
Socket::close() const
{
    int& socket = impl->fd;
    if (socket == -1) return;
    if (::close(socket) < 0) throw QPID_POSIX_ERROR(errno);
    socket = -1;
}

int Socket::listen(uint16_t port, int backlog) const
{
    const int& socket = impl->fd;
    int yes=1;
    QPID_POSIX_CHECK(setsockopt(socket,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes)));
    struct sockaddr_in name;
    name.sin_family = AF_INET;
    name.sin_port = htons(port);
    name.sin_addr.s_addr = 0;
    if (::bind(socket, (struct sockaddr*)&name, sizeof(name)) < 0)
        throw Exception(QPID_MSG("Can't bind to port " << port << ": " << strError(errno)));
    if (::listen(socket, backlog) < 0)
        throw Exception(QPID_MSG("Can't listen on port " << port << ": " << strError(errno)));
    
    socklen_t namelen = sizeof(name);
    if (::getsockname(socket, (struct sockaddr*)&name, &namelen) < 0)
        throw QPID_POSIX_ERROR(errno);

    return ntohs(name.sin_port);
}

Socket* Socket::accept(struct sockaddr *addr, socklen_t *addrlen) const
{
    int afd = ::accept(impl->fd, addr, addrlen);
    if ( afd >= 0)
        return new Socket(new IOHandlePrivate(afd));
    else if (errno == EAGAIN)
        return 0;
    else throw QPID_POSIX_ERROR(errno);
}

int Socket::read(void *buf, size_t count) const
{
    return ::read(impl->fd, buf, count);
}

int Socket::write(const void *buf, size_t count) const
{
    return ::write(impl->fd, buf, count);
}

std::string Socket::getSockname() const
{
    return getName(impl->fd, true);
}

std::string Socket::getPeername() const
{
    return getName(impl->fd, false);
}

std::string Socket::getPeerAddress() const
{
    if (!connectname.empty())
        return std::string (connectname);
    return getName(impl->fd, false, true);
}

std::string Socket::getLocalAddress() const
{
    return getName(impl->fd, true, true);
}

uint16_t Socket::getLocalPort() const
{
    return std::atoi(getService(impl->fd, true).c_str());
}

uint16_t Socket::getRemotePort() const
{
    return atoi(getService(impl->fd, true).c_str());
}

int Socket::getError() const
{
    int       result;
    socklen_t rSize = sizeof (result);

    if (::getsockopt(impl->fd, SOL_SOCKET, SO_ERROR, &result, &rSize) < 0)
        throw QPID_POSIX_ERROR(errno);

    return result;
}

void Socket::setTcpNoDelay(bool nodelay) const
{
    if (nodelay) {
        int flag = 1;
        int result = setsockopt(impl->fd, IPPROTO_TCP, TCP_NODELAY, (char *)&flag, sizeof(flag));
        QPID_POSIX_CHECK(result);
    }
}

}} // namespace qpid::sys
