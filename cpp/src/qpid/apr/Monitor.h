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

#include <boost/noncopyable.hpp>
#include <qpid/sys/Time.h>
#include "apr-1/apr_thread_mutex.h"
#include "apr-1/apr_thread_cond.h"
#include "APRBase.h"

namespace qpid {
namespace sys {

template <class L>
class ScopedLock
{
  public:
    ScopedLock(L& l) : mutex(l) { l.lock(); }
    ~ScopedLock() { mutex.unlock(); }
  private:
    L& mutex;
};


class Mutex : private boost::noncopyable
{
  public:
    typedef ScopedLock<Mutex> ScopedLock;
    
    Mutex();
    ~Mutex();
    void lock() { CHECK_APR_SUCCESS(apr_thread_mutex_lock(mutex)); }
    void unlock() { CHECK_APR_SUCCESS(apr_thread_mutex_unlock(mutex)); }
    void trylock() { CHECK_APR_SUCCESS(apr_thread_mutex_trylock(mutex)); }

  protected:
    apr_thread_mutex_t* mutex;
};

/** A condition variable and a mutex */
class Monitor : public Mutex
{
  public:
    Monitor();
    ~Monitor();
    void wait();
    void wait(int64_t nsecs);
    void notify();
    void notifyAll();

  private:
    apr_thread_cond_t* condition;
};


}}


#endif
