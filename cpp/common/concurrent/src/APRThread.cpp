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
#include "APRBase.h"
#include "APRThread.h"
#include "apr_portable.h"

using namespace qpid::concurrent;

void* APR_THREAD_FUNC ExecRunnable(apr_thread_t* thread, void *data){
    ((Runnable*) data)->run();
    CHECK_APR_SUCCESS(apr_thread_exit(thread, APR_SUCCESS));
    return NULL;
} 

APRThread::APRThread(apr_pool_t* _pool, Runnable* _runnable) : pool(_pool), runnable(_runnable){}

APRThread::~APRThread(){
}

void APRThread::start(){
    CHECK_APR_SUCCESS(apr_thread_create(&runner, NULL, ExecRunnable,(void*) runnable, pool));
}

void APRThread::join(){
    apr_status_t status;
    CHECK_APR_SUCCESS(apr_thread_join(&status, runner));
}

void APRThread::interrupt(){
    CHECK_APR_SUCCESS(apr_thread_exit(runner, APR_SUCCESS));
}

unsigned int qpid::concurrent::APRThread::currentThread(){
    return apr_os_thread_current();
}
