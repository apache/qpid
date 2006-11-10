#ifndef _sys_Thread_h
#define _sys_Thread_h

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

#include <qpid/sys/Runnable.h>

#ifdef USE_APR
#  include <apr-1/apr_thread_proc.h>
#  include <apr-1/apr_portable.h>
#  include <qpid/apr/APRPool.h>
#  include <qpid/apr/APRBase.h>
#else
#  include <qpid/posix/check.h>
#  include <pthread.h>
#endif

namespace qpid {
namespace sys {

class Thread
{
  public:
    inline Thread();
    inline explicit Thread(qpid::sys::Runnable*);
    inline void join();
    inline static Thread current();

  private:
#ifdef USE_APR
    static void* APR_THREAD_FUNC runRunnable(apr_thread_t* thread, void *data);
    inline Thread(apr_thread_t* t);
    apr_thread_t* thread;
#else
    static void* runRunnable(void* runnable);
    inline Thread(pthread_t);
    pthread_t thread;
#endif    
};


Thread::Thread() : thread(0) {}

// APR ================================================================
#ifdef USE_APR

Thread::Thread(Runnable* runnable) {
    CHECK_APR_SUCCESS(
        apr_thread_create(&thread, 0, runRunnable, runnable, APRPool::get()));
}

void Thread::join(){
    apr_status_t status;
    if (thread != 0) 
        CHECK_APR_SUCCESS(apr_thread_join(&status, thread));
}

Thread::Thread(apr_thread_t* t) : thread(t) {}

Thread Thread::current(){
    apr_thread_t* thr;
    apr_os_thread_t osthr = apr_os_thread_current();
    CHECK_APR_SUCCESS(apr_os_thread_put(&thr, &osthr, APRPool::get()));
    return Thread(thr);
}

// POSIX ================================================================
#else

Thread::Thread(Runnable* runnable) {
    CHECK(pthread_create(&thread, NULL, runRunnable, runnable));
}

void Thread::join(){
    if (thread != 0) CHECK(pthread_join(thread, 0));
}

Thread::Thread(pthread_t thr) : thread(thr) {}

Thread Thread::current() {
    return Thread(pthread_self());
}
#endif

}}

#endif  /*!_sys_Thread_h*/
