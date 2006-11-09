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
#include "APRPool.h"

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
    
    inline Mutex();
    inline ~Mutex();
    inline void lock();
    inline void unlock();
    inline void trylock();

  protected:
    apr_thread_mutex_t* mutex;
};

/** A condition variable and a mutex */
class Monitor : public Mutex
{
  public:
    inline Monitor();
    inline ~Monitor();
    inline void wait();
    inline void wait(int64_t nsecs);
    inline void notify();
    inline void notifyAll();

  private:
    apr_thread_cond_t* condition;
};



Mutex::Mutex() {
    CHECK_APR_SUCCESS(apr_thread_mutex_create(&mutex, APR_THREAD_MUTEX_NESTED, APRPool::get()));
}

Mutex::~Mutex(){
    CHECK_APR_SUCCESS(apr_thread_mutex_destroy(mutex));
}

void Mutex::lock() {
    CHECK_APR_SUCCESS(apr_thread_mutex_lock(mutex));
}
void Mutex::unlock() {
    CHECK_APR_SUCCESS(apr_thread_mutex_unlock(mutex));
}

void Mutex::trylock() {
    CHECK_APR_SUCCESS(apr_thread_mutex_trylock(mutex));
}

Monitor::Monitor() {
    CHECK_APR_SUCCESS(apr_thread_cond_create(&condition, APRPool::get()));
}

Monitor::~Monitor() {
    CHECK_APR_SUCCESS(apr_thread_cond_destroy(condition));
}

void Monitor::wait() {
    CHECK_APR_SUCCESS(apr_thread_cond_wait(condition, mutex));
}

void Monitor::wait(int64_t nsecs){
    // APR uses microseconds.
    apr_status_t status = apr_thread_cond_timedwait(
        condition, mutex, nsecs/1000);
    if(!status == APR_TIMEUP) CHECK_APR_SUCCESS(status);
}

void Monitor::notify(){
    CHECK_APR_SUCCESS(apr_thread_cond_signal(condition));
}

void Monitor::notifyAll(){
    CHECK_APR_SUCCESS(apr_thread_cond_broadcast(condition));
}


}}


#endif
