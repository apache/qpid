/* 
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

// TODO aconway 2006-12-15: Locking review.

// TODO aconway 2006-12-15: use Descriptor pointers everywhere,
// get them from channel, pass them to Event constructors.
// Eliminate lookup.


#include "EventChannel.h"
#include "check.h"

#include "qpid/QpidError.h"
#include "qpid/sys/AtomicCount.h"

#include <mqueue.h>
#include <string.h>
#include <iostream>

#include <sys/errno.h>
#include <sys/socket.h>
#include <sys/epoll.h>

#include <typeinfo>
#include <iostream>
#include <queue>

#include <boost/ptr_container/ptr_map.hpp>
#include <boost/noncopyable.hpp>
#include <boost/bind.hpp>

using namespace std;


namespace qpid {
namespace sys {


// ================================================================
// Private class declarations

namespace {

typedef enum { IN, OUT } Direction;

typedef std::pair<Event*, Event*> EventPair;

/**
 * Template to zero out a C-struct on construction. Avoids uninitialized memory
 * warnings from valgrind or other mem checking tool.
 */
template <class T> struct CleanStruct : public T {
    CleanStruct() { memset(this, 0, sizeof(*this)); }
};

} // namespace

/**
 * Queue of events corresponding to one IO direction (IN or OUT).
 * Each Descriptor contains two Queues.
 */
class EventChannel::Queue : private boost::noncopyable
{
  public:
    Queue(Descriptor& container, Direction dir);

    /** Called by Event classes in prepare() */ 
    void push(Event* e);

    /** Called when epoll wakes.
     *@return The next completed event or 0.
     */
    Event* wake(uint32_t epollFlags);

    Event* pop() { Event* e = queue.front(); queue.pop_front(); return e; }

    bool empty() { return queue.empty(); }

    void setBit(uint32_t &epollFlags);

    void shutdown();
    
  private:
    typedef std::deque<Event*> EventQ; 

    inline bool isMyEvent(uint32_t flags) { return flags | myEvent; }

    Mutex& lock;                // Shared with Descriptor.
    Descriptor& descriptor;
    uint32_t myEvent;           // Epoll event flag.
    EventQ queue;
};


/**
 * Manages a file descriptor in an epoll set.
 *
 * Can be shutdown and re-activated for the same file descriptor.
 */
class EventChannel::Descriptor : private boost::noncopyable {
  public:
    explicit Descriptor(int fd) : epollFd(-1), myFd(fd),
                   inQueue(*this, IN), outQueue(*this, OUT) {}

    void activate(int epollFd_);

    /** Epoll woke up for this descriptor. */
    Event* wake(uint32_t epollEvents);

    /** Shut down: close and remove file descriptor.
     * May be re-activated if fd is reused.
     */
    void shutdown();

    // TODO aconway 2006-12-18: Nasty. Need to clean up interaction.
    void shutdownUnsafe();      

    bool isShutdown() { return epollFd == -1; }

    Queue& getQueue(Direction d) { return d==IN ? inQueue : outQueue; }
    int getFD() const { return myFd; }

  private:
    void update();
    void epollCtl(int op, uint32_t events);
    Queue* pick();

    Mutex lock;
    int epollFd;
    int myFd;
    Queue inQueue, outQueue;
    bool preferIn;

  friend class Queue;
};

 
/**
 * Holds a map of Descriptors, which do most of the work.
 */
class EventChannel::Impl {
  public:
    Impl(int size = 256);

    ~Impl();

    /**
     * Activate descriptor
     */
    void activate(Descriptor& d) {
    	d.activate(epollFd);
    }

    /** Wait for an event, return 0 on timeout */
    Event* wait(Duration timeout);

    void shutdown();

  private:

    Monitor monitor;     
    int epollFd;
    int shutdownPipe[2];
    AtomicCount nWaiters;
    bool isShutdown;
};


// ================================================================
// EventChannel::Queue::implementation.

static const char* shutdownMsg = "Event queue shut down.";

EventChannel::Queue::Queue(Descriptor& d, Direction dir) :
    lock(d.lock), descriptor(d),
    myEvent(dir==IN ? EPOLLIN : EPOLLOUT)
{}

