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
#include "qpid/sys/IOHandle.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/DeletionManager.h"
#include "qpid/sys/posix/check.h"
#include "qpid/sys/posix/PrivatePosix.h"

#include <sys/epoll.h>
#include <errno.h>
#include <signal.h>

#include <assert.h>
#include <queue>
#include <exception>

namespace qpid {
namespace sys {

// Deletion manager to handle deferring deletion of PollerHandles to when they definitely aren't being used
DeletionManager<PollerHandlePrivate> PollerHandleDeletionManager;

//  Instantiate (and define) class static for DeletionManager
template <>
DeletionManager<PollerHandlePrivate>::AllThreadsStatuses DeletionManager<PollerHandlePrivate>::allThreadsStatuses(0);

class PollerHandlePrivate {
    friend class Poller;
    friend class PollerHandle;

    enum FDStat {
        ABSENT,
        MONITORED,
        INACTIVE,
        HUNGUP,
        MONITORED_HUNGUP,
        DELETED
    };

    int fd;
    ::__uint32_t events;
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

    bool isDeleted() const {
        return stat == DELETED;
    }

    void setDeleted() {
        stat = DELETED;
    }
};

PollerHandle::PollerHandle(const IOHandle& h) :
    impl(new PollerHandlePrivate(toFd(h.impl)))
{}

PollerHandle::~PollerHandle() {
    {
    ScopedLock<Mutex> l(impl->lock);
    if (impl->isDeleted()) {
    	return;
    }
    if (impl->isActive()) {
        impl->setDeleted();
    }
    }
    PollerHandleDeletionManager.markForDeletion(impl);
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
    static int alwaysReadableFd;

    class InterruptHandle: public PollerHandle {
    	std::queue<PollerHandle*> handles;
    	
    	void processEvent(Poller::EventType) {
    		PollerHandle* handle = handles.front();
    		handles.pop();
    		assert(handle);
    		
    		// Synthesise event
    		Poller::Event event(handle, Poller::INTERRUPTED);

    		// Process synthesised event
    		event.process();
    	}

    public:
    	InterruptHandle() :
    		PollerHandle(DummyIOHandle)
    	{}
    	
    	void addHandle(PollerHandle& h) {
    		handles.push(&h);
    	}
    	
    	PollerHandle* getHandle() {
    		PollerHandle* handle = handles.front();
    		handles.pop();
    		return handle;
    	}
    	
    	bool queuedHandles() {
    		return handles.size() > 0;
    	}
    };
    
    const int epollFd;
    bool isShutdown;
    InterruptHandle interruptHandle;
    ::sigset_t sigMask;

    static ::__uint32_t directionToEpollEvent(Poller::Direction dir) {
        switch (dir) {
            case Poller::INPUT:  return ::EPOLLIN;
            case Poller::OUTPUT: return ::EPOLLOUT;
            case Poller::INOUT:  return ::EPOLLIN | ::EPOLLOUT;
            default: return 0;
        }
    }

    static Poller::EventType epollToDirection(::__uint32_t events) {
        // POLLOUT & POLLHUP are mutually exclusive really, but at least socketpairs
        // can give you both!
        events = (events & ::EPOLLHUP) ? events & ~::EPOLLOUT : events;
        ::__uint32_t e = events & (::EPOLLIN | ::EPOLLOUT);
        switch (e) {
            case ::EPOLLIN: return Poller::READABLE;
            case ::EPOLLOUT: return Poller::WRITABLE;
            case ::EPOLLIN | ::EPOLLOUT: return Poller::READ_WRITABLE;
            default:
              return (events & (::EPOLLHUP | ::EPOLLERR)) ?
                    Poller::DISCONNECTED : Poller::INVALID;
        }
    }

    PollerPrivate() :
        epollFd(::epoll_create(DefaultFds)),
        isShutdown(false) {
        QPID_POSIX_CHECK(epollFd);
        ::sigemptyset(&sigMask);
        // Add always readable fd into our set (but not listening to it yet)
        ::epoll_event epe;
        epe.events = 0;
        epe.data.u64 = 0;
        QPID_POSIX_CHECK(::epoll_ctl(epollFd, EPOLL_CTL_ADD, alwaysReadableFd, &epe));   
    }

    ~PollerPrivate() {
        // It's probably okay to ignore any errors here as there can't be data loss
        ::close(epollFd);
    }
    
    void interrupt(bool all=false) {
	    ::epoll_event epe;
	    if (all) {
	        // Not EPOLLONESHOT, so we eventually get all threads
		    epe.events = ::EPOLLIN;
		    epe.data.u64 = 0; // Keep valgrind happy
	    } else {
	    	// Use EPOLLONESHOT so we only wake a single thread
		    epe.events = ::EPOLLIN | ::EPOLLONESHOT;
		    epe.data.u64 = 0; // Keep valgrind happy
		    epe.data.ptr = &static_cast<PollerHandle&>(interruptHandle);	    
	    }
	    QPID_POSIX_CHECK(::epoll_ctl(epollFd, EPOLL_CTL_MOD, alwaysReadableFd, &epe));	
    }
    
    void interruptAll() {
    	interrupt(true);
    }
};

PollerPrivate::ReadablePipe PollerPrivate::alwaysReadable;
int PollerPrivate::alwaysReadableFd = alwaysReadable.getFD();

void Poller::addFd(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    ::epoll_event epe;
    int op;

    if (eh.isIdle()) {
        op = EPOLL_CTL_ADD;
        epe.events = PollerPrivate::directionToEpollEvent(dir) | ::EPOLLONESHOT;
    } else {
        assert(eh.isActive());
        op = EPOLL_CTL_MOD;
        epe.events = eh.events | PollerPrivate::directionToEpollEvent(dir);
    }
    epe.data.u64 = 0; // Keep valgrind happy
    epe.data.ptr = &handle;

    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, op, eh.fd, &epe));

