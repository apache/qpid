/*
 *
 * Copyright (c) 2008 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/sys/LockFile.h"
#include "check.h"

#include <windows.h>

namespace qpid {
namespace sys {

class LockFilePrivate {
    friend class LockFile;

    HANDLE fd;

public:
    LockFilePrivate(HANDLE f) : fd(f) {}
};

LockFile::LockFile(const std::string& path_, bool create)
  : path(path_), created(create) {

    HANDLE h = CreateFile(path.c_str(),
                          GENERIC_READ|GENERIC_WRITE,
                          0, /* Disable opens by any other attempter */
                          0, /* Default security */
                          OPEN_ALWAYS, /* Create if needed */
                          FILE_FLAG_DELETE_ON_CLOSE, /* Delete file when closed */
                          NULL);
    QPID_WINDOWS_CHECK_NOT(h, INVALID_HANDLE_VALUE);
    impl.reset(new LockFilePrivate(h));
}

LockFile::~LockFile() {
    if (impl) {
        if (impl->fd != INVALID_HANDLE_VALUE) {
            CloseHandle(impl->fd);
        }
    }
}

pid_t LockFile::readPid(void) const {
    if (!impl)
        throw Exception("Lock file not open");

    pid_t pid;
    DWORD desired_read = sizeof(pid_t);
    DWORD actual_read = 0;
    if (!ReadFile(impl->fd, &pid, desired_read, &actual_read, 0)) {
        throw Exception("Cannot read lock file " + path);
    }
    return pid;
}

void LockFile::writePid(void) {
    if (!impl)
        throw Exception("Lock file not open");

    pid_t pid = GetCurrentProcessId();
    DWORD desired_write = sizeof(pid_t);
    DWORD written = 0;
    if (!WriteFile(impl->fd, &pid, desired_write, &written, 0)) {
        throw Exception("Cannot write lock file " + path);
    }
}
 
}}  /* namespace qpid::sys */