void EventChannel::Queue::push(Event* e) {
    Mutex::ScopedLock l(lock);
    if (descriptor.isShutdown())
        THROW_QPID_ERROR(INTERNAL_ERROR, shutdownMsg);
    queue.push_back(e);
    descriptor.update(); 
}

void EventChannel::Queue::setBit(uint32_t &epollFlags) {
    if (queue.empty())
        epollFlags &= ~myEvent;
    else
        epollFlags |= myEvent;
}

// TODO aconway 2006-12-20: REMOVE
Event* EventChannel::Queue::wake(uint32_t epollFlags) {
    // Called with lock held.
    if (!queue.empty() && (isMyEvent(epollFlags))) {
        assert(!queue.empty());
        Event* e = queue.front();
        assert(e);
        if (!e->getException()) {
            // TODO aconway 2006-12-20: Can/should we move event completion
            // out into dispatch() so it doesn't happen in Descriptor locks?
            e->complete(descriptor);
        }
        queue.pop_front();
        return e;
    }
    return 0;
}
        
void EventChannel::Queue::shutdown() {
    // Mark all pending events with a shutdown exception.
    // The server threads will remove and dispatch the events.
    // 
    qpid::QpidError ex(INTERNAL_ERROR, shutdownMsg, SRCLINE);
    for_each(queue.begin(), queue.end(),
             boost::bind(&Event::setException, _1, ex));
}


// ================================================================
// Descriptor


void EventChannel::Descriptor::activate(int epollFd_) {
    Mutex::ScopedLock l(lock);
    if (isShutdown()) {
        epollFd = epollFd_;     // We're back in business.
        epollCtl(EPOLL_CTL_ADD, 0);
    }
}

void EventChannel::Descriptor::shutdown() {
    Mutex::ScopedLock l(lock);
    shutdownUnsafe();
}

void EventChannel::Descriptor::shutdownUnsafe() {
    // Caller holds lock.
    ::close(myFd);
    epollFd = -1;               // Mark myself as shutdown.
    inQueue.shutdown();
    outQueue.shutdown();
}

// TODO aconway 2006-12-20: Inline into wake().
void EventChannel::Descriptor::update() {
    // Caller holds lock.
    if (isShutdown())             // Nothing to do
        return;
    uint32_t events =  EPOLLONESHOT | EPOLLERR | EPOLLHUP;
    inQueue.setBit(events);
    outQueue.setBit(events);
    epollCtl(EPOLL_CTL_MOD, events);
}
    
void EventChannel::Descriptor::epollCtl(int op, uint32_t events) {
    // Caller holds lock
    assert(!isShutdown());
    CleanStruct<epoll_event> ee;
    ee.data.ptr = this;
    ee.events = events;
    int status = ::epoll_ctl(epollFd, op, myFd, &ee);
    if (status < 0) {
    	if (errno == EEXIST) // It's okay to add an existing fd
    		return;
        else if (errno == EBADF)     // FD was closed externally.
            shutdownUnsafe();
        else
            throw QPID_POSIX_ERROR(errno);
    }
}
    

EventChannel::Queue* EventChannel::Descriptor::pick() {
    if (inQueue.empty() && outQueue.empty()) 
        return 0;
    if (inQueue.empty() || outQueue.empty())
        return !inQueue.empty() ? &inQueue : &outQueue;
    // Neither is empty, pick fairly.
    preferIn = !preferIn;
    return preferIn ? &inQueue : &outQueue;
}

Event* EventChannel::Descriptor::wake(uint32_t epollEvents) {
    Mutex::ScopedLock l(lock);
    // On error, shut down the Descriptor and both queues.
    if (epollEvents & (EPOLLERR | EPOLLHUP)) {
        shutdownUnsafe();
        // TODO aconway 2006-12-20: This error handling models means
        // that any error reported by epoll will result in a shutdown
        // exception on the events. Can we get more accurate error
        // reporting somehow?
    }
    Queue*q = 0;
    bool in = (epollEvents & EPOLLIN);
    bool out = (epollEvents & EPOLLOUT);
    if ((in && out) || isShutdown()) 
        q = pick();         // Choose fairly, either non-empty queue.
    else if (in) 
        q = &inQueue;
    else if (out) 
        q = &outQueue;
    Event* e = (q && !q->empty()) ? q->pop() : 0;
    update();
    if (e)
        e->complete(*this);
    return e;
}



