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
#include "TimerWarnings.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace sys {

TimerWarnings::TimerWarnings(Duration reportInterval) :
    interval(reportInterval),
    nextReport(now(), reportInterval)
{}

void TimerWarnings::late(const std::string& task, Duration delay) {
    taskStats[task].lateDelay.add(delay);
    log();
}

void TimerWarnings::overran(const std::string& task, Duration overrun, Duration time)
{
    taskStats[task].overranOverrun.add(overrun);
    taskStats[task].overranTime.add(time);
    log();
}

void TimerWarnings::lateAndOverran(
    const std::string& task, Duration delay, Duration overrun, Duration time)
{
    taskStats[task].lateAndOverranDelay.add(delay);
    taskStats[task].lateAndOverranOverrun.add(overrun);
    taskStats[task].lateAndOverranTime.add(time);
    log();
}

void TimerWarnings::log() {
    if (!taskStats.empty() && nextReport < now()) {
        for (TaskStatsMap::iterator i = taskStats.begin(); i != taskStats.end(); ++i) {
            std::string task = i->first;
            TaskStats& stats = i->second;
            if (stats.lateDelay.count)
                QPID_LOG(debug, task << " task late "
                         << stats.lateDelay.count << " times by "
                         << stats.lateDelay.average()/TIME_MSEC << "ms on average.");

            if (stats.overranOverrun.count)
                QPID_LOG(debug, task << " task overran "
                         << stats.overranOverrun.count << " times by "
                         << stats.overranOverrun.average()/TIME_MSEC << "ms (taking "
                         << stats.overranTime.average() << "ns) on average.");

            if (stats.lateAndOverranOverrun.count)
                QPID_LOG(debug, task << " task late and overran "
                         << stats.lateAndOverranOverrun.count << " times: late "
                         << stats.lateAndOverranDelay.average()/TIME_MSEC << "ms, overran "
                         << stats.lateAndOverranOverrun.average()/TIME_MSEC << "ms (taking "
                         << stats.lateAndOverranTime.average() << "ns) on average.");

        }
        nextReport = AbsTime(now(), interval);
        taskStats.clear();
    }
}

}} // namespace qpid::sys
