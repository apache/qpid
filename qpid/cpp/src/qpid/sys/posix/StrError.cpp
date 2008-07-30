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

#include "qpid/sys/StrError.h"

// Ensure we get the POSIX verion of strerror_r
#ifndef _XOPEN_SOURCE
#define _XOPEN_SOURCE 600
#include <string.h>
#undef _XOPEN_SOURCE
#else
#include <string.h>
#endif


namespace qpid {
namespace sys {

std::string strError(int err) {
    char buf[512];
    //POSIX strerror_r doesn't return the buffer
    ::strerror_r(err, buf, sizeof(buf));
    return std::string(buf);
}

}}
