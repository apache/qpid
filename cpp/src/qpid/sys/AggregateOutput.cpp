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

#include "qpid/sys/AggregateOutput.h"
#include "qpid/log/Statement.h"
#include <algorithm>

namespace qpid {
namespace sys {

AggregateOutput::AggregateOutput() : busy(false) {}

namespace {
// Clear the busy flag and notify waiting threads in destructor.
struct ScopedBusy {
    bool& flag;
    Monitor& monitor;
    ScopedBusy(bool& f, Monitor& m) : flag(f), monitor(m) { f = true; }
    ~ScopedBusy() { flag = false; monitor.notifyAll(); }
};
}

bool AggregateOutput::doOutput() {
    Mutex::ScopedLock l(lock);
    ScopedBusy sb(busy, lock);

    while (!tasks.empty()) {
        OutputTask* t=tasks.front();
        tasks.pop_front();
        taskSet.erase(t);
        bool didOutput;
        {
            // Allow concurrent call to addOutputTask.
            // removeOutputTask will wait till !busy before removing a task.
            Mutex::ScopedUnlock u(lock);
            didOutput = t->doOutput();
        }
        if (didOutput) {
            if (taskSet.insert(t).second) {
                tasks.push_back(t);
            }
            return true;
        }
    }
    return false;
}
  
void AggregateOutput::addOutputTask(OutputTask* task) {
    Mutex::ScopedLock l(lock);
    if (taskSet.insert(task).second) {
        tasks.push_back(task);
    }
}

void AggregateOutput::removeOutputTask(OutputTask* task) {
    Mutex::ScopedLock l(lock);
    while (busy) lock.wait();
    taskSet.erase(task);
    tasks.erase(std::remove(tasks.begin(), tasks.end(), task), tasks.end());
}
  
void AggregateOutput::removeAll()
{
    Mutex::ScopedLock l(lock);
    while (busy) lock.wait();
    taskSet.clear();
    tasks.clear();
}
  

}} // namespace qpid::sys
