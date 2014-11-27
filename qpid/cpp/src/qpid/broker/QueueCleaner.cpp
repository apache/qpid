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
#include "qpid/broker/QueueCleaner.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/sys/Timer.h"
#include "qpid/sys/Time.h"

#include <boost/function.hpp>
#include <boost/bind.hpp>

namespace qpid {
namespace broker {

namespace {
    typedef boost::function0<void> FireFunction;
    class Task : public sys::TimerTask
    {
    public:
        Task(FireFunction f, sys::Duration duration);
        void fire();
    private:
        FireFunction fireFunction;
    };

    Task::Task(FireFunction f, qpid::sys::Duration d) : sys::TimerTask(d,"QueueCleaner"), fireFunction(f) {}

    void Task::fire()
    {
        fireFunction();
    }
}
QueueCleaner::QueueCleaner(QueueRegistry& q, boost::shared_ptr<sys::Poller> p, sys::Timer* t)
    : queues(q), timer(t), purging(boost::bind(&QueueCleaner::purge, this, _1), p)
{
    purging.start();
}

QueueCleaner::~QueueCleaner()
{
    purging.stop();
    if (task) task->cancel();
}

void QueueCleaner::start(qpid::sys::Duration p)
{
    period = p;
    task = new Task(boost::bind(&QueueCleaner::fired, this), p);
    timer->add(task);
}

void QueueCleaner::setTimer(qpid::sys::Timer* timer) {
    this->timer = timer;
}

void QueueCleaner::fired()
{
    queues.eachQueue(boost::bind(&PurgeSet::push, &purging, _1));
    if (purging.size() == 0) {
        QueuePtr nullQueuePtr;
        purging.push(nullQueuePtr);
    }
    QPID_LOG(debug, "QueueCleaner requested purge of " << purging.size() << " queues");
}

QueueCleaner::QueuePtrs::const_iterator QueueCleaner::purge(const QueueCleaner::QueuePtrs& batch)
{
    // Purge for one second at most
    // If running overtime then reschedule through pollable queue
    sys::AbsTime tmoTime = sys::AbsTime(sys::AbsTime::now(), 1 * sys::TIME_SEC);
    int nPurged = 0;
    for (QueuePtrs::const_iterator i = batch.begin(); i != batch.end(); ++i) {
        if (sys::AbsTime::now() < tmoTime) {
            nPurged++;
            if (*i) {
                (*i)->purgeExpired(period);
            }
        } else {
            purging.push(*i);
        }
    }
    QPID_LOG(debug, "QueueCleaner purged " << nPurged << " of " << batch.size()
        << " queues in this timeslice");
    if (purging.empty()) {
        task->restart();
        timer->add(task);
        QPID_LOG(debug, "QueueCleaner restarted purge timer");
    }
    return batch.end();
}

}} // namespace qpid::broker
