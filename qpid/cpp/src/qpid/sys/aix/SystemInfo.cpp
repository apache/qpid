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
#include "qpid/sys/posix/PrivatePosix.h"
#include <procinfo.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <unistd.h>
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
    return sysconf(_SC_NPROCESSORS_ONLN);
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
    class HandleCloser : public IOHandle {
    public:
      HandleCloser(int fd) : IOHandle(fd) {}
      ~HandleCloser() { ::close(fd); fd = -1; }
    };

    inline bool isInetOrInet6(::sockaddr* sa) {
        switch (sa->sa_family) {
            case AF_INET:
            case AF_INET6:
                return true;
            default:
                return false;
        }
    }

    inline void *InetAddr(::sockaddr* sa) {
        switch (sa->sa_family) {
            case AF_INET:
                return &(reinterpret_cast<struct sockaddr_in *>(sa)->sin_addr);
            case AF_INET6:
                return &(reinterpret_cast<struct sockaddr_in6 *>(sa)->sin6_addr);
            default:
                return 0;
        }
    }

    typedef std::map<std::string, std::vector<std::string> > InterfaceInfo;
    std::map<std::string, std::vector<std::string> > cachedInterfaces;

    void cacheInterfaceInfo() {
        int status = 0;
        int handle = ::socket (PF_INET, SOCK_DGRAM, 0);
        QPID_POSIX_CHECK(handle);
        HandleCloser h(handle);

        size_t num_ifs = 0;
        struct ifconf ifc;
        status = ::ioctl(handle, SIOCGSIZIFCONF, (caddr_t)&ifc.ifc_len);
        QPID_POSIX_CHECK(status);

        std::auto_ptr<char> auto_ifc_buf(new char[ifc.ifc_len]);
        memset (auto_ifc_buf.get(), 0, ifc.ifc_len);

        status = ::ioctl(handle, SIOCGIFCONF, (caddr_t)auto_ifc_buf.get());
        QPID_POSIX_CHECK(status);

        char *buf_start = auto_ifc_buf.get();
        char *buf_end = buf_start + ifc.ifc_len;

        for (char *ptr = buf_start; ptr < buf_end; ) {
            struct ifreq *req = reinterpret_cast<struct ifreq *>(ptr);
	    ptr += IFNAMSIZ;
	    ptr += req->ifr_addr.sa_len;
	    if (!strcmp("lo0", req->ifr_name) || !isInetOrInet6(&req->ifr_addr))
                continue;
	    char dots[INET6_ADDRSTRLEN];
            if (! ::inet_ntop(req->ifr_addr.sa_family, InetAddr(&req->ifr_addr), dots, sizeof(dots)))
                throw QPID_POSIX_ERROR(errno);
            std::string address(dots);
            cachedInterfaces[req->ifr_name].push_back(address);
        }
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

// AIX specific
string SystemInfo::getProcessName()
{
    struct procsinfo my_info;
    pid_t my_pid = getpid();
    int status = getprocs(&my_info, sizeof(my_info), 0, 0, &my_pid, 1);
    QPID_POSIX_CHECK(status);
    std::string my_name(my_info.pi_comm);
    return my_name;
}

// Always true.  Only Windows has exception cases.
bool SystemInfo::threadSafeShutdown()
{
    return true;
}


}} // namespace qpid::sys
