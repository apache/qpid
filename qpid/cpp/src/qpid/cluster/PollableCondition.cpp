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

// Linux implementation of PollableCondition using the conditionfd(2) system call.

// FIXME aconway 2008-08-11: this could be of more general interest,
// move to common lib.
// 

#include "qpid/sys/posix/PrivatePosix.h"
#include "qpid/cluster/PollableCondition.h"
#include "qpid/Exception.h"
#include <sys/eventfd.h>

namespace qpid {
namespace cluster {

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
    
}} // namespace qpid::cluster

#endif  /*!QPID_SYS_LINUX_POLLABLECONDITION_CPP*/
