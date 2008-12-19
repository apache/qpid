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

#include <string>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include "check.h"

namespace qpid {
namespace sys {

class LockFilePrivate {
    friend class LockFile;

    int fd;

public:
    LockFilePrivate(int f) : fd(f) {}
};

LockFile::LockFile(const std::string& path_, bool create)
  : path(path_), created(create) {
   
    errno = 0;
    int flags=create ? O_WRONLY|O_CREAT|O_NOFOLLOW : O_RDWR;
    int fd = ::open(path.c_str(), flags, 0644);
    if (fd < 0) throw ErrnoException("Cannot open " + path, errno);
    if (::lockf(fd, F_TLOCK, 0) < 0) {
        ::close(fd);
        throw ErrnoException("Cannot lock " + path, errno);
    }
    impl.reset(new LockFilePrivate(fd));
}

LockFile::~LockFile() {
    if (impl) {
        int f = impl->fd;
        if (f >= 0) {
            (void) ::lockf(f, F_ULOCK, 0); // Suppress warnings about ignoring return value.
            ::close(f);
            impl->fd = -1;
        }
    }
}

pid_t LockFile::readPid(void) const {
    if (!impl)
        throw Exception("Lock file not open");

    pid_t pid;
    int desired_read = sizeof(pid_t);
    if (desired_read > ::read(impl->fd, &pid, desired_read) ) {
        throw Exception("Cannot read lock file " + path);
    }
    return pid;
}

void LockFile::writePid(void) {
    if (!impl)
        throw Exception("Lock file not open");

    pid_t pid = getpid();
    int desired_write = sizeof(pid_t);
    if (desired_write > ::write(impl->fd, &pid, desired_write)) {
        throw Exception("Cannot write lock file " + path);
    }
}
 
}}  /* namespace qpid::sys */
