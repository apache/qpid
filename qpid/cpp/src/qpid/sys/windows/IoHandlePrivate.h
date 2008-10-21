#ifndef _sys_windows_IoHandlePrivate_h
#define _sys_windows_IoHandlePrivate_h

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

#include "AsynchIoResult.h"

#include <winsock2.h>

namespace qpid {
namespace sys {

// Private fd related implementation details
// There should be either a valid socket handle or a completer callback.
// Handle is used to associate with poller's iocp; completer is used to
// inject a completion that will very quickly trigger a callback to the
// completer from an I/O thread.
class IOHandlePrivate {
public:
    IOHandlePrivate(SOCKET f = INVALID_SOCKET,
                    AsynchIoResult::Completer cb = 0) :
    fd(f), event(cb)
    {}
    
    SOCKET fd;
    AsynchIoResult::Completer event;
};

SOCKET toFd(const IOHandlePrivate* h);

}}

#endif /* _sys_windows_IoHandlePrivate_h */
