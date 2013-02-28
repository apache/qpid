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

/* GetNativeSystemInfo call requires _WIN32_WINNT 0x0501 or higher */
#ifndef _WIN32_WINNT
#  define _WIN32_WINNT 0x0501
#endif

#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"

#include <assert.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <tlhelp32.h>

#ifndef HOST_NAME_MAX
#  define HOST_NAME_MAX 256
#endif

namespace qpid {
namespace sys {

long  SystemInfo::concurrency() {
    SYSTEM_INFO sys_info;
    ::GetSystemInfo (&sys_info);
    long activeProcessors = 0;
    DWORD_PTR mask = sys_info.dwActiveProcessorMask;
    while (mask != 0) {
        if (mask & 1)
            ++activeProcessors;
        mask >>= 1;
    }
    return activeProcessors;
}

bool SystemInfo::getLocalHostname (Address &address) {
    char name[HOST_NAME_MAX];
    if (::gethostname(name, sizeof(name)) != 0) {
        errno = WSAGetLastError();
        return false;
    }
    address.host = name;
    return true;
}

static const std::string LOCALHOST("127.0.0.1");
static const std::string TCP("tcp");

// Null function which always fails to find an network interface name
bool SystemInfo::getInterfaceAddresses(const std::string&, std::vector<std::string>&)
{
    return false;
}

void SystemInfo::getSystemId (std::string &osName,
                              std::string &nodeName,
                              std::string &release,
                              std::string &version,
                              std::string &machine)
{
    osName = "Microsoft Windows";

    char node[MAX_COMPUTERNAME_LENGTH + 1];
    DWORD nodelen = MAX_COMPUTERNAME_LENGTH + 1;
    GetComputerName (node, &nodelen);
    nodeName = node;

    OSVERSIONINFOEX vinfo;
    vinfo.dwOSVersionInfoSize = sizeof(vinfo);
    GetVersionEx ((OSVERSIONINFO *)&vinfo);

    SYSTEM_INFO sinfo;
    GetNativeSystemInfo(&sinfo);

    switch(vinfo.dwMajorVersion) {
    case 5:
        switch(vinfo.dwMinorVersion) {
        case 0:
            release  ="2000";
            break;
        case 1:
            release = "XP";
            break;
        case 2:
            if (sinfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64 ||
                sinfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_IA64)
                release = "XP-64";
            else
                release = "Server 2003";
            break;
        default:
            release = "Windows";
        }
        break;
    case 6:
        if (vinfo.wProductType == VER_NT_SERVER)
            release = "Server 2008";
        else
            release = "Vista";
        break;
    default:
        release = "Microsoft Windows";
    }
    version = vinfo.szCSDVersion;

    switch(sinfo.wProcessorArchitecture) {
    case PROCESSOR_ARCHITECTURE_AMD64:
        machine = "x86-64";
        break;
    case PROCESSOR_ARCHITECTURE_IA64:
        machine = "IA64";
        break;
    case PROCESSOR_ARCHITECTURE_INTEL:
        machine = "x86";
        break;
    default:
        machine = "unknown";
        break;
    }
}

uint32_t SystemInfo::getProcessId()
{
    return static_cast<uint32_t>(::GetCurrentProcessId());
}

uint32_t SystemInfo::getParentProcessId()
{
    // Only want info for the current process, so ask for something specific.
    // The module info won't be used here but it keeps the snapshot limited to
    // the current process so a search through all processes is not needed.
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, 0);
    if (snap == INVALID_HANDLE_VALUE)
        return 0;
    PROCESSENTRY32 entry;
    entry.dwSize = sizeof(entry);
    if (!Process32First(snap, &entry))
        entry.th32ParentProcessID = 0;
    CloseHandle(snap);
    return static_cast<uint32_t>(entry.th32ParentProcessID);
}

std::string SystemInfo::getProcessName()
{
    std::string name;

    // Only want info for the current process, so ask for something specific.
    // The module info won't be used here but it keeps the snapshot limited to
    // the current process so a search through all processes is not needed.
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, 0);
    if (snap == INVALID_HANDLE_VALUE)
        return name;
    PROCESSENTRY32 entry;
    entry.dwSize = sizeof(entry);
    if (!Process32First(snap, &entry))
        entry.szExeFile[0] = '\0';
    CloseHandle(snap);
    name = entry.szExeFile;
    return name;
}


#ifdef _DLL
namespace windows {
// set from one or more Qpid DLLs: i.e. in DllMain with DLL_PROCESS_DETACH
QPID_EXPORT bool processExiting = false;
QPID_EXPORT bool libraryUnloading = false;
}
#endif

bool SystemInfo::threadSafeShutdown()
{
#ifdef _DLL
    if (!windows::processExiting && !windows::libraryUnloading) {
        // called before exit() or FreeLibrary(), or by a DLL without
        // a participating DllMain.
        QPID_LOG(warning, "invalid query for shutdown state");
        throw qpid::Exception(QPID_MSG("Unable to determine shutdown state."));
    }
    return !windows::processExiting;
#else
    // Not a DLL: shutdown can only be by exit() or return from main().
    return false;
#endif
}

}} // namespace qpid::sys
