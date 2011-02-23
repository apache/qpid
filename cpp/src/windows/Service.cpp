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

#include <boost/format.hpp>
#include <sstream>
#include <iostream>
using std::ostringstream;
#include "qpid/log/Statement.h"
#include "Service.h"

#pragma comment(lib, "advapi32.lib")

namespace {
//
// Purpose:
//   Called by SCM whenever a control code is sent to the service
//   using the ControlService function.
//
// Parameters:
//   dwCtrl - control code
//
// Return value:
//   None
//
VOID WINAPI SvcCtrlHandler(DWORD dwCtrl)
{
  qpid::windows::Service * pWinService = qpid::windows::Service::getInstance();
  if (pWinService)
      pWinService->svcCtrlHandler(dwCtrl);
}

/**
  * Entrypoint from the system to run the service.
  */
VOID WINAPI SvcMain(DWORD dwArgc, char *lpszArgv[])
{
    qpid::windows::Service* pService = qpid::windows::Service::getInstance();
    if (pService)
        pService->svcMain(dwArgc, lpszArgv);
}

}  // namespace

namespace qpid {
namespace windows {

/**
  * Ye service instance
  */
Service* Service::s_pInstance = 0;

/**
  * Run the service
  *
  * @return false if the service could not be started
  */
bool Service::run(tServiceMainProc main)
{
    // Register main
    if (!main)
        return false;
    m_main = main;

    // TO_DO: Add any additional services for the process to this table.
    SERVICE_TABLE_ENTRY DispatchTable[] =
    {
        { const_cast<LPSTR>(m_serviceName.c_str()), (LPSERVICE_MAIN_FUNCTION) SvcMain },
        { NULL, NULL }
    };

    // This call returns when the service has stopped.
    // The process should simply terminate when the call returns.

    if (!StartServiceCtrlDispatcher(DispatchTable)) {
        reportApiErrorEvent("StartServiceCtrlDispatcher");
        return false;
    }
    return true;
}

/**
  * Build a command argc/argv set from a string
  */
void WinService::commandLineFromString( const string & args, vector<string> & argsv, int * pargc, char ** pargv[] )
{
	// Build the substring vector
	size_t s = 0;
	while( true )
	{
		// Skip leading space
		size_t p = args.find_first_not_of( " ", s );
		if( p != args.npos )
			s = p;

		p = args.find_first_of( " \"", s );									// Look for next quote or space

		if( p == args.npos )												// didn't find it ?
		{
			if( s < args.size() - 1 )										// If anything left
				argsv.push_back( args.substr( s ) );						// substring = rest of string
			break;															// we're done breaking up
		}

		if( args[p] == '\"' )												// found an open quote ?
		{
			size_t p2 = args.find_first_of( "\"", p + 1 );					// search for close quote
			if( p2 == args.npos )											// didn't find it ?
			{
				argsv.push_back( args.substr( p + 1 ) );					// substring = rest of string
				break;														// we're done breaking up
			}	
			if( p2 - p > 0 )												// If something in between the quotes
				argsv.push_back( args.substr( p + 1, ( p2 - p ) - 1 ) );	// use it
			s = p2 + 1;														// resume past close quote
		}

		else																// otherwise found a space
		{
			argsv.push_back( args.substr( s, p - s ) );						// use it
			s = p + 1;														// resume past space
		}
	}

	// Build the argv list
	if( pargc )
		* pargc = argsv.size();
	if( pargv )
	{
		char ** ppc = * pargv = new char*[ argsv.size() ];
		for( size_t i = 0; i < argsv.size(); i++ )
			ppc[ i ] = const_cast<char*>( argsv[i].c_str());
	}
}

/**
  *
  */
Service::Service(const string& serviceName)
    : ghSvcStopEvent(NULL),
      m_main(0),
      m_shutdownProc(0),
      m_serviceName(serviceName),
      m_pShutdownContext(0)
{
    s_pInstance = this;
}

/**
  *
  */
SC_HANDLE Service::openSvcManager()
{
    SC_HANDLE schSCManager = ::OpenSCManager(NULL,    // local computer
                                             NULL,    // ServicesActive database
                                             SC_MANAGER_ALL_ACCESS); // Rights
    if (NULL == schSCManager) {
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("OpenSCManager failed: %1%") % GetLastError()).str());
        //printf("OpenSCManager failed (%d)\n", GetLastError());
        return 0;
    }

