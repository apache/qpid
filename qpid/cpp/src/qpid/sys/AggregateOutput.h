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
#ifndef _AggregateOutput_
#define _AggregateOutput_

#include "qpid/sys/Monitor.h"
#include "qpid/sys/OutputTask.h"

#include "qpid/CommonImportExport.h"

#include <algorithm>
#include <deque>
#include <set>

namespace qpid {
namespace sys {

/**
 * Holds a collection of output tasks, doOutput picks the next one to execute.
 * 
 * Tasks are automatically removed if their doOutput() or hasOutput() returns false.
 * 
 * Thread safe. addOutputTask may be called in one connection thread while
 * doOutput is called in another.
 */

class QPID_COMMON_CLASS_EXTERN AggregateOutput : public OutputTask
{
    typedef std::deque<OutputTask*> TaskList;
    typedef std::set<OutputTask*> TaskSet;

    Monitor lock;
    TaskList tasks;
    TaskSet taskSet;
    bool busy;

  public:
    QPID_COMMON_EXTERN AggregateOutput();

    // These may be called concurrently with any function.
    QPID_COMMON_EXTERN void addOutputTask(OutputTask* t);

    // These functions must not be called concurrently with each other.
    QPID_COMMON_EXTERN bool doOutput();
    QPID_COMMON_EXTERN void removeOutputTask(OutputTask* t);
    QPID_COMMON_EXTERN void removeAll();

    /** Apply f to each OutputTask* in the tasks list */
    template <class F> void eachOutput(F f) {
        Mutex::ScopedLock l(lock);
        std::for_each(tasks.begin(), tasks.end(), f);
    }
};

}} // namespace qpid::sys


#endif
