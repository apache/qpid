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
#include "qpid/sys/APRBase.h"
#include "qpid/sys/ThreadFactory.h"

using namespace qpid::sys;

ThreadFactory::ThreadFactory(){
    APRBase::increment();
    CHECK_APR_SUCCESS(apr_pool_create(&pool, NULL));
}

ThreadFactory::~ThreadFactory(){
    apr_pool_destroy(pool);
    APRBase::decrement();
}

Thread* ThreadFactory::create(Runnable* runnable){
    return new Thread(pool, runnable);
}
