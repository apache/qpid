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

#include "Cluster.h"
#include "ClusterTimer.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/ClusterTimerWakeupBody.h"
#include "qpid/framing/ClusterTimerDropBody.h"

namespace qpid {
namespace cluster {

using boost::intrusive_ptr;
using std::max;
using sys::Timer;
using sys::TimerTask;


ClusterTimer::ClusterTimer(Cluster& c) : cluster(c) {
    // Allow more generous overrun threshold with cluster as we
    // have to do a CPG round trip before executing the task.
    overran = 10*sys::TIME_MSEC;
    late = 100*sys::TIME_MSEC;
}

ClusterTimer::~ClusterTimer() {}

// Initialization or deliver thread.
void ClusterTimer::add(intrusive_ptr<TimerTask> task)
{
    QPID_LOG(trace, "Adding cluster timer task " << task->getName());
    Map::iterator i = map.find(task->getName());
    if (i != map.end())
        throw Exception(QPID_MSG("Task already exists with name " << task->getName()));
    map[task->getName()] = task;
    // Only the elder actually activates the task with the Timer base class.
    if (cluster.isElder()) {
        QPID_LOG(trace, "Elder activating cluster timer task " << task->getName());
        Timer::add(task);
    }
}

// Timer thread
void ClusterTimer::fire(intrusive_ptr<TimerTask> t) {
    // Elder mcasts wakeup on fire, task is not fired until deliverWakeup
    if (cluster.isElder()) {
        QPID_LOG(trace, "Sending cluster timer wakeup " << t->getName());
        cluster.getMulticast().mcastControl(
            framing::ClusterTimerWakeupBody(framing::ProtocolVersion(), t->getName()),
            cluster.getId());
    }
    else
        QPID_LOG(trace, "Cluster timer task fired, but not elder " << t->getName());
}

// Timer thread
void ClusterTimer::drop(intrusive_ptr<TimerTask> t) {
    // Elder mcasts drop, task is droped in deliverDrop
    if (cluster.isElder()) {
        QPID_LOG(trace, "Sending cluster timer drop " << t->getName());
        cluster.getMulticast().mcastControl(
            framing::ClusterTimerDropBody(framing::ProtocolVersion(), t->getName()),
            cluster.getId());
    }
    else
        QPID_LOG(trace, "Cluster timer task dropped, but not on elder " << t->getName());
}

// Deliver thread
void ClusterTimer::deliverWakeup(const std::string& name) {
    QPID_LOG(trace, "Cluster timer wakeup delivered for " << name);
    Map::iterator i = map.find(name);
    if (i == map.end())
        throw Exception(QPID_MSG("Cluster timer wakeup non-existent task " << name));
    else {
        intrusive_ptr<TimerTask> t = i->second;
        map.erase(i);
        Timer::fire(t);
    }
}

// Deliver thread
void ClusterTimer::deliverDrop(const std::string& name) {
    QPID_LOG(trace, "Cluster timer drop delivered for " << name);
    Map::iterator i = map.find(name);
    if (i == map.end())
        throw Exception(QPID_MSG("Cluster timer drop non-existent task " << name));
    else {
        intrusive_ptr<TimerTask> t = i->second;
        map.erase(i);
    }
}

// Deliver thread
void ClusterTimer::becomeElder() {
    for (Map::iterator i = map.begin(); i != map.end(); ++i) {
        Timer::add(i->second);
    }
}

}}
