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
#include <sys/file.h>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>

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
    if (::stat(cpath, &s)) {
        if (errno == ENOENT) {
            if (::mkdir(cpath, 0755))
                throw Exception ("Can't create data directory: " + path);
        }
        else
            throw Exception ("Data directory not found: " + path);
    }
    std::string lockFileName(path);
    lockFileName += "/lock";
    lockFile = std::auto_ptr<sys::LockFile>(new sys::LockFile(lockFileName, true));
}

DataDir::~DataDir () {}

} // namespace qpid

