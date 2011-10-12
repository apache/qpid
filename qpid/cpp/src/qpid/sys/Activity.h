#ifndef QPID_SYS_ACTIVITY_H
#define QPID_SYS_ACTIVITY_H

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

/**
 * An activity that may be executed by multiple threads concurrently.
 * An activity has 3 states:
 * - active: may have active threads, new threads may enter.
 * - stopping: may have active threads but  no new threads may enter.
 * - stopped: no active threads and no new threads may enter.
 */
class Activity {
  public:
    /**
     * Initially active.
     *@param stoppedCallback: called when all threads have stopped.
     */
    Activity(boost::function<void()> stoppedCallback)
        : busy(0), stopped(false), notify(stoppedCallback) {}

    /** Mark the scope of an activity thread like this:
     * <pre>
     * {
     *   Activity::Scope working(activity);
     *   if (working) { do stuff } // Only if activity is active.
     * }
     * </pre>
     */
    class Scope {
        Activity& state;
        bool entered;
      public:
        Scope(Activity& s) : state(s) { entered = state.enter(); }
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
        stopped = true;
        check(l);
    }

    /** Set the state to "started", allow threads to enter.
     * If already stopping this will prevent notify function from being called.
     */
    void start() {
        sys::Monitor::ScopedLock l(lock);
        stopped = false;
    }

    /** True if Activity is stopped with no */
    bool isStopped() {
        sys::Monitor::ScopedLock l(lock);
        return stopped && busy == 0;
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
        check(l);
    }

    void check(const sys::Monitor::ScopedLock&) {
        // Called with lock held.
        if (stopped && busy == 0 && notify) notify();
    }

    uint busy;
    bool stopped;
    sys::Monitor lock;
    boost::function< void() > notify;
};

}} // namespace qpid::sys

#endif  /*!QPID_SYS_ACTIVITY_H*/