    // Record monitoring state of this fd
    eh.events = epe.events;
    eh.setActive();
}

void Poller::delFd(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(!eh.isIdle());
    int rc = ::epoll_ctl(impl->epollFd, EPOLL_CTL_DEL, eh.fd, 0);
    // Ignore EBADF since deleting a nonexistent fd has the overall required result!
    // And allows the case where a sloppy program closes the fd and then does the delFd()
    if (rc == -1 && errno != EBADF) {
	    QPID_POSIX_CHECK(rc);
    }
    eh.setIdle();
}

// modFd is equivalent to delFd followed by addFd
void Poller::modFd(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(!eh.isIdle());

    ::epoll_event epe;
    epe.events = PollerPrivate::directionToEpollEvent(dir) | ::EPOLLONESHOT;
    epe.data.u64 = 0; // Keep valgrind happy
    epe.data.ptr = &handle;

    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, EPOLL_CTL_MOD, eh.fd, &epe));

    // Record monitoring state of this fd
    eh.events = epe.events;
    eh.setActive();
}

void Poller::rearmFd(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(eh.isInactive());

    ::epoll_event epe;
    epe.events = eh.events;
    epe.data.u64 = 0; // Keep valgrind happy
    epe.data.ptr = &handle;

    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, EPOLL_CTL_MOD, eh.fd, &epe));

    eh.setActive();
}

void Poller::shutdown() {
    // NB: this function must be async-signal safe, it must not
    // call any function that is not async-signal safe.

    // Allow sloppy code to shut us down more than once
    if (impl->isShutdown)
        return;

    // Don't use any locking here - isShutdown will be visible to all
    // after the epoll_ctl() anyway (it's a memory barrier)
    impl->isShutdown = true;

    impl->interruptAll();
}

