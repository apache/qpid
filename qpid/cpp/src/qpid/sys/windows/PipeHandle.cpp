//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
// 
//   http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

#include "qpid/sys/PipeHandle.h"
#include "check.h"
#include <io.h>
#include <fcntl.h>
#include <errno.h>
#include <winsock2.h>

namespace qpid {
namespace sys {

PipeHandle::PipeHandle(bool nonBlocking) {

    int pair[2];
    pair[0] = pair[1] = -1;

    if (_pipe(pair, 128, O_BINARY) == -1)
        throw qpid::Exception(QPID_MSG("Creation of pipe failed"));

    writeFd = pair[0];
    readFd = pair[1]; 

    // Set the socket to non-blocking
    if (nonBlocking) {
        unsigned long nonblock = 1;
        ioctlsocket(readFd, FIONBIO, &nonblock);
    }
}

PipeHandle::~PipeHandle() {
    close(readFd);
    close(writeFd);
}

int PipeHandle::read(void* buf, size_t bufSize) {
    return ::read(readFd, buf, bufSize);
}

int PipeHandle::write(const void* buf, size_t bufSize) {
    return ::write(writeFd, buf, bufSize);
}

int PipeHandle::getReadHandle() {
    return readFd;
}

}} // namespace qpid::sys
