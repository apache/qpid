#ifndef _sys_Condition_h
#define _sys_Condition_h

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

#include <sys/errno.h>
#include <boost/noncopyable.hpp>
#include "Mutex.h"
#include "Time.h"

#ifdef USE_APR
#include <apr_thread_cond.h>
#endif

namespace qpid {
namespace sys {

/**
 * A condition variable for thread synchronization.
 */
class Condition
{
  public:
    inline Condition();
    inline ~Condition();
    inline void wait(Mutex&);
    inline bool wait(Mutex&, const Time& absoluteTime);
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

Condition::Condition() {
    CHECK_APR_SUCCESS(apr_thread_cond_create(&condition, APRPool::get()));
}

Condition::~Condition() {
    CHECK_APR_SUCCESS(apr_thread_cond_destroy(condition));
}

void Condition::wait(Mutex& mutex) {
    CHECK_APR_SUCCESS(apr_thread_cond_wait(condition, mutex.mutex));
}

bool Condition::wait(Mutex& mutex, const Time& absoluteTime){
    // APR uses microseconds.
    apr_status_t status =
        apr_thread_cond_timedwait(
            condition, mutex.mutex, absoluteTime/TIME_USEC);
    if(status != APR_TIMEUP) CHECK_APR_SUCCESS(status);
    return status == 0;
}

void Condition::notify(){
    CHECK_APR_SUCCESS(apr_thread_cond_signal(condition));
}

void Condition::notifyAll(){
    CHECK_APR_SUCCESS(apr_thread_cond_broadcast(condition));
}

#else
// POSIX ================================================================

Condition::Condition() {
    QPID_POSIX_THROW_IF(pthread_cond_init(&condition, 0));
}

Condition::~Condition() {
    QPID_POSIX_THROW_IF(pthread_cond_destroy(&condition));
}

void Condition::wait(Mutex& mutex) {
    QPID_POSIX_THROW_IF(pthread_cond_wait(&condition, &mutex.mutex));
}

bool Condition::wait(Mutex& mutex, const Time& absoluteTime){
    struct timespec ts;
    toTimespec(ts, absoluteTime);
    int status = pthread_cond_timedwait(&condition, &mutex.mutex, &ts);
    if (status != 0) {
        if (status == ETIMEDOUT) return false;
        throw QPID_POSIX_ERROR(status);
    }
    return true;
}

void Condition::notify(){
    QPID_POSIX_THROW_IF(pthread_cond_signal(&condition));
}

void Condition::notifyAll(){
    QPID_POSIX_THROW_IF(pthread_cond_broadcast(&condition));
}
#endif  /*USE_APR*/


}}
#endif  /*!_sys_Condition_h*/