bool Poller::interrupt(PollerHandle& handle) {
	{
	    PollerHandlePrivate& eh = *handle.impl;
	    ScopedLock<Mutex> l(eh.lock);
	    if (eh.isInactive()) {
	    	return false;
	    }
	    ::epoll_event epe;
	    epe.events = 0;
	    epe.data.u64 = 0; // Keep valgrind happy
	    epe.data.ptr = &eh;
	    QPID_POSIX_CHECK(::epoll_ctl(impl->epollFd, EPOLL_CTL_MOD, eh.fd, &epe));
	    eh.setInactive();
	}

	PollerPrivate::InterruptHandle& ih = impl->interruptHandle;
    PollerHandlePrivate& eh = *static_cast<PollerHandle&>(ih).impl;
    ScopedLock<Mutex> l(eh.lock);
	ih.addHandle(handle);
    
	impl->interrupt();
    eh.setActive();
    return true;
}

void Poller::run() {
	// Make sure we can't be interrupted by signals at a bad time
	::sigset_t ss;
	::sigfillset(&ss);
    ::pthread_sigmask(SIG_SETMASK, &ss, 0);

    do {
        Event event = wait();

        // If can read/write then dispatch appropriate callbacks        
        if (event.handle) {
            event.process();
        } else {
            // Handle shutdown
            switch (event.type) {
            case SHUTDOWN:
                return;
            default:
                // This should be impossible
                assert(false);
            }
        }
    } while (true);
}

Poller::Event Poller::wait(Duration timeout) {
    epoll_event epe;
    int timeoutMs = (timeout == TIME_INFINITE) ? -1 : timeout / TIME_MSEC;
    AbsTime targetTimeout = 
        (timeout == TIME_INFINITE) ?
            FAR_FUTURE :
            AbsTime(now(), timeout); 

    // Repeat until we weren't interrupted by signal
    do {
        PollerHandleDeletionManager.markAllUnusedInThisThread();
        // Need to run on kernels without epoll_pwait()
        // - fortunately in this case we don't really need the atomicity of epoll_pwait()
#if 1
        sigset_t os;
        pthread_sigmask(SIG_SETMASK, &impl->sigMask, &os);
        int rc = ::epoll_wait(impl->epollFd, &epe, 1, timeoutMs);
        pthread_sigmask(SIG_SETMASK, &os, 0);
#else
        int rc = ::epoll_pwait(impl->epollFd, &epe, 1, timeoutMs, &impl->sigMask);
#endif
        // Check for shutdown
        if (impl->isShutdown) {
            PollerHandleDeletionManager.markAllUnusedInThisThread();
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
            if (eh.isActive()) {

                // Check if this is an interrupt
                if (handle == &impl->interruptHandle) {
                	PollerHandle* wrappedHandle = impl->interruptHandle.getHandle();
                	// If there is an interrupt queued behind this one we need to arm it
                	// We do it this way so that another thread can pick it up
                	if (impl->interruptHandle.queuedHandles()) {
                		impl->interrupt();
                		eh.setActive();
                	} else {
                		eh.setInactive();
                	}
                	return Event(wrappedHandle, INTERRUPTED);
                }

                // If the connection has been hungup we could still be readable
                // (just not writable), allow us to readable until we get here again
                if (epe.events & ::EPOLLHUP) {
                    if (eh.isHungup()) {
                        return Event(handle, DISCONNECTED);
                    }
                    eh.setHungup();
                } else {
                    eh.setInactive();
                }
                return Event(handle, PollerPrivate::epollToDirection(epe.events));
            } else if (eh.isDeleted()) {
                // The handle has been deleted whilst still active and so must be removed
                // from the poller
                int rc = ::epoll_ctl(impl->epollFd, EPOLL_CTL_DEL, eh.fd, 0);
                // Ignore EBADF since it's quite likely that we could race with closing the fd
                if (rc == -1 && errno != EBADF) {
                    QPID_POSIX_CHECK(rc);
                }
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
        // If the wait wasn't indefinite, we check whether we are after the target wait
        // time or not
        if (timeoutMs == -1) {
            continue;
        }
        if (rc == 0 && now() > targetTimeout) {
            PollerHandleDeletionManager.markAllUnusedInThisThread();
            return Event(0, TIMEOUT);
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
