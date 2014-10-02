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
#include "qpid/log/Logger.h"

#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>
#include <arpa/inet.h>
#include <iosfwd>

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

std::string SocketAddress::asString(::sockaddr const * const addr, size_t addrlen, bool dispNameOnly, bool hideDecoration)
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
        case AF_INET6:
            if (!hideDecoration) {
                s += "["; s += dispName; s+= "]";
            } else {
                s += dispName;
            }
            break;
        case AF_UNIX: s += "UNIX:"; break;
        default: throw Exception(QPID_MSG("Unexpected socket type"));
    }
    if (!dispNameOnly) {
        s += ":";
        s += servName;
    }
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

std::string SocketAddress::asString(bool numeric, bool dispNameOnly, bool hideDecoration) const
{
    if (!numeric)
        return host + ":" + port;
    // Canonicalise into numeric id
    const ::addrinfo& ai = getAddrInfo(*this);

    return asString(ai.ai_addr, ai.ai_addrlen, dispNameOnly, hideDecoration);
}

std::string SocketAddress::getHost() const
{
    return host;
}

/**
 * Return true if this SocketAddress is IPv4 or IPv6
 */
bool SocketAddress::isIp() const
{
    const ::addrinfo& ai = getAddrInfo(*this);
    return ai.ai_family == AF_INET || ai.ai_family == AF_INET6;
}

/**
 * this represents the low address of an ACL address range.
 * Given rangeHi that represents the high address,
 * return a string showing the numeric comparisons that the
 * inRange checks will do for address pair.
 */
std::string SocketAddress::comparisonDetails(const SocketAddress& rangeHi) const
{
    std::ostringstream os;
    SocketAddress thisSa(*this);
    SocketAddress rangeHiSa(rangeHi);
    (void) getAddrInfo(thisSa);
    (void) getAddrInfo(rangeHiSa);
    os << "(" << thisSa.asString(true, true, false) <<
          "," << rangeHiSa.asString(true, true, false) << ")";
    while (thisSa.nextAddress()) {
        if (!rangeHiSa.nextAddress()) {
            throw(Exception(QPID_MSG("Comparison iteration fails: " + (*this).asString() +
                                      rangeHi.asString())));
        }
        os << ",(" << thisSa.asString(true, true, false) <<
            "," << rangeHiSa.asString(true, true, false) << ")";
    }
    if (rangeHiSa.nextAddress()) {
        throw(Exception(QPID_MSG("Comparison iteration fails: " + (*this).asString() +
                                    rangeHi.asString())));
    }
    std::string result = os.str();
    return result;
}

/**
 * For ACL address matching make sure that the two addresses, *this
 * which is the low address and hiPeer which is the high address, are
 * both numeric ip addresses of the same family and that hi > *this.
 *
 * Note that if the addresses resolve to more than one struct addrinfo
 * then this and the hiPeer must be equal. This avoids having to do
 * difficult range checks where the this and hiPeer both resolve to
 * multiple IPv4 or IPv6 addresses.
 *
 * This check is run at acl file load time and not at run tme.
 */
bool SocketAddress::isComparable(const SocketAddress& hiPeer) const {
    try {
        // May only compare if this socket is IPv4 or IPv6
        SocketAddress lo(*this);
        const ::addrinfo& peerLoInfo = getAddrInfo(lo);
        if (!(peerLoInfo.ai_family == AF_INET || peerLoInfo.ai_family == AF_INET6)) {
            return false;
        }
        try {
            // May only compare if peer socket is same family
            SocketAddress hi(hiPeer);
            const ::addrinfo& peerHiInfo = getAddrInfo(hi);
            if (peerLoInfo.ai_family != peerHiInfo.ai_family) {
                return false;
            }
            // Host names that resolve to lists are allowed if they are equal.
            // For example: localhost, or fjord.lab.example.com
            if ((*this).asString() == hiPeer.asString()) {
                return true;
            }
            // May only compare if this and peer resolve to single address.
            if (lo.nextAddress() || hi.nextAddress()) {
                return false;
            }
            // Make sure that the lo/hi relationship is ok
            int res;
            if (!compareAddresses(peerLoInfo, peerHiInfo, res) || res < 0) {
                return false;
            }
            return true;
        } catch (Exception) {
            // failed to resolve hi
            return false;
        }
    } catch (Exception) {
        // failed to resolve lo
        return false;
    }
}

