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
#include "qpid/sys/AtomicCount.h"
#include "qpid/sys/DeletionManager.h"
#include "qpid/sys/posix/check.h"
#include "qpid/sys/posix/PrivatePosix.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Condition.h"

#include <poll.h>
#include <errno.h>
#include <signal.h>

#include <assert.h>
#include <queue>
#include <set>
#include <exception>

/*
 *
 * This is a qpid::sys::Poller implementation for Posix systems.
 *
 * This module follows the structure of the Linux EpollPoller as closely as possible
 * to simplify maintainability.  Noteworthy differences:
 *
 * The Linux epoll_xxx() calls present one event at a time to multiple callers whereas poll()
 * returns one or more events to a single caller.  The EventStream class layers a
 * "one event per call" view of the poll() result to multiple threads.
 *
 * The HandleSet is the master set of in-use PollerHandles.  The EventStream
 * maintains a snapshot copy taken just before the call to poll() that remains static
 * until all flagged events have been processed.
 *
 * There is an additional window where the PollerHandlePrivate class may survive the
 * parent PollerHandle destructor, i.e. between snapshots.
 *
 * Safe interrupting of the Poller is implemented using the "self-pipe trick".
 *
 */

namespace qpid {
namespace sys {

// Deletion manager to handle deferring deletion of PollerHandles to when they definitely aren't being used
DeletionManager<PollerHandlePrivate> PollerHandleDeletionManager;

//  Instantiate (and define) class static for DeletionManager
template <>
DeletionManager<PollerHandlePrivate>::AllThreadsStatuses DeletionManager<PollerHandlePrivate>::allThreadsStatuses(0);

class PollerHandlePrivate {
    friend class Poller;
    friend class PollerPrivate;
    friend class PollerHandle;
    friend class HandleSet;

    enum FDStat {
        ABSENT,
        MONITORED,
        INACTIVE,
        HUNGUP,
        MONITORED_HUNGUP,
        INTERRUPTED,
        INTERRUPTED_HUNGUP,
        DELETED
    };

    short events;
    const IOHandle* ioHandle;
    PollerHandle* pollerHandle;
    FDStat stat;
    Mutex lock;

    PollerHandlePrivate(const IOHandle* h, PollerHandle* p) :
      events(0),
      ioHandle(h),
      pollerHandle(p),
      stat(ABSENT) {
    }

    int fd() const {
        return ioHandle->fd;
    }

    bool isActive() const {
        return stat == MONITORED || stat == MONITORED_HUNGUP;
    }

    void setActive() {
        stat = (stat == HUNGUP || stat == INTERRUPTED_HUNGUP)
            ? MONITORED_HUNGUP
            : MONITORED;
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
        return
            stat == MONITORED_HUNGUP ||
            stat == HUNGUP ||
            stat == INTERRUPTED_HUNGUP;
    }

    void setHungup() {
        assert(stat == MONITORED);
        stat = HUNGUP;
    }

    bool isInterrupted() const {
        return stat == INTERRUPTED || stat == INTERRUPTED_HUNGUP;
    }

    void setInterrupted() {
        stat = (stat == MONITORED_HUNGUP || stat == HUNGUP)
            ? INTERRUPTED_HUNGUP
            : INTERRUPTED;
    }

    bool isDeleted() const {
        return stat == DELETED;
    }

