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

#include "qpid/sys/Path.h"
#include "qpid/sys/StrError.h"
#include "qpid/Exception.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <direct.h>
#include <errno.h>
#include <windows.h>
#include <strsafe.h>


namespace qpid {
namespace sys {

const std::string Path::separator("\\");

namespace {
// Return true for success, false for ENOENT, throw otherwise.
bool getStat(const std::string& path, struct _stat& s) {
    if (::_stat(path.c_str(), &s)) {
        if (errno == ENOENT) return false;
        throw qpid::Exception("cannot stat: " + path + ": " + strError(errno));
    }
    return true;
}

bool isFlag(const std::string& path, unsigned long flag) {
    struct _stat  s;
    return getStat(path, s) && (s.st_mode & flag);
}
}

bool Path::exists () const {
    struct _stat  s;
    return getStat(path, s);
}

bool Path::isFile() const { return isFlag(path, _S_IFREG); }
bool Path::isDirectory() const { return isFlag(path, _S_IFDIR); }

bool Path::isAbsolute() const {
    return (path.size() > 0 && (path[0] == separator[0] || path[0] == '/'))
        || (path.size() > 1 && (isalpha(path[0]) && path[1] == ':'));
}

}} // namespace qpid::sys
