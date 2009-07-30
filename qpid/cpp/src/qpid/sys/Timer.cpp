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
#include <iostream>
#include <numeric>

using boost::intrusive_ptr;
using std::max;

namespace qpid {
namespace sys {

TimerTask::TimerTask(Duration timeout) :
    sortTime(AbsTime::FarFuture()),
    period(timeout),
    nextFireTime(AbsTime::now(), timeout),
    cancelled(false)
{}

TimerTask::TimerTask(AbsTime time) :
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

void TimerTask::setupNextFire() {
    if (period && readyToFire()) {
        nextFireTime = AbsTime(nextFireTime, period);
        cancelled = false;
    }
}

// Only allow tasks to be delayed
void TimerTask::restart() { nextFireTime = max(nextFireTime, AbsTime(AbsTime::now(), period)); }
void TimerTask::delayTill(AbsTime time) { period = 0; nextFireTime = max(nextFireTime, time); }

void TimerTask::cancel() {
    ScopedLock<Mutex> l(callbackLock);
    cancelled = true;
}
bool TimerTask::isCancelled() const { return cancelled; }

Timer::Timer() :
    active(false) 
{
    start();
}

Timer::~Timer() 
{
    stop();
}

void Timer::run()
{
    Monitor::ScopedLock l(monitor);
    while (active) {
        if (tasks.empty()) {
            monitor.wait();
        } else {
            intrusive_ptr<TimerTask> t = tasks.top();
            tasks.pop();
            {
            ScopedLock<Mutex> l(t->callbackLock);
            if (t->cancelled) {
                continue;
            } else if(t->readyToFire()) {
                Monitor::ScopedUnlock u(monitor);
                t->fireTask();
                continue;
            } else {
                // If the timer was adjusted into the future it might no longer
                // be the next event, so push and then get top to make sure
                // You can only push events into the future
                assert(!(t->nextFireTime < t->sortTime));
                t->sortTime = t->nextFireTime;
                tasks.push(t);
            }
            }
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

bool operator<(const intrusive_ptr<TimerTask>& a,
                       const intrusive_ptr<TimerTask>& b)
{
    // Lower priority if time is later
    return a.get() && b.get() && a->sortTime > b->sortTime;
}

}}
