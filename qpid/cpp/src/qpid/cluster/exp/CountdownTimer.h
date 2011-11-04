#ifndef QPID_CLUSTER_EXP_COUNTDOWNTIMER_H
#define QPID_CLUSTER_EXP_COUNTDOWNTIMER_H

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright countdown.  The ASF licenses this file
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

#include "qpid/sys/Timer.h"
#include <boost/function.hpp>

namespace qpid {
namespace cluster {

/** Manage the CountdownTimeout */
class CountdownTimer {
  public:
    /**
     * Resettable count-down timer for a fixed interval.
     *@param cb callback when countdown expires.
     *@param t Timer to  use for countdown.
     *@param d duration of countdown.
     */
    CountdownTimer(boost::function<void()> cb, sys::Timer& t, sys::Duration d)
        : task(new Task(*this, d)), timerRunning(false), callback(cb), timer(t) {}

    ~CountdownTimer() { stop(); }

    /** Start the countdown if not already started. */
    void start() {
        sys::Mutex::ScopedLock l(lock);
        if (!timerRunning) {
            timerRunning = true;
            task->restart();
            timer.add(task);
        }
    }

    /** Stop the countdown if not already stopped. */
    void stop() {
        sys::Mutex::ScopedLock l(lock);
        if (timerRunning) {
            timerRunning = false;
            task->cancel();
        }
    }

    bool isRunning() const { return timerRunning; }

  private:

    class Task : public sys::TimerTask {
        CountdownTimer& parent;
      public:
        Task(CountdownTimer& ct, const sys::Duration& d) :
            TimerTask(d, "CountdownTimer::Task"), parent(ct) {}
        void fire() { parent.fire(); }
    };

    // Called when countdown expires.
    void fire() {
        bool doCallback = false;
        {
            sys::Mutex::ScopedLock l(lock);
            doCallback = timerRunning;
            timerRunning = false;
        }
        if (doCallback) callback();
    }

    sys::Mutex lock;
    boost::intrusive_ptr<Task> task;
    bool timerRunning;
    boost::function<void()> callback;
    sys::Timer& timer;
    sys::Duration duration;
};


}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_COUNTDOWNTIMER_H*/
