/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
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

#include "qpid/sys/FileSysDir.h"
#include "qpid/sys/StrError.h"
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>
#include <dirent.h>
#include <stdlib.h>

namespace qpid {
namespace sys {

bool FileSysDir::exists (void) const
{
    const  char *cpath = dirPath.c_str ();
    struct stat  s;
    if (::stat(cpath, &s)) {
        if (errno == ENOENT) {
            return false;
        }
        throw qpid::Exception (strError(errno) +
                               ": Can't check directory: " + dirPath);
    }
    if (S_ISDIR(s.st_mode))
        return true;
    throw qpid::Exception(dirPath + " is not a directory");
}

void FileSysDir::mkdir(void)
{
    if (::mkdir(dirPath.c_str(), 0755))
        throw Exception ("Can't create directory: " + dirPath);
}

void FileSysDir::forEachFile(Callback cb) const {

    ::dirent** namelist;

    int n = scandir(dirPath.c_str(), &namelist, 0, alphasort);
    if (n == -1) throw Exception (strError(errno) + ": Can't scan directory: " + dirPath);

    for (int i = 0; i<n; ++i) {
        std::string fullpath = dirPath + "/" + namelist[i]->d_name;
        // Filter out non files/stat problems etc.
        struct ::stat s;
        // Can't throw here without leaking memory, so just do nothing with
        // entries for which stat() fails.
        if (!::stat(fullpath.c_str(), &s)) {
            if (S_ISREG(s.st_mode)) {
                cb(fullpath);
            }
        }
        ::free(namelist[i]);
    }
    ::free(namelist);
}

}} // namespace qpid::sys
