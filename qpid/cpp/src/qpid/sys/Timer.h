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
    QPID_COMMON_EXTERN TimerTask(Duration period, const std::string& name);
    QPID_COMMON_EXTERN TimerTask(AbsTime fireTime, const std::string& name);
    QPID_COMMON_EXTERN virtual ~TimerTask();

    QPID_COMMON_EXTERN void setupNextFire();
    QPID_COMMON_EXTERN void restart();
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

    QPID_COMMON_EXTERN virtual void add(boost::intrusive_ptr<TimerTask> task);
    QPID_COMMON_EXTERN virtual void start();
    QPID_COMMON_EXTERN virtual void stop();

  protected:
    QPID_COMMON_EXTERN virtual void fire(boost::intrusive_ptr<TimerTask> task);
    QPID_COMMON_EXTERN virtual void drop(boost::intrusive_ptr<TimerTask> task);
    // Allow derived classes to change the late/overran thresholds.
    Duration late;
    Duration overran;
    Duration lateCancel;
    TimerWarnings warn;
};


}}


#endif