    void setDeleted() {
        stat = DELETED;
    }
};

PollerHandle::PollerHandle(const IOHandle& h) :
    impl(new PollerHandlePrivate(&h, this))
{}

PollerHandle::~PollerHandle() {
    {
    ScopedLock<Mutex> l(impl->lock);
    if (impl->isDeleted()) {
        return;
    }
    impl->pollerHandle = 0;
    if (impl->isInterrupted()) {
        impl->setDeleted();
        return;
    }
    assert(impl->isIdle());
    impl->setDeleted();
    }
    PollerHandleDeletionManager.markForDeletion(impl);
}

class HandleSet
{
    Mutex lock;
    bool stale;
    std::set<PollerHandlePrivate*> handles;
  public:
    HandleSet() : stale(true) {}
    void add(PollerHandlePrivate*);
    void remove(PollerHandlePrivate*);
    void cleanup();
    bool snapshot(std::vector<PollerHandlePrivate *>& , std::vector<struct ::pollfd>&);
    void setStale();
};

void HandleSet::add(PollerHandlePrivate* h)
{
    ScopedLock<Mutex> l(lock);
    handles.insert(h);
}
void HandleSet::remove(PollerHandlePrivate* h)
{
    ScopedLock<Mutex> l(lock);
    handles.erase(h);
}
void HandleSet::cleanup()
{
    // Inform all registered handles of disconnection
    std::set<PollerHandlePrivate*> copy;
    handles.swap(copy);
    for (std::set<PollerHandlePrivate*>::const_iterator i = copy.begin(); i != copy.end(); ++i) {
        PollerHandlePrivate& eh = **i;
        {
            ScopedLock<Mutex> l(eh.lock);
            if (!eh.isDeleted()) {
                Poller::Event event((*i)->pollerHandle, Poller::DISCONNECTED);
                event.process();
            }
        }
    }
}
void HandleSet::setStale()
{
    // invalidate cached pollfds for next snapshot
    ScopedLock<Mutex> l(lock);
    stale = true;
}

/**
 * Concrete implementation of Poller to use Posix poll()
 * interface
 */
class PollerPrivate {
    friend class Poller;
    friend class EventStream;
    friend class HandleSet;

    class SignalPipe {
        /**
         * Used to wakeup a thread in ::poll()
         */
        int fds[2];
        bool signaled;
        bool permanent;
        Mutex lock;
    public:
        SignalPipe() : signaled(false), permanent(false) {
            QPID_POSIX_CHECK(::pipe(fds));
        }

        ~SignalPipe() {
            ::close(fds[0]);
            ::close(fds[1]);
        }

        int getFD() {
            return fds[0];
        }

        bool isSet() {
            return signaled;
        }

        void set() {
            ScopedLock<Mutex> l(lock);
            if (signaled)
                return;
            signaled = true;
            QPID_POSIX_CHECK(::write(fds[1], " ", 1));
        }

        void reset() {
            if (permanent)
                return;
            ScopedLock<Mutex> l(lock);
            if (signaled) {
                char ignore;
                QPID_POSIX_CHECK(::read(fds[0], &ignore, 1));
                signaled = false;
            }
        }

        void setPermanently() {
            // async signal safe calls only.  No locking.
            permanent = true;
            signaled = true;
            QPID_POSIX_CHECK(::write(fds[1], "  ", 2));
            // poll() should never block now
        }
    };

    // Collect pending events and serialize access.  Maintain array of pollfd structs.
    class EventStream {
        typedef Poller::Event Event;
        PollerPrivate& pollerPrivate;
        SignalPipe& signalPipe;
        std::queue<PollerHandlePrivate*> interruptedHandles;
        std::vector<struct ::pollfd> pollfds;
        std::vector<PollerHandlePrivate*> pollHandles;
        Mutex streamLock;
        Mutex serializeLock;
        Condition serializer;
        bool busy;
        int currentPollfd;
        int pollCount;
        int waiters;

    public:

        EventStream(PollerPrivate* p) : pollerPrivate(*p), signalPipe(p->signalPipe), busy(false),
                                        currentPollfd(0), pollCount(0), waiters(0) {
            // The signal pipe is the first element of pollfds and pollHandles
            pollfds.reserve(8);
            pollfds.resize(1);
            pollfds[0].fd = pollerPrivate.signalPipe.getFD();
            pollfds[0].events = POLLIN;
            pollfds[0].revents = 0;

            pollHandles.reserve(8);
            pollHandles.resize(1);
            pollHandles[0] = 0;
        }

        void addInterrupt(PollerHandle& handle) {
            ScopedLock<Mutex> l(streamLock);
            interruptedHandles.push(handle.impl);
        }

