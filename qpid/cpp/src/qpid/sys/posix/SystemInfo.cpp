/*
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

#include "qpid/log/Statement.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/posix/check.h"
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <sys/utsname.h>
#include <sys/types.h> // For FreeBSD
#include <sys/socket.h> // For FreeBSD
#include <netinet/in.h> // For FreeBSD
#include <ifaddrs.h>
#include <unistd.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <map>
#include <netdb.h>
#include <string.h>

#ifndef HOST_NAME_MAX
#  define HOST_NAME_MAX 256
#endif

using namespace std;

namespace qpid {
namespace sys {

long  SystemInfo::concurrency() {
#ifdef _SC_NPROCESSORS_ONLN    // Linux specific.
    return sysconf(_SC_NPROCESSORS_ONLN);
#else
    return -1;
#endif
}

bool SystemInfo::getLocalHostname (Address &address) {
    char name[HOST_NAME_MAX];
    if (::gethostname(name, sizeof(name)) != 0)
        return false;
    address.host = name;
    return true;
}

static const string LOOPBACK("127.0.0.1");
static const string TCP("tcp");

// Test IPv4 address for loopback
inline bool IN_IS_ADDR_LOOPBACK(const ::in_addr* a) {
    return ((ntohl(a->s_addr) & 0xff000000) == 0x7f000000);
}

inline bool isLoopback(const ::sockaddr* addr) {
    switch (addr->sa_family) {
        case AF_INET: return IN_IS_ADDR_LOOPBACK(&((const ::sockaddr_in*)(const void*)addr)->sin_addr);
        case AF_INET6: return IN6_IS_ADDR_LOOPBACK(&((const ::sockaddr_in6*)(const void*)addr)->sin6_addr);
        default: return false;
    }
}

namespace {
    inline socklen_t sa_len(::sockaddr* sa)
    {
        switch (sa->sa_family) {
            case AF_INET:
                return sizeof(struct sockaddr_in);
            case AF_INET6:
                return sizeof(struct sockaddr_in6);
            default:
                return sizeof(struct sockaddr_storage);
        }
    }

    inline bool isInetOrInet6(::sockaddr* sa) {
        switch (sa->sa_family) {
            case AF_INET:
            case AF_INET6:
                return true;
            default:
                return false;
        }
    }
    typedef std::map<std::string, std::vector<std::string> > InterfaceInfo;
    std::map<std::string, std::vector<std::string> > cachedInterfaces;

    void cacheInterfaceInfo() {
        // Get interface info
        ::ifaddrs* interfaceInfo;
        QPID_POSIX_CHECK( ::getifaddrs(&interfaceInfo) );

        char name[NI_MAXHOST];
        for (::ifaddrs* info = interfaceInfo; info != 0; info = info->ifa_next) {

            // Only use IPv4/IPv6 interfaces
            if (!info->ifa_addr || !isInetOrInet6(info->ifa_addr)) continue;

            int rc=::getnameinfo(info->ifa_addr, sa_len(info->ifa_addr),
                                 name, sizeof(name), 0, 0,
                                 NI_NUMERICHOST);
            if (rc >= 0) {
                std::string address(name);
                cachedInterfaces[info->ifa_name].push_back(address);
            } else {
                throw qpid::Exception(QPID_MSG(gai_strerror(rc)));
            }
        }
        ::freeifaddrs(interfaceInfo);
    }
}

bool SystemInfo::getInterfaceAddresses(const std::string& interface, std::vector<std::string>& addresses) {
    if ( cachedInterfaces.empty() ) cacheInterfaceInfo();
    InterfaceInfo::iterator i = cachedInterfaces.find(interface);
    if ( i==cachedInterfaces.end() ) return false;
    std::copy(i->second.begin(), i->second.end(), std::back_inserter(addresses));
    return true;
}

void SystemInfo::getInterfaceNames(std::vector<std::string>& names ) {
    if ( cachedInterfaces.empty() ) cacheInterfaceInfo();

    for (InterfaceInfo::const_iterator i = cachedInterfaces.begin(); i!=cachedInterfaces.end(); ++i) {
        names.push_back(i->first);
    }
}

void SystemInfo::getSystemId (std::string &osName,
                              std::string &nodeName,
                              std::string &release,
                              std::string &version,
                              std::string &machine)
{
    struct utsname _uname;
    if (uname (&_uname) == 0)
    {
        osName = _uname.sysname;
        nodeName = _uname.nodename;
        release = _uname.release;
        version = _uname.version;
        machine = _uname.machine;
    }
}

uint32_t SystemInfo::getProcessId()
{
    return (uint32_t) ::getpid();
}

uint32_t SystemInfo::getParentProcessId()
{
    return (uint32_t) ::getppid();
}

// Linux specific (Solaris has quite different stuff in /proc)
string SystemInfo::getProcessName()
{
    string value;

    ifstream input("/proc/self/status");
    if (input.good()) {
        while (!input.eof()) {
            string key;
            input >> key;
            if (key == "Name:") {
                input >> value;
                break;
            }
        }
        input.close();
    }

    return value;
}

// Always true.  Only Windows has exception cases.
bool SystemInfo::threadSafeShutdown()
{
    return true;
}


}} // namespace qpid::sys
