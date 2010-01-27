#ifndef QPID_CLUSTER_PERIODICTIMERIMPL_H
#define QPID_CLUSTER_PERIODICTIMERIMPL_H

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
#include "qpid/sys/Mutex.h"
#include <map>

namespace qpid {
namespace cluster {

class Cluster;

/**
 * Cluster implementation of PeriodicTimer.
 *
 * All members run a periodic task, elder mcasts periodic-timer control.
 * Actual task is executed on delivery of periodic-timer.
 */
class PeriodicTimerImpl : public sys::PeriodicTimer
{
  public:
    PeriodicTimerImpl(Cluster& cluster);
    void add(const Task& task, sys::Duration period, const std::string& taskName);
    void deliver(const std::string& name);

  private:

    class TaskEntry : public sys::TimerTask {
      public:
        TaskEntry(Cluster&, const Task&, sys::Duration period, const std::string& name);
        void fire();
        void deliver();
      private:
        sys::Mutex lock;
        Cluster& cluster;
        sys::Timer& timer;
        Task task;
        std::string name;
        bool inFlight;
    };

    typedef std::map<std::string, boost::intrusive_ptr<TaskEntry> > Map;
    struct TaskImpl;

    sys::Mutex lock;
    Map map;
    Cluster& cluster;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_PERIODICTIMER_H*/
