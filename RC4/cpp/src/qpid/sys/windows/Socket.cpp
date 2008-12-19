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
#include "IoHandlePrivate.h"
#include "check.h"
#include "qpid/sys/Time.h"

#include <cstdlib>
#include <string.h>
#include <iostream>
#include <memory.h>

#include <winsock2.h>
#include <ws2tcpip.h>

#include <boost/format.hpp>

// Need to initialize WinSock. Ideally, this would be a singleton or embedded
// in some one-time initialization function. I tried boost singleton and could
// not get it to compile (and others located in google had the same problem).
// So, this simple static with an interlocked increment will do for known
// use cases at this time. Since this will only shut down winsock at process
// termination, there may be some problems with client programs that also
// expect to load and unload winsock, but we'll see...
// If someone does get an easy-to-use singleton sometime, converting to it
// may be preferable.

namespace {

static LONG volatile initialized = 0;

class WinSockSetup {
    //  : public boost::details::pool::singleton_default<WinSockSetup> {

public:
    WinSockSetup() {
        LONG timesEntered = InterlockedIncrement(&initialized);
        if (timesEntered > 1)
          return;
        err = 0;
        WORD wVersionRequested;
        WSADATA wsaData;

        /* Request WinSock 2.2 */
        wVersionRequested = MAKEWORD(2, 2);
        err = WSAStartup(wVersionRequested, &wsaData);
    }

    ~WinSockSetup() {
        WSACleanup();
    }

public:
    int error(void) const { return err; }

protected:
    DWORD err;
};

static WinSockSetup setup;

} /* namespace */

namespace qpid {
namespace sys {

namespace {

std::string getName(SOCKET fd, bool local, bool includeService = false)
{
    sockaddr_in name; // big enough for any socket address    
    socklen_t namelen = sizeof(name);
    if (local) {
        QPID_WINSOCK_CHECK(::getsockname(fd, (sockaddr*)&name, &namelen));
    } else {
        QPID_WINSOCK_CHECK(::getpeername(fd, (sockaddr*)&name, &namelen));
    }

    char servName[NI_MAXSERV];
    char dispName[NI_MAXHOST];
    if (includeService) {
        if (int rc = ::getnameinfo((sockaddr*)&name, namelen,
                                   dispName, sizeof(dispName), 
                                   servName, sizeof(servName), 
                                   NI_NUMERICHOST | NI_NUMERICSERV) != 0)
            throw qpid::Exception(QPID_MSG(gai_strerror(rc)));
        return std::string(dispName) + ":" + std::string(servName);
    } else {
        if (int rc = ::getnameinfo((sockaddr*)&name, namelen,
                                   dispName, sizeof(dispName),
                                   0, 0,
                                   NI_NUMERICHOST) != 0)
            throw qpid::Exception(QPID_MSG(gai_strerror(rc)));
        return dispName;
    }
}

std::string getService(SOCKET fd, bool local)
{
    sockaddr_in name; // big enough for any socket address    
    socklen_t namelen = sizeof(name);
    
    if (local) {
        QPID_WINSOCK_CHECK(::getsockname(fd, (sockaddr*)&name, &namelen));
    } else {
        QPID_WINSOCK_CHECK(::getpeername(fd, (sockaddr*)&name, &namelen));
    }

    char servName[NI_MAXSERV];
    if (int rc = ::getnameinfo((sockaddr*)&name, namelen,
                               0, 0, 
                               servName, sizeof(servName), 
                               NI_NUMERICHOST | NI_NUMERICSERV) != 0)
        throw qpid::Exception(QPID_MSG(gai_strerror(rc)));
    return servName;
}
}  // namespace

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
    SOCKET& socket = impl->fd;
    if (socket != INVALID_SOCKET) Socket::close();
    SOCKET s = ::socket (PF_INET, SOCK_STREAM, 0);
    if (s == INVALID_SOCKET) throw QPID_WINDOWS_ERROR(WSAGetLastError());
    socket = s;
}

void Socket::setTimeout(const Duration& interval) const
{
    const SOCKET& socket = impl->fd;
    int64_t nanosecs = interval;
    nanosecs /= (1000 * 1000); // nsecs -> usec -> msec
    int msec = 0;
    if (nanosecs > std::numeric_limits<int>::max())
        msec = std::numeric_limits<int>::max();
    else
        msec = static_cast<int>(nanosecs);
    setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, (char *)&msec, sizeof(msec));
    setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, (char *)&msec, sizeof(msec));
}

void Socket::setNonblocking() const {
    u_long nonblock = 1;
    QPID_WINSOCK_CHECK(ioctlsocket(impl->fd, FIONBIO, &nonblock));
}