    return schSCManager;
}

/**
  *
  */
SC_HANDLE Service::openService(SC_HANDLE hSvcManager,
                               const string& serviceName,
                               DWORD rights)
{
    // Get a handle to the service.
    SC_HANDLE schService = ::OpenService(hSvcManager,
                                         serviceName.c_str(),
                                         rights);
    if (schService == NULL) {
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("OpenService failed: %1%") % GetLastError()).str());
        //printf("OpenService failed (%d)\n", GetLastError());
        ::CloseServiceHandle(hSvcManager);
        return false;
    }

    return schService;
}

/**
  * Install this executable as a service
  */
bool Service::install(const string& serviceName,
                      const string& args,
                      DWORD startType,
                      const string& account,
                      const string& password,
                      const string& depends)
{
    SC_HANDLE schSCManager;
    SC_HANDLE schService;
    char szPath[MAX_PATH];

    // Handle dependent service name list
    char * pDepends = 0;
    string depends2 = depends;
    if (!depends2.empty()) {
        // CDL to null delimiter w/ trailing double null
        size_t p = 0;
        while ((p = depends2.find_first_of( ',', p)) != string::npos)
            depends2.replace(p, 1, 1, '\0');
        depends2.push_back('\0');
        depends2.push_back('\0');
        pDepends = const_cast<char*>(depends2.c_str()); // win doesn't modify, so can use in this case
    }

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

    // Get fully qualified .exe name
    if (!::GetModuleFileName(NULL, szPath, MAX_PATH)) {
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("Cannot install service: %1%") % GetLastError()).str());
        //printf("Cannot install service (%d)\n", GetLastError());
        return false;
    }

    string imagePath = szPath;
    if (!args.empty())
        imagePath += " " + args;

    // Get a handle to the SCM database.
    if (!(schSCManager = openSvcManager()))
        return false;

    // Create the service
    schService = ::CreateService(schSCManager,              // SCM database
                                 serviceName.c_str(),       // name of service
                                 serviceName.c_str(),       // name to display
                                 SERVICE_ALL_ACCESS,        // desired access
                                 SERVICE_WIN32_OWN_PROCESS, // service type
                                 startType,                 // start type
                                 SERVICE_ERROR_NORMAL,      // error cntrl type
                                 imagePath.c_str(),         // path to service's binary w/ optional arguments
                                 NULL,                      // no load ordering group
                                 NULL,                      // no tag identifier
                                 pDepends,                  // Dependant svc list
                                 account.empty() ? NULL : account.c_str(), // account name, or NULL for LocalSystem
                                 password.empty() ? NULL : password.c_str()); // password, or NULL for none
    if (schService == NULL) {
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("CreateService failed: %1%") % GetLastError()).str());
        //printf("CreateService failed (%d)\n", GetLastError());
        ::CloseServiceHandle(schSCManager);
        return false;
    }
    //Note [ds, 27.09.2010]
    QPID_LOG(info, "Service installed successfully");
    //printf("Service installed successfully\n");
    ::CloseServiceHandle(schService);
    ::CloseServiceHandle(schSCManager);

    return true;
}

/**
  *
  */
