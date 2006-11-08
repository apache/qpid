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
#include "Monitor.h"
#include "APRPool.h"

using namespace qpid::sys;

Mutex::Mutex()
{
    APRBase::increment();
    // TODO aconway 2006-11-08: Switch to non-nested.
    CHECK_APR_SUCCESS(apr_thread_mutex_create(&mutex, APR_THREAD_MUTEX_NESTED, APRPool::get()));
}

Mutex::~Mutex(){
    CHECK_APR_SUCCESS(apr_thread_mutex_destroy(mutex));
    APRBase::decrement();
}

Monitor::Monitor() 
{
    CHECK_APR_SUCCESS(apr_thread_cond_create(&condition, APRPool::get()));
}

Monitor::~Monitor(){
    CHECK_APR_SUCCESS(apr_thread_cond_destroy(condition));
}



void Monitor::wait(){
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

