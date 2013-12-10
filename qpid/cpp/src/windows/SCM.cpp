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

#include "qpid/log/Statement.h"
#include "qpid/sys/windows/check.h"
#include "SCM.h"

#pragma comment(lib, "advapi32.lib")

namespace qpid {
namespace windows {

namespace {

// Container that will close a SC_HANDLE upon destruction.
class AutoServiceHandle {
public:
    AutoServiceHandle(SC_HANDLE h_ = NULL) : h(h_) {}
    ~AutoServiceHandle() { if (h != NULL) ::CloseServiceHandle(h); }
    void release() { h = NULL; }
    void reset(SC_HANDLE newHandle)
    {
        if (h != NULL)
            ::CloseServiceHandle(h);
        h = newHandle;
    }
    operator SC_HANDLE() const { return h; }

private:
    SC_HANDLE h;
};

}

SCM::SCM() : scmHandle(NULL)
{
}

SCM::~SCM()
{
    if (NULL != scmHandle)
        ::CloseServiceHandle(scmHandle);
}

/**
  * Install this executable as a service
  */
void SCM::install(const string& serviceName,
                  const string& serviceDesc,
                  const string& args,
                  DWORD startType,
                  const string& account,
                  const string& password,
                  const string& depends)
{
    // Handle dependent service name list; Windows wants a set of nul-separated
    // names ending with a double nul.
    string depends2 = depends;
    if (!depends2.empty()) {
        // CDL to null delimiter w/ trailing double null
        size_t p = 0;
        while ((p = depends2.find_first_of( ',', p)) != string::npos)
            depends2.replace(p, 1, 1, '\0');
        depends2.push_back('\0');
        depends2.push_back('\0');
    }

#if 0
    // I'm nervous about adding a user/password check here. Is this a
    // potential attack vector, letting users check passwords without
    // control?   -Steve Huston, Feb 24, 2011

    // Validate account, password
    HANDLE hToken = NULL;
    bool logStatus = false;
    if (!account.empty() && !password.empty() &&
        !(logStatus = ::LogonUserA(account.c_str(),
                                   "",
                                   password.c_str(),
                                   LOGON32_LOGON_NETWORK,
                                   LOGON32_PROVIDER_DEFAULT,
                                   &hToken ) != 0))
        std::cout << "warning: supplied account & password failed with LogonUser." << std::endl;
    if (logStatus)
        ::CloseHandle(hToken);
#endif

    // Get fully qualified .exe name
    char myPath[MAX_PATH];
    DWORD myPathLength = ::GetModuleFileName(NULL, myPath, MAX_PATH);
    QPID_WINDOWS_CHECK_NOT(myPathLength, 0);
    string imagePath(myPath, myPathLength);
    if (!args.empty())
        imagePath += " " + args;

    // Ensure there's a handle to the SCM database.
    openSvcManager();

    // Create the service
    SC_HANDLE svcHandle;
    svcHandle = ::CreateService(scmHandle,                 // SCM database
                                serviceName.c_str(),       // name of service
                                serviceDesc.c_str(),       // name to display
                                SERVICE_ALL_ACCESS,        // desired access
                                SERVICE_WIN32_OWN_PROCESS, // service type
                                startType,                 // start type
                                SERVICE_ERROR_NORMAL,      // error cntrl type
                                imagePath.c_str(),         // path to service's binary w/ optional arguments
                                NULL,                      // no load ordering group
                                NULL,                      // no tag identifier
                                depends2.empty() ? NULL : depends2.c_str(),
                                account.empty() ? NULL : account.c_str(), // account name, or NULL for LocalSystem
                                password.empty() ? NULL : password.c_str()); // password, or NULL for none
    QPID_WINDOWS_CHECK_NULL(svcHandle);
    ::CloseServiceHandle(svcHandle);
    QPID_LOG(info, "Service installed successfully");
}

/**
  *
  */
void SCM::uninstall(const string& serviceName)
{
    // Ensure there's a handle to the SCM database.
    openSvcManager();
    AutoServiceHandle svc(::OpenService(scmHandle,
                                        serviceName.c_str(),
                                        DELETE));
    QPID_WINDOWS_CHECK_NULL((SC_HANDLE)svc);
    QPID_WINDOWS_CHECK_NOT(::DeleteService(svc), 0);
    QPID_LOG(info, "Service deleted successfully.");
}

/**
  * Attempt to start the service.
  */
void SCM::start(const string& serviceName)
{
    // Ensure we have a handle to the SCM database.
    openSvcManager();

    // Get a handle to the service.
    AutoServiceHandle svc(::OpenService(scmHandle,
                                        serviceName.c_str(),
                                        SERVICE_ALL_ACCESS));
    QPID_WINDOWS_CHECK_NULL(svc);

    // Check the status in case the service is not stopped.
    DWORD state = waitForStateChangeFrom(svc, SERVICE_STOP_PENDING);
    if (state == SERVICE_STOP_PENDING)
        throw qpid::Exception("Timed out waiting for running service to stop.");

    // Attempt to start the service.
    QPID_WINDOWS_CHECK_NOT(::StartService(svc, 0, NULL), 0);

    QPID_LOG(info, "Service start pending...");

    // Check the status until the service is no longer start pending.
    state = waitForStateChangeFrom(svc, SERVICE_START_PENDING);
    // Determine whether the service is running.
    if (state == SERVICE_RUNNING) {
        QPID_LOG(info, "Service started successfully");
    }
    else {
        throw qpid::Exception(QPID_MSG("Service not yet running; state now " << state));
    }
}

/**
  *
  */
void SCM::stop(const string& serviceName)
{
    // Ensure a handle to the SCM database.
    openSvcManager();

    // Get a handle to the service.
    AutoServiceHandle svc(::OpenService(scmHandle,
                                        serviceName.c_str(),
                                        SERVICE_STOP | SERVICE_QUERY_STATUS |
                                        SERVICE_ENUMERATE_DEPENDENTS));
    QPID_WINDOWS_CHECK_NULL(svc);

    // Make sure the service is not already stopped; if it's stop-pending,
    // wait for it to finalize.
    DWORD state = waitForStateChangeFrom(svc, SERVICE_STOP_PENDING);
    if (state == SERVICE_STOPPED) {
        QPID_LOG(info, "Service is already stopped");
        return;
    }

    // If the service is running, dependencies must be stopped first.
    std::auto_ptr<ENUM_SERVICE_STATUS> deps;
    DWORD numDeps = getDependentServices(svc, deps);
    for (DWORD i = 0; i < numDeps; i++)
        stop(deps.get()[i].lpServiceName);

    // Dependents stopped; send a stop code to the service.
    SERVICE_STATUS_PROCESS ssp;
    if (!::ControlService(svc, SERVICE_CONTROL_STOP, (LPSERVICE_STATUS)&ssp))
        throw qpid::Exception(QPID_MSG("Stopping " << serviceName << ": " <<
                                       qpid::sys::strError(::GetLastError())));

    // Wait for the service to stop.
    state = waitForStateChangeFrom(svc, SERVICE_STOP_PENDING);
    if (state == SERVICE_STOPPED)
        QPID_LOG(info, QPID_MSG("Service " << serviceName <<
                                " stopped successfully."));
}

/**
  *
  */
void SCM::openSvcManager()
{
    if (NULL != scmHandle)
        return;

    scmHandle = ::OpenSCManager(NULL,    // local computer
                                NULL,    // ServicesActive database
                                SC_MANAGER_ALL_ACCESS); // Rights
    QPID_WINDOWS_CHECK_NULL(scmHandle);
}

DWORD SCM::waitForStateChangeFrom(SC_HANDLE svc, DWORD originalState)
{
    SERVICE_STATUS_PROCESS ssStatus;
    DWORD bytesNeeded;
    DWORD waitTime;
    if (!::QueryServiceStatusEx(svc,                    // handle to service
                                SC_STATUS_PROCESS_INFO, // information level
                                (LPBYTE)&ssStatus,      // address of structure
                                sizeof(ssStatus),       // size of structure
                                &bytesNeeded))          // size needed if buffer is too small
        throw QPID_WINDOWS_ERROR(::GetLastError());

    // Save the tick count and initial checkpoint.
    DWORD startTickCount = ::GetTickCount();
    DWORD oldCheckPoint = ssStatus.dwCheckPoint;

    // Wait for the service to change out of the noted state.
    while (ssStatus.dwCurrentState == originalState) {
        // Do not wait longer than the wait hint. A good interval is
        // one-tenth of the wait hint but not less than 1 second
        // and not more than 10 seconds.
        waitTime = ssStatus.dwWaitHint / 10;
        if (waitTime < 1000)
            waitTime = 1000;
        else if (waitTime > 10000)
            waitTime = 10000;

        ::Sleep(waitTime);

        // Check the status until the service is no longer stop pending.
        if (!::QueryServiceStatusEx(svc,
                                    SC_STATUS_PROCESS_INFO,
                                    (LPBYTE) &ssStatus,
                                    sizeof(ssStatus),
                                    &bytesNeeded))
            throw QPID_WINDOWS_ERROR(::GetLastError());

        if (ssStatus.dwCheckPoint > oldCheckPoint) {
            // Continue to wait and check.
            startTickCount = ::GetTickCount();
            oldCheckPoint = ssStatus.dwCheckPoint;
        } else {
            if ((::GetTickCount() - startTickCount) > ssStatus.dwWaitHint)
                break;
        }
    }
    return ssStatus.dwCurrentState;
}

/**
  * Get the services that depend on @arg svc.  All dependent service info
  * is returned in an array of ENUM_SERVICE_STATUS structures via @arg deps.
  *
  * @retval The number of dependent services.
  */
DWORD SCM::getDependentServices(SC_HANDLE svc,
                                std::auto_ptr<ENUM_SERVICE_STATUS>& deps)
{
    DWORD bytesNeeded;
    DWORD numEntries;

    // Pass a zero-length buffer to get the required buffer size.
    if (::EnumDependentServices(svc,
                                SERVICE_ACTIVE, 
                                0,
                                0,
                                &bytesNeeded,
                                &numEntries)) {
        // If the Enum call succeeds, then there are no dependent
        // services, so do nothing.
        return 0;
    }

    if (::GetLastError() != ERROR_MORE_DATA)
        throw QPID_WINDOWS_ERROR((::GetLastError()));

    // Allocate a buffer for the dependencies.
    deps.reset((LPENUM_SERVICE_STATUS)(new char[bytesNeeded]));
    // Enumerate the dependencies.
    if (!::EnumDependentServices(svc,
                                 SERVICE_ACTIVE,
                                 deps.get(),
                                 bytesNeeded,
                                 &bytesNeeded,
                                 &numEntries))
        throw QPID_WINDOWS_ERROR((::GetLastError()));
    return numEntries;
}

} }   // namespace qpid::windows
