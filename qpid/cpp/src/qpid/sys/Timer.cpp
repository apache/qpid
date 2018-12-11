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
#include "qpid/sys/Timer.h"
#include "qpid/sys/Mutex.h"
#include "qpid/log/Statement.h"

#include <numeric>

using boost::intrusive_ptr;
using std::max;

namespace qpid {
namespace sys {

TimerTask::TimerTask(Duration timeout, const std::string&  n) :
    name(n),
    sortTime(AbsTime::FarFuture()),
    period(timeout),
    nextFireTime(AbsTime::now(), timeout),
    cancelled(false)
{}

TimerTask::TimerTask(AbsTime time, const std::string&  n) :
    name(n),
    sortTime(AbsTime::FarFuture()),
    period(0),
    nextFireTime(time),
    cancelled(false)
{}

TimerTask::~TimerTask() {}

bool TimerTask::readyToFire() const {
    return !(nextFireTime > AbsTime::now());
}

void TimerTask::fireTask() {
    cancelled = true;
    fire();
}

// This can only be used to setup the next fire time. After the Timer has already fired
void TimerTask::setupNextFire() {
    if (period && readyToFire()) {
        nextFireTime = max(AbsTime::now(), AbsTime(nextFireTime, period));
        cancelled = false;
    } else {
        QPID_LOG(error, name << " couldn't setup next timer firing: " << Duration(nextFireTime, AbsTime::now()) << "[" << period << "]");
    }
}

// Only allow tasks to be delayed
void TimerTask::restart() {
    ScopedLock<Mutex> l(callbackLock);
    nextFireTime = max(nextFireTime, AbsTime(AbsTime::now(), period));
    cancelled = false;
}

void TimerTask::cancel() {
    ScopedLock<Mutex> l(callbackLock);
    cancelled = true;
}

void TimerTask::setFired() {
    // Set nextFireTime to just before now, making readyToFire() true.
    nextFireTime = AbsTime(sys::now(), Duration(-1));
}


Timer::Timer() :
    active(false),
    late(50 * TIME_MSEC),
    overran(2 * TIME_MSEC),
    lateCancel(500 * TIME_MSEC),
    warn(5 * TIME_SEC)
{
    start();
}

Timer::~Timer()
{
    stop();
}

// TODO AStitcher 21/08/09 The threshholds for emitting warnings are a little arbitrary
void Timer::run()
{
    Monitor::ScopedLock l(monitor);
    while (active) {
        if (tasks.empty()) {
            monitor.wait();
        } else {
            intrusive_ptr<TimerTask> t = tasks.top();
            tasks.pop();
            assert(!(t->nextFireTime < t->sortTime));

            // warn on extreme lateness
            AbsTime start(AbsTime::now());
            Duration delay(t->sortTime, start);
            {
            ScopedLock<Mutex> l(t->callbackLock);
            if (t->cancelled) {
                {
                    Monitor::ScopedUnlock u(monitor);
                    drop(t);
                }
                if (delay > lateCancel) {
                    QPID_LOG(debug, t->name << " cancelled timer woken up " <<
                             delay / TIME_MSEC << "ms late");
                }
                continue;
            } else if(Duration(t->nextFireTime, start) >= 0) {
                {
                    Monitor::ScopedUnlock u(monitor);
                    fire(t);
                }
                // Warn if callback overran next timer's start.
                AbsTime end(AbsTime::now());
                Duration overrun (0);
                if (!tasks.empty()) {
                    overrun = Duration(tasks.top()->nextFireTime, end);
                }
                bool warningsEnabled;
                QPID_LOG_TEST(warning, warningsEnabled);
                if (warningsEnabled) {
                    if (overrun > overran) {
                        if (delay > overran) // if delay is significant to an overrun.
                            warn.lateAndOverran(t->name, delay, overrun, Duration(start, end));
                        else
                            warn.overran(t->name, overrun, Duration(start, end));
                    }
                    else if (delay > late)
                        warn.late(t->name, delay);
                }
                continue;
            } else {
                // If the timer was adjusted into the future it might no longer
                // be the next event, so push and then get top to make sure
                // You can only push events into the future
                t->sortTime = t->nextFireTime;
                tasks.push(t);
            }
            }
            assert(!tasks.empty());
            monitor.wait(tasks.top()->sortTime);
        }
    }
}

void Timer::add(intrusive_ptr<TimerTask> task)
{
    Monitor::ScopedLock l(monitor);
    task->sortTime = task->nextFireTime;
    tasks.push(task);
    monitor.notify();
}

void Timer::start()
{
    Monitor::ScopedLock l(monitor);
    if (!active) {
        active = true;
        runner = Thread(this);
    }
}

void Timer::stop()
{
    {
        Monitor::ScopedLock l(monitor);
        if (!active) return;
        active = false;
        monitor.notifyAll();
    }
    runner.join();
}

// Allow subclasses to override behavior when firing a task.
void Timer::fire(boost::intrusive_ptr<TimerTask> t) {
    try {
        t->fireTask();
    } catch (const std::exception& e) {
        QPID_LOG(error, "Exception thrown by timer task " << t->getName() << ": " << e.what());
    }
}

// Provided for subclasses: called when a task is droped.
void Timer::drop(boost::intrusive_ptr<TimerTask>) {}

bool operator<(const intrusive_ptr<TimerTask>& a,
                       const intrusive_ptr<TimerTask>& b)
{
    // Lower priority if time is later
    return a.get() && b.get() && a->sortTime > b->sortTime;
}

}}
