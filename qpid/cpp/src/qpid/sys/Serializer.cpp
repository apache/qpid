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

#include "qpid/sys/Serializer.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>

#include <assert.h>

namespace qpid {
namespace sys {

Serializer::Serializer(bool allowImmediate, Task notifyDispatchFn)
    : state(IDLE), immediate(allowImmediate), notifyDispatch(notifyDispatchFn)
{
    if (notifyDispatch.empty())
        notifyDispatch = boost::bind(&Serializer::notifyWorker, this);
}

Serializer::~Serializer() {
    {
        Mutex::ScopedLock l(lock);
        state = SHUTDOWN;
        lock.notify();
    }
    if (worker.id() != 0)
        worker.join();
}

void Serializer::dispatch(Task& task) {
    Mutex::ScopedUnlock u(lock);
    // Preconditions: lock is held, state is EXECUTING or DISPATCHING
    assert(state != IDLE);
    assert(state != SHUTDOWN);
    assert(state == EXECUTING || state == DISPATCHING);
    try {
        task();
    } catch (const std::exception& e) {
        QPID_LOG(critical, "Unexpected exception in Serializer::dispatch"
                 << e.what());
        assert(0);              // Should not happen.
    } catch (...) {
        QPID_LOG(critical, "Unexpected exception in Serializer::dispatch.");
        assert(0);              // Should not happen.
    }
}

void Serializer::execute(Task& task) {
    bool needNotify = false;
    {
        Mutex::ScopedLock l(lock);
        assert(state != SHUTDOWN);
        if (immediate && state == IDLE) {
            state = EXECUTING;
            dispatch(task);
            if (state != SHUTDOWN) {
                assert(state == EXECUTING);
                state = IDLE;
            }
        }
        else 
            queue.push_back(task);

        if (!queue.empty() && state == IDLE) {
            state = DISPATCHING;
            needNotify = true;
        }
    }
    if (needNotify)
        notifyDispatch();       // Not my function, call outside lock.
}

void Serializer::dispatch() {
    Mutex::ScopedLock l(lock);
    // TODO aconway 2007-07-16: This loop could be unbounded
    // if other threads add work while we're in dispatch(Task&).
    // If we need to bound it we could dispatch just the elements
    // that were enqueued when dispatch() was first called - save
    // begin() iterator and pop only up to that.
    while (!queue.empty() && state != SHUTDOWN) {
        assert(state == DISPATCHING);
        dispatch(queue.front());
        queue.pop_front();
    }
    if (state != SHUTDOWN) {
        assert(state == DISPATCHING);
        state = IDLE;
    }
}

void Serializer::notifyWorker() {
    if (!worker.id())
        worker = Thread(*this);
    else
        lock.notify();
}

void Serializer::run() {
    Mutex::ScopedLock l(lock);
    while (state != SHUTDOWN) {
        dispatch();
        lock.wait();
    }
}

}} // namespace qpid::sys
