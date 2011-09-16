#ifndef QPID_SYS_STOPPABLE_H
#define QPID_SYS_STOPPABLE_H

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

#include <boost/function.hpp>

namespace qpid {
namespace sys {

// FIXME aconway 2011-05-25: needs better name

/**
 * An activity that may be executed by multiple threads, and can be stopped.
 *
 * Stopping prevents new threads from entering and calls a callback
 * when all busy threads have left.
 */
class Stoppable {
  public:
    /**
     * Initially not stopped.
     *@param stoppedCallback: called when all threads have stopped.
     */
    Stoppable(boost::function<void()> stoppedCallback)
        : busy(0), stopped(false), notify(stoppedCallback) {}

    /** Mark the scope of a busy thread like this:
     * <pre>
     * {
     *   Stoppable::Scope working(stoppable);
     *   if (working) { do stuff }
     * }
     * </pre>
     */
    class Scope {
        Stoppable& state;
        bool entered;
      public:
        Scope(Stoppable& s) : state(s) { entered = state.enter(); }
        ~Scope() { if (entered) state.exit(); }
        operator bool() const { return entered; }
    };

  friend class Scope;

    /**
     * Set state to "stopped", so no new threads can enter.
     * Notify function will be called when all busy threads have left.
     * No-op if already stopping.
     */
    void stop() {
        sys::Monitor::ScopedLock l(lock);
        if (stopped) return;
        stopped = true;
        check();
    }

    /** Set the state to "started", allow threads to enter.
     * If already stopping this will prevent notify function from being called.
     */
    void start() {
        sys::Monitor::ScopedLock l(lock);
        stopped = false;
    }

  private:

    // Busy thread enters scope
    bool enter() {
        sys::Monitor::ScopedLock l(lock);
        if (!stopped) ++busy;
        return !stopped;
    }

    // Busy thread exits scope
    void exit() {
        sys::Monitor::ScopedLock l(lock);
        assert(busy > 0);
        --busy;
        check();
    }

    void check() {
        // Called with lock held.
        if (stopped && busy == 0 && notify) notify();
    }

    uint busy;
    bool stopped;
    sys::Monitor lock;
    boost::function< void() > notify;
};

}} // namespace qpid::sys

#endif  /*!QPID_SYS_STOPPABLE_H*/
