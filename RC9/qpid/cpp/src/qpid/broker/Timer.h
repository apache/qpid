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

#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/RefCounted.h"

#include <memory>
#include <queue>

#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace broker {

struct TimerTask : public RefCounted {
    const qpid::sys::Duration duration;
    qpid::sys::AbsTime time;
    volatile bool cancelled;

    TimerTask(qpid::sys::Duration timeout);
    TimerTask(qpid::sys::AbsTime time);
    virtual ~TimerTask();
    void reset();
    virtual void fire() = 0;
};

struct Later {
    bool operator()(const boost::intrusive_ptr<TimerTask>& a,
                    const boost::intrusive_ptr<TimerTask>& b) const;
};

class Timer : private qpid::sys::Runnable {
  protected:
    qpid::sys::Monitor monitor;            
    std::priority_queue<boost::intrusive_ptr<TimerTask>,
                        std::vector<boost::intrusive_ptr<TimerTask> >,
                        Later> tasks;
    qpid::sys::Thread runner;
    bool active;

    virtual void run();

  public:
    Timer();
    virtual ~Timer();

    void add(boost::intrusive_ptr<TimerTask> task);
    void start();
    void stop();

};


}}


#endif
