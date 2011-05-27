#ifndef QPID_SYS_TIMERWARNINGS_H
#define QPID_SYS_TIMERWARNINGS_H

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

#include "qpid/sys/Time.h"
#include <map>
#include <string>

namespace qpid {
namespace sys {

/**
 * The Timer class logs warnings when timer tasks are late and/or overrun.
 *
 * It is too expensive to log a warning for every late/overrun
 * incident, doing so aggravates the problem of tasks over-running and
 * being late.
 *
 * This class collects statistical data about each incident and prints
 * an aggregated report at regular intervals.
 */
class TimerWarnings
{
  public:
    TimerWarnings(Duration reportInterval);

    void late(const std::string& task, Duration delay);

    void overran(const std::string& task, Duration overrun, Duration time);

    void lateAndOverran(const std::string& task,
                        Duration delay, Duration overrun, Duration time);

  private:
    struct Statistic {
        Statistic() : total(0), count(0) {}
        void add(int64_t value) { total += value; ++count; }
        int64_t average() const { return count ? total/count : 0; }
        int64_t total;
        int64_t count;
    };

    // Keep statistics for 3 classes of incident:  late, overrun and both.
    struct TaskStats {
        Statistic lateDelay;    // Just late
        Statistic overranOverrun, overranTime; // Just overrun
        // Both
        Statistic lateAndOverranDelay, lateAndOverranOverrun, lateAndOverranTime;
    };

    typedef std::map<std::string, TaskStats> TaskStatsMap;

    void log();

    Duration interval;
    AbsTime nextReport;
    TaskStatsMap taskStats;
};
}} // namespace qpid::sys

#endif  /*!QPID_SYS_TIMERWARNINGS_H*/
