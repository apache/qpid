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

#include <windows.h>
#include <clfsw32.h>
#include <clfsmgmtw32.h>
#include <sstream>
#include <string>
#include <vector>
#include <stdlib.h>
#include <qpid/sys/windows/check.h>

#include "Log.h"

namespace qpid {
namespace store {
namespace ms_clfs {

Log::~Log()
{
    if (marshal != 0)
        ::DeleteLogMarshallingArea(marshal);
    ::CloseHandle(handle);
}

void
Log::open(const std::string& path, const TuningParameters& params)
{
    this->containerSize = static_cast<ULONGLONG>(params.containerSize);
    logPath = path;
    std::string logSpec = "log:" + path;
    size_t specLength = logSpec.length();
    std::auto_ptr<wchar_t> wLogSpec(new wchar_t[specLength + 1]);
    size_t converted;
    mbstowcs_s(&converted,
               wLogSpec.get(), specLength+1,
               logSpec.c_str(), specLength);
    handle = ::CreateLogFile(wLogSpec.get(),
                             GENERIC_WRITE | GENERIC_READ,
                             0,
                             0,
                             OPEN_ALWAYS,
                             0);
    QPID_WINDOWS_CHECK_NOT(handle, INVALID_HANDLE_VALUE);
    CLFS_INFORMATION info;
    ULONG infoSize = sizeof(info);
    BOOL ok = ::GetLogFileInformation(handle, &info, &infoSize);
    QPID_WINDOWS_CHECK_NOT(ok, 0);
    ok = ::RegisterManageableLogClient(handle, 0);
    QPID_WINDOWS_CHECK_NOT(ok, 0);

    // Set up policies for how many containers to initially create and how
    // large each container should be. Also, auto-grow the log when container
    // space runs out.
    CLFS_MGMT_POLICY logPolicy;
    logPolicy.Version = CLFS_MGMT_POLICY_VERSION;
    logPolicy.LengthInBytes = sizeof(logPolicy);
    logPolicy.PolicyFlags = 0;

    // If this is the first time this log is opened, give an opportunity to
    // initialize its content.
    bool needInitialize(false);
    if (info.TotalContainers == 0) {
        // New log; set the configured container size and create the
        // initial set of containers.
        logPolicy.PolicyType = ClfsMgmtPolicyNewContainerSize;
        logPolicy.PolicyParameters.NewContainerSize.SizeInBytes = containerSize;
        ok = ::InstallLogPolicy(handle, &logPolicy);
        QPID_WINDOWS_CHECK_NOT(ok, 0);

        ULONGLONG desired(params.containers), actual(0);
        ok = ::SetLogFileSizeWithPolicy(handle, &desired, &actual);
        QPID_WINDOWS_CHECK_NOT(ok, 0);

        needInitialize = true;
    }
    // Ensure that the log is extended as needed and will shrink when 50%
    // becomes unused.
    logPolicy.PolicyType = ClfsMgmtPolicyAutoGrow;
    logPolicy.PolicyParameters.AutoGrow.Enabled = 1;
    ok = ::InstallLogPolicy(handle, &logPolicy);
    QPID_WINDOWS_CHECK_NOT(ok, 0);
    logPolicy.PolicyType = ClfsMgmtPolicyAutoShrink;
    logPolicy.PolicyParameters.AutoShrink.Percentage = params.shrinkPct;
    ok = ::InstallLogPolicy(handle, &logPolicy);
    QPID_WINDOWS_CHECK_NOT(ok, 0);

    // Need a marshaling area
    ok = ::CreateLogMarshallingArea(handle,
                                    NULL, NULL, NULL,    // Alloc, free, context
                                    marshallingBufferSize(),
                                    params.maxWriteBuffers,
                                    1,                   // Max read buffers
                                    &marshal);
    QPID_WINDOWS_CHECK_NOT(ok, 0);
    if (needInitialize)
        initialize();
}

uint32_t
Log::marshallingBufferSize()
{
  // Default implementation returns the minimum marshalling buffer size;
  // derived ones should come up with a more fitting value.
  //
  // Find the directory name part of the log specification, including the
  // trailing '\'.
  size_t dirMarker = logPath.rfind('\\');
  if (dirMarker == std::string::npos)
      dirMarker = logPath.rfind('/');
  DWORD bytesPerSector;
  DWORD dontCare;
  ::GetDiskFreeSpace(logPath.substr(0, dirMarker).c_str(),
                     &dontCare,
                     &bytesPerSector,
                     &dontCare,
                     &dontCare);
  return bytesPerSector;
}

CLFS_LSN
Log::write(void* entry, uint32_t length, CLFS_LSN* prev)
{
    CLFS_WRITE_ENTRY desc;
    desc.Buffer = entry;
    desc.ByteLength = length;
    CLFS_LSN lsn;
    BOOL ok = ::ReserveAndAppendLog(marshal,
                                    &desc, 1,            // Buffer descriptor
                                    0, prev,             // Undo-Next, Prev
                                    0, 0,                // Reservation
                                    CLFS_FLAG_FORCE_FLUSH,
                                    &lsn,
                                    0);
    QPID_WINDOWS_CHECK_NOT(ok, 0);
    return lsn;
}

// Get the current base LSN of the log.
CLFS_LSN
Log::getBase()
{
    CLFS_INFORMATION info;
    ULONG infoSize = sizeof(info);
    BOOL ok = ::GetLogFileInformation(handle, &info, &infoSize);
    QPID_WINDOWS_CHECK_NOT(ok, 0);
    return info.BaseLsn;
}

void
Log::moveTail(const CLFS_LSN& oldest)
{
    BOOL ok = ::AdvanceLogBase(marshal,
                               const_cast<PCLFS_LSN>(&oldest),
                               0, NULL);
    // If multiple threads are manipulating things they may get out of
    // order when moving the tail; if someone already moved it further
    // than this, it's ok - ignore it.
    if (ok || ::GetLastError() == ERROR_LOG_START_OF_LOG)
        return;
    QPID_WINDOWS_CHECK_NOT(ok, 0);
}

}}}  // namespace qpid::store::ms_clfs
