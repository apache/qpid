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

#include "Exception.h"
#include "DataDir.h"
#include "qpid/log/Statement.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <cerrno>

namespace qpid {

DataDir::DataDir (std::string path) :
    enabled (!path.empty ()),
    dirPath (path)
{
    if (!enabled)
    {
        QPID_LOG (info, "No data directory - Disabling persistent configuration");
        return;
    }

    const  char *cpath = dirPath.c_str ();
    struct stat  s;

    if (::stat (cpath, &s))
        throw Exception ("Data directory not found: " + path);

    std::string lockFile (path);
    lockFile = lockFile + "/lock";
    int fd = ::open (lockFile.c_str (), O_CREAT | O_EXCL,
                     S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
    if (fd == -1)
    {
        if (errno == EEXIST)
            throw Exception ("Data directory is locked by another process");
        if (errno == EACCES)
            throw Exception ("Insufficient privileges for data directory");
        throw Exception(
            QPID_MSG("Error locking " << lockFile << ": " << strError(errno)));
    }

    QPID_LOG (info, "Locked data directory: " << dirPath);
}

DataDir::~DataDir ()
{
    if (dirPath.empty ())
        return;

    std::string lockFile (dirPath);
    lockFile = lockFile + "/lock";

    ::unlink (lockFile.c_str ());
    QPID_LOG (info, "Unlocked data directory: " << dirPath);
}

} // namespace qpid