bool Service::start(const string& serviceName)
{
    SC_HANDLE schSCManager;
    SC_HANDLE schService;

    SERVICE_STATUS_PROCESS ssStatus;
    DWORD dwOldCheckPoint;
    DWORD dwStartTickCount;
    DWORD dwWaitTime;
    DWORD dwBytesNeeded;

    // Get a handle to the SCM database.
    if (!(schSCManager = openSvcManager()))
        return false;

    // Get a handle to the service.
    if (!(schService = openService(schSCManager, serviceName, SERVICE_ALL_ACCESS)))
        return false;

    // Check the status in case the service is not stopped.
    if (!::QueryServiceStatusEx(schService,             // handle to service
                                SC_STATUS_PROCESS_INFO, // information level
                                (LPBYTE) &ssStatus,     // address of structure
                                sizeof(SERVICE_STATUS_PROCESS), // size of structure
                                &dwBytesNeeded)) {      // size needed if buffer is too small
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("QueryServiceStatusEx failed: %1%") % GetLastError()).str());
        //printf("QueryServiceStatusEx failed (%d)\n", GetLastError());
        ::CloseServiceHandle(schService);
        ::CloseServiceHandle(schSCManager);
        return false;
    }

    // Check if the service is already running. It would be possible
    // to stop the service here, but for simplicity this example just returns.
    if (ssStatus.dwCurrentState != SERVICE_STOPPED &&
        ssStatus.dwCurrentState != SERVICE_STOP_PENDING) {
        //Note [ds, 27.09.2010]
        QPID_LOG(warning, "Cannot start the service because it is already running");
        //printf("Cannot start the service because it is already running\n");
        ::CloseServiceHandle(schService);
        ::CloseServiceHandle(schSCManager);
        return false;
    }

    // Save the tick count and initial checkpoint.
    dwStartTickCount = ::GetTickCount();
    dwOldCheckPoint = ssStatus.dwCheckPoint;

    // Wait for the service to stop before attempting to start it.
    while (ssStatus.dwCurrentState == SERVICE_STOP_PENDING) {
        // Do not wait longer than the wait hint. A good interval is
        // one-tenth of the wait hint but not less than 1 second
        // and not more than 10 seconds.
        dwWaitTime = ssStatus.dwWaitHint / 10;
        if (dwWaitTime < 1000)
            dwWaitTime = 1000;
        else if (dwWaitTime > 10000)
            dwWaitTime = 10000;

        ::Sleep(dwWaitTime);

        // Check the status until the service is no longer stop pending.
        if (!::QueryServiceStatusEx(
                schService,                     // handle to service
                SC_STATUS_PROCESS_INFO,         // information level
                (LPBYTE) &ssStatus,             // address of structure
                sizeof(SERVICE_STATUS_PROCESS), // size of structure
                &dwBytesNeeded)) {              // size needed if buffer is too small
            //Note [ds, 27.09.2010]
            QPID_LOG(error, (boost::format("QueryServiceStatusEx failed: %1%") % GetLastError()).str());
            //printf("QueryServiceStatusEx failed (%d)\n", GetLastError());
            ::CloseServiceHandle(schService);
            ::CloseServiceHandle(schSCManager);
            return false;
        }

        if (ssStatus.dwCheckPoint > dwOldCheckPoint) {
            // Continue to wait and check.
            dwStartTickCount = GetTickCount();
            dwOldCheckPoint = ssStatus.dwCheckPoint;
        } else {
            if ((::GetTickCount() - dwStartTickCount) > ssStatus.dwWaitHint) {
                //Note [ds, 27.09.2010]
                QPID_LOG(error, "Service was in SERVICE_STOP_PENDING state, timeout waiting for service to stop");
                //printf("Timeout waiting for service to stop\n");
                ::CloseServiceHandle(schService);
                ::CloseServiceHandle(schSCManager);
                return false;
            }
        }
    }

    // Attempt to start the service.
    if (!::StartService(schService,   // handle to service
                        0,            // number of arguments
                        NULL)) {      // no arguments
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("StartService failed: %1%") % GetLastError()).str());
        //printf("StartService failed (%d)\n", GetLastError());
        ::CloseServiceHandle(schService);
        ::CloseServiceHandle(schSCManager);
        return false;
    }

    //Note [ds, 27.09.2010]
    QPID_LOG(info, "Service start pending...");
    //printf("Service start pending...\n");

    // Check the status until the service is no longer start pending.
    if (!::QueryServiceStatusEx(
            schService,                     // handle to service
            SC_STATUS_PROCESS_INFO,         // info level
            (LPBYTE) &ssStatus,             // address of structure
            sizeof(SERVICE_STATUS_PROCESS), // size of structure
            &dwBytesNeeded)) {              // if buffer too small
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("QueryServiceStatusEx failed: %1%") % GetLastError()).str());
        //printf("QueryServiceStatusEx failed (%d)\n", GetLastError());
        ::CloseServiceHandle(schService);
        ::CloseServiceHandle(schSCManager);
        return false;
    }

    // Save the tick count and initial checkpoint.
    dwStartTickCount = ::GetTickCount();
    dwOldCheckPoint = ssStatus.dwCheckPoint;

    while (ssStatus.dwCurrentState == SERVICE_START_PENDING) {
        // Do not wait longer than the wait hint. A good interval is
        // one-tenth the wait hint, but no less than 1 second and no
        // more than 10 seconds.
 
        dwWaitTime = ssStatus.dwWaitHint / 10;

        if (dwWaitTime < 1000)
            dwWaitTime = 1000;
        else if (dwWaitTime > 10000)
            dwWaitTime = 10000;
        ::Sleep(dwWaitTime);

        // Check the status again.
        if (!::QueryServiceStatusEx(schService,             // handle to svc
                                    SC_STATUS_PROCESS_INFO, // info level
                                    (LPBYTE) &ssStatus,     // address of structure
                                    sizeof(SERVICE_STATUS_PROCESS), // size of structure
                                    &dwBytesNeeded)) {      // if buff too small
            //Note [ds, 27.09.2010]
            QPID_LOG(error, (boost::format("QueryServiceStatusEx failed: %1%") % GetLastError()).str());
            //printf("QueryServiceStatusEx failed (%d)\n", GetLastError());
            break;
        }
 
        if (ssStatus.dwCheckPoint > dwOldCheckPoint) {
            // Continue to wait and check.
            dwStartTickCount = GetTickCount();
            dwOldCheckPoint = ssStatus.dwCheckPoint;
        }
        else {
            if ((::GetTickCount() - dwStartTickCount) > ssStatus.dwWaitHint) {
                // No progress made within the wait hint.
                break;
            }
        }
    }

    // Determine whether the service is running.
    if (ssStatus.dwCurrentState == SERVICE_RUNNING) {
        //Note [ds, 27.09.2010]
        QPID_LOG(info, "Service started successfully");
        //printf("Service started successfully.\n");

        ::CloseServiceHandle(schService);
	::CloseServiceHandle(schSCManager);
        return true;
    }

    //Note [ds, 27.09.2010]
    QPID_LOG(error, (boost::format("Service not started: %1% ") % GetLastError()).str());
    //printf("Service not started. \n");
    QPID_LOG(error, (boost::format("Current State: %1% ") % ssStatus.dwCurrentState).str());
    //printf("  Current State: %d\n", ssStatus.dwCurrentState);
    QPID_LOG(error, (boost::format("Exit Code: %1% ") % ssStatus.dwWin32ExitCode).str());
    //printf("  Exit Code: %d\n", ssStatus.dwWin32ExitCode);
    QPID_LOG(error, (boost::format("Check Point: %1% ") % ssStatus.dwCheckPoint).str());
    //printf("  Check Point: %d\n", ssStatus.dwCheckPoint);
    QPID_LOG(error, (boost::format("Wait Hint: %1% ") % ssStatus.dwWaitHint).str());
    //printf("  Wait Hint: %d\n", ssStatus.dwWaitHint);

    ::CloseServiceHandle(schService);
    ::CloseServiceHandle(schSCManager);
    return false;
}

