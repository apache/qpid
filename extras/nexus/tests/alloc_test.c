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

#include "test_case.h"
#include <stdio.h>
#include <string.h>
#include "alloc_private.h"

typedef struct {
    int A;
    int B;
} object_t;

nx_alloc_config_t config = {3, 7, 10};

ALLOC_DECLARE(object_t);
ALLOC_DEFINE_CONFIG(object_t, &config);


static char* check_stats(nx_alloc_stats_t *stats, uint64_t ah, uint64_t fh, uint64_t ht, uint64_t rt, uint64_t rg)
{
    if (stats->total_alloc_from_heap         != ah) return "Incorrect alloc-from-heap";
    if (stats->total_free_to_heap            != fh) return "Incorrect free-to-heap";
    if (stats->held_by_threads               != ht) return "Incorrect held-by-threads";
    if (stats->batches_rebalanced_to_threads != rt) return "Incorrect rebalance-to-threads";
    if (stats->batches_rebalanced_to_global  != rg) return "Incorrect rebalance-to-global";
    return 0;
}


static char* test_alloc_basic(void *context)
{
    object_t         *obj[50];
    int               idx;
    nx_alloc_stats_t *stats;
    char             *error;

    for (idx = 0; idx < 20; idx++)
        obj[idx] = new_object_t();

    stats = alloc_stats_object_t();
    error = check_stats(stats, 21, 0, 21, 0, 0);
    if (error) return error;

    for (idx = 0; idx < 20; idx++)
        free_object_t(obj[idx]);

    error = check_stats(stats, 21, 5, 6, 0, 5);
    if (error) return error;

    for (idx = 0; idx < 20; idx++)
        obj[idx] = new_object_t();

    error = check_stats(stats, 27, 5, 21, 3, 5);
    if (error) return error;

    return 0;
}


int alloc_tests(void)
{
    int result = 0;
    nx_alloc_initialize();

    TEST_CASE(test_alloc_basic, 0);

    return result;
}

