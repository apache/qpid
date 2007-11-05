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
#include <boost/intrusive_ptr.hpp>
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"

namespace qpid {
namespace broker {

struct TimerTask
{
    const qpid::sys::Duration duration;
    typedef boost::shared_ptr<TimerTask> shared_ptr;

    qpid::sys::AbsTime time;
    volatile bool cancelled;

    TimerTask(qpid::sys::Duration timeout);
    TimerTask(qpid::sys::AbsTime time);
    virtual ~TimerTask();
    void reset();
    virtual void fire() = 0;
};

struct TimerTaskA : public TimerTask
{
    typedef boost::intrusive_ptr<TimerTaskA> intrusive_ptr;

    TimerTaskA(qpid::sys::Duration timeout);
    TimerTaskA(qpid::sys::AbsTime time);
    virtual ~TimerTaskA();
    
    size_t          ref_cnt;
    inline size_t refcnt(void) { return ref_cnt;}
    inline void ref(void) { ref_cnt++; }
    inline void unref(void) { ref_cnt--; }
};

struct Later
{
    bool operator()(const TimerTask::shared_ptr& a, const TimerTask::shared_ptr& b) const;
};

struct LaterA
{
     bool operator()(const TimerTaskA::intrusive_ptr& a, const TimerTaskA::intrusive_ptr& b) const;
};


class Timer : private qpid::sys::Runnable
{
protected:
    qpid::sys::Monitor monitor;            
    std::priority_queue<TimerTask::shared_ptr, std::vector<TimerTask::shared_ptr>, Later> tasks;
    qpid::sys::Thread runner;
    bool active;

    virtual void run();
    void signalStop();

public:
    Timer();
    virtual ~Timer();

    void add(TimerTask::shared_ptr task);
    void start();
    void stop();

};

class TimerA : private qpid::sys::Runnable
{
protected:
    qpid::sys::Monitor monitor;            
    std::priority_queue<TimerTaskA::intrusive_ptr&, std::vector<TimerTaskA::intrusive_ptr>,
             LaterA> itasks;
    qpid::sys::Thread runner;
    bool active;
    
    virtual void run();
    void signalStop();

public:
    TimerA();
    virtual ~TimerA();

    void add(TimerTaskA::intrusive_ptr& task);
    void start();
    void stop();
};

void intrusive_ptr_add_ref(TimerTaskA* r);
void intrusive_ptr_release(TimerTaskA* r);


}
}


#endif
