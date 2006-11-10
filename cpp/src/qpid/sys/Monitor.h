#ifndef _sys_Monitor_h
#define _sys_Monitor_h

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

#include <boost/noncopyable.hpp>

#ifdef USE_APR
#  include <apr-1/apr_thread_mutex.h>
#  include <apr-1/apr_thread_cond.h>
#  include <qpid/apr/APRBase.h>
#  include <qpid/apr/APRPool.h>
#else
#  include <pthread.h>
#  include <qpid/sys/Time.h>
#  include <qpid/posix/check.h>
#endif

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
#ifdef USE_APR
    apr_thread_mutex_t* mutex;
#else
    pthread_mutex_t mutex;
#endif
};

/** A condition variable and a mutex */
class Monitor : public Mutex
{
  public:
    inline Monitor();
    inline ~Monitor();
    inline void wait();
    inline bool wait(int64_t nsecs);
    inline void notify();
    inline void notifyAll();

  private:
#ifdef USE_APR
    apr_thread_cond_t* condition;
#else
    pthread_cond_t condition;
#endif
};


// APR ================================================================
#ifdef USE_APR

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

bool Monitor::wait(int64_t nsecs){
    // APR uses microseconds.
    apr_status_t status = apr_thread_cond_timedwait(
        condition, mutex, nsecs/1000);
    if(status != APR_TIMEUP) CHECK_APR_SUCCESS(status);
    return status == 0;
}

void Monitor::notify(){
    CHECK_APR_SUCCESS(apr_thread_cond_signal(condition));
}

void Monitor::notifyAll(){
    CHECK_APR_SUCCESS(apr_thread_cond_broadcast(condition));
}


}}


// POSIX ================================================================
#else
/**
 * PODMutex is a POD, can be static-initialized with
 * PODMutex m = QPID_PODMUTEX_INITIALIZER
 */
struct PODMutex 
{
    typedef ScopedLock<PODMutex> ScopedLock;

    inline void lock();
    inline void unlock();
    inline void trylock();

    // Must be public to be a POD:
    pthread_mutex_t mutex;
};

#define QPID_MUTEX_INITIALIZER { PTHREAD_MUTEX_INITIALIZER }


void PODMutex::lock() {
    CHECK(pthread_mutex_lock(&mutex));
}
void PODMutex::unlock() {
    CHECK(pthread_mutex_unlock(&mutex));
}

void PODMutex::trylock() {
    CHECK(pthread_mutex_trylock(&mutex));
}


Mutex::Mutex() {
    CHECK(pthread_mutex_init(&mutex, 0));
}

Mutex::~Mutex(){
    CHECK(pthread_mutex_destroy(&mutex));
}

void Mutex::lock() {
    CHECK(pthread_mutex_lock(&mutex));
}
void Mutex::unlock() {
    CHECK(pthread_mutex_unlock(&mutex));
}

void Mutex::trylock() {
    CHECK(pthread_mutex_trylock(&mutex));
}

Monitor::Monitor() {
    CHECK(pthread_cond_init(&condition, 0));
}

Monitor::~Monitor() {
    CHECK(pthread_cond_destroy(&condition));
}

void Monitor::wait() {
    CHECK(pthread_cond_wait(&condition, &mutex));
}

bool Monitor::wait(int64_t nsecs){
    Time t(nsecs);
    int status = pthread_cond_timedwait(&condition, &mutex, &t.getTimespec());
    if(status != 0) {
        if (errno == ETIMEDOUT) return false;
        CHECK(status);
    }
    return true;
}

void Monitor::notify(){
    CHECK(pthread_cond_signal(&condition));
}

void Monitor::notifyAll(){
    CHECK(pthread_cond_broadcast(&condition));
}


}}
#endif  /*USE_APR*/
#endif  /*!_sys_Monitor_h*/
