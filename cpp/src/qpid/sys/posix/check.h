#ifndef _posix_check_h
#define _posix_check_h

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

#include "qpid/Exception.h"

#include <cerrno>

#define QPID_POSIX_ERROR(ERRNO) qpid::Exception(QPID_MSG(qpid::strError(ERRNO)));

/** THROW QPID_POSIX_ERROR(errno) if RESULT is less than zero */
#define QPID_POSIX_CHECK(RESULT)                        \
    if ((RESULT) < 0) throw QPID_POSIX_ERROR((errno))

/** Throw a posix error if errNo is non-zero */
#define QPID_POSIX_THROW_IF(ERRNO)              \
    if ((ERRNO) != 0) throw QPID_POSIX_ERROR((ERRNO))
#endif  /*!_posix_check_h*/
