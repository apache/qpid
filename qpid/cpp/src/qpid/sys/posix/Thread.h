#ifndef _sys_posix_Thread_h
#define _sys_posix_Thread_h

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

#include "check.h"
#include <pthread.h>

namespace qpid {
namespace sys {

class Runnable;

class Thread
{
  public:
    inline static Thread current();

    /** ID of current thread for logging.
     * Workaround for broken Thread::current() in APR
     */
    static long logId() { return current().id(); }

    inline static void yield();

    inline Thread();
    inline explicit Thread(qpid::sys::Runnable*);
    inline explicit Thread(qpid::sys::Runnable&);
    
    inline void join();

    inline long id();
        
  private:
    static void* runRunnable(void* runnable);
    inline Thread(pthread_t);
    pthread_t thread;
};


Thread::Thread() : thread(0) {}

Thread::Thread(Runnable* runnable) {
    QPID_POSIX_ASSERT_THROW_IF(pthread_create(&thread, NULL, runRunnable, runnable));
}

Thread::Thread(Runnable& runnable) {
    QPID_POSIX_ASSERT_THROW_IF(pthread_create(&thread, NULL, runRunnable, &runnable));
}

void Thread::join(){
    if (thread != 0)
        QPID_POSIX_ASSERT_THROW_IF(pthread_join(thread, 0));
}

long Thread::id() {
    return long(thread);
}

Thread::Thread(pthread_t thr) : thread(thr) {}

Thread Thread::current() {
    return Thread(pthread_self());
}

void Thread::yield() 
{
    QPID_POSIX_ASSERT_THROW_IF(pthread_yield());
}


}}
#endif  /*!_sys_posix_Thread_h*/
