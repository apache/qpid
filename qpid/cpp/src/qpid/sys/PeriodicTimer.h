#ifndef QPID_SYS_PERIODICTIMER_H
#define QPID_SYS_PERIODICTIMER_H

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

#include "Timer.h"
#include <boost/function.hpp>

namespace qpid {
namespace sys {

/**
 * Interface of a timer for periodic tasks that should be synchronized
 * across all brokers in a periodic. The standalone broker
 * implementation simply wraps qpid::sys::Timer. The clustered broker
 * implementation synchronizes execution of periodic tasks on all
 * periodic members.
 */
class PeriodicTimer
{
  public:
    typedef boost::function<void()> Task;

    QPID_COMMON_EXTERN virtual ~PeriodicTimer() {}

    /**
     * Add a named task to be executed at the given period.
     *
     * The task registered under the same name will be executed on
     * all brokers at the given period.
     */
    virtual void add(const Task& task, Duration period, const std::string& taskName) = 0;

};
}} // namespace qpid::sys

#endif  /*!QPID_SYS_PERIODICTIMER_H*/