/**
  *
  */
bool Service::stop(const string& serviceName)
{
    SC_HANDLE schSCManager;
    SC_HANDLE schService;

    SERVICE_STATUS_PROCESS ssp;
    DWORD dwStartTime = GetTickCount();
    DWORD dwBytesNeeded;
    DWORD dwTimeout = 30000; // 30-second time-out
    DWORD dwWaitTime;

    // Get a handle to the SCM database.
    if (!(schSCManager = openSvcManager()))
        return false;

    // Get a handle to the service.
    if (!(schService = openService(schSCManager,
                                   serviceName,
                                   SERVICE_STOP | SERVICE_QUERY_STATUS |
                                   SERVICE_ENUMERATE_DEPENDENTS)))
        return false;

    // Make sure the service is not already stopped.
    if  (!::QueryServiceStatusEx(schService,
                                 SC_STATUS_PROCESS_INFO,
                                 (LPBYTE)&ssp,
                                 sizeof(SERVICE_STATUS_PROCESS),
                                 &dwBytesNeeded)) {
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("QueryServiceStatusEx failed: %1%") % GetLastError()).str());
        //printf("QueryServiceStatusEx failed (%d)\n", GetLastError());
        goto stop_cleanup;
    }

    if (ssp.dwCurrentState == SERVICE_STOPPED) {
        //Note [ds, 27.09.2010]
        QPID_LOG(info, "Service is already stopped");
        //printf("Service is already stopped.\n");
        goto stop_cleanup;
    }

    // If a stop is pending, wait for it.
    while (ssp.dwCurrentState == SERVICE_STOP_PENDING) {
        //Note [ds, 27.09.2010]
        QPID_LOG(info, "Service stop pending...");
        //printf("Service stop pending...\n");

        // Do not wait longer than the wait hint. A good interval is
        // one-tenth of the wait hint but not less than 1 second
        // and not more than 10 seconds.
        dwWaitTime = ssp.dwWaitHint / 10;
        if (dwWaitTime < 1000)
            dwWaitTime = 1000;
        else if (dwWaitTime > 10000)
          dwWaitTime = 10000;
        ::Sleep(dwWaitTime);

        if (!::QueryServiceStatusEx(schService,
                                    SC_STATUS_PROCESS_INFO,
                                    (LPBYTE)&ssp,
                                    sizeof(SERVICE_STATUS_PROCESS),
                                    &dwBytesNeeded)) {
            //Note [ds, 27.09.2010]
            QPID_LOG(error, (boost::format("QueryServiceStatusEx failed: %1%") % GetLastError()).str());
            //printf("QueryServiceStatusEx failed (%d)\n", GetLastError());
            goto stop_cleanup;
        }

        if (ssp.dwCurrentState == SERVICE_STOPPED) {
            //Note [ds, 27.09.2010]
            QPID_LOG(info, "Service stopped successfully.");
            //printf("Service stopped successfully.\n");
            goto stop_cleanup;
        }

        if ((::GetTickCount() - dwStartTime) > dwTimeout) {
            //Note [ds, 27.09.2010]
            QPID_LOG(error, "Service stop timed out.");
            //printf("Service stop timed out.\n");
            goto stop_cleanup;
        }
    }

    // If the service is running, dependencies must be stopped first.
    StopDependentServices(schSCManager, schService);

    // Send a stop code to the service.
    if (!::ControlService(schService,
                          SERVICE_CONTROL_STOP,
                          (LPSERVICE_STATUS)&ssp)) {
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("ControlService failed: %1%") % GetLastError()).str());
        //printf( "ControlService failed (%d)\n", GetLastError() );
        goto stop_cleanup;
    }

    // Wait for the service to stop.
    while (ssp.dwCurrentState != SERVICE_STOPPED) {
        ::Sleep(ssp.dwWaitHint);
        if (!::QueryServiceStatusEx(schService,
                                    SC_STATUS_PROCESS_INFO,
                                    (LPBYTE)&ssp,
                                    sizeof(SERVICE_STATUS_PROCESS),
                                    &dwBytesNeeded)) {
            //Note [ds, 27.09.2010]
            QPID_LOG(error, (boost::format("QueryServiceStatusEx failed: %1%") % GetLastError()).str());
            //printf( "QueryServiceStatusEx failed (%d)\n", GetLastError() );
            goto stop_cleanup;
        }

        if (ssp.dwCurrentState == SERVICE_STOPPED)
            break;

        if ((::GetTickCount() - dwStartTime) > dwTimeout) {
            //Note [ds, 27.09.2010]
            QPID_LOG(error, "Wait timed out.");
            //printf( "Wait timed out\n" );
            goto stop_cleanup;
        }
    }
    //Note [ds, 27.09.2010]
    QPID_LOG(info,"Service stopped successfully.");
    //printf("Service stopped successfully\n");

    ::CloseServiceHandle(schService);
    ::CloseServiceHandle(schSCManager);
    return true;

