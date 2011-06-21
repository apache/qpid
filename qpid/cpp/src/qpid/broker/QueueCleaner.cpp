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
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueCleaner.h"

#include "qpid/broker/Broker.h"
#include <boost/bind.hpp>

namespace qpid {
namespace broker {

QueueCleaner::QueueCleaner(QueueRegistry& q, sys::Timer* t) : queues(q), timer(t) {}

QueueCleaner::~QueueCleaner()
{
    if (task) task->cancel();
}

void QueueCleaner::start(qpid::sys::Duration p)
{
    period = p;
    task = new Task(*this, p);
    timer->add(task);
}

void QueueCleaner::setTimer(qpid::sys::Timer* timer) {
    this->timer = timer;
}


QueueCleaner::Task::Task(QueueCleaner& p, qpid::sys::Duration d) : sys::TimerTask(d,"QueueCleaner"), parent(p) {}

void QueueCleaner::Task::fire()
{
    parent.fired();
}

namespace {
struct CollectQueues
{
    std::vector<Queue::shared_ptr>* queues;
    CollectQueues(std::vector<Queue::shared_ptr>* q) : queues(q) {}
    void operator()(Queue::shared_ptr q)
    {
        queues->push_back(q);
    }
};
}

void QueueCleaner::fired()
{
    //collect copy of list of queues to avoid holding registry lock while we perform purge
    std::vector<Queue::shared_ptr> copy;
    CollectQueues collect(&copy);
    queues.eachQueue(collect);
    std::for_each(copy.begin(), copy.end(), boost::bind(&Queue::purgeExpired, _1, period));
    task->setupNextFire();
    timer->add(task);
}


}} // namespace qpid::broker
