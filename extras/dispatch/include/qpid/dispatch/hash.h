#ifndef __dispatch_hash_h__
#define __dispatch_hash_h__ 1
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
#include <qpid/dispatch/iterator.h>

typedef struct hash_t hash_t;

hash_t *hash(int bucket_exponent, int batch_size, int value_is_const);
void hash_free(hash_t *h);

size_t hash_size(hash_t *h);
int    hash_insert(hash_t *h, dx_field_iterator_t *key, void *val);
int    hash_insert_const(hash_t *h, dx_field_iterator_t *key, const void *val);
int    hash_retrieve(hash_t *h, dx_field_iterator_t *key, void **val);
int    hash_retrieve_const(hash_t *h, dx_field_iterator_t *key, const void **val);
int    hash_remove(hash_t *h, dx_field_iterator_t *key);

#endif