stop_cleanup:
    ::CloseServiceHandle(schService);
    ::CloseServiceHandle(schSCManager);
    return false;
}

/**
  *
  */
BOOL Service::StopDependentServices(SC_HANDLE schSCManager,
                                    SC_HANDLE schService)
{
    DWORD i;
    DWORD dwBytesNeeded;
    DWORD dwCount;

    LPENUM_SERVICE_STATUS   lpDependencies = NULL;
    ENUM_SERVICE_STATUS     ess;
    SC_HANDLE               hDepService;
    SERVICE_STATUS_PROCESS  ssp;

    DWORD dwStartTime = ::GetTickCount();
    DWORD dwTimeout = 30000; // 30-second time-out

    // Pass a zero-length buffer to get the required buffer size.
    if (::EnumDependentServices(schService,
                                SERVICE_ACTIVE, 
                                lpDependencies,
                                0,
                                &dwBytesNeeded,
                                &dwCount)) {
        // If the Enum call succeeds, then there are no dependent
        // services, so do nothing.
        return TRUE;
    }
    else {
        if (::GetLastError() != ERROR_MORE_DATA)
            return FALSE; // Unexpected error

        // Allocate a buffer for the dependencies.
        lpDependencies = (LPENUM_SERVICE_STATUS) HeapAlloc(::GetProcessHeap(),
                                                           HEAP_ZERO_MEMORY,
                                                           dwBytesNeeded);
        if (!lpDependencies)
            return FALSE;

        __try {
            // Enumerate the dependencies.
            if (!::EnumDependentServices(schService,
                                         SERVICE_ACTIVE,
                                         lpDependencies,
                                         dwBytesNeeded,
                                         &dwBytesNeeded,
                                         &dwCount))
                return FALSE;

            for (i = 0; i < dwCount; i++) {
                ess = *(lpDependencies + i);
                // Open the service.
                hDepService = ::OpenService(schSCManager,
                                            ess.lpServiceName,
                                            SERVICE_STOP | SERVICE_QUERY_STATUS);
                if (!hDepService)
                    return FALSE;

                __try {
                    // Send a stop code.
                    if (!::ControlService(hDepService,
                                          SERVICE_CONTROL_STOP,
                                          (LPSERVICE_STATUS) &ssp))
                        return FALSE;

                    // Wait for the service to stop.
                    while (ssp.dwCurrentState != SERVICE_STOPPED) {
                        ::Sleep(ssp.dwWaitHint);
                        if (!::QueryServiceStatusEx(hDepService,
                                                    SC_STATUS_PROCESS_INFO,
                                                    (LPBYTE)&ssp,
                                                    sizeof(ssp),
                                                    &dwBytesNeeded))
                        return FALSE;

                        if (ssp.dwCurrentState == SERVICE_STOPPED)
                            break;

                        if ((::GetTickCount() - dwStartTime) > dwTimeout)
                            return FALSE;
                    }
                }
                __finally {
                    // Always release the service handle.
                    ::CloseServiceHandle(hDepService);
                }
            }
        }
        __finally {
            // Always free the enumeration buffer.
            ::HeapFree(GetProcessHeap(), 0, lpDependencies);
        }
    }
    return TRUE;
}

