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
#include "PeriodicTimerImpl.h"
#include "Cluster.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/ClusterPeriodicTimerBody.h"

namespace qpid {
namespace cluster {

PeriodicTimerImpl::PeriodicTimerImpl(Cluster& c) : cluster(c) {}

PeriodicTimerImpl::TaskEntry::TaskEntry(
    Cluster& c, const Task& t, sys::Duration d, const std::string& n)
    : TimerTask(d), cluster(c), timer(c.getBroker().getTimer()),
      task(t), name(n), inFlight(false)
{}

void PeriodicTimerImpl::TaskEntry::fire() {
    sys::Mutex::ScopedLock l(lock);
    // Only the elder mcasts.
    // Don't mcast another if we haven't yet received the last one.
    if (cluster.isElder() && !inFlight) {
        inFlight = true;
        cluster.getMulticast().mcastControl(
            framing::ClusterPeriodicTimerBody(framing::ProtocolVersion(), name),
            cluster.getId());
    }
    setupNextFire();
    timer.add(this);
}

void PeriodicTimerImpl::TaskEntry::deliver() {
    task();
    sys::Mutex::ScopedLock l(lock);
    inFlight = false;
}


void PeriodicTimerImpl::add(
    const Task& task, sys::Duration period, const std::string& name)
{
    sys::Mutex::ScopedLock l(lock);
    if (map.find(name) != map.end())
        throw Exception(QPID_MSG("Cluster timer task name added twice: " << name));
    map[name] = new TaskEntry(cluster, task, period, name);
}

void PeriodicTimerImpl::deliver(const std::string& name) {
    Map::iterator i;
    {
        sys::Mutex::ScopedLock l(lock);
        i = map.find(name);
        if (i == map.end())
            throw Exception(QPID_MSG("Cluster timer unknown task: " << name));
    }
    i->second->deliver();
}

}} // namespace qpid::cluster
