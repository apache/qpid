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

/*
 * Include this file once in each DLL that relies on SystemInfo.h:
 * threadSafeShutdown().  Note that Thread.cpp has a more elaborate
 * DllMain, that also provides this functionality separately.
 *
 * Teardown is in the reverse order of the DLL dependencies used
 * during the load phase.  The calls to DllMain and the static
 * destructors are from the same thread, so no locking is necessary
 * and there is no downside to an invocation of DllMain by multiple
 * Qpid DLLs.
 */

#ifdef _DLL

#include <qpid/ImportExport.h>
#include <windows.h>

namespace qpid {
namespace sys {
namespace windows {

QPID_IMPORT bool processExiting;
QPID_IMPORT bool libraryUnloading;

}}} // namespace qpid::sys::SystemInfo


BOOL APIENTRY DllMain(HMODULE hm, DWORD reason, LPVOID reserved) {
    switch (reason) {
    case DLL_PROCESS_ATTACH:
    case DLL_THREAD_ATTACH:
    case DLL_THREAD_DETACH:
        break;

    case DLL_PROCESS_DETACH:
        // Remember how the process is terminating this DLL.
        if (reserved != NULL) {
            qpid::sys::windows::processExiting = true;
            // Danger: all threading suspect, including indirect use of malloc or locks.
            // Think twice before adding more functionality here.
            return TRUE;
        }
        else {
            qpid::sys::windows::libraryUnloading = true;
        }
        break;
    }
    return TRUE;
}


#endif
