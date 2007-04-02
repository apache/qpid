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


#include "../QpidError.h"
#include "ScopedIncrement.h"
#include "ProducerConsumer.h"

namespace qpid {
namespace sys {

// // ================ ProducerConsumer

ProducerConsumer::ProducerConsumer(size_t init_items)
    : items(init_items), waiters(0), shutdownFlag(false)
{}

void ProducerConsumer::shutdown() {
    Mutex::ScopedLock l(monitor);
    shutdownFlag = true;
    monitor.notifyAll();
    // Wait for waiting consumers to wake up.
    while (waiters > 0)
        monitor.wait();
}

size_t ProducerConsumer::available() const {
    Mutex::ScopedLock l(monitor);
    return items;
}

size_t ProducerConsumer::consumers() const {
    Mutex::ScopedLock l(monitor);
    return waiters;
}

// ================ Lock

ProducerConsumer::Lock::Lock(ProducerConsumer& p)
    : pc(p), lock(p.monitor), status(INCOMPLETE) {}

bool ProducerConsumer::Lock::isOk() const {
    return !pc.isShutdown() && status==INCOMPLETE;
}
    
void ProducerConsumer::Lock::checkOk() const {
    assert(!pc.isShutdown());
    assert(status == INCOMPLETE);
}

ProducerConsumer::Lock::~Lock() {
    assert(status != INCOMPLETE || pc.isShutdown());
}

void ProducerConsumer::Lock::confirm() {
    checkOk();
    status = CONFIRMED;
}
    
void ProducerConsumer::Lock::cancel() {
    checkOk();
    status = CANCELLED;
}

// ================ ProducerLock

ProducerConsumer::ProducerLock::ProducerLock(ProducerConsumer& p) : Lock(p)
{}


ProducerConsumer::ProducerLock::~ProducerLock() {
    if (status == CONFIRMED) {
        pc.items++;
        pc.monitor.notify(); // Notify a consumer. 
    }
}

// ================ ConsumerLock
    
ProducerConsumer::ConsumerLock::ConsumerLock(ProducerConsumer& p) : Lock(p)
{
    if (isOk()) {
        ScopedIncrement<size_t> inc(pc.waiters);
        while (pc.items == 0 && !pc.shutdownFlag) {
            pc.monitor.wait();
        }
    }
}

ProducerConsumer::ConsumerLock::ConsumerLock(
    ProducerConsumer& p, const Time& timeout) : Lock(p)
{
    if (isOk()) {
        // Don't wait if timeout==0
        if (timeout == 0) {
            if (pc.items == 0)
                status = TIMEOUT;
            return;
        }
        else {
            Time deadline = now() + timeout;
            ScopedIncrement<size_t> inc(pc.waiters);
            while (pc.items == 0 && !pc.shutdownFlag) {
                if (!pc.monitor.wait(deadline)) {
                    status = TIMEOUT;
                    return;
                }
            }
        }
    }
}

ProducerConsumer::ConsumerLock::~ConsumerLock() {
    if (pc.isShutdown())  {
        if (pc.waiters == 0)
            pc.monitor.notifyAll(); // Notify shutdown thread(s)
    }
    else if (status==CONFIRMED) {
        pc.items--;
        if (pc.items > 0)
            pc.monitor.notify();    // Notify another consumer.
    }
}


}} // namespace qpid::sys