        // Serialize access to the stream.
        Event next(Duration timeout) {
            AbsTime targetTimeout =
                (timeout == TIME_INFINITE) ?
                FAR_FUTURE :
                AbsTime(now(), timeout);


            ScopedLock<Mutex> l(serializeLock);
            Event event(0, Poller::INVALID);
            while (busy) {
                waiters++;
                bool timedout = !serializer.wait(serializeLock, targetTimeout);
                waiters--;

                if (busy && timedout) {
                    return Event(0, Poller::TIMEOUT);
                }
            }
            busy = true;
            {
                ScopedUnlock<Mutex> ul(serializeLock);
                event = getEvent(targetTimeout);
            }
            busy = false;

            if (waiters > 0)
                serializer.notify();
            return event;
        }

        Event getEvent(AbsTime targetTimeout) {
            bool timeoutPending = false;

            ScopedLock<Mutex> l(streamLock); // hold lock except for poll()

            // loop until poll event, async interrupt, or timeout
            while (true) {

                // first check for any interrupts
                while (interruptedHandles.size() > 0) {
                    PollerHandlePrivate& eh = *interruptedHandles.front();
                    interruptedHandles.pop();
                    {
                        ScopedLock<Mutex> lk(eh.lock);
                        if (!eh.isDeleted()) {
                            if (!eh.isIdle()) {
                                eh.setInactive();
                            }

                            // nullify the corresponding pollfd event, if any
                            int ehfd = eh.fd();
                            std::vector<struct ::pollfd>::iterator i = pollfds.begin() + 1; // skip self pipe at front
                            for (; i != pollfds.end(); i++) {
                                if (i->fd == ehfd) {
                                    i->events = 0;
                                    if (i->revents) {
                                        i->revents = 0;
                                        pollCount--;
                                    }
                                    break;
                                }
                            }
                            return Event(eh.pollerHandle, Poller::INTERRUPTED);
                        }
                    }
                    PollerHandleDeletionManager.markForDeletion(&eh);
                }

                // Check for shutdown
                if (pollerPrivate.isShutdown) {
                    PollerHandleDeletionManager.markAllUnusedInThisThread();
                    return Event(0, Poller::SHUTDOWN);
                }

                // search for any remaining events from earlier poll()
                int nfds = pollfds.size();
                while ((pollCount > 0) && (currentPollfd < nfds)) {
                    int index = currentPollfd++;
                    short evt = pollfds[index].revents;
                    if (evt != 0) {
                        pollCount--;
                        PollerHandlePrivate& eh = *pollHandles[index];
                        ScopedLock<Mutex> l(eh.lock);
                        // stop polling this handle until resetMode()
                        pollfds[index].events = 0;

                        // the handle could have gone inactive since snapshot taken
                        if (eh.isActive()) {
                            PollerHandle* handle = eh.pollerHandle;
                            assert(handle);

                            // If the connection has been hungup we could still be readable
                            // (just not writable), allow us to readable until we get here again
                            if (evt & POLLHUP) {
                                if (eh.isHungup()) {
                                    eh.setInactive();
                                    // Don't set up last Handle so that we don't reset this handle
                                    // on re-entering Poller::wait. This means that we will never
                                    // be set active again once we've returned disconnected, and so
                                    // can never be returned again.
                                    return Event(handle, Poller::DISCONNECTED);
                                }
                                eh.setHungup();
                            } else {
                                eh.setInactive();
                            }
                            return Event(handle, PollerPrivate::epollToDirection(evt));
                        }
                    }
                }

                if (timeoutPending) {
                    return Event(0, Poller::TIMEOUT);
                }

                // no outstanding events, poll() for more
                {
                    ScopedUnlock<Mutex> ul(streamLock);

                    bool refreshed = pollerPrivate.registeredHandles.snapshot(pollHandles, pollfds);
                    if (refreshed) {
                        // we just drained all interruptedHandles and got a fresh snapshot
                        PollerHandleDeletionManager.markAllUnusedInThisThread();
                    }

                    if (!signalPipe.isSet()) {
                        int timeoutMs = -1;
                        if (!(targetTimeout == FAR_FUTURE)) {
                            timeoutMs = Duration(now(), targetTimeout) / TIME_MSEC;
                            if (timeoutMs < 0)
                                timeoutMs = 0;
                        }

                        pollCount = ::poll(&pollfds[0], pollfds.size(), timeoutMs);

                        if (pollCount ==-1 && errno != EINTR) {
                            QPID_POSIX_CHECK(pollCount);
                        }
                        else if (pollCount == 0) {
                            // timeout, unless shutdown or interrupt arrives in another thread
                            timeoutPending = true;
                        }
                        else {
                            if (pollfds[0].revents) {
                                pollCount--; // signal pipe doesn't count
                            }
                        }
                    }
                    else
                        pollCount = 0;
                    signalPipe.reset();
                }
                currentPollfd = 1;
            }
        }
    };

