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
    wchar_t *wLogSpec = new wchar_t[specLength + 1];
    size_t converted;
    mbstowcs_s(&converted, wLogSpec, specLength+1, logSpec.c_str(), specLength);
    wLogSpec[converted] = L'\0';
    handle = ::CreateLogFile(wLogSpec,
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
    // If this is the first time this log is opened, give an opportunity to
    // initialize its content.
    bool needInitialize(false);
    if (info.TotalContainers == 0) {
        std::vector<const std::wstring> paths;
        LPWSTR cPaths[1024];
        size_t pathLength = logPath.length();
        wchar_t *wLogPath = new wchar_t[pathLength + 1];
        mbstowcs_s(&converted, wLogPath, pathLength+1,
                   logPath.c_str(), pathLength);
        wLogPath[converted] = L'\0';
        for (unsigned short i = 0; i < params.containers && i < 1024; ++i) {
            std::wostringstream path;
            path << wLogPath << L"-container-" << i << std::ends;
            paths.push_back(path.str ());
            cPaths[i] = const_cast<LPWSTR>(paths[i].c_str());
        }
        ok = ::AddLogContainerSet(handle,
                                  params.containers,
                                  &this->containerSize,
                                  cPaths,
                                  NULL);
        QPID_WINDOWS_CHECK_NOT(ok, 0);
        needInitialize = true;
    }
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
                                    CLFS_FLAG_FORCE_FLUSH,                   // CLFS_FLAGS_NO_FLAGS
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
    QPID_WINDOWS_CHECK_NOT(ok, 0);
}

}}}  // namespace qpid::store::ms_clfs