/**
  *
  */
bool Service::uninstall(const string& serviceName)
{
    SC_HANDLE schSCManager;
    SC_HANDLE schService;

    // Get a handle to the SCM database.
    if (!(schSCManager = openSvcManager()))
        return false;

    // Get a handle to the service.
    if (!(schService = openService(schSCManager, serviceName, DELETE)))
        return false;

    // Delete the service.
    if (!::DeleteService(schService)) {
        //Note [ds, 27.09.2010]
        QPID_LOG(error, (boost::format("DeleteService failed: %1%") % GetLastError()).str());
        //printf("DeleteService failed (%d)\n", GetLastError());
        ::CloseServiceHandle(schService);
        ::CloseServiceHandle(schSCManager);
        return false;
    }

    //Note [ds, 27.09.2010]
    QPID_LOG(info, "Service deleted successfully.");
    //printf("Service deleted successfully\n");
    ::CloseServiceHandle(schService);
    ::CloseServiceHandle(schSCManager);
    return true;
}

/**
  *
  */
bool Service::getServiceImagePathArgs(string& args)
{
    // Get service ImagePath string from registry
    string subkey = "SYSTEM\\CurrentControlSet\\Services\\" + m_serviceName;
    HKEY hKey;
    LONG lResult;
    if ((lResult = ::RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                                  subkey.c_str(),
                                  0,
                                  KEY_READ,
                                  &hKey)) != ERROR_SUCCESS) {
        reportApiErrorEvent("RegOpenKeyEx");
        return false;
    }

    DWORD type, len;
    char buf[1024];
    len = sizeof(buf) - 1;
    lResult = ::RegQueryValueEx(hKey,
                                "ImagePath",
                                0,
                                &type,
                                reinterpret_cast<LPBYTE>(buf),
                                &len);
    ::RegCloseKey(hKey);
    if (lResult != ERROR_SUCCESS) {
        reportApiErrorEvent( "RegQueryValueEx" );
        return false;
    }
    buf[len] = 0; // Guarantee string ends with null pad (required - Windows 'quirk')
    string ip(buf); // The service ImagePath string

    // Strip service path from start
    char szPath[MAX_PATH];
    if (!::GetModuleFileName(NULL, szPath, MAX_PATH)) {
        reportApiErrorEvent( "GetModuleFileName");
        return false;
    }
    string sp(szPath); // The service path string
    string ipa;
    if (sp.length() < ip.length())
        ipa = ip.substr(sp.length() + 1);
    args = ipa;
    return true;
}

