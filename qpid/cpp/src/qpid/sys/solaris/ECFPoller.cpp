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

#include "qpid/log/Logger.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/IOHandle.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/DeletionManager.h"
#include "qpid/sys/posix/check.h"
#include "qpid/sys/posix/PrivatePosix.h"

#include <port.h>
#include <poll.h>
#include <errno.h>

#include <assert.h>
#include <vector>
#include <exception>


//TODO: Remove this
#include "qpid/sys/Dispatcher.h"

namespace qpid {
namespace sys {

// Deletion manager to handle deferring deletion of PollerHandles to when they definitely aren't being used 
DeletionManager<PollerHandle> PollerHandleDeletionManager;

//  Instantiate (and define) class static for DeletionManager
template <>
DeletionManager<PollerHandle>::AllThreadsStatuses DeletionManager<PollerHandle>::allThreadsStatuses(0);

class PollerHandlePrivate {
    friend class Poller;
    friend class PollerHandle;

    enum FDStat {
        ABSENT,
        MONITORED,
        INACTIVE,
        HUNGUP,
        MONITORED_HUNGUP
    };

    int fd;
    uint32_t events;
    FDStat stat;
    Mutex lock;

    PollerHandlePrivate(int f) :
      fd(f),
      events(0),
      stat(ABSENT) {
    }
    
    bool isActive() const {
        return stat == MONITORED || stat == MONITORED_HUNGUP;
    }

    void setActive() {
        stat = (stat == HUNGUP) ? MONITORED_HUNGUP : MONITORED;
    }

    bool isInactive() const {
        return stat == INACTIVE || stat == HUNGUP;
    }

    void setInactive() {
        stat = INACTIVE;
    }

    bool isIdle() const {
        return stat == ABSENT;
    }

    void setIdle() {
        stat = ABSENT;
    }

    bool isHungup() const {
        return stat == MONITORED_HUNGUP || stat == HUNGUP;
    }

    void setHungup() {
        assert(stat == MONITORED);
        stat = HUNGUP;
    }
};

PollerHandle::PollerHandle(const IOHandle& h) :
    impl(new PollerHandlePrivate(toFd(h.impl)))
{}

PollerHandle::~PollerHandle() {
    delete impl;
}

void PollerHandle::deferDelete() {
    PollerHandleDeletionManager.markForDeletion(this);
}

/**
 * Concrete implementation of Poller to use the Solaris Event Completion
 * Framework interface
 */
class PollerPrivate {
    friend class Poller;

    const int portId;

    static uint32_t directionToPollEvent(Poller::Direction dir) {
        switch (dir) {
            case Poller::INPUT:  return POLLIN;
            case Poller::OUTPUT: return POLLOUT;
            case Poller::INOUT:  return POLLIN | POLLOUT;
            default: return 0;
        }
    }

    static Poller::EventType pollToDirection(uint32_t events) {
        uint32_t e = events & (POLLIN | POLLOUT);
        switch (e) {
            case POLLIN: return Poller::READABLE;
            case POLLOUT: return Poller::WRITABLE;
            case POLLIN | POLLOUT: return Poller::READ_WRITABLE;
            default:
                return (events & (POLLHUP | POLLERR)) ?
                    Poller::DISCONNECTED : Poller::INVALID;
        }
    }
  
    PollerPrivate() :
        portId(::port_create()) {
    }

    ~PollerPrivate() {
    }
};

void Poller::addFd(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);

    uint32_t events = 0;
  
    if (eh.isIdle()) {
        events = PollerPrivate::directionToPollEvent(dir);
    } else {
        assert(eh.isActive());
        events = eh.events | PollerPrivate::directionToPollEvent(dir);
    }