/**
 * *this SocketAddress was created from the numeric IP address of a
 *  connecting host.
 * The lo and hi addresses are the limit checks from the ACL file.
 * Return true if this address is in range of any of the address pairs
 * in the limit check range.
 *
 * This check is executed on every incoming connection.
 */
bool SocketAddress::inRange(const SocketAddress& lo,
                            const SocketAddress& hi) const
{
    (*this).firstAddress();
    lo.firstAddress();
    hi.firstAddress();
    const ::addrinfo& thisInfo = getAddrInfo(*this);
    const ::addrinfo& loInfo   = getAddrInfo(lo);
    const ::addrinfo& hiInfo   = getAddrInfo(hi);
    if (inRange(thisInfo, loInfo, hiInfo)) {
        return true;
    }
    while (lo.nextAddress()) {
        if (!hi.nextAddress()) {
            assert (false);
            throw(Exception(QPID_MSG("Comparison iteration fails: " +
                                     lo.asString() + hi.asString())));
        }
        const ::addrinfo& loInfo = getAddrInfo(lo);
        const ::addrinfo& hiInfo = getAddrInfo(hi);
        if (inRange(thisInfo, loInfo, hiInfo)) {
            return true;
        }
    }
    return false;
}

/**
 * *this SocketAddress was created from the numeric IP address of a
 *  connecting host.
 * The lo and hi addresses are one binary address pair from a range
 * given in an ACL file.
 * Return true if this binary address is '>= lo' and '<= hi'.
 */
bool SocketAddress::inRange(const ::addrinfo& thisInfo,
                            const ::addrinfo& lo,
                            const ::addrinfo& hi) const
{
    int resLo;
    int resHi;
    if (!compareAddresses(lo, thisInfo, resLo)) {
        return false;
    }
    if (!compareAddresses(hi, thisInfo, resHi)) {
        return false;
    }
    if (resLo < 0) {
        return false;
    }
    if (resHi > 0) {
        return false;
    }
    return true;
}

/**
 * Compare this address against two binary low/high addresses.
 * return true with result holding the comparison.
 */
bool SocketAddress::compareAddresses(const struct addrinfo& lo,
                                     const struct addrinfo& hi,
                                     int& result) const
{
    if (lo.ai_family != hi.ai_family) {
        return false;
    }
    if (lo.ai_family == AF_INET) {
        void* taddr;

        taddr = (void*)lo.ai_addr;
        struct sockaddr_in* sin4lo = (struct sockaddr_in*)taddr;
        taddr = (void*)hi.ai_addr;
        struct sockaddr_in* sin4hi = (struct sockaddr_in*)taddr;
        result = memcmp(&sin4hi->sin_addr, &sin4lo->sin_addr, sizeof(in_addr));
    } else if (lo.ai_family == AF_INET6) {
        void* taddr;

        taddr = (void*)lo.ai_addr;
        struct sockaddr_in6* sin6lo = (struct sockaddr_in6*)taddr;
        taddr = (void*)hi.ai_addr;
        struct sockaddr_in6* sin6hi = (struct sockaddr_in6*)taddr;
        result = memcmp(&sin6hi->sin6_addr, &sin6lo->sin6_addr, sizeof(in6_addr));
    } else {
        assert (false);
        return false;
    }
    return true;
}

void SocketAddress::firstAddress() const {
    if (addrInfo) {
        currentAddrInfo = addrInfo;
    } else {
        (void) getAddrInfo(*this);
    }
}

bool SocketAddress::nextAddress() const {
    bool r = currentAddrInfo->ai_next != 0;
    if (r)
        currentAddrInfo = currentAddrInfo->ai_next;
    return r;
}

const ::addrinfo& getAddrInfo(const SocketAddress& sa)
{
    if (!sa.addrInfo) {
        ::addrinfo hints;
        ::memset(&hints, 0, sizeof(hints));
        hints.ai_family = AF_UNSPEC; // Allow both IPv4 and IPv6
        hints.ai_socktype = SOCK_STREAM;

        const char* node = 0;
        if (sa.host.empty()) {
            hints.ai_flags = AI_PASSIVE;
        } else {
            hints.ai_flags = AI_ADDRCONFIG; // Only use protocols that we have configured interfaces for
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