/**
  *
  */
void WINAPI Service::svcMain(DWORD dwArgc, char *lpszArgv[])
{
    // Use service name passed in
    m_serviceName = lpszArgv[0];

    // Register the handler function for the service
    if (!(gSvcStatusHandle = ::RegisterServiceCtrlHandler(m_serviceName.c_str(),
                                                          SvcCtrlHandler))) {
        reportApiErrorEvent( TEXT("RegisterServiceCtrlHandler") );
        return;
    }

    // Initialize SERVICE_STATUS members
    gSvcStatus.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
    gSvcStatus.dwServiceSpecificExitCode = 0;

    // Report initial status to the SCM
    ReportSvcStatus(SERVICE_START_PENDING, NO_ERROR, 3000);

    // Only got 1 arg?  Try to obtain auto-start arg list
    // For now, always use it...
    string ipa;
    if (!getServiceImagePathArgs(ipa))
        return;
    Service::reportInfoEvent("ipa = " + ipa);

    vector<string> argsv;
    int pargc;
    char ** pargv = 0;
    commandLineFromString( ipa, argsv, &pargc, &pargv );

    // Assemble command line
    string r_args = "rargs:";
    for( DWORD i=0; i<dwArgc; i++ ) {
        if (i)
            r_args += " ";
        r_args += lpszArgv[i];
    }
    Service::reportInfoEvent(r_args);
    Service::reportInfoEvent(string("Hello world!"));

    // Report running status when initialization is complete.
    ReportSvcStatus(SERVICE_RUNNING, NO_ERROR, 0);

    // Create stop event
    ghSvcStopEvent = ::CreateEvent(NULL,    // default security attributes
                                   TRUE,    // manual reset event
                                   FALSE,   // not signaled
                                   NULL);   // no name
    if (ghSvcStopEvent == NULL) {
        ReportSvcStatus(SERVICE_STOPPED, NO_ERROR, 0);
        return;
    }

    // Pass control to main or init function
    //if( m_main )
    //	m_main( dwArgc, lpszArgv );
    //else
    //    svcInit( dwArgc, lpszArgv );

    if (m_main)
        m_main(pargc, pargv);
    else
        svcInit(pargc, pargv);

    if (pargv)
        delete[] pargv;

    // Report stop as our last action...
    ReportSvcStatus(SERVICE_STOPPED, NO_ERROR, 0);
}

//
// Purpose:
//   The service code
//
// Parameters:
//   dwArgc - Number of arguments in the lpszArgv array
//   lpszArgv - Array of strings. The first string is the name of
//     the service and subsequent strings are passed by the process
//     that called the StartService function to start the service.
//
// Return value:
//   None
//
void Service::svcInit(DWORD dwArgc, char *lpszArgv[])
{
    // TO_DO: Declare and set any required variables.
    //   Be sure to periodically call ReportSvcStatus() with
    //   SERVICE_START_PENDING. If initialization fails, call
    //   ReportSvcStatus with SERVICE_STOPPED.

    // Create an event. The control handler function, SvcCtrlHandler,
    // signals this event when it receives the stop control code.



    // TO_DO: Perform work until service stops.

    while (1) {
        // Check whether to stop the service.
        ::WaitForSingleObject(ghSvcStopEvent, INFINITE);
        ReportSvcStatus(SERVICE_STOPPED, NO_ERROR, 0);
        return;
    }
}

