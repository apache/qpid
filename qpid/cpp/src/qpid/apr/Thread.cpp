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

#include "Thread.h"
#include "APRPool.h"
#include "APRBase.h"
#include <apr-1/apr_portable.h>

using namespace qpid::sys;
using qpid::sys::Runnable;

namespace {
void* APR_THREAD_FUNC run(apr_thread_t* thread, void *data) {
    reinterpret_cast<Runnable*>(data)->run();
    CHECK_APR_SUCCESS(apr_thread_exit(thread, APR_SUCCESS));
    return NULL;
} 
}

Thread::Thread() : thread(0) {}

Thread::Thread(Runnable* runnable) {
    CHECK_APR_SUCCESS(
        apr_thread_create(&thread, NULL, run, runnable, APRPool::get()));
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
