#ifndef QPID_DATADIR_H
#define QPID_DATADIR_H

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

#include <string>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>

namespace qpid {

class LockFile {
public:
    LockFile(const std::string& path_, bool create):
        path(path_), fd(-1), created(create) {
        int flags=create ? O_WRONLY|O_CREAT|O_NOFOLLOW : O_RDWR;
        fd = ::open(path.c_str(), flags, 0644);
        if (fd < 0) throw Exception("Cannot open " + path + ": " + strError(errno));
        if (::lockf(fd, F_TLOCK, 0) < 0) throw Exception("Cannot lock " + path + ": " + strError(errno));
    }

    ~LockFile() {
        if (fd >= 0) {
            (void) ::lockf(fd, F_ULOCK, 0); // Suppress warnings about ignoring return value.
            ::close(fd);
        }
    }

    std::string path;
    int fd;
    bool created;
};

/**
 * DataDir class.
 */
class DataDir
{
    const bool        enabled;
    const std::string dirPath;
    std::auto_ptr<LockFile> lockFile;

  public:

    DataDir (std::string path);
    ~DataDir ();

    bool        isEnabled () { return enabled; }
    std::string getPath   () { return dirPath; }
};
 
} // namespace qpid

#endif  /*!QPID_DATADIR_H*/
