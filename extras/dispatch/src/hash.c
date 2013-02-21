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

#include <qpid/dispatch/hash.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/alloc.h>
#include <stdio.h>
#include <string.h>

typedef struct hash_item_t {
    DEQ_LINKS(struct hash_item_t);
    unsigned char *key;
    union {
        void       *val;
        const void *val_const;
    } v;
} hash_item_t;

ALLOC_DECLARE(hash_item_t);
ALLOC_DEFINE(hash_item_t);
DEQ_DECLARE(hash_item_t, items_t);


typedef struct bucket_t {
    items_t items;    
} bucket_t;


struct hash_t {
    bucket_t     *buckets;
    unsigned int  bucket_count;
    unsigned int  bucket_mask;
    int           batch_size;
    size_t        size;
    int           is_const;
};


// djb2 hash algorithm
static unsigned long hash_function(dx_field_iterator_t *iter)
{
    unsigned long hash = 5381;
    int c;

    while (!dx_field_iterator_end(iter)) {
        c = (int) dx_field_iterator_octet(iter);
        hash = ((hash << 5) + hash) + c; /* hash * 33 + c */
    }

    return hash;
}


hash_t *hash(int bucket_exponent, int batch_size, int value_is_const)
{
    int i;
    hash_t *h = NEW(hash_t);

    if (!h)
        return 0;

    h->bucket_count = 1 << bucket_exponent;
    h->bucket_mask  = h->bucket_count - 1;
    h->batch_size   = batch_size;
    h->size         = 0;
    h->is_const     = value_is_const;
    h->buckets = NEW_ARRAY(bucket_t, h->bucket_count);
    for (i = 0; i < h->bucket_count; i++) {
        DEQ_INIT(h->buckets[i].items);
    }

    return h;
}


void hash_free(hash_t *h)
{
    // TODO - Implement this
}


size_t hash_size(hash_t *h)
{
    return h ? h->size : 0;
}


static hash_item_t *hash_internal_insert(hash_t *h, dx_field_iterator_t *key, int *error)
{
    unsigned long  idx  = hash_function(key) & h->bucket_mask;
    hash_item_t   *item = DEQ_HEAD(h->buckets[idx].items);

    *error = 0;

    while (item) {
        if (dx_field_iterator_equal(key, item->key))
            break;
        item = item->next;
    }

    if (item) {
        *error = -1;
        return 0;
    }

    item = new_hash_item_t();
    if (!item) {
        *error = -2;
        return 0;
    }

    DEQ_ITEM_INIT(item);
    item->key = dx_field_iterator_copy(key);

    DEQ_INSERT_TAIL(h->buckets[idx].items, item);
    h->size++;
    return item;
}


int hash_insert(hash_t *h, dx_field_iterator_t *key, void *val)
{
    int          error = 0;
    hash_item_t *item  = hash_internal_insert(h, key, &error);

    if (item)
        item->v.val = val;
    return error;
}


int hash_insert_const(hash_t *h, dx_field_iterator_t *key, const void *val)
{
    if (!h->is_const)
        return -3;

    int          error = 0;
    hash_item_t *item  = hash_internal_insert(h, key, &error);

    if (item)
        item->v.val_const = val;
    return error;
}


static hash_item_t *hash_internal_retrieve(hash_t *h, dx_field_iterator_t *key)
{
    unsigned long  idx  = hash_function(key) & h->bucket_mask;
    hash_item_t   *item = DEQ_HEAD(h->buckets[idx].items);

    while (item) {
        if (dx_field_iterator_equal(key, item->key))
            break;
        item = item->next;
    }

    return item;
}


int hash_retrieve(hash_t *h, dx_field_iterator_t *key, void **val)
{
    hash_item_t *item = hash_internal_retrieve(h, key);
    if (item) {
        *val = item->v.val;
        return 0;
    }
    return -1;
}


int hash_retrieve_const(hash_t *h, dx_field_iterator_t *key, const void **val)
{
    if (!h->is_const)
        return -3;

    hash_item_t *item = hash_internal_retrieve(h, key);
    if (item) {
        *val = item->v.val_const;
        return 0;
    }
    return -1;
}


int hash_remove(hash_t *h, dx_field_iterator_t *key)
{
    unsigned long  idx  = hash_function(key) & h->bucket_mask;
    hash_item_t   *item = DEQ_HEAD(h->buckets[idx].items);

    while (item) {
        if (dx_field_iterator_equal(key, item->key))
            break;
        item = item->next;
    }

    if (item) {
        free(item->key);
        DEQ_REMOVE(h->buckets[idx].items, item);
        free_hash_item_t(item);
        h->size--;
        return 0;
    }

    return -1;
}

