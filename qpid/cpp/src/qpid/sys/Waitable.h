#ifndef QPID_SYS_WAITABLE_H
#define QPID_SYS_WAITABLE_H

/*
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

#include "Monitor.h"

#include <assert.h>

namespace qpid {
namespace sys {

/**
 * A monitor that keeps track of waiting threads.  Threads declare a
 * ScopedWait around wait() inside a ScopedLock to be considered
 * waiters.
 */
class Waitable : public Monitor {
  public:
    Waitable() : waiters(0) {}

    /** Use this inside a scoped lock around the
     * call to Monitor::wait to be counted as a waiter
     */
    struct ScopedWait {
        Waitable& w;
        ScopedWait(Waitable& w_) : w(w_) { ++w.waiters; }
        ~ScopedWait() { if (--w.waiters==0) w.notifyAll(); }
    };

    /** Block till there are no more ScopedWaits.
     *@pre Must be called inside a ScopedLock but NOT a ScopedWait.
     */
    void waitWaiters() {
        while (waiters != 0) 
            wait();
    }

    /** Returns the number of outstanding ScopedWaits.
     * Must be called with the lock held.
     */
    size_t hasWaiters() { return waiters; }
    
  private:
  friend struct ScopedWait;
    size_t waiters;
};

}} // namespace qpid::sys



#endif  /*!QPID_SYS_WAITABLE_H*/