    bool isShutdown;
    HandleSet registeredHandles;
    AtomicCount threadCount;
    SignalPipe signalPipe;
    EventStream eventStream;

    static short directionToEpollEvent(Poller::Direction dir) {
        switch (dir) {
            case Poller::INPUT:  return POLLIN;
            case Poller::OUTPUT: return POLLOUT;
            case Poller::INOUT:  return POLLIN | POLLOUT;
            default: return 0;
        }
    }

    static Poller::EventType epollToDirection(short events) {
        // POLLOUT & POLLHUP are mutually exclusive really, but at least socketpairs
        // can give you both!
        events = (events & POLLHUP) ? events & ~POLLOUT : events;
        short e = events & (POLLIN | POLLOUT);
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
        isShutdown(false), eventStream(this) {
    }

    ~PollerPrivate() {}

    void resetMode(PollerHandlePrivate& handle);

    void interrupt() {
        signalPipe.set();
    }

    void interruptAll() {
        // be async signal safe
        signalPipe.setPermanently();
    }
};


void Poller::registerHandle(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(eh.isIdle());

    eh.setActive();
    impl->registeredHandles.add(handle.impl);
    // not stale until monitored
}

void Poller::unregisterHandle(PollerHandle& handle) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(!eh.isIdle());

    eh.setIdle();
    impl->registeredHandles.remove(handle.impl);
    impl->registeredHandles.setStale();
    impl->interrupt();
}

void PollerPrivate::resetMode(PollerHandlePrivate& eh) {
    PollerHandle* ph;
    {
        // Called after an event has been processed for a handle
        ScopedLock<Mutex> l(eh.lock);
        assert(!eh.isActive());

        if (eh.isIdle() || eh.isDeleted()) {
            return;
        }

        if (eh.events==0) {
            eh.setActive();
            return;
        }

        if (!eh.isInterrupted()) {
            // Handle still in use, allow events to resume.
            eh.setActive();
            registeredHandles.setStale();
            // Ouch. This scales poorly for large handle sets.
            // TODO: avoid new snapshot, perhaps create an index to pollfds or a
            // pending reset queue to be processed before each poll().  However, the real
            // scalable solution is to implement the OS-specific epoll equivalent.
            interrupt();
            return;
        }
        ph = eh.pollerHandle;
    }

    eventStream.addInterrupt(*ph);
    interrupt();
}

void Poller::monitorHandle(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(!eh.isIdle());

    short oldEvents = eh.events;
    eh.events |= PollerPrivate::directionToEpollEvent(dir);

    // If no change nothing more to do - avoid unnecessary system call
    if (oldEvents==eh.events) {
        return;
    }

    // If we're not actually listening wait till we are to perform change
    if (!eh.isActive()) {
        return;
    }

    // tell polling thread to update its pollfds
    impl->registeredHandles.setStale();
    impl->interrupt();
}

void Poller::unmonitorHandle(PollerHandle& handle, Direction dir) {
    PollerHandlePrivate& eh = *handle.impl;
    ScopedLock<Mutex> l(eh.lock);
    assert(!eh.isIdle());

    short oldEvents = eh.events;
    eh.events &= ~PollerPrivate::directionToEpollEvent(dir);

    // If no change nothing more to do - avoid unnecessary system call
    if (oldEvents==eh.events) {
        return;
    }

    // If we're not actually listening wait till we are to perform change
    if (!eh.isActive()) {
        return;
    }

    impl->registeredHandles.setStale();
    impl->interrupt();
}

void Poller::shutdown() {
    // NB: this function must be async-signal safe, it must not
    // call any function that is not async-signal safe.

    // Allow sloppy code to shut us down more than once
    if (impl->isShutdown)
        return;

    // Don't use any locking here - isShutdown will be visible to all
    // after the write() anyway (it's a memory barrier)
    impl->isShutdown = true;

    impl->interruptAll();
}

bool Poller::interrupt(PollerHandle& handle) {
    {
        PollerHandlePrivate& eh = *handle.impl;
        ScopedLock<Mutex> l(eh.lock);
        if (eh.isIdle() || eh.isDeleted()) {
            return false;
        }

        if (eh.isInterrupted()) {
            return true;
        }

        if (eh.isInactive()) {
            eh.setInterrupted();
            return true;
        }
        eh.setInterrupted();
        eh.events = 0;
    }

    impl->registeredHandles.setStale();
    impl->eventStream.addInterrupt(handle);
    impl->interrupt();
    return true;
}

void Poller::run() {
    // Ensure that we exit thread responsibly under all circumstances
    try {
        // Make sure we can't be interrupted by signals at a bad time
        ::sigset_t ss;
        ::sigfillset(&ss);
        ::pthread_sigmask(SIG_SETMASK, &ss, 0);

        ++(impl->threadCount);
        do {
            Event event = wait();

            // If can read/write then dispatch appropriate callbacks
            if (event.handle) {
                event.process();
            } else {
                // Handle shutdown
                switch (event.type) {
                case SHUTDOWN:
                    //last thread to respond to shutdown cleans up:
                    if (--(impl->threadCount) == 0) impl->registeredHandles.cleanup();
                    PollerHandleDeletionManager.destroyThreadState();
                    return;
                default:
                    // This should be impossible
                    assert(false);
                }
            }
        } while (true);
    } catch (const std::exception& e) {
        QPID_LOG(error, "IO worker thread exiting with unhandled exception: " << e.what());
    }
    PollerHandleDeletionManager.destroyThreadState();
    --(impl->threadCount);
}

bool Poller::hasShutdown()
{
    return impl->isShutdown;
}

Poller::Event Poller::wait(Duration timeout) {
    static __thread PollerHandlePrivate* lastReturnedHandle = 0;

    if (lastReturnedHandle) {
        impl->resetMode(*lastReturnedHandle);
        lastReturnedHandle = 0;
    }

    Event event = impl->eventStream.next(timeout);

    switch (event.type) {
    case INTERRUPTED:
    case READABLE:
    case WRITABLE:
    case READ_WRITABLE:
        lastReturnedHandle = event.handle->impl;
        break;
    default:
        ;
    }

    return event;
}

// Concrete constructors
Poller::Poller() :
    impl(new PollerPrivate())
{}

Poller::~Poller() {
    delete impl;
}


bool HandleSet::snapshot(std::vector<PollerHandlePrivate *>& hs , std::vector<struct ::pollfd>& fds)
{
    // Element 0 of the vectors is always the signal pipe, leave undisturbed
    {
        ScopedLock<Mutex> l(lock);
        if (!stale)
            return false;       // no refresh done

        hs.resize(1);
        for (std::set<PollerHandlePrivate*>::const_iterator i = handles.begin(); i != handles.end(); ++i) {
            hs.push_back(*i);
        }
        stale = false;
        // have copy of handle set (in vector form), drop the lock and build the pollfds
    }

    // sync pollfds to same sizing as the handles
    int sz = hs.size();
    fds.resize(sz);

    for (int j = 1; j < sz; ++j) {
        // create a pollfd entry for each handle
        struct ::pollfd& pollfd = fds[j];
        PollerHandlePrivate& eh = *hs[j];
        ScopedLock<Mutex> lk(eh.lock);

        if (!eh.isInactive() && !eh.isDeleted()) {
            pollfd.fd = eh.fd();
            pollfd.events = eh.events;
        } else {
            pollfd.fd = -1;     // tell poll() to ignore this fd
            pollfd.events = 0;
        }
    }
    return true;
}


}}
