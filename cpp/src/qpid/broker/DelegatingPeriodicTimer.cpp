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
#include "DelegatingPeriodicTimer.h"

namespace qpid {
namespace broker {

DelegatingPeriodicTimer::DelegatingPeriodicTimer() {}

void DelegatingPeriodicTimer::add(
    const Task& task, sys::Duration period, const std::string& taskName)
{
    if (delegate.get())
        delegate->add(task, period, taskName);
    else {
        Entry e;
        e.task = task;
        e.period = period;
        e.name = taskName;
        entries.push_back(e);
    }
}

void DelegatingPeriodicTimer::setDelegate(std::auto_ptr<PeriodicTimer> impl) {
    assert(impl.get());
    assert(!delegate.get());
    delegate = impl;
    for (Entries::iterator i = entries.begin(); i != entries.end(); ++i)
        delegate->add(i->task, i->period, i->name);
}

}} // namespace qpid::broker
