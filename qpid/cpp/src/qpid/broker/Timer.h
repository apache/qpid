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
#ifndef _Timer_
#define _Timer_

#include <memory>
#include <queue>
#include <boost/shared_ptr.hpp>
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"

namespace qpid {
namespace broker {

struct TimerTask
{
    typedef boost::shared_ptr<TimerTask> shared_ptr;

    const qpid::sys::AbsTime time;
    volatile bool cancelled;

    TimerTask(qpid::sys::Duration timeout);
    TimerTask(qpid::sys::AbsTime time);
    virtual ~TimerTask();
    virtual void fire() = 0;
};

 struct Later
 {
     bool operator()(const TimerTask::shared_ptr& a, const TimerTask::shared_ptr& b) const;
 };

class Timer : private qpid::sys::Runnable
{
    qpid::sys::Monitor monitor;            
    std::priority_queue<TimerTask::shared_ptr, std::vector<TimerTask::shared_ptr>, Later> tasks;
    qpid::sys::Thread runner;
    bool active;

    void run();
    void signalStop();

public:
    Timer();
    ~Timer();

    void add(TimerTask::shared_ptr task);
    void start();
    void stop();

};

}
}


#endif
