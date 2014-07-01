#ifndef _sys_posix_Condition_h
#define _sys_posix_Condition_h

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

#include "qpid/sys/posix/PrivatePosix.h"

#include "qpid/sys/Mutex.h"
#include "qpid/sys/Time.h"

#include <time.h>
#include <sys/errno.h>
#include <boost/noncopyable.hpp>

namespace qpid {
namespace sys {

/**
 * A condition variable for thread synchronization.
 */
class Condition
{
  public:
    Condition();
    ~Condition();
    void wait(Mutex&);
    bool wait(Mutex&, const AbsTime& absoluteTime);
    void notify();
    void notifyAll();

  private:
    pthread_cond_t condition;
};

inline Condition::~Condition() {
    QPID_POSIX_ABORT_IF(pthread_cond_destroy(&condition));
}

inline void Condition::wait(Mutex& mutex) {
    QPID_POSIX_ASSERT_THROW_IF(pthread_cond_wait(&condition, &mutex.mutex));
}

inline bool Condition::wait(Mutex& mutex, const AbsTime& absoluteTime){
    struct timespec ts;
    toTimespec(ts, absoluteTime);
    int status = pthread_cond_timedwait(&condition, &mutex.mutex, &ts);
    if (status != 0) {
        if (status == ETIMEDOUT) return false;
        throw QPID_POSIX_ERROR(status);
    }
    return true;
}

inline void Condition::notify(){
    QPID_POSIX_ASSERT_THROW_IF(pthread_cond_signal(&condition));
}

inline void Condition::notifyAll(){
    QPID_POSIX_ASSERT_THROW_IF(pthread_cond_broadcast(&condition));
}

}}
#endif  /*!_sys_posix_Condition_h*/
