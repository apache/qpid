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

#include "qpid/sys/SystemInfo.h"

#include "qpid/sys/posix/check.h"

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
#include <netdb.h>

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

static const string LOCALHOST("127.0.0.1");
static const string TCP("tcp");

void SystemInfo::getLocalIpAddresses (uint16_t port,
                                      std::vector<Address> &addrList) {
    ::ifaddrs* ifaddr = 0;
    QPID_POSIX_CHECK(::getifaddrs(&ifaddr));
    for (::ifaddrs* ifap = ifaddr; ifap != 0; ifap = ifap->ifa_next) {
        if (ifap->ifa_addr == 0) continue;

        int family = ifap->ifa_addr->sa_family;
        switch (family) {
        case AF_INET: {
            char dispName[NI_MAXHOST];
            int rc = ::getnameinfo(
                        ifap->ifa_addr,
                        (family == AF_INET)
                            ? sizeof(struct sockaddr_in)
                            : sizeof(struct sockaddr_in6),
                        dispName, sizeof(dispName),
                        0, 0, NI_NUMERICHOST);
            if (rc != 0) {
                throw QPID_POSIX_ERROR(rc);
            }
            string addr(dispName);
            if (addr != LOCALHOST) {
                addrList.push_back(Address(TCP, addr, port));
            }
            break;
        }
        // TODO: Url parsing currently can't cope with IPv6 addresses so don't return them
        // when it can cope move this line to above "case AF_INET:"
        case AF_INET6:
        default:
            continue;
        }
    }
    freeifaddrs(ifaddr);

    if (addrList.empty()) {
        addrList.push_back(Address(TCP, LOCALHOST, port));
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

}} // namespace qpid::sys
