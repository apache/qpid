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

#include "qpid/sys/posix/BSDSocket.h"

#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/posix/check.h"
#include "qpid/sys/posix/PrivatePosix.h"

#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/errno.h>
#include <unistd.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <netdb.h>
#include <cstdlib>
#include <sstream>
#include <string.h>

namespace qpid {
namespace sys {

namespace {
std::string getName(int fd, bool local)
{
    ::sockaddr_storage name_s; // big enough for any socket address
    ::sockaddr* name = (::sockaddr*)&name_s;
    ::socklen_t namelen = sizeof(name_s);

    if (local) {
        QPID_POSIX_CHECK( ::getsockname(fd, name, &namelen) );
    } else {
        QPID_POSIX_CHECK( ::getpeername(fd, name, &namelen) );
    }

    return SocketAddress::asString(name, namelen);
}

uint16_t getLocalPort(int fd)
{
    ::sockaddr_storage name_s; // big enough for any socket address
    ::sockaddr* name = (::sockaddr*)&name_s;
    ::socklen_t namelen = sizeof(name_s);

    QPID_POSIX_CHECK( ::getsockname(fd, name, &namelen) );

    return SocketAddress::getPort(name);
}
}

BSDSocket::BSDSocket() :
    handle(new IOHandle),
    fd(-1),
    lastErrorCode(0),
    nonblocking(false),
    nodelay(false)
{}

Socket* createSocket()
{
    return new BSDSocket;
}

BSDSocket::BSDSocket(int fd0) :
    handle(new IOHandle(fd0)),
    fd(fd0),
    lastErrorCode(0),
    nonblocking(false),
    nodelay(false)
{}

BSDSocket::~BSDSocket()
{}

BSDSocket::operator const IOHandle&() const
{
    return *handle;
}

void BSDSocket::createSocket(const SocketAddress& sa) const
{
    int& socket = fd;
    if (socket != -1) BSDSocket::close();
    int s = ::socket(getAddrInfo(sa).ai_family, getAddrInfo(sa).ai_socktype, 0);
    if (s < 0) throw QPID_POSIX_ERROR(errno);
    socket = s;
    *handle = IOHandle(s);

    try {
        if (nonblocking) setNonblocking();
        if (nodelay) setTcpNoDelay();
        if (getAddrInfo(sa).ai_family == AF_INET6) {
            int flag = 1;
            int result = ::setsockopt(socket, IPPROTO_IPV6, IPV6_V6ONLY, (char *)&flag, sizeof(flag));
            QPID_POSIX_CHECK(result);
        }
    } catch (std::exception&) {
        ::close(s);
        socket = -1;
        *handle = IOHandle();
        throw;
    }
}

void BSDSocket::setNonblocking() const {
    int& socket = fd;
    nonblocking = true;
    if (socket != -1) {
        QPID_POSIX_CHECK(::fcntl(socket, F_SETFL, O_NONBLOCK));
    }
}

void BSDSocket::setTcpNoDelay() const
{
    int& socket = fd;
    nodelay = true;
    if (socket != -1) {
        int flag = 1;
        int result = ::setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, (char *)&flag, sizeof(flag));
        QPID_POSIX_CHECK(result);
    }
}

void BSDSocket::connect(const SocketAddress& addr) const
{
    // The display name for an outbound connection needs to be the name that was specified
    // for the address rather than a resolved IP address as we don't know which of
    // the IP addresses is actually the one that will be connected to.
    peername = addr.asString(false);

    // However the string we compare with the local port must be numeric or it might not
    // match when it should as getLocalAddress() will always be numeric
    std::string connectname = addr.asString();

    createSocket(addr);

    const int& socket = fd;
    // TODO the correct thing to do here is loop on failure until you've used all the returned addresses
    if ((::connect(socket, getAddrInfo(addr).ai_addr, getAddrInfo(addr).ai_addrlen) < 0) &&
        (errno != EINPROGRESS)) {
        throw Exception(QPID_MSG(strError(errno) << ": " << peername));
    }
    // When connecting to a port on the same host which no longer has
    // a process associated with it, the OS occasionally chooses the
    // remote port (which is unoccupied) as the port to bind the local
    // end of the socket, resulting in a "circular" connection.
    //
    // Raise an error if we see such a connection, since we know there is
    // no listener on the peer address.
    //
    if (getLocalAddress() == connectname) {
        close();
        throw Exception(QPID_MSG("Connection refused: " << peername));
    }
}

void BSDSocket::finishConnect(const SocketAddress&) const
{
}

void
BSDSocket::close() const
{
    int& socket = fd;
    if (socket == -1) return;
    if (::close(socket) < 0) throw QPID_POSIX_ERROR(errno);
    socket = -1;
    *handle = IOHandle();
}

int BSDSocket::listen(const SocketAddress& sa, int backlog) const
{
    createSocket(sa);

    const int& socket = fd;
    int yes=1;
    QPID_POSIX_CHECK(::setsockopt(socket,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes)));

    if (::bind(socket, getAddrInfo(sa).ai_addr, getAddrInfo(sa).ai_addrlen) < 0)
        throw Exception(QPID_MSG("Can't bind to port " << sa.asString() << ": " << strError(errno)));
    if (::listen(socket, backlog) < 0)
        throw Exception(QPID_MSG("Can't listen on port " << sa.asString() << ": " << strError(errno)));

    return getLocalPort(socket);
}

Socket* BSDSocket::accept() const
{
    int afd = ::accept(fd, 0, 0);
    if ( afd >= 0) {
        BSDSocket* s = new BSDSocket(afd);
        s->localname = localname;
        return s;
    }
    else if (errno == EAGAIN)
        return 0;
    else throw QPID_POSIX_ERROR(errno);
}

std::string BSDSocket::lastErrorCodeText() const
{
    std::stringstream s;
    s << strError(lastErrorCode);
    s << "(" << lastErrorCode << ")";
    return s.str();
}

int BSDSocket::read(void *buf, size_t count) const
{
    int rc = ::read(fd, buf, count);
    lastErrorCode = errno;
    return rc;
}

int BSDSocket::write(const void *buf, size_t count) const
{
    int rc = ::write(fd, buf, count);
    lastErrorCode = errno;
    return rc;
}

std::string BSDSocket::getPeerAddress() const
{
    if (peername.empty()) {
        peername = getName(fd, false);
    }
    return peername;
}

std::string BSDSocket::getLocalAddress() const
{
    if (localname.empty()) {
        localname = getName(fd, true);
    }
    return localname;
}

int BSDSocket::getError() const
{
    int       result;
    socklen_t rSize = sizeof (result);

    if (::getsockopt(fd, SOL_SOCKET, SO_ERROR, &result, &rSize) < 0)
        throw QPID_POSIX_ERROR(errno);

    return result;
}

int BSDSocket::getKeyLen() const
{
    return 0;
}

std::string BSDSocket::getPeerAuthId() const
{
    return std::string();
}

std::string BSDSocket::getLocalAuthId() const
{
    return "dummy";
}

}} // namespace qpid::sys
