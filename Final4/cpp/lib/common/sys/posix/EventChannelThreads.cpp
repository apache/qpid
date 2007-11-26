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

#include "EventChannelThreads.h"
#include <sys/Runnable.h>
#include <iostream>
using namespace std;
#include <boost/bind.hpp>

namespace qpid {
namespace sys {

EventChannelThreads::shared_ptr EventChannelThreads::create(
    EventChannel::shared_ptr ec)
{
    return EventChannelThreads::shared_ptr(new EventChannelThreads(ec));
}

EventChannelThreads::EventChannelThreads(EventChannel::shared_ptr ec) :
    channel(ec), nWaiting(0), state(RUNNING)
{
    // TODO aconway 2006-11-15: Estimate initial threads based on CPUs.
    addThread();
}

EventChannelThreads::~EventChannelThreads() {
    shutdown();
    join();
}

void EventChannelThreads::shutdown() 
{
    ScopedLock lock(*this);
    if (state != RUNNING)       // Already shutting down.
        return;
    for (size_t i = 0; i < workers.size(); ++i) {
        channel->postEvent(terminate);
    }
    state = TERMINATE_SENT;
    notify();                // Wake up one join() thread.
}

void EventChannelThreads::join() 
{
    {
        ScopedLock lock(*this);
        while (state == RUNNING)    // Wait for shutdown to start.
            wait();
        if (state == SHUTDOWN)      // Shutdown is complete
            return;
        if (state == JOINING) {
            // Someone else is doing the join.
            while (state != SHUTDOWN)
                wait();
            return;
        }
        // I'm the  joining thread
        assert(state == TERMINATE_SENT); 
        state = JOINING; 
    } // Drop the lock.

    for (size_t i = 0; i < workers.size(); ++i) {
        assert(state == JOINING); // Only this thread can change JOINING.
        workers[i].join();
    }
    state = SHUTDOWN;
    notifyAll();                // Notify other join() threaeds.
}

void EventChannelThreads::addThread() {
    ScopedLock l(*this);
    workers.push_back(Thread(*this));
}

void EventChannelThreads::run()
{
    // Start life waiting. Decrement on exit.
    AtomicCount::ScopedIncrement inc(nWaiting);
    try {
        while (true) {
            Event* e = channel->getEvent(); 
            assert(e != 0);
            if (e == &terminate) {
                return;
            }
            AtomicCount::ScopedDecrement dec(nWaiting);
            // I'm no longer waiting, make sure someone is.
            if (dec == 0)
                addThread();
            e->dispatch();
        }
    }
    catch (const std::exception& e) {
        // TODO aconway 2006-11-15: need better logging across the board.
        std::cerr << "EventChannelThreads::run() caught: " << e.what()
                  << std::endl;
    }
    catch (...) {
        std::cerr << "EventChannelThreads::run() caught unknown exception."
                  << std::endl;
    }
}

}}