//
// Purpose:
//   Sets the current service status and reports it to the SCM.
//
// Parameters:
//   dwCurrentState - The current state (see SERVICE_STATUS)
//   dwWin32ExitCode - The system error code
//   dwWaitHint - Estimated time for pending operation,
//     in milliseconds
//
// Return value:
//   None
//
void Service::ReportSvcStatus(DWORD dwCurrentState,
                              DWORD dwWin32ExitCode,
                              DWORD dwWaitHint)
{
    static DWORD dwCheckPoint = 1;

    // Fill in the SERVICE_STATUS structure.
    gSvcStatus.dwCurrentState = dwCurrentState;
    gSvcStatus.dwWin32ExitCode = dwWin32ExitCode;
    gSvcStatus.dwWaitHint = dwWaitHint;

    if (dwCurrentState == SERVICE_START_PENDING)
        gSvcStatus.dwControlsAccepted = 0;
    else
        gSvcStatus.dwControlsAccepted = SERVICE_ACCEPT_STOP;

    if ((dwCurrentState == SERVICE_RUNNING) ||
        (dwCurrentState == SERVICE_STOPPED)   )
        gSvcStatus.dwCheckPoint = 0;
    else
        gSvcStatus.dwCheckPoint = dwCheckPoint++;

    // Report the status of the service to the SCM.
    ::SetServiceStatus(gSvcStatusHandle, &gSvcStatus);
}


void WINAPI Service::svcCtrlHandler(DWORD dwCtrl)
{
   // Handle the requested control code.
    switch(dwCtrl) {
    case SERVICE_CONTROL_STOP:
        ReportSvcStatus(SERVICE_STOP_PENDING, NO_ERROR, 0);
        // Signal the service to stop.
        //         SetEvent(ghSvcStopEvent);
        if (m_shutdownProc)
            m_shutdownProc(m_pShutdownContext);

        ReportSvcStatus(gSvcStatus.dwCurrentState, NO_ERROR, 0);
        break;
 
    case SERVICE_CONTROL_INTERROGATE:
        break;
 
    default:
        break;
    }
}

//
// Purpose:
//   Logs messages to the event log
//
// Parameters:
//   szFunction - name of function that failed
//
// Return value:
//   None
//
// Remarks:
//   The service must have an entry in the Application event log.
//
void Service::reportApiErrorEvent(char * szFunction)
{
    HANDLE hEventSource;
    LPCTSTR lpszStrings[2];
    //    char Buffer[80];

    hEventSource = ::RegisterEventSource(NULL, m_serviceName.c_str());
    if (NULL != hEventSource) {
        lpszStrings[0] = m_serviceName.c_str();

        //		sprintf( Buffer, "%s failed with %d", szFunction, GetLastError());
        //		lpszStrings[1] = Buffer;
        ostringstream buf;
        buf << szFunction << " failed with " << GetLastError();
        lpszStrings[1] = buf.str().c_str();

#define SVC_ERROR ((DWORD)0xC0020001L)

        ::ReportEvent(hEventSource,        // event log handle
                      EVENTLOG_ERROR_TYPE, // event type
                      0,                   // event category
                      SVC_ERROR,           // event identifier
                      NULL,                // no security identifier
                      2,                   // size of lpszStrings array
                      0,                   // no binary data
                      lpszStrings,         // array of strings
                      NULL);               // no binary data

        ::DeregisterEventSource(hEventSource);
    }
}

/**
  *
  */
void Service::reportInfoEvent(const string & message)
{
    HANDLE hEventSource = ::RegisterEventSource(NULL, m_serviceName.c_str());
    if (hEventSource) {
// #define SVC_ERROR ((DWORD)0xC0020001L)
#define SVC_INFO ((DWORD)0x40020001L)

        LPCTSTR lpszStrings[2];
        lpszStrings[0] = m_serviceName.c_str();
        lpszStrings[1] = message.c_str();

        ::ReportEvent(hEventSource,        // event log handle
                      EVENTLOG_INFORMATION_TYPE, // event type
                      0,                   // event category
                      SVC_INFO,           // event identifier
                      NULL,                // no security identifier
                      2,                   // size of lpszStrings array
                      0,                   // no binary data
                      lpszStrings,         // array of strings
                      NULL);               // no binary data

        ::DeregisterEventSource( hEventSource );
    }
}