    //port_associate can be used to add an association or modify an
    //existing one
    QPID_POSIX_CHECK(::port_associate(impl->portId, PORT_SOURCE_FD, (uintptr_t) eh.fd, events, &handle));
    eh.events = events;
    eh.setActive();
    QPID_LOG(trace, "Poller::addFd(handle=" << &handle
             << "[" << typeid(&handle).name()
             << "], fd=" << eh.fd << ")");
    //assert(dynamic_cast<DispatchHandle*>(&handle));
}

void Poller::delFd(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(!eh.isIdle());
    int rc = ::port_dissociate(impl->portId, PORT_SOURCE_FD, (uintptr_t) eh.fd);
    //Allow closing an invalid fd, allowing users to close fd before
    //doing delFd()
    if (rc == -1 && errno != EBADFD) {
        QPID_POSIX_CHECK(rc);
    }
    eh.setIdle();
    QPID_LOG(trace, "Poller::delFd(handle=" << &handle
             << ", fd=" << eh.fd << ")");
}

// modFd is equivalent to delFd followed by addFd
void Poller::modFd(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(!eh.isIdle());

    eh.events = PollerPrivate::directionToPollEvent(dir);
  
    //If fd is already associated, events and user arguments are updated
    //So, no need to check if fd is already associated
    QPID_POSIX_CHECK(::port_associate(impl->portId, PORT_SOURCE_FD, (uintptr_t) eh.fd, eh.events, &handle));
    eh.setActive();
    QPID_LOG(trace, "Poller::modFd(handle=" << &handle
             << ", fd=" << eh.fd << ")");
}

void Poller::rearmFd(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(eh.isInactive());
  
    QPID_POSIX_CHECK(::port_associate(impl->portId, PORT_SOURCE_FD, (uintptr_t) eh.fd, eh.events, &handle));
    eh.setActive();
    QPID_LOG(trace, "Poller::rearmdFd(handle=" << &handle
             << ", fd=" << eh.fd << ")");
}

void Poller::shutdown() {
    //Send an Alarm to the port
    //We need to send a nonzero event mask, using POLLHUP, but
    //The wait method will only look for a PORT_ALERT_SET
    QPID_POSIX_CHECK(::port_alert(impl->portId, PORT_ALERT_SET, POLLHUP, NULL));
    QPID_LOG(trace, "Poller::shutdown");
}

Poller::Event Poller::wait(Duration timeout) {
    timespec_t tout;
    timespec_t* ptout = NULL;
    port_event_t pe;
    
    if (timeout != TIME_INFINITE) {
      tout.tv_sec = 0;
      tout.tv_nsec = timeout;
      ptout = &tout;
    }

    do {
        PollerHandleDeletionManager.markAllUnusedInThisThread();
        QPID_LOG(trace, "About to enter port_get. Thread "
                 << pthread_self()
                 << ", timeout=" << timeout);
        
        int rc = ::port_get(impl->portId, &pe, ptout);

        if (rc < 0) {
            switch (errno) {
            case EINTR:
                continue;
            case ETIME:
                return Event(0, TIMEOUT);
            default:
                QPID_POSIX_CHECK(rc);
            }
        } else {
            //We use alert mode to notify the shutdown of the Poller
            if (pe.portev_source == PORT_SOURCE_ALERT) {
                return Event(0, SHUTDOWN);            
            }
            if (pe.portev_source == PORT_SOURCE_FD) {
                PollerHandle *handle = static_cast<PollerHandle*>(pe.portev_user);
                PollerHandlePrivate& eh = *handle->impl;
                ScopedLock<Mutex> l(eh.lock);
                QPID_LOG(trace, "About to send handle: " << handle);
                
                if (eh.isActive()) {
                  if (pe.portev_events & POLLHUP) {
                    if (eh.isHungup()) {
                      return Event(handle, DISCONNECTED);
                    }
                    eh.setHungup();
                  } else {
                    eh.setInactive();
                  }
                  QPID_LOG(trace, "Sending event (thread: "
                           << pthread_self() << ") for handle " << handle
                           << ", direction= "
                           << PollerPrivate::pollToDirection(pe.portev_events));
                  return Event(handle, PollerPrivate::pollToDirection(pe.portev_events));
                }
            }
        }
    } while (true);
}

// Concrete constructors
Poller::Poller() :
    impl(new PollerPrivate())
{}

Poller::~Poller() {
    delete impl;
}

}}
