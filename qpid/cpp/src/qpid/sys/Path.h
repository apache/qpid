#ifndef QPID_SYS_PATH_H
#define QPID_SYS_PATH_H

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

#include "qpid/CommonImportExport.h"
#include <string>

namespace qpid {
namespace sys {

/**
 * @class Path
 *
 * Represents a filesystem path with some basic operations. Can be extended.
 */
class QPID_COMMON_CLASS_EXTERN Path {

    std::string path;

  public:
    // Path separator, forward or backslash
    static const QPID_COMMON_EXTERN std::string separator;

    Path(const std::string& path_=std::string()) : path(path_) {}

    std::string str() const { return path; }
    bool empty() const { return path.empty(); }

    QPID_COMMON_EXTERN bool exists() const;
    QPID_COMMON_EXTERN bool isFile() const;
    QPID_COMMON_EXTERN bool isDirectory() const;
    QPID_COMMON_EXTERN bool isAbsolute() const;

    /** Join with appropriate path separator. */
    Path& operator+=(const Path& tail) { path = path + separator + tail.path; return *this; }
};

inline Path operator+(const Path& head, const Path& tail) { Path p(head); return p += tail; }

}} // namespace qpid::sys

#endif  /*!QPID_SYS_PATH_H*/
