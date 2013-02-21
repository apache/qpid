#ifndef __dispatch_alloc_h__
#define __dispatch_alloc_h__ 1
/*
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
 */

#include <stdlib.h>
#include <stdint.h>
#include <qpid/dispatch/threading.h>

typedef struct dx_alloc_pool_t dx_alloc_pool_t;

typedef struct {
    int  transfer_batch_size;
    int  local_free_list_max;
    int  global_free_list_max;
} dx_alloc_config_t;

typedef struct {
    uint64_t total_alloc_from_heap;
    uint64_t total_free_to_heap;
    uint64_t held_by_threads;
    uint64_t batches_rebalanced_to_threads;
    uint64_t batches_rebalanced_to_global;
} dx_alloc_stats_t;

typedef struct {
    char              *type_name;
    size_t             type_size;
    size_t            *additional_size;
    size_t             total_size;
    dx_alloc_config_t *config;
    dx_alloc_stats_t  *stats;
    dx_alloc_pool_t   *global_pool;
    sys_mutex_t       *lock;
} dx_alloc_type_desc_t;


void *dx_alloc(dx_alloc_type_desc_t *desc, dx_alloc_pool_t **tpool);
void dx_dealloc(dx_alloc_type_desc_t *desc, dx_alloc_pool_t **tpool, void *p);


#define ALLOC_DECLARE(T) \
    T *new_##T();        \
    void free_##T(T *p)

#define ALLOC_DEFINE_CONFIG(T,S,A,C)                                \
    dx_alloc_type_desc_t __desc_##T = {#T, S, A, 0, C, 0, 0, 0};    \
    __thread dx_alloc_pool_t *__local_pool_##T = 0;                 \
    T *new_##T() { return (T*) dx_alloc(&__desc_##T, &__local_pool_##T); }  \
    void free_##T(T *p) { dx_dealloc(&__desc_##T, &__local_pool_##T, (void*) p); } \
    dx_alloc_stats_t *alloc_stats_##T() { return __desc_##T.stats; }

#define ALLOC_DEFINE(T) ALLOC_DEFINE_CONFIG(T, sizeof(T), 0, 0)


#endif
