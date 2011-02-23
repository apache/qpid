#ifndef WINDOWS_SERVICE_H
#define WINDOWS_SERVICE_H

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

#include <string>
#include <vector>
using std::string;
using std::vector;

#ifdef UNICODE
#undef UNICODE
#endif

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <windows.h>

namespace qpid {
namespace windows {

class Service
{
public:

    /**
     * @param serviceName the name to register the service as
     */
    Service(const string& serviceName);

    /**
     * Install this executable as a service
     *
     * @param serviceName   The name of the service
     * @param args          The argument list to pass into the service
     * @param startType     The start type: SERVICE_DEMAND_START,
     *                      SERVICE_AUTO_START, SERVICE_DISABLED
     * @param account       If not empty, the account name to install this
     *                      service under
     * @param password      If not empty, the account password to install this
     *                      service with
     * @param depends       If not empty, a comma delimited list of services
     *                      that must start before this one
     * @return false on error, which will be sent to stdout
     */
    static bool install(const string& serviceName,
                        const string& args,
                        DWORD startType = SERVICE_DEMAND_START,
                        const string& account = "",
                        const string& password = "",
                        const string& depends = "");

    /**
     * Uninstall this executable as a service
     *
     * @param serviceName the name of the service
     * @return false on error, which will be sent to stdout
     */
    static bool uninstall(const string& serviceName);

    /**
     * Start the specified service
     *
     * @param serviceName the name of the service
     * @return false on error, which will be sent to stdout
     */
    static bool start(const string& serviceName);

    /**
     * Stop the specified service
     *
     * @param serviceName the name of the service
     * @return false on error, which will be sent to stdout
     */
    static bool stop(const string &serviceName);

    typedef VOID (WINAPI *tServiceMainProc)(DWORD dwNumServicesArgs,
                                            LPSTR *lpServiceArgVectors);

    /**
     * Run the service
     *
     * @return false if the service could not be started
     */
    bool run(tServiceMainProc main);

    typedef void (WINAPI *tShutdownProc)(void* pContext);

    /**
     * Set the shutdown proc
     */
    void setShutdownProc(tShutdownProc shutdownProc,
                         void * pContext)
        { m_shutdownProc = shutdownProc; m_pShutdownContext = pContext; }

    /**
     *
     */
    void reportApiErrorEvent(char* szFunction);

    /**
     *
     */
    void WINAPI svcMain(DWORD dwArgc, char *lpszArgv[]);

    /**
     *
     */
    void WINAPI svcCtrlHandler(DWORD dwCtrl);

    /**
     *
     */
    void svcInit(DWORD dwArgc, char *lpszArgv[]);

    /**
     *
     */
    static Service* getInstance() { return s_pInstance; }

    void reportInfoEvent(const string& message);

protected:

    static SC_HANDLE openSvcManager();

    static SC_HANDLE openService(SC_HANDLE hSvcManager,
                                 const string& serviceName,
                                 DWORD rights);

    bool getServiceImagePathArgs(string& args);

    /**
     * Build a command argc/argv set from a string
     *
     * Note that by convention, the argv list does not OWN the actual substring 
     * pointers.  To maintain that convention, this function also requires a 
     * temporary array of strings to be maintained, which will own the substring
     * memory.  Note that the substring pointers are only valid while this 
     * temporary array exists.  While technically unnecessary (the argv list 
     * COULD own the substrings), it simplifies substring management, as it is
     * trivial to place the substring vector on the stack and thereby guarantee
     * it is freed automatically.
     *
     * @param args the string to parse
     * @param argsv the vector to build to hold the substrings
     * @param pargc pointer to the int to return the number of substrings in
     * @param pargv pointer to the character pointer array to allocate to hold
     *              substring pointers
     */
    // REPLACE THIS WITH boost split_winmain    static void commandLineFromString( const string & args, vector<string> & argsv, int * pargc, char ** pargv[] );

    /**
     *
     */
    void ReportSvcStatus(DWORD dwCurrentState,
                         DWORD dwWin32ExitCode,
                         DWORD dwWaitHint);

    /**
     *
     */
    static BOOL StopDependentServices(SC_HANDLE schSCManager,
                                      SC_HANDLE schService);

    static Service *        s_pInstance;          ///< Singleton

    SERVICE_STATUS          gSvcStatus;           ///<
    SERVICE_STATUS_HANDLE   gSvcStatusHandle;     ///<
    HANDLE                  ghSvcStopEvent;       ///<
    string                  m_serviceName;        ///< Specified service name
    tServiceMainProc        m_main;               ///< Registered app entry point
    tShutdownProc           m_shutdownProc;       ///< Registered shutdown function pointer
    void*                   m_pShutdownContext;   ///< Context pointer supplied to shutdown proc
};

}}  // namespace qpid::windows

#endif  /* #ifndef WINDOWS_SERVICE_H */