// ================================================================
// EventChannel::Impl


EventChannel::Impl::Impl(int epollSize):
    epollFd(-1), isShutdown(false)
{
    // Create the epoll file descriptor.
    epollFd = epoll_create(epollSize);
    QPID_POSIX_CHECK(epollFd);

    // Create a pipe and write a single byte.  The byte is never
    // read so the pipes read fd is always ready for read.
    // We activate the FD when there are messages in the queue.
    QPID_POSIX_CHECK(::pipe(shutdownPipe));
    static char zero = '\0';
    QPID_POSIX_CHECK(::write(shutdownPipe[1], &zero, 1));
}

EventChannel::Impl::~Impl() {
    shutdown();
    ::close(epollFd);
    ::close(shutdownPipe[0]);
    ::close(shutdownPipe[1]);
}


void EventChannel::Impl::shutdown() {
    Monitor::ScopedLock l(monitor);
    if (!isShutdown) { // I'm starting shutdown.
        isShutdown = true;
        if (nWaiters == 0)
            return;

        // TODO aconway 2006-12-20: If I just close the epollFd will
        // that wake all threads? If so with what? Would be simpler than:

        CleanStruct<epoll_event> ee;
        ee.data.ptr = 0;
        ee.events = EPOLLIN;
        QPID_POSIX_CHECK(
            epoll_ctl(epollFd, EPOLL_CTL_ADD, shutdownPipe[0], &ee));
    }
    // Wait for nWaiters to get out.
    while (nWaiters > 0) {
        monitor.wait();
    }
}

// TODO aconway 2006-12-20: DEBUG remove
struct epoll {
    epoll(uint32_t e) : events(e) { }
    uint32_t events;
};

#define BIT(X) out << ((e.events & X) ? __STRING(X) "." : "")
ostream& operator << (ostream& out, epoll e) {
    out << "epoll_event.events: ";
    BIT(EPOLLIN);
    BIT(EPOLLPRI);
    BIT(EPOLLOUT);
    BIT(EPOLLRDNORM);
    BIT(EPOLLRDBAND);
    BIT(EPOLLWRNORM);
    BIT(EPOLLWRBAND);
    BIT(EPOLLMSG);
    BIT(EPOLLERR);
    BIT(EPOLLHUP);
    BIT(EPOLLONESHOT);
    BIT(EPOLLET);
    return out;
}
    
    
    
/**
 * Wait for epoll to wake up, return the descriptor or 0 on timeout.
 */
Event* EventChannel::Impl::wait(Duration timeoutNs)
{
    {
        Monitor::ScopedLock l(monitor);
        if (isShutdown)
            throw ShutdownException();
    }
    
    // Increase nWaiters for the duration, notify the monitor if I'm
    // the last one out.
    // 
    AtomicCount::ScopedIncrement si(
        nWaiters, boost::bind(&Monitor::notifyAll, &monitor));

    // No lock, all thread safe calls or local variables:
    // 
    const long timeoutMs =
        (timeoutNs == TIME_INFINITE) ? -1 : timeoutNs/TIME_MSEC;
    CleanStruct<epoll_event> ee;
    Event* event = 0;

    // Loop till we get a completed event. Some events may repost
    // themselves and return 0, e.g. incomplete read or write events.
    //TODO aconway 2006-12-20: FIX THIS!
    while (!event) {
        int n = ::epoll_wait(epollFd, &ee, 1, timeoutMs); // Thread safe.
        if (n == 0)             // Timeout
            return 0;
        if (n < 0 && errno == EINTR) // Interrupt, ignore it.
            continue;
        if (n < 0)              
            throw QPID_POSIX_ERROR(errno);
        assert(n == 1);
        Descriptor* ed =
            reinterpret_cast<Descriptor*>(ee.data.ptr);
        if (ed == 0)            // We're being shut-down.
            throw ShutdownException();
        assert(ed != 0);
        event = ed->wake(ee.events);
    }
    return event;
}

