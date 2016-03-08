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

#include "qpid/sys/windows/WinSocket.h"

#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/windows/check.h"
#include "qpid/sys/windows/IoHandlePrivate.h"
#include "qpid/sys/SystemInfo.h"

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

WinSocket::WinSocket() :
    handle(new IOHandle),
    nonblocking(false),
    nodelay(false)
{}

Socket* createSocket()
{
    return new WinSocket;
}

WinSocket::WinSocket(SOCKET fd) :
    handle(new IOHandle(fd)),
    nonblocking(false),
    nodelay(false)
{}

WinSocket::operator const IOHandle&() const
{
    return *handle;
}

void WinSocket::createSocket(const SocketAddress& sa) const
{
    SOCKET& socket = handle->fd;
    if (socket != INVALID_SOCKET) WinSocket::close();

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

void WinSocket::setNonblocking() const {
    u_long nonblock = 1;
    QPID_WINSOCK_CHECK(ioctlsocket(handle->fd, FIONBIO, &nonblock));
}

void
WinSocket::connect(const SocketAddress& addr) const
{
    peername = addr.asString(false);

    createSocket(addr);

    const SOCKET& socket = handle->fd;
    int err;
    WSASetLastError(0);
    if ((::connect(socket, getAddrInfo(addr).ai_addr, getAddrInfo(addr).ai_addrlen) != 0) &&
        ((err = ::WSAGetLastError()) != WSAEWOULDBLOCK))
        throw qpid::Exception(QPID_MSG(strError(err) << ": " << peername));
}

void
WinSocket::finishConnect(const SocketAddress&) const
{
}

void
WinSocket::close() const
{
    SOCKET& socket = handle->fd;
    if (socket == INVALID_SOCKET) return;
    QPID_WINSOCK_CHECK(closesocket(socket));
    socket = INVALID_SOCKET;
}


int WinSocket::write(const void *buf, size_t count) const
{
    const SOCKET& socket = handle->fd;
    int sent = ::send(socket, (const char *)buf, count, 0);
    if (sent == SOCKET_ERROR)
        return -1;
    return sent;
}

int WinSocket::read(void *buf, size_t count) const
{
    const SOCKET& socket = handle->fd;
    int received = ::recv(socket, (char *)buf, count, 0);
    if (received == SOCKET_ERROR)
        return -1;
    return received;
}

int WinSocket::listen(const SocketAddress& addr, int backlog) const
{
    createSocket(addr);

    const SOCKET& socket = handle->fd;
    BOOL yes=1;
    QPID_WINSOCK_CHECK(setsockopt(socket, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, (char *)&yes, sizeof(yes)));

    if (::bind(socket, getAddrInfo(addr).ai_addr, getAddrInfo(addr).ai_addrlen) == SOCKET_ERROR)
        throw Exception(QPID_MSG("Can't bind to " << addr.asString() << ": " << strError(WSAGetLastError())));
    if (::listen(socket, backlog) == SOCKET_ERROR)
        throw Exception(QPID_MSG("Can't listen on " <<addr.asString() << ": " << strError(WSAGetLastError())));

    return getLocalPort(socket);
}

Socket* WinSocket::accept() const
{
  SOCKET afd = ::accept(handle->fd, 0, 0);
    if (afd != INVALID_SOCKET)
        return new WinSocket(afd);
    else if (WSAGetLastError() == EAGAIN)
        return 0;
    else throw QPID_WINDOWS_ERROR(WSAGetLastError());
}

std::string WinSocket::getPeerAddress() const
{
    if (peername.empty()) {
        peername = getName(handle->fd, false);
    }
    return peername;
}

std::string WinSocket::getLocalAddress() const
{
    if (localname.empty()) {
        localname = getName(handle->fd, true);
    }
    return localname;
}

int WinSocket::getError() const
{
    int       result;
    socklen_t rSize = sizeof (result);

    QPID_WINSOCK_CHECK(::getsockopt(handle->fd, SOL_SOCKET, SO_ERROR, (char *)&result, &rSize));
    return result;
}

// TODO: I don't think this can ever be called!
std::string WinSocket::lastErrorCodeText() const
{
    return strError(::WSAGetLastError());
}
void WinSocket::setTcpNoDelay() const
{
    SOCKET& socket = handle->fd;
    nodelay = true;
    if (socket != INVALID_SOCKET) {
        int flag = 1;
        int result = setsockopt(handle->fd,
                                IPPROTO_TCP,
                                TCP_NODELAY,
                                (char *)&flag,
                                sizeof(flag));
        QPID_WINSOCK_CHECK(result);
    }
}

int WinSocket::getKeyLen() const
{
    return 0;
}

std::string WinSocket::getPeerAuthId() const
{
    return std::string();
}

std::string WinSocket::getLocalAuthId() const
{
    return "dummy";
}

}} // namespace qpid::sys
