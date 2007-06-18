#ifndef _sys_Poller_h
#define _sys_Poller_h

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

#include "Time.h"

#include <stdint.h>

#include <boost/shared_ptr.hpp>

namespace qpid {
namespace sys {

/**
 * Handle class to use for polling
 */
class Poller;
class PollerHandlePrivate;
class PollerHandle {
    friend class Poller;

    PollerHandlePrivate* impl;
    const int fd;

public:
    PollerHandle(int fd0);
    virtual ~PollerHandle();

    int getFD() const { return fd; }
};

/**
 * Poller: abstract class to encapsulate a file descriptor poll to be used
 * by a reactor
 */
class PollerPrivate;
class Poller {
    PollerPrivate* impl;

public:
    typedef boost::shared_ptr<Poller> shared_ptr;

    enum Direction {
        NONE,
        IN,
        OUT,
        INOUT,
        SHUTDOWN
    };

    struct Event {
        PollerHandle* handle;
        Direction dir;
        
        Event(PollerHandle* handle0, Direction dir0) :
          handle(handle0),
          dir(dir0) {
        }
    };
    
    Poller();
    ~Poller();
    void shutdown();

    void addFd(PollerHandle& handle, Direction dir);
    void delFd(PollerHandle& handle);
    void modFd(PollerHandle& handle, Direction dir);
    void rearmFd(PollerHandle& handle);
    Event wait(Duration timeout = TIME_INFINITE);
};

}}
#endif // _sys_Poller_h
