/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _Monitor_
#define _Monitor_

#include "apr-1/apr_thread_mutex.h"
#include "apr-1/apr_thread_cond.h"
#include "qpid/concurrent/Monitor.h"

namespace qpid {
namespace concurrent {

class Monitor
{
    apr_pool_t* pool;
    apr_thread_mutex_t* mutex;
    apr_thread_cond_t* condition;

  public:
    Monitor();
    virtual ~Monitor();
    virtual void wait();
    virtual void wait(u_int64_t time);
    virtual void notify();
    virtual void notifyAll();
    virtual void acquire();
    virtual void release();
};

class Locker
{
  public:
    Locker(Monitor& monitor_) : monitor(monitor_) { monitor.acquire(); }
    ~Locker() { monitor.release(); }
  private:
    Monitor& monitor;
};
}}


#endif
