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

#include <QpidError.h>

#include "check.h"
#include "EventChannel.h"

using namespace std;


namespace qpid {
namespace sys {


// ================================================================
// Private class declarations

namespace {

typedef enum { IN, OUT } Direction;
typedef std::pair<Event*, Event*> EventPair;
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
    Descriptor() : epollFd(-1), myFd(-1),
                   inQueue(*this, IN), outQueue(*this, OUT) {}

    void activate(int epollFd_, int myFd_);

    /** Epoll woke up for this descriptor. */
    EventPair wake(uint32_t epollEvents);

    /** Shut down: close and remove file descriptor.
     * May be re-activated if fd is reused.
     */
    void shutdown();

    // TODO aconway 2006-12-18: Nasty. Need to clean up interaction.
    void shutdownUnsafe();      

    bool isShutdown() { return epollFd == -1; }

    Queue& getQueue(Direction d) { return d==IN ? inQueue : outQueue; }

  private:
    void update();
    void epollCtl(int op, uint32_t events);

    Mutex lock;
    int epollFd;
    int myFd;
    Queue inQueue, outQueue;

  friend class Queue;
};

 
/**
 * Holds the epoll fd, Descriptor map and dispatch queue.
 * Most of the epoll work is done by the Descriptors.
 */
class EventChannel::Impl {
  public:
    Impl(int size = 256);

    ~Impl();

    /**
     * Registers fd if not already registered.
     */
    Descriptor& getDescriptor(int fd);

    /** Wait for an event, return 0 on timeout */
    Event* wait(Time timeout);

    Queue& getDispatchQueue() { return *dispatchQueue; }

  private:

    typedef boost::ptr_map<int, Descriptor> DescriptorMap;

