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
namespace qpid {
namespace sys {

/**
 * An activity that may be executed by multiple threads, and can be stopped.
 * Stopping prevents new threads from entering and waits till exiting busy threads leave.
 */
class Stoppable {
  public:
    Stoppable() : busy(0), stopped(false) {}
    ~Stoppable() { stop(); }

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
        Scope(Stoppable& s) : state(s) { entered = s.enter(); }
        ~Scope() { if (entered) state.exit(); }
        operator bool() const { return entered; }
    };

  friend class Scope;

    /** Mark  stopped, wait for all threads to leave their busy scope. */
    void stop() {
        sys::Monitor::ScopedLock l(lock);
        stopped = true;
        while (busy > 0) lock.wait();
    }

    /** Set the state to started.
     *@pre state is stopped and no theads are busy.
     */
    void start() {
        sys::Monitor::ScopedLock l(lock);
        assert(stopped && busy == 0); // FIXME aconway 2011-05-06: error handling.
        stopped = false;
    }

  private:
    uint busy;
    bool stopped;
    sys::Monitor lock;

    bool enter() {
        sys::Monitor::ScopedLock l(lock);
        if (!stopped) ++busy;
        return !stopped;
    }

    void exit() {
        sys::Monitor::ScopedLock l(lock);
        assert(busy > 0);
        if (--busy == 0) lock.notifyAll();
    }
};

}} // namespace qpid::sys

#endif  /*!QPID_SYS_STOPPABLE_H*/