//EventChannel::Descriptor& EventChannel::Impl::getDescriptor(int fd) {
//    Mutex::ScopedLock l(monitor);
//    Descriptor& ed = descriptors[fd];
//    ed.activate(epollFd, fd);
//    return ed;
//}


// ================================================================
// EventChannel

EventChannel::shared_ptr EventChannel::create() {
    return shared_ptr(new EventChannel());
}

EventChannel::EventChannel() : impl(new EventChannel::Impl()) {}

EventChannel::~EventChannel() {}

void EventChannel::post(Event& e) 
{
    e.prepare(*impl);
}

Event* EventChannel::wait(Duration timeoutNs)
{
    return impl->wait(timeoutNs);
}

void EventChannel::shutdown() {
    impl->shutdown();
}


// ================================================================
// Event and subclasses.

Event::~Event() {}
    
Exception::shared_ptr_const Event::getException() const {
    return exception;
}

void Event::throwIfException() {
    if (getException())
        exception->throwSelf();
}

void Event::dispatch()
{
    if (!callback.empty())
        callback();
}

void Event::setException(const std::exception& e) {
    const Exception* ex = dynamic_cast<const Exception*>(&e);
    if (ex) 
        exception.reset(ex->clone().release());
    else 
        exception.reset(new Exception(e));
#ifndef NDEBUG
    // Throw and re-catch the exception. Has no effect on the
    // program but it triggers debuggers watching for throw.  The
    // context that sets the exception is more informative for
    // debugging purposes than the one that ultimately throws it.
    // 
    try {
        throwIfException();
    }
    catch (...) { }             // Ignored.
#endif
}

int FDEvent::getFDescriptor() const {
	return descriptor.getFD();
}

// TODO: AMS 21/12/06 Don't like the inline new, probably cause a memory leak
ReadEvent::ReadEvent(int fd, void* buf, size_t sz,Callback cb, bool noWait) :
	IOEvent(cb, *(new EventChannel::Descriptor(fd)), sz, noWait), buffer(buf), bytesRead(0) {
}

void ReadEvent::prepare(EventChannel::Impl& impl) {
	EventChannel::Descriptor& d = getDescriptor();
	impl.activate(d);
    d.getQueue(IN).push(this);
}

void ReadEvent::complete(EventChannel::Descriptor& ed)
{
    ssize_t n = ::read(getFDescriptor(),
                       static_cast<char*>(buffer) + bytesRead,
                       size - bytesRead);
    if (n > 0)
        bytesRead += n;
    if (n == 0 || (n < 0 && errno != EAGAIN)) {
        // Use ENODATA for file closed.
        setException(QPID_POSIX_ERROR(n == 0 ? ENODATA : errno));
        ed.shutdownUnsafe(); 
    }
}

WriteEvent::WriteEvent(int fd, const void* buf, size_t sz, Callback cb) :
    IOEvent(cb, *(new EventChannel::Descriptor(fd)), sz, noWait), buffer(buf), bytesWritten(0) {
}

void WriteEvent::prepare(EventChannel::Impl& impl) {
	EventChannel::Descriptor& d = getDescriptor();
	impl.activate(d);
    d.getQueue(OUT).push(this);
}


void WriteEvent::complete(EventChannel::Descriptor& ed)
{
    ssize_t n = ::write(getFDescriptor(),
                        static_cast<const char*>(buffer) + bytesWritten,
                        size - bytesWritten);
    if (n > 0)
        bytesWritten += n;
    if(n < 0 && errno != EAGAIN) {
        setException(QPID_POSIX_ERROR(errno));
        ed.shutdownUnsafe(); // Called with lock held.
    }
}

AcceptEvent::AcceptEvent(int fd, Callback cb) :
    FDEvent(cb, *(new EventChannel::Descriptor(fd))), accepted(0) {
}

void AcceptEvent::prepare(EventChannel::Impl& impl) {
	EventChannel::Descriptor& d = getDescriptor();
	impl.activate(d);
    d.getQueue(IN).push(this);
}

void AcceptEvent::complete(EventChannel::Descriptor& ed)
{
    accepted = ::accept(getFDescriptor(), 0, 0);
    if (accepted < 0) {
        setException(QPID_POSIX_ERROR(errno));
        ed.shutdownUnsafe(); // Called with lock held.
    }
}

}}
