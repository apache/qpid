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
#include <boost/current_function.hpp>

#include <QpidError.h>
#include <sys/Monitor.h>

#include "check.h"
#include "EventChannel.h"

using namespace std;


// Convenience template to zero out a struct.
template <class S> struct ZeroStruct : public S {
    ZeroStruct() { memset(this, 0, sizeof(*this)); }
};
    
namespace qpid {
namespace sys {


/**
 * EventHandler wraps an epoll file descriptor. Acts as private
 * interface between EventChannel and subclasses.
 *
 * Also implements Event interface for events that are not associated
 * with a file descriptor and are passed via the message queue.
 */ 
class EventHandler : public Event, private Monitor
{
  public:
    EventHandler(int epollSize = 256);
    ~EventHandler();

    int getEpollFd() { return epollFd; }
    void epollAdd(int fd, uint32_t epollEvents, Event* event);
    void epollMod(int fd, uint32_t epollEvents, Event* event);
    void epollDel(int fd);

    void mqPut(Event* event);
    Event* mqGet();
    
  protected:
    // Should never be called, only complete.
    void prepare(EventHandler&) { assert(0); }
    Event* complete(EventHandler& eh);
    
  private:
    int epollFd;
    std::string mqName;
    int mqFd;
    std::queue<Event*> mqEvents;
};

EventHandler::EventHandler(int epollSize)
{
    epollFd = epoll_create(epollSize);
    if (epollFd < 0) throw QPID_POSIX_ERROR(errno);

    // Create a POSIX message queue for non-fd events.
    // We write one byte and never read it is always ready for read
    // when we add it to epoll.
    // 
    ZeroStruct<struct mq_attr> attr;
    attr.mq_maxmsg = 1;
    attr.mq_msgsize = 1;
    do {
        char tmpnam[L_tmpnam];
        tmpnam_r(tmpnam);
        mqName = tmpnam + 4; // Skip "tmp/"
        mqFd = mq_open(
            mqName.c_str(), O_CREAT|O_EXCL|O_RDWR|O_NONBLOCK, S_IRWXU, &attr);
        if (mqFd < 0) throw QPID_POSIX_ERROR(errno);
    } while (mqFd == EEXIST);  // Name already taken, try again.

    static char zero = '\0';
    mq_send(mqFd, &zero, 1, 0);
    epollAdd(mqFd, 0, this);
}

EventHandler::~EventHandler() {
    mq_close(mqFd);
    mq_unlink(mqName.c_str());
}

void EventHandler::mqPut(Event* event) {
    ScopedLock l(*this);
    assert(event != 0);
    mqEvents.push(event);
    epollMod(mqFd, EPOLLIN|EPOLLONESHOT, this);
}

Event* EventHandler::mqGet() {
    ScopedLock l(*this);
    if (mqEvents.empty()) 
        return 0;
    Event* event = mqEvents.front();
    mqEvents.pop();
    if(!mqEvents.empty())
        epollMod(mqFd, EPOLLIN|EPOLLONESHOT, this);
    return event;
}

void EventHandler::epollAdd(int fd, uint32_t epollEvents, Event* event)
{
    ZeroStruct<struct epoll_event> ee;
    ee.data.ptr = event;
    ee.events = epollEvents;
    if (epoll_ctl(epollFd, EPOLL_CTL_ADD, fd, &ee) < 0) 
        throw QPID_POSIX_ERROR(errno);
}

void EventHandler::epollMod(int fd, uint32_t epollEvents, Event* event)
{
    ZeroStruct<struct epoll_event> ee;
    ee.data.ptr = event;
    ee.events = epollEvents;
    if (epoll_ctl(epollFd, EPOLL_CTL_MOD, fd, &ee) < 0) 
        throw QPID_POSIX_ERROR(errno);
}

void EventHandler::epollDel(int fd) {
    if (epoll_ctl(epollFd, EPOLL_CTL_DEL, fd, 0) < 0)
        throw QPID_POSIX_ERROR(errno);
}

Event* EventHandler::complete(EventHandler& eh)
{
    assert(&eh == this);
    Event* event =  mqGet();
    return event==0 ? 0 : event->complete(eh);
}
    
// ================================================================
// EventChannel

EventChannel::shared_ptr EventChannel::create() {
    return shared_ptr(new EventChannel());
}

EventChannel::EventChannel() : handler(new EventHandler()) {}

EventChannel::~EventChannel() {}

void EventChannel::postEvent(Event& e) 
{
    e.prepare(*handler);
}

Event* EventChannel::getEvent()
{
    static const int infiniteTimeout = -1; 
    ZeroStruct<struct epoll_event> epollEvent;

    // Loop until we can complete the event. Some events may re-post
    // themselves and return 0 from complete, e.g. partial reads. // 
    Event* event = 0;
    while (event == 0) {
        int eventCount = epoll_wait(handler->getEpollFd(),
                                    &epollEvent, 1, infiniteTimeout);
        if (eventCount < 0) {
            if (errno != EINTR) {
                // TODO aconway 2006-11-28: Proper handling/logging of errors.
                cerr << BOOST_CURRENT_FUNCTION << " ignoring error "
                     << PosixError::getMessage(errno) << endl;
                assert(0);
            }
        }
        else if (eventCount == 1) {
            event = reinterpret_cast<Event*>(epollEvent.data.ptr);
            assert(event != 0);
            try {
                event = event->complete(*handler);
            }
            catch (const Exception& e) {
                if (event)
                    event->setError(e);
            }
            catch (const std::exception& e) {
                if (event)
                    event->setError(e);
            }
        }
    }
    return event;
}

Event::~Event() {}
    
void Event::prepare(EventHandler& handler)
{
    handler.mqPut(this);
}

bool Event::hasError() const {
    return error;
}

void Event::throwIfError() throw (Exception) {
    if (hasError())
        error.throwSelf();
}

Event* Event::complete(EventHandler&)
{
    return this;
}

void Event::dispatch()
{
    try {
        if (!callback.empty())
            callback();
    } catch (const std::exception&) {
        throw;
    } catch (...) {
        throw QPID_ERROR(INTERNAL_ERROR, "Unknown exception.");
    }
}

void Event::setError(const ExceptionHolder& e) {
    error = e;
}

void ReadEvent::prepare(EventHandler& handler)
{
    handler.epollAdd(descriptor, EPOLLIN | EPOLLONESHOT, this);
}

ssize_t ReadEvent::doRead() {
    ssize_t n = ::read(descriptor, static_cast<char*>(buffer) + received,
                       size - received);
    if (n > 0) received += n;
    return n;
}

Event* ReadEvent::complete(EventHandler& handler)
{
    // Read as much as possible without blocking.
    ssize_t n = doRead();
    while (n > 0 && received < size) doRead();

    if (received == size) {
        handler.epollDel(descriptor);
        received = 0;           // Reset for re-use.
        return this;
    }
    else if (n <0 && (errno == EAGAIN)) {
        // Keep polling for more.
        handler.epollMod(descriptor, EPOLLIN | EPOLLONESHOT, this);
        return 0;
    }
    else {
        // Unexpected EOF or error. Throw ENODATA for EOF.
        handler.epollDel(descriptor);
        received = 0;           // Reset for re-use.
        throw QPID_POSIX_ERROR((n < 0) ? errno : ENODATA);
    }
}

void WriteEvent::prepare(EventHandler& handler)
{
    handler.epollAdd(descriptor, EPOLLOUT | EPOLLONESHOT, this);
}

Event* WriteEvent::complete(EventHandler& handler)
{
    ssize_t n = write(descriptor, static_cast<const char*>(buffer) + written,
                      size - written);
    if (n < 0) throw QPID_POSIX_ERROR(errno);
    written += n;
    if(written < size) {
        // Keep polling.
        handler.epollMod(descriptor, EPOLLOUT | EPOLLONESHOT, this);
        return 0;
    }
    written = 0;                // Reset for re-use.
    handler.epollDel(descriptor);
    return this;
}

void AcceptEvent::prepare(EventHandler& handler)
{
    handler.epollAdd(descriptor, EPOLLIN | EPOLLONESHOT, this);
}

Event* AcceptEvent::complete(EventHandler& handler)
{
    handler.epollDel(descriptor);
    accepted = ::accept(descriptor, 0, 0);
    if (accepted < 0) throw QPID_POSIX_ERROR(errno);
    return this;
}

}}
