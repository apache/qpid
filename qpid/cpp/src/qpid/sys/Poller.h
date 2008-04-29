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

#include <boost/shared_ptr.hpp>

namespace qpid {
namespace sys {

/**
 * Handle class to use for polling
 */
class IOHandle;
class Poller;
class PollerHandlePrivate;
class PollerHandle {
    friend class Poller;

    PollerHandlePrivate* const impl;

public:
    PollerHandle(const IOHandle& h);
    
    // Usual way to delete (will defer deletion until we
    // can't be returned from a Poller::wait any more)
    void deferDelete();
    
    // Class clients shouldn't ever use this
    virtual ~PollerHandle();
};

/**
 * Poller: abstract class to encapsulate a file descriptor poll to be used
 * by a reactor
 */
class PollerPrivate;
class Poller {
    PollerPrivate* const impl;

public:
    typedef boost::shared_ptr<Poller> shared_ptr;

    enum Direction {
        NONE = 0,
        IN,
        OUT,
        INOUT
    };

    enum EventType {
        INVALID = 0,
        READABLE,
        WRITABLE,
        READ_WRITABLE,
        DISCONNECTED,
        SHUTDOWN,
        TIMEOUT
    };

    struct Event {
        PollerHandle* handle;
        EventType type;
        
        Event(PollerHandle* handle0, EventType type0) :
          handle(handle0),
          type(type0) {
        }
    };
    
    Poller();
    ~Poller();
    /** Note: this function is async-signal safe */
    void shutdown();

    void addFd(PollerHandle& handle, Direction dir);
    void delFd(PollerHandle& handle);
    void modFd(PollerHandle& handle, Direction dir);
    void rearmFd(PollerHandle& handle);
    Event wait(Duration timeout = TIME_INFINITE);
};

}}
#endif // _sys_Poller_h
