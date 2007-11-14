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

SerializerBase::SerializerBase(bool allowImmediate)
    : state(IDLE), immediate(allowImmediate) {}

void SerializerBase::shutdown() {
    {
        Mutex::ScopedLock l(lock);
        if (state == SHUTDOWN) return;
        state = SHUTDOWN;
        lock.notify();
    }
    if (worker.id() != 0)
        worker.join();
}

void SerializerBase::notifyWorker() {
    // Call with lock held.
    if (!worker.id())
        worker = Thread(*this);
    else
        lock.notify();
}

bool SerializerBase::running() {
    Mutex::ScopedLock l(lock);
    return state != SHUTDOWN;
}

void SerializerBase::wait() {
    Mutex::ScopedLock l(lock);
    lock.wait();
}

void SerializerBase::run() {
    while (running()) {
        dispatch();
        wait();
    }
}

}} // namespace qpid::sys
