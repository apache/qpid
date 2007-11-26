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

#include "APRPool.h"
#include "APRBase.h"
#include <boost/pool/detail/singleton.hpp>
#include <iostream>
#include <sstream>


using namespace qpid::sys;

APRPool::APRPool(){
    APRBase::increment();
    allocated_pools = new std::stack<apr_pool_t*>();    
    CHECK_APR_SUCCESS(apr_pool_create(&pool, NULL));
    CHECK_APR_SUCCESS(apr_thread_mutex_create(&poolGuard, APR_THREAD_MUTEX_NESTED, pool));
}

APRPool::~APRPool(){
    while(allocated_pools->size() > 0) {
        apr_pool_t* pool = allocated_pools->top();
        allocated_pools->pop();
        apr_pool_destroy(pool);
    }
    apr_pool_destroy(pool);
    apr_thread_mutex_destroy(poolGuard);
    delete allocated_pools;
    APRBase::decrement();
}

void APRPool::free_pool(apr_pool_t* pool) {
    CHECK_APR_SUCCESS(apr_thread_mutex_lock(poolGuard));
    allocated_pools->push(pool);
    CHECK_APR_SUCCESS(apr_thread_mutex_unlock(poolGuard));
}

apr_pool_t* APRPool::allocate_pool() {
    CHECK_APR_SUCCESS(apr_thread_mutex_lock(poolGuard));
    apr_pool_t* retval;
    if (allocated_pools->size() == 0) {
        CHECK_APR_SUCCESS(apr_pool_create(&retval, pool));
    }
    else {
        retval = allocated_pools->top();
        allocated_pools->pop();
    }
    CHECK_APR_SUCCESS(apr_thread_mutex_unlock(poolGuard));
    return retval;    
}

apr_pool_t* APRPool::get() {
    return 
        boost::details::pool::singleton_default<APRPool>::instance().allocate_pool(); 
}

void APRPool::free(apr_pool_t* pool) {    
    boost::details::pool::singleton_default<APRPool>::instance().free_pool(pool);
}

