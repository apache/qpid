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
#include "qpid/concurrent/APRBase.h"
#include "qpid/concurrent/APRMonitor.h"
#include <iostream>

qpid::concurrent::APRMonitor::APRMonitor(){
    APRBase::increment();
    CHECK_APR_SUCCESS(apr_pool_create(&pool, NULL));
    CHECK_APR_SUCCESS(apr_thread_mutex_create(&mutex, APR_THREAD_MUTEX_NESTED, pool));
    CHECK_APR_SUCCESS(apr_thread_cond_create(&condition, pool));
}

qpid::concurrent::APRMonitor::~APRMonitor(){
    CHECK_APR_SUCCESS(apr_thread_cond_destroy(condition));
    CHECK_APR_SUCCESS(apr_thread_mutex_destroy(mutex));
    apr_pool_destroy(pool);
    APRBase::decrement();
}

void qpid::concurrent::APRMonitor::wait(){
    CHECK_APR_SUCCESS(apr_thread_cond_wait(condition, mutex));
}


void qpid::concurrent::APRMonitor::wait(u_int64_t time){
    apr_status_t status = apr_thread_cond_timedwait(condition, mutex, time * 1000);
    if(!status == APR_TIMEUP) CHECK_APR_SUCCESS(status);
}

void qpid::concurrent::APRMonitor::notify(){
    CHECK_APR_SUCCESS(apr_thread_cond_signal(condition));
}

void qpid::concurrent::APRMonitor::notifyAll(){
    CHECK_APR_SUCCESS(apr_thread_cond_broadcast(condition));
}

void qpid::concurrent::APRMonitor::acquire(){
    CHECK_APR_SUCCESS(apr_thread_mutex_lock(mutex));
}

void qpid::concurrent::APRMonitor::release(){
    CHECK_APR_SUCCESS(apr_thread_mutex_unlock(mutex));
}