void Socket::connect(const std::string& host, uint16_t port) const
{
    std::stringstream portstream;
    portstream << port << std::ends;
    std::string portstr = portstream.str();
    std::stringstream namestream;
    namestream << host << ":" << port;
    connectname = namestream.str();

    const SOCKET& socket = impl->fd;
    // TODO: Be good to make this work for IPv6 as well as IPv4. Would require
    // other changes, such as waiting to create the socket until after we
    // have the address family. Maybe unbundle the translation of names here;
    // use TcpAddress to resolve things and make this class take a TcpAddress
    // and grab its address family to create the socket.
    struct addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;   // We always creating AF_INET-only sockets.
    hints.ai_socktype = SOCK_STREAM; // We always do TCP
    addrinfo *addrs;
    int status = getaddrinfo(host.c_str(), portstr.c_str(), &hints, &addrs);
    if (status != 0)
        throw Exception(QPID_MSG("Cannot resolve " << host << ": " <<
                                 gai_strerror(status)));
    addrinfo *addr = addrs;
    int error = 0;
    WSASetLastError(0);
    while (addr != 0) {
        if ((::connect(socket, addr->ai_addr, addr->ai_addrlen) == 0) ||
            (WSAGetLastError() == WSAEWOULDBLOCK))
            break;
        // Error... save this error code and see if there are other address
        // to try before throwing the exception.
        error = WSAGetLastError();
        addr = addr->ai_next;
    }
    freeaddrinfo(addrs);
    if (error)
        throw qpid::Exception(QPID_MSG(strError(error) << ": " << connectname));
}

void
Socket::close() const
{
    SOCKET& socket = impl->fd;
    if (socket == INVALID_SOCKET) return;
    QPID_WINSOCK_CHECK(closesocket(socket));
    socket = INVALID_SOCKET;
}


int Socket::write(const void *buf, size_t count) const
{
    const SOCKET& socket = impl->fd;
    int sent = ::send(socket, (const char *)buf, count, 0);
    if (sent == SOCKET_ERROR)
        return -1;
    return sent;
}

int Socket::read(void *buf, size_t count) const
{
    const SOCKET& socket = impl->fd;
    int received = ::recv(socket, (char *)buf, count, 0);
    if (received == SOCKET_ERROR)
        return -1;
    return received;
}

int Socket::listen(uint16_t port, int backlog) const
{
    const SOCKET& socket = impl->fd;
    BOOL yes=1;
    QPID_WINSOCK_CHECK(setsockopt(socket, SOL_SOCKET, SO_REUSEADDR, (char *)&yes, sizeof(yes)));
    struct sockaddr_in name;
    memset(&name, 0, sizeof(name));
    name.sin_family = AF_INET;
    name.sin_port = htons(port);
    name.sin_addr.s_addr = 0;
    if (::bind(socket, (struct sockaddr*)&name, sizeof(name)) == SOCKET_ERROR)
        throw Exception(QPID_MSG("Can't bind to port " << port << ": " << strError(WSAGetLastError())));
    if (::listen(socket, backlog) == SOCKET_ERROR)
        throw Exception(QPID_MSG("Can't listen on port " << port << ": " << strError(WSAGetLastError())));
    
    socklen_t namelen = sizeof(name);
    QPID_WINSOCK_CHECK(::getsockname(socket, (struct sockaddr*)&name, &namelen));
    return ntohs(name.sin_port);
}

Socket* Socket::accept(struct sockaddr *addr, socklen_t *addrlen) const
{
    SOCKET afd = ::accept(impl->fd, addr, addrlen);
    if (afd != INVALID_SOCKET)
        return new Socket(new IOHandlePrivate(afd));
    else if (WSAGetLastError() == EAGAIN)
        return 0;
    else throw QPID_WINDOWS_ERROR(WSAGetLastError());
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
    return atoi(getService(impl->fd, true).c_str());
}

uint16_t Socket::getRemotePort() const
{
    return atoi(getService(impl->fd, true).c_str());
}

int Socket::getError() const
{
    int       result;
    socklen_t rSize = sizeof (result);

    QPID_WINSOCK_CHECK(::getsockopt(impl->fd, SOL_SOCKET, SO_ERROR, (char *)&result, &rSize));
    return result;
}

void Socket::setTcpNoDelay(bool nodelay) const
{
    if (nodelay) {
        int flag = 1;
        int result = setsockopt(impl->fd,
                                IPPROTO_TCP,
                                TCP_NODELAY,
                                (char *)&flag,
                                sizeof(flag));
        QPID_WINSOCK_CHECK(result);
    }
}

}} // namespace qpid::sys
