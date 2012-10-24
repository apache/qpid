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
    int& socket = impl->fd;
    if (socket != -1) Socket::close();
    int s = ::socket(getAddrInfo(sa).ai_family, getAddrInfo(sa).ai_socktype, 0);
    if (s < 0) throw QPID_POSIX_ERROR(errno);
    socket = s;

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
        throw;
    }
}

Socket* Socket::createSameTypeSocket() const {
    int& socket = impl->fd;
    // Socket currently has no actual socket attached
    if (socket == -1)
        return new Socket;

    ::sockaddr_storage sa;
    ::socklen_t salen = sizeof(sa);
    QPID_POSIX_CHECK(::getsockname(socket, (::sockaddr*)&sa, &salen));
    int s = ::socket(sa.ss_family, SOCK_STREAM, 0); // Currently only work with SOCK_STREAM
    if (s < 0) throw QPID_POSIX_ERROR(errno);
    return new Socket(new IOHandlePrivate(s));
}

void Socket::setNonblocking() const {
    int& socket = impl->fd;
    nonblocking = true;
    if (socket != -1) {
        QPID_POSIX_CHECK(::fcntl(socket, F_SETFL, O_NONBLOCK));
    }
}

void Socket::setTcpNoDelay() const
{
    int& socket = impl->fd;
    nodelay = true;
    if (socket != -1) {
        int flag = 1;
        int result = ::setsockopt(impl->fd, IPPROTO_TCP, TCP_NODELAY, (char *)&flag, sizeof(flag));
        QPID_POSIX_CHECK(result);
    }
}

void Socket::connect(const SocketAddress& addr) const
{
    // The display name for an outbound connection needs to be the name that was specified
    // for the address rather than a resolved IP address as we don't know which of
    // the IP addresses is actually the one that will be connected to.
    peername = addr.asString(false);

    // However the string we compare with the local port must be numeric or it might not
    // match when it should as getLocalAddress() will always be numeric
    std::string connectname = addr.asString();

    createSocket(addr);

    const int& socket = impl->fd;
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
    // This seems like something the OS should prevent but I have
    // confirmed that sporadic hangs in
    // cluster_tests.LongTests.test_failover on RHEL5 are caused by
    // such a circular connection.
    //
    // Raise an error if we see such a connection, since we know there is
    // no listener on the peer address.
    //
    if (getLocalAddress() == connectname) {
        close();
        throw Exception(QPID_MSG("Connection refused: " << peername));
    }
}

void
Socket::close() const
{
    int& socket = impl->fd;
    if (socket == -1) return;
    if (::close(socket) < 0) throw QPID_POSIX_ERROR(errno);
    socket = -1;
}

int Socket::listen(const SocketAddress& sa, int backlog) const
{
    createSocket(sa);

    const int& socket = impl->fd;
    int yes=1;
    QPID_POSIX_CHECK(::setsockopt(socket,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes)));

    if (::bind(socket, getAddrInfo(sa).ai_addr, getAddrInfo(sa).ai_addrlen) < 0)
        throw Exception(QPID_MSG("Can't bind to port " << sa.asString() << ": " << strError(errno)));
    if (::listen(socket, backlog) < 0)
        throw Exception(QPID_MSG("Can't listen on port " << sa.asString() << ": " << strError(errno)));

    return getLocalPort(socket);
}

Socket* Socket::accept() const
{
    int afd = ::accept(impl->fd, 0, 0);
    if ( afd >= 0) {
        Socket* s = new Socket(new IOHandlePrivate(afd));
        s->localname = localname;
        return s;
    }
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

    if (::getsockopt(impl->fd, SOL_SOCKET, SO_ERROR, &result, &rSize) < 0)
        throw QPID_POSIX_ERROR(errno);

    return result;
}

}} // namespace qpid::sys
