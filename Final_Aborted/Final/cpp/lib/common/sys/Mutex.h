#ifndef _sys_Mutex_h
#define _sys_Mutex_h

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

#ifdef USE_APR
#  include <apr_thread_mutex.h>
#  include <apr_pools.h>
#  include "apr/APRBase.h"
#  include "apr/APRPool.h"
#else
#  include <pthread.h>
#  include <posix/check.h>
#endif
#include <boost/noncopyable.hpp>

namespace qpid {
namespace sys {

/**
 * Scoped lock template: calls lock() in ctor, unlock() in dtor.
 * L can be any class with lock() and unlock() functions.
 */
template <class L>
class ScopedLock
{
  public:
    ScopedLock(L& l) : mutex(l) { l.lock(); }
    ~ScopedLock() { mutex.unlock(); }
  private:
    L& mutex;
};

/**
 * Mutex lock.
 */
class Mutex : private boost::noncopyable {
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
    apr_pool_t* pool;
#else
    pthread_mutex_t mutex;
#endif
};

#ifdef USE_APR
// APR ================================================================

Mutex::Mutex() {
    pool = APRPool::get();
    CHECK_APR_SUCCESS(apr_thread_mutex_create(&mutex, APR_THREAD_MUTEX_NESTED, pool));
}

Mutex::~Mutex(){
    CHECK_APR_SUCCESS(apr_thread_mutex_destroy(mutex));
    APRPool::free(pool);    
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

#else
// POSIX ================================================================

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
    QPID_POSIX_THROW_IF(pthread_mutex_lock(&mutex));
}
void PODMutex::unlock() {
    QPID_POSIX_THROW_IF(pthread_mutex_unlock(&mutex));
}

void PODMutex::trylock() {
    QPID_POSIX_THROW_IF(pthread_mutex_trylock(&mutex));
}


Mutex::Mutex() {
    QPID_POSIX_THROW_IF(pthread_mutex_init(&mutex, 0));
}

Mutex::~Mutex(){
    QPID_POSIX_THROW_IF(pthread_mutex_destroy(&mutex));
}

void Mutex::lock() {
    QPID_POSIX_THROW_IF(pthread_mutex_lock(&mutex));
}
void Mutex::unlock() {
    QPID_POSIX_THROW_IF(pthread_mutex_unlock(&mutex));
}

void Mutex::trylock() {
    QPID_POSIX_THROW_IF(pthread_mutex_trylock(&mutex));
}

#endif // USE_APR

}}



#endif  /*!_sys_Mutex_h*/
