#ifndef QPID_BROKER_PERIODICTIMER_H
#define QPID_BROKER_PERIODICTIMER_H

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

#include "qpid/sys/PeriodicTimer.h"
#include <vector>
#include <memory>

namespace qpid {
namespace broker {

/**
 * A PeriodicTimer implementation that delegates to another PeriodicTimer.
 *
 * Tasks added while there is no delegate timer are stored.
 * When a delgate timer is set, stored tasks are added to it.
 */
class DelegatingPeriodicTimer : public sys::PeriodicTimer
{
  public:
    DelegatingPeriodicTimer();
    /** Add a task: if no delegate, store it. When delegate set, add stored tasks */
    void add(const Task& task, sys::Duration period, const std::string& taskName);
    /** Set the delegate, transfers ownership of delegate. */
    void setDelegate(std::auto_ptr<PeriodicTimer> delegate);
    bool hasDelegate() { return delegate.get(); }
  private:
    struct Entry { Task task; sys::Duration period; std::string name; };
    typedef std::vector<Entry> Entries;
    std::auto_ptr<PeriodicTimer> delegate;
    Entries entries;

};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_PERIODICTIMER_H*/
