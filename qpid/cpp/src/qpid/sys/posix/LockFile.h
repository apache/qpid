#ifndef _sys_posix_LockFile_h
#define _sys_posix_LockFile_h

#include "check.h"

#include <boost/noncopyable.hpp>
#include <string>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

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
namespace qpid {
namespace sys {
    
class LockFile : private boost::noncopyable {
public:
    LockFile(const std::string& path_, bool create):
        path(path_), fd(-1), created(create) {
        errno = 0;
        int flags=create ? O_WRONLY|O_CREAT|O_NOFOLLOW : O_RDWR;
        fd = ::open(path.c_str(), flags, 0644);
        if (fd < 0) throw ErrnoException("Cannot open " + path, errno);
        if (::lockf(fd, F_TLOCK, 0) < 0) throw ErrnoException("Cannot lock " + path, errno);
    }

    ~LockFile() {
        if (fd >= 0) {
            ::lockf(fd, F_ULOCK, 0);
            ::close(fd);
        }
    }

    std::string path;
    int fd;
    bool created;
};
 
}
}
#endif /*!_sys_posix_LockFile_h*/
