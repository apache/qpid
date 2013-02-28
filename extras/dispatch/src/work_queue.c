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

#include <qpid/dispatch/ctools.h>
#include "work_queue.h"
#include <string.h>
#include <stdio.h>

#define BATCH_SIZE 100
typedef struct work_item_t work_item_t;

struct work_item_t {
    DEQ_LINKS(work_item_t);
    pn_connector_t *conn;
};

DEQ_DECLARE(work_item_t, work_list_t);

struct work_queue_t {
    work_list_t items;
    work_list_t free_list;
};

static void allocate_batch(work_queue_t *w)
{
    int i;
    work_item_t *batch = NEW_ARRAY(work_item_t, BATCH_SIZE);
    if (!batch)
        return;

    memset(batch, 0, sizeof(work_item_t) * BATCH_SIZE);

    for (i = 0; i < BATCH_SIZE; i++)
        DEQ_INSERT_TAIL(w->free_list, &batch[i]);
}


work_queue_t *work_queue(void)
{
    work_queue_t *w = NEW(work_queue_t);
    if (!w)
        return 0;

    DEQ_INIT(w->items);
    DEQ_INIT(w->free_list);

    allocate_batch(w);

    return w;
}


void work_queue_free(work_queue_t *w)
{
    if (!w)
        return;

    // KEEP TRACK OF BATCHES AND FREE
    free(w);
}


void work_queue_put(work_queue_t *w, pn_connector_t *conn)
{
    work_item_t *item;

    if (!w)
        return;
    if (DEQ_SIZE(w->free_list) == 0)
        allocate_batch(w);
    if (DEQ_SIZE(w->free_list) == 0)
        return;

    item = DEQ_HEAD(w->free_list);
    DEQ_REMOVE_HEAD(w->free_list);

    item->conn = conn;

    DEQ_INSERT_TAIL(w->items, item);
}


pn_connector_t *work_queue_get(work_queue_t *w)
{
    work_item_t    *item;
    pn_connector_t *conn;

    if (!w)
        return 0;
    item = DEQ_HEAD(w->items);
    if (!item)
        return 0;

    DEQ_REMOVE_HEAD(w->items);
    conn = item->conn;
    item->conn = 0;

    DEQ_INSERT_TAIL(w->free_list, item);

    return conn;
}


int work_queue_empty(work_queue_t *w)
{
    return !w || DEQ_SIZE(w->items) == 0;
}


int work_queue_depth(work_queue_t *w)
{
    if (!w)
        return 0;
    return DEQ_SIZE(w->items);
}

