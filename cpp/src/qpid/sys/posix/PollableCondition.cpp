#ifndef QPID_SYS_LINUX_POLLABLECONDITION_CPP
#define QPID_SYS_LINUX_POLLABLECONDITION_CPP

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

#include "PollableCondition.h"
#include "qpid/sys/posix/PrivatePosix.h"
#include "qpid/Exception.h"

#include <unistd.h>
#include <fcntl.h>

namespace qpid {
namespace sys {

PollableCondition::PollableCondition() : IOHandle(new sys::IOHandlePrivate) {
    int fds[2];
    if (::pipe(fds) == -1)
        throw ErrnoException(QPID_MSG("Can't create PollableCondition"));
    impl->fd = fds[0];
    writeFd = fds[1];
    if (::fcntl(impl->fd, F_SETFL, O_NONBLOCK) == -1)
        throw ErrnoException(QPID_MSG("Can't create PollableCondition"));
    if (::fcntl(writeFd, F_SETFL, O_NONBLOCK) == -1)
        throw ErrnoException(QPID_MSG("Can't create PollableCondition"));
}

bool PollableCondition::clear() {
    char buf[256];
    ssize_t n;
    bool wasSet = false;
    while ((n = ::read(impl->fd, buf, sizeof(buf))) > 0) 
        wasSet = true;
    if (n == -1 && errno != EAGAIN) throw ErrnoException(QPID_MSG("Error clearing PollableCondition"));
    return wasSet;
}

void PollableCondition::set() {
    static const char dummy=0;
    ssize_t n = ::write(writeFd, &dummy, 1);
    if (n == -1 && errno != EAGAIN) throw ErrnoException("Error setting PollableCondition");
}


#if 0
// FIXME aconway 2008-08-12: More efficient Linux implementation using
// eventfd system call.  Move to separate file & do configure.ac test
// to enable this when ::eventfd() is available.

#include <sys/eventfd.h>

namespace qpid {
namespace sys {

PollableCondition::PollableCondition() : IOHandle(new sys::IOHandlePrivate) {
    impl->fd = ::eventfd(0, 0);
    if (impl->fd < 0) throw ErrnoException("conditionfd() failed");
}

bool PollableCondition::clear() {
    char buf[8];
    ssize_t n = ::read(impl->fd, buf, 8);
    if (n != 8) throw ErrnoException("read failed on conditionfd");
    return *reinterpret_cast<uint64_t*>(buf);
}

void PollableCondition::set() {
    static const uint64_t value=1;
    ssize_t n = ::write(impl->fd, reinterpret_cast<const void*>(&value), 8);
    if (n != 8) throw ErrnoException("write failed on conditionfd");
}
    
#endif

}} // namespace qpid::sys

#endif  /*!QPID_SYS_LINUX_POLLABLECONDITION_CPP*/
