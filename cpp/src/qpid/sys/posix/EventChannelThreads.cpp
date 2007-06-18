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

#include <iostream>
#include <limits>

#include <boost/bind.hpp>

#include "qpid/sys/Runnable.h"

#include "EventChannelThreads.h"

namespace qpid {
namespace sys {

const size_t EventChannelThreads::unlimited =
    std::numeric_limits<size_t>::max();

EventChannelThreads::shared_ptr EventChannelThreads::create(
    EventChannel::shared_ptr ec, size_t min, size_t max
)
{
    return EventChannelThreads::shared_ptr(
        new EventChannelThreads(ec, min, max));
}

EventChannelThreads::EventChannelThreads(
    EventChannel::shared_ptr ec, size_t min, size_t max) :
    minThreads(std::max(size_t(1), min)),
    maxThreads(std::min(min, max)),
    channel(ec),
    nWaiting(0),
    state(RUNNING)
{
    Monitor::ScopedLock l(monitor);
    while (workers.size() < minThreads) 
        workers.push_back(Thread(*this));
}

EventChannelThreads::~EventChannelThreads() {
    shutdown();
    join();
}

void EventChannelThreads::shutdown() 
{
    Monitor::ScopedLock lock(monitor);
    if (state != RUNNING)       // Already shutting down.
        return;
    state = TERMINATING;
    channel->shutdown();
    monitor.notify();           // Wake up one join() thread.
}

void EventChannelThreads::join() 
{
    {
        Monitor::ScopedLock lock(monitor);
        while (state == RUNNING)    // Wait for shutdown to start.
            monitor.wait();
        if (state == SHUTDOWN)      // Shutdown is complete
            return;
        if (state == JOINING) {
            // Someone else is doing the join.
            while (state != SHUTDOWN)
                monitor.wait();
            return;
        }
        // I'm the  joining thread
        assert(state == TERMINATING); 
        state = JOINING; 
    } // Drop the lock.

    for (size_t i = 0; i < workers.size(); ++i) {
        assert(state == JOINING); // Only this thread can change JOINING.
        workers[i].join();
    }
    state = SHUTDOWN;
    monitor.notifyAll();        // Notify any other join() threads.
}

void EventChannelThreads::addThread() {
    Monitor::ScopedLock l(monitor);
    if (workers.size() < maxThreads)
        workers.push_back(Thread(*this));
}

void EventChannelThreads::run()
{
    // Start life waiting. Decrement on exit.
    AtomicCount::ScopedIncrement inc(nWaiting);
    try {
        while (true) {
            Event* e = channel->wait(); 
            assert(e != 0);
            AtomicCount::ScopedDecrement dec(nWaiting);
            // Make sure there's at least one waiting thread.
            if (dec == 0 && state == RUNNING)
                addThread();
            e->dispatch();
        }
    }
    catch (const EventChannel::ShutdownException& e) {
        return;
    }
    catch (const std::exception& e) {
        Exception::log(e, "Exception in EventChannelThreads::run()");
    }
}

}}
