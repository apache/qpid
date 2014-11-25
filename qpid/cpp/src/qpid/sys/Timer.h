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
#ifndef sys_Timer
#define sys_Timer

#include "qpid/sys/TimerWarnings.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/RefCounted.h"
#include "qpid/CommonImportExport.h"
#include <memory>
#include <queue>

#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace sys {

class Timer;

class TimerTask : public RefCounted {
  friend class Timer;
  friend class TimerTaskCallbackScope;
  friend bool operator<(const boost::intrusive_ptr<TimerTask>&,
                        const boost::intrusive_ptr<TimerTask>&);

    std::string name;
    AbsTime sortTime;
    Duration period;
    AbsTime nextFireTime;
    qpid::sys::Monitor stateMonitor;
    enum {WAITING, CALLING, CANCELLED} state;

    bool prepareToFire();
    void finishFiring();
    bool readyToFire() const;
    void fireTask();

  public:
    /** Create a periodic TimerTask
     *
     * This TimerTask type will trigger after the specified duration
     * and can also be retriggered again after the same duration
     * by using setupNextFire() after it has been fired.
     *
     * Before it has been triggered you can use restart() to push off
     * triggering the TimerTask by the specified duration.
     */
    QPID_COMMON_EXTERN TimerTask(Duration period, const std::string& name);

    /** Create a TimerTask that fires at a given absolute time
     *
     * This is a once only Timer and cannot be restarted after it has fired.
     */
    QPID_COMMON_EXTERN TimerTask(AbsTime fireTime, const std::string& name);

    QPID_COMMON_EXTERN virtual ~TimerTask();

    /** Adjust a periodic TimerTask for next firing time
     *
     * Called after a TimerTask has been triggered - probably in the fire()
     * callback function itself. This will set up a TimerTask for the next
     * triggering.
     *
     * Note that the TimerTask will need to be added again to the Timer.
     */
    QPID_COMMON_EXTERN void setupNextFire();

    /** Restart a TimerTask so to delay it being firing
     *
     * This can be called either with the TimerTask already added to a Timer
     * or after the task has been triggered. It has the effect of delaying
     * the task triggering by the initially specified duration.
     */
    QPID_COMMON_EXTERN void restart();

    /** Cancel a TimerTask so that it is no longer triggered
     *
     * After cancelling the only thing you can do nothing further
     * with a TimerTask.
     *
     * The Timer will delete the cancelled TimerTask.
     */
    QPID_COMMON_EXTERN void cancel();

    std::string getName() const { return name; }

  protected:
    // Must be overridden with callback
    virtual void fire() = 0;
};

// For the priority_queue order
bool operator<(const boost::intrusive_ptr<TimerTask>& a,
               const boost::intrusive_ptr<TimerTask>& b);

class Timer : private Runnable {
    qpid::sys::Monitor monitor;
    std::priority_queue<boost::intrusive_ptr<TimerTask> > tasks;
    qpid::sys::Thread runner;
    bool active;

    // Runnable interface
    void run();

  public:
    QPID_COMMON_EXTERN Timer();
    QPID_COMMON_EXTERN virtual ~Timer();

    /** Add an TimerTask to the Timer queue
     *
     * Once a TimerTask has been triggered by (calling its fire() function)
     * the TimerTask is no longer on the Timer queue and needs to be added again.
     *
     * Note that TimerTasks must never be added more than once to a Timer
     * and must never be added simultaneuosly to multiple Timers.
     */
    QPID_COMMON_EXTERN virtual void add(boost::intrusive_ptr<TimerTask> task);

    /** Start the Timer
     *
     * This will start a new thread that runs the Timer and the fire callbacks.
     */
    QPID_COMMON_EXTERN virtual void start();

    /** Stop the Timer
     *
     * This will stop the Timer and its thread.
     */
    QPID_COMMON_EXTERN virtual void stop();

  protected:
    QPID_COMMON_EXTERN virtual void fire(boost::intrusive_ptr<TimerTask> task);

    // Allow derived classes to change the late/overran thresholds.
    Duration late;
    Duration overran;
    Duration lateCancel;
    TimerWarnings warn;
};


}}


#endif
