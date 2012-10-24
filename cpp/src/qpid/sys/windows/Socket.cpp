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

#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/windows/check.h"
#include "qpid/sys/windows/IoHandlePrivate.h"
#include "qpid/sys/SystemInfo.h"

// Ensure we get all of winsock2.h
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0501
#endif

#include <winsock2.h>

namespace qpid {
namespace sys {

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
        if (SystemInfo::threadSafeShutdown())
            WSACleanup();
    }

public:
    int error(void) const { return err; }

protected:
    DWORD err;
};

static WinSockSetup setup;

std::string getName(SOCKET fd, bool local)
{
    ::sockaddr_storage name_s; // big enough for any socket address
    ::sockaddr* name = (::sockaddr*)&name_s;
    ::socklen_t namelen = sizeof(name_s);

    if (local) {
        QPID_WINSOCK_CHECK(::getsockname(fd, name, &namelen));
    } else {
        QPID_WINSOCK_CHECK(::getpeername(fd, name, &namelen));
    }

    return SocketAddress::asString(name, namelen);
}

uint16_t getLocalPort(int fd)
{
    ::sockaddr_storage name_s; // big enough for any socket address
    ::sockaddr* name = (::sockaddr*)&name_s;
    ::socklen_t namelen = sizeof(name_s);

    QPID_WINSOCK_CHECK(::getsockname(fd, name, &namelen));

    return SocketAddress::getPort(name);
}
}  // namespace

Socket::Socket() :
    IOHandle(new IOHandlePrivate),
    nonblocking(false),
    nodelay(false)
{}

Socket::Socket(IOHandlePrivate* h) :
    IOHandle(h),
    nonblocking(false),
    nodelay(false)
{}

void Socket::createSocket(const SocketAddress& sa) const
{
    SOCKET& socket = impl->fd;
    if (socket != INVALID_SOCKET) Socket::close();

    SOCKET s = ::socket (getAddrInfo(sa).ai_family,
                         getAddrInfo(sa).ai_socktype,
                         0);
    if (s == INVALID_SOCKET) throw QPID_WINDOWS_ERROR(WSAGetLastError());
    socket = s;

    try {
        if (nonblocking) setNonblocking();
        if (nodelay) setTcpNoDelay();
    } catch (std::exception&) {
        ::closesocket(s);
        socket = INVALID_SOCKET;
        throw;
    }
}

Socket* Socket::createSameTypeSocket() const {
    SOCKET& socket = impl->fd;
    // Socket currently has no actual socket attached
    if (socket == INVALID_SOCKET)
        return new Socket;

    ::sockaddr_storage sa;
    ::socklen_t salen = sizeof(sa);
    QPID_WINSOCK_CHECK(::getsockname(socket, (::sockaddr*)&sa, &salen));
    SOCKET s = ::socket(sa.ss_family, SOCK_STREAM, 0); // Currently only work with SOCK_STREAM
    if (s == INVALID_SOCKET) throw QPID_WINDOWS_ERROR(WSAGetLastError());
    return new Socket(new IOHandlePrivate(s));
}

void Socket::setNonblocking() const {
    u_long nonblock = 1;
    QPID_WINSOCK_CHECK(ioctlsocket(impl->fd, FIONBIO, &nonblock));
}

void
Socket::connect(const SocketAddress& addr) const
{
    peername = addr.asString(false);

    createSocket(addr);

    const SOCKET& socket = impl->fd;
    int err;
    WSASetLastError(0);
    if ((::connect(socket, getAddrInfo(addr).ai_addr, getAddrInfo(addr).ai_addrlen) != 0) &&
        ((err = ::WSAGetLastError()) != WSAEWOULDBLOCK))
        throw qpid::Exception(QPID_MSG(strError(err) << ": " << peername));
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

int Socket::listen(const SocketAddress& addr, int backlog) const
{
    createSocket(addr);

    const SOCKET& socket = impl->fd;
    BOOL yes=1;
    QPID_WINSOCK_CHECK(setsockopt(socket, SOL_SOCKET, SO_REUSEADDR, (char *)&yes, sizeof(yes)));

    if (::bind(socket, getAddrInfo(addr).ai_addr, getAddrInfo(addr).ai_addrlen) == SOCKET_ERROR)
        throw Exception(QPID_MSG("Can't bind to " << addr.asString() << ": " << strError(WSAGetLastError())));
    if (::listen(socket, backlog) == SOCKET_ERROR)
        throw Exception(QPID_MSG("Can't listen on " <<addr.asString() << ": " << strError(WSAGetLastError())));

    return getLocalPort(socket);
}

Socket* Socket::accept() const
{
  SOCKET afd = ::accept(impl->fd, 0, 0);
    if (afd != INVALID_SOCKET)
        return new Socket(new IOHandlePrivate(afd));
    else if (WSAGetLastError() == EAGAIN)
        return 0;
    else throw QPID_WINDOWS_ERROR(WSAGetLastError());
}

std::string Socket::getPeerAddress() const
{
    if (peername.empty()) {
        peername = getName(impl->fd, false);
    }
    return peername;
}

std::string Socket::getLocalAddress() const
{
    if (localname.empty()) {
        localname = getName(impl->fd, true);
    }
    return localname;
}

int Socket::getError() const
{
    int       result;
    socklen_t rSize = sizeof (result);

    QPID_WINSOCK_CHECK(::getsockopt(impl->fd, SOL_SOCKET, SO_ERROR, (char *)&result, &rSize));
    return result;
}

void Socket::setTcpNoDelay() const
{
    SOCKET& socket = impl->fd;
    nodelay = true;
    if (socket != INVALID_SOCKET) {
        int flag = 1;
        int result = setsockopt(impl->fd,
                                IPPROTO_TCP,
                                TCP_NODELAY,
                                (char *)&flag,
                                sizeof(flag));
        QPID_WINSOCK_CHECK(result);
    }
}

inline IOHandlePrivate* IOHandlePrivate::getImpl(const qpid::sys::IOHandle &h)
{
    return h.impl;
}

SOCKET toSocketHandle(const Socket& s)
{
    return IOHandlePrivate::getImpl(s)->fd;
}

}} // namespace qpid::sys
