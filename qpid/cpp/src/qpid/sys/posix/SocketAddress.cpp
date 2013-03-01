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

#include "qpid/sys/SocketAddress.h"

#include "qpid/Exception.h"
#include "qpid/Msg.h"

#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>

namespace qpid {
namespace sys {

SocketAddress::SocketAddress(const std::string& host0, const std::string& port0) :
    host(host0),
    port(port0),
    addrInfo(0),
    currentAddrInfo(0)
{
}

SocketAddress::SocketAddress(const SocketAddress& sa) :
    host(sa.host),
    port(sa.port),
    addrInfo(0),
    currentAddrInfo(0)
{
}

SocketAddress& SocketAddress::operator=(const SocketAddress& sa)
{
    SocketAddress temp(sa);

    std::swap(temp, *this);
    return *this;
}

SocketAddress::~SocketAddress()
{
    if (addrInfo) {
        ::freeaddrinfo(addrInfo);
    }
}

std::string SocketAddress::asString(::sockaddr const * const addr, size_t addrlen)
{
    char servName[NI_MAXSERV];
    char dispName[NI_MAXHOST];
    if (int rc=::getnameinfo(addr, addrlen,
        dispName, sizeof(dispName),
                             servName, sizeof(servName),
                             NI_NUMERICHOST | NI_NUMERICSERV) != 0)
        throw qpid::Exception(QPID_MSG(gai_strerror(rc)));
    std::string s;
    switch (addr->sa_family) {
        case AF_INET: s += dispName; break;
        case AF_INET6: s += "["; s += dispName; s+= "]"; break;
        case AF_UNIX: s += "UNIX:"; break;
        default: throw Exception(QPID_MSG("Unexpected socket type"));
    }
    s += ":";
    s += servName;
    return s;
}

uint16_t SocketAddress::getPort(::sockaddr const * const addr)
{
    switch (addr->sa_family) {
        case AF_INET: return ntohs(((const ::sockaddr_in*)(const void*)addr)->sin_port);
        case AF_INET6: return ntohs(((const ::sockaddr_in6*)(const void*)addr)->sin6_port);
        default:throw Exception(QPID_MSG("Unexpected socket type"));
    }
}

std::string SocketAddress::asString(bool numeric) const
{
    if (!numeric)
        return host + ":" + port;
    // Canonicalise into numeric id
    const ::addrinfo& ai = getAddrInfo(*this);

    return asString(ai.ai_addr, ai.ai_addrlen);
}

std::string SocketAddress::getHost() const
{
    return host;
}

bool SocketAddress::nextAddress() {
    bool r = currentAddrInfo->ai_next != 0;
    if (r)
        currentAddrInfo = currentAddrInfo->ai_next;
    return r;
}

void SocketAddress::setAddrInfoPort(uint16_t port) {
    if (!currentAddrInfo) return;

    ::addrinfo& ai = *currentAddrInfo;
    switch (ai.ai_family) {
    case AF_INET: ((::sockaddr_in*)(void*)ai.ai_addr)->sin_port = htons(port); return;
    case AF_INET6:((::sockaddr_in6*)(void*)ai.ai_addr)->sin6_port = htons(port); return;
    default: throw Exception(QPID_MSG("Unexpected socket type"));
    }
}

const ::addrinfo& getAddrInfo(const SocketAddress& sa)
{
    if (!sa.addrInfo) {
        ::addrinfo hints;
        ::memset(&hints, 0, sizeof(hints));
        hints.ai_flags = AI_ADDRCONFIG; // Only use protocols that we have configured interfaces for
        hints.ai_family = AF_UNSPEC; // Allow both IPv4 and IPv6
        hints.ai_socktype = SOCK_STREAM;

        const char* node = 0;
        if (sa.host.empty()) {
            hints.ai_flags |= AI_PASSIVE;
        } else {
            node = sa.host.c_str();
        }
        const char* service = sa.port.empty() ? "0" : sa.port.c_str();

        int n = ::getaddrinfo(node, service, &hints, &sa.addrInfo);
        if (n != 0)
            throw Exception(QPID_MSG("Cannot resolve " << sa.asString(false) << ": " << ::gai_strerror(n)));
        sa.currentAddrInfo = sa.addrInfo;
    }

    return *sa.currentAddrInfo;
}

}}
