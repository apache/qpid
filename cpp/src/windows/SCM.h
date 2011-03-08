#ifndef WINDOWS_SCM_H
#define WINDOWS_SCM_H

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

#include <memory>
#include <string>
using std::string;

#ifdef UNICODE
#undef UNICODE
#endif

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <windows.h>

namespace qpid {
namespace windows {

/**
 * @class SCM
 *
 * Access the Windows Service Control Manager.
 */
class SCM
{
public:
    SCM();
    ~SCM();

    /**
     * Install this executable as a service
     *
     * @param serviceName   The name of the service
     * @param serviceDesc   Description of the service's purpose
     * @param args          The argument list to pass into the service
     * @param startType     The start type: SERVICE_DEMAND_START,
     *                      SERVICE_AUTO_START, SERVICE_DISABLED
     * @param account       If not empty, the account name to install this
     *                      service under
     * @param password      If not empty, the account password to install this
     *                      service with
     * @param depends       If not empty, a comma delimited list of services
     *                      that must start before this one
     */
    void install(const string& serviceName,
                 const string& serviceDesc,
                 const string& args,
                 DWORD startType = SERVICE_DEMAND_START,
                 const string& account = "NT AUTHORITY\\LocalSystem",
                 const string& password = "",
                 const string& depends = "");

    /**
     * Uninstall this executable as a service
     *
     * @param serviceName the name of the service
     */
    void uninstall(const string& serviceName);

    /**
     * Start the specified service
     *
     * @param serviceName the name of the service
     */
    void start(const string& serviceName);

    /**
     * Stop the specified service
     *
     * @param serviceName the name of the service
     */
    void stop(const string &serviceName);

private:
    SC_HANDLE  scmHandle;

    void openSvcManager();
    DWORD waitForStateChangeFrom(SC_HANDLE svc, DWORD originalState);
    DWORD getDependentServices(SC_HANDLE svc,
                               std::auto_ptr<ENUM_SERVICE_STATUS>& deps);

};

}}  // namespace qpid::windows

#endif  /* #ifndef WINDOWS_SCM_H */