    Mutex lock;     
    int epollFd;
    DescriptorMap descriptors;
    int pipe[2];
    Queue* dispatchQueue;
};



// ================================================================
// EventChannel::Queue::implementation.

static const char* shutdownMsg = "Event queue shut down.";

EventChannel::Queue::Queue(Descriptor& d, Direction dir) : lock(d.lock), descriptor(d),
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

Event* EventChannel::Queue::wake(uint32_t epollFlags) {
    // Called with lock held.
    if (!queue.empty() && (isMyEvent(epollFlags))) {
        Event* e = queue.front()->complete(descriptor);
        if (e) {
            queue.pop_front();
            return e;
        }
    }
    return 0;
}
        
void EventChannel::Queue::shutdown() {
    // Mark all pending events with a shutdown exception.
    // The server threads will remove and dispatch the events.
    // 
    qpid::QpidError ex(INTERNAL_ERROR, shutdownMsg, QPID_ERROR_LOCATION);
    for_each(queue.begin(), queue.end(),
             boost::bind(&Event::setException, _1, ex));
}


// ================================================================
// Descriptor


void EventChannel::Descriptor::activate(int epollFd_, int myFd_) {
    Mutex::ScopedLock l(lock);
    assert(myFd < 0 || (myFd == myFd_)); // Can't change fd.
    if (epollFd < 0) {          // Means we're not polling.
        epollFd = epollFd_;
        myFd = myFd_;
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
    epollFd = -1;               // Indicate we are not polling.
    inQueue.shutdown();
    outQueue.shutdown();
    epollCtl(EPOLL_CTL_DEL, 0);
}

void EventChannel::Descriptor::update() {
    // Caller holds lock.
    uint32_t events =  EPOLLONESHOT | EPOLLERR | EPOLLHUP;
    inQueue.setBit(events);
    outQueue.setBit(events);
    epollCtl(EPOLL_CTL_MOD, events);
}
    
void EventChannel::Descriptor::epollCtl(int op, uint32_t events) {
    // Caller holds lock
    assert(!isShutdown());
    struct epoll_event ee;
    memset(&ee, 0, sizeof(ee));
    ee.data.ptr = this;
    ee.events = events;
    int status = ::epoll_ctl(epollFd, op, myFd, &ee);
    if (status < 0)
        throw QPID_POSIX_ERROR(errno);
}
    

EventPair EventChannel::Descriptor::wake(uint32_t epollEvents) {
    Mutex::ScopedLock l(lock);
    cout << "DEBUG: " << std::hex << epollEvents << std::dec << endl;
    // If we have an error:
    if (epollEvents & (EPOLLERR | EPOLLHUP)) {
        shutdownUnsafe();
        // Complete both sides on error so the event can fail and
        // mark itself with an exception.
        epollEvents |= EPOLLIN | EPOLLOUT;
    }
    EventPair ready(inQueue.wake(epollEvents), outQueue.wake(epollEvents));
    update();
    return ready;
}


// ================================================================
// EventChannel::Impl


EventChannel::Impl::Impl(int epollSize):
    epollFd(-1), dispatchQueue(0)
{
    // Create the epoll file descriptor.
    epollFd = epoll_create(epollSize);
    QPID_POSIX_CHECK(epollFd);

    // Create a pipe and write a single byte.  The byte is never
    // read so the pipes read fd is always ready for read.
    // We activate the FD when there are messages in the queue.
    QPID_POSIX_CHECK(::pipe(pipe));
    static char zero = '\0';
    QPID_POSIX_CHECK(::write(pipe[1], &zero, 1));
    dispatchQueue = &getDescriptor(pipe[0]).getQueue(IN);
}

EventChannel::Impl::~Impl() {
    close(epollFd);
    close(pipe[0]);
    close(pipe[1]);
}


/**
 * Wait for epoll to wake up, return the descriptor or 0 on timeout.
 */
Event* EventChannel::Impl::wait(Time timeoutNs)
{
    // No lock, all thread safe calls or local variables:
    // 
    const long timeoutMs =
        (timeoutNs == TIME_INFINITE) ? -1 : timeoutNs/TIME_MSEC;
    struct epoll_event ee;
    Event* event = 0;
    bool doSwap = true;

    // Loop till we get a completed event. Some events may repost
    // themselves and return 0, e.g. incomplete read or write events.
    //
    while (!event) {
        int n = epoll_wait(epollFd, &ee, 1, timeoutMs); // Thread safe.
        if (n == 0)             // Timeout
            return 0;
        if (n < 0 && errno != EINTR) // Interrupt, ignore it.
            continue;
        if (n < 0)              
            throw QPID_POSIX_ERROR(errno);
        assert(n == 1);
        Descriptor* ed =
            reinterpret_cast<Descriptor*>(ee.data.ptr);
        assert(ed);
        EventPair ready = ed->wake(ee.events); 

        // We can only return one event so if both completed push one
        // onto the dispatch queue to be dispatched in another thread.
        if (ready.first && ready.second) {
            // Keep it fair: in & out take turns to be returned first.
            if (doSwap)
                swap(ready.first, ready.second);
            doSwap = !doSwap;
            event = ready.first;
            dispatchQueue->push(ready.second);
        }
        else {
            event = ready.first ? ready.first : ready.second;
        }
    }
    return event;
}

EventChannel::Descriptor& EventChannel::Impl::getDescriptor(int fd) {
    Mutex::ScopedLock l(lock);
    Descriptor& ed = descriptors[fd];
    ed.activate(epollFd, fd);
    return ed;
}


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

void EventChannel::post(Event* e) {
    assert(e);
    post(*e);
}

Event* EventChannel::wait(Time timeoutNs)
{
    return impl->wait(timeoutNs);
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


void ReadEvent::prepare(EventChannel::Impl& impl) {
    impl.getDescriptor(descriptor).getQueue(IN).push(this);
}

Event* ReadEvent::complete(EventChannel::Descriptor& ed)
{
    ssize_t n = ::read(descriptor,
                       static_cast<char*>(buffer) + bytesRead,
                       size - bytesRead);

    if (n < 0 && errno != EAGAIN) { // Error
        setException(QPID_POSIX_ERROR(errno));
        ed.shutdownUnsafe(); // Called with lock held.
    }
    else if (n == 0) {               // End of file
        // TODO aconway 2006-12-13: Don't treat EOF as exception
        // unless we're partway thru a !noWait read.
        setException(QPID_POSIX_ERROR(ENODATA));
        ed.shutdownUnsafe(); // Called with lock held.
    }
    else {
        if (n > 0)              // possible that n < 0 && errno == EAGAIN
            bytesRead += n;
        if (bytesRead  < size && !noWait) {
            // Continue reading, not enough data.
            return 0;
        }
    }
    return this;
}


void WriteEvent::prepare(EventChannel::Impl& impl) {
    impl.getDescriptor(descriptor).getQueue(OUT).push(this);
}


Event* WriteEvent::complete(EventChannel::Descriptor& ed)
{
    ssize_t n = ::write(descriptor,
                        static_cast<const char*>(buffer) + bytesWritten,
                        size - bytesWritten);
    if(n < 0 && errno == EAGAIN && noWait) {
        return 0;
    }
    if (n < 0 || (bytesWritten += n) < size) {
        setException(QPID_POSIX_ERROR(errno));
        ed.shutdownUnsafe(); // Called with lock held.
    }
    return this;
}

void AcceptEvent::prepare(EventChannel::Impl& impl) {
    impl.getDescriptor(descriptor).getQueue(IN).push(this);
}

Event* AcceptEvent::complete(EventChannel::Descriptor& ed)
{
    accepted = ::accept(descriptor, 0, 0);
    if (accepted < 0) {
        setException(QPID_POSIX_ERROR(errno));
        ed.shutdownUnsafe(); // Called with lock held.
    }
    return this;
}

void DispatchEvent::prepare(EventChannel::Impl& impl) {
    impl.getDispatchQueue().push(this);
}

Event* DispatchEvent::complete(EventChannel::Descriptor&)
{
    return this;
}

}}
