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

#include "qpid/sys/Poller.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/posix/check.h"

#include <sys/epoll.h>
#include <errno.h>

#include <assert.h>
#include <vector>
#include <exception>

namespace qpid {
namespace sys {

class PollerHandlePrivate {
    friend class Poller;
    friend class PollerHandle;

    enum FDStat {
        ABSENT,
        MONITORED,
        INACTIVE
    };

    ::__uint32_t events;
    FDStat stat;
    Mutex lock;

    PollerHandlePrivate() :
      events(0),
      stat(ABSENT) {
    }
};

PollerHandle::PollerHandle(int fd0) :
    impl(new PollerHandlePrivate),
    fd(fd0)
{}
    
PollerHandle::~PollerHandle() {
    delete impl;
}

/**
 * Concrete implementation of Poller to use the Linux specific epoll
 * interface
 */
class PollerPrivate {
    friend class Poller;

    static const int DefaultFds = 256;

    struct ReadablePipe {
        int fds[2];
        
        /**
         * This encapsulates an always readable pipe which we can add
         * to the epoll set to force epoll_wait to return
         */
        ReadablePipe() {
            QPID_POSIX_CHECK(::pipe(fds));
            // Just write the pipe's fds to the pipe
            QPID_POSIX_CHECK(::write(fds[1], fds, 2));
        }
        
        ~ReadablePipe() {
            ::close(fds[0]);
            ::close(fds[1]);
        }
        
        int getFD() {
            return fds[0];
        }
    };
    
    static ReadablePipe alwaysReadable;
    
    const int epollFd;
    bool isShutdown;

    static ::__uint32_t directionToEpollEvent(Poller::Direction dir) {
        switch (dir) {
            case Poller::IN: return ::EPOLLIN;
            case Poller::OUT: return ::EPOLLOUT;
            case Poller::INOUT: return ::EPOLLIN | ::EPOLLOUT;
            default: return 0;
        }
    }
    
    static Poller::Direction epollToDirection(::__uint32_t events) {
        ::__uint32_t e = events & (::EPOLLIN | ::EPOLLOUT);
        switch (e) {
            case ::EPOLLIN: return Poller::IN;
            case ::EPOLLOUT: return Poller::OUT;
            case ::EPOLLIN | ::EPOLLOUT: return Poller::INOUT;
            default: return Poller::NONE;
        }
    }

    PollerPrivate() :
        epollFd(::epoll_create(DefaultFds)),
        isShutdown(false) {
        QPID_POSIX_CHECK(epollFd);
    }

    ~PollerPrivate() {
        // It's probably okay to ignore any errors here as there can't be data loss
        ::close(epollFd);
    }
};

PollerPrivate::ReadablePipe PollerPrivate::alwaysReadable;

void Poller::addFd(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    ::epoll_event epe;
    int op;
    
    if (eh.stat == PollerHandlePrivate::ABSENT) {
        op = EPOLL_CTL_ADD;
        epe.events = PollerPrivate::directionToEpollEvent(dir) | ::EPOLLONESHOT;
    } else {
        assert(eh.stat == PollerHandlePrivate::MONITORED);
        op = EPOLL_CTL_MOD;
        epe.events = eh.events | PollerPrivate::directionToEpollEvent(dir);
    }
    epe.data.ptr = &handle;
    
    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, op, handle.getFD(), &epe));
    
    // Record monitoring state of this fd
    eh.events = epe.events;
    eh.stat = PollerHandlePrivate::MONITORED;
}

void Poller::delFd(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(eh.stat != PollerHandlePrivate::ABSENT);
    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, EPOLL_CTL_DEL, handle.getFD(), 0));
    eh.stat = PollerHandlePrivate::ABSENT;
}

// modFd is equivalent to delFd followed by addFd
void Poller::modFd(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(eh.stat != PollerHandlePrivate::ABSENT);
    
    ::epoll_event epe;
    epe.events = PollerPrivate::directionToEpollEvent(dir) | ::EPOLLONESHOT;
    epe.data.ptr = &handle;
    
    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, EPOLL_CTL_MOD, handle.getFD(), &epe));
    
    // Record monitoring state of this fd
    eh.events = epe.events;
    eh.stat = PollerHandlePrivate::MONITORED;
}

void Poller::rearmFd(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(eh.stat == PollerHandlePrivate::INACTIVE);

    ::epoll_event epe;
    epe.events = eh.events;        
    epe.data.ptr = &handle;

    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, EPOLL_CTL_MOD, handle.getFD(), &epe));

    eh.stat = PollerHandlePrivate::MONITORED;
}

void Poller::shutdown() {
    // Don't use any locking here - isshutdown will be visible to all
    // after the epoll_ctl() anyway (it's a memory barrier)
    impl->isShutdown = true;
    
    // Add always readable fd to epoll (not EPOLLONESHOT)
    int fd = impl->alwaysReadable.getFD();
    ::epoll_event epe;
    epe.events = ::EPOLLIN;
    epe.data.ptr = 0;
    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, EPOLL_CTL_ADD, fd, &epe));
}

Poller::Event Poller::wait(Duration timeout) {
    epoll_event epe;
    int timeoutMs = (timeout == TIME_INFINITE) ? -1 : timeout / TIME_MSEC;

    // Repeat until we weren't interupted
    do {
        int rc = ::epoll_wait(impl->epollFd, &epe, 1, timeoutMs);
        
        if (impl->isShutdown) {
            return Event(0, SHUTDOWN);            
        }
        
        if (rc ==-1 && errno != EINTR) {
            QPID_POSIX_CHECK(rc);
        } else if (rc > 0) {
            assert(rc == 1);
            PollerHandle* handle = static_cast<PollerHandle*>(epe.data.ptr);
            PollerHandlePrivate& eh = *handle->impl;
            
            ScopedLock<Mutex> l(eh.lock);
            
            // the handle could have gone inactive since we left the epoll_wait
            if (eh.stat == PollerHandlePrivate::MONITORED) {
                eh.stat = PollerHandlePrivate::INACTIVE;
                return Event(handle, PollerPrivate::epollToDirection(epe.events));
            }
        }
        // We only get here if one of the following:
        // * epoll_wait was interrupted by a signal
        // * epoll_wait timed out
        // * the state of the handle changed after being returned by epoll_wait
        //
        // The only things we can do here are return a timeout or wait more.
        // Obviously if we timed out we return timeout; if the wait was meant to
        // be indefinite then we should never return with a time out so we go again.
        // If the wait wasn't indefinite, but we were interrupted then we have to return
        // with a timeout as we don't know how long we've waited so far and so we can't
        // continue the wait.
        if (rc == 0 || timeoutMs == -1) {
            return Event(0, NONE);
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
