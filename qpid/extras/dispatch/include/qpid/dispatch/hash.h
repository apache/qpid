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
#include <qpid/dispatch/error.h>

typedef struct dx_hash_t        dx_hash_t;
typedef struct dx_hash_handle_t dx_hash_handle_t;

dx_hash_t *dx_hash(int bucket_exponent, int batch_size, int value_is_const);
void dx_hash_free(dx_hash_t *h);

size_t dx_hash_size(dx_hash_t *h);
dx_error_t dx_hash_insert(dx_hash_t *h, dx_field_iterator_t *key, void *val, dx_hash_handle_t **handle);
dx_error_t dx_hash_insert_const(dx_hash_t *h, dx_field_iterator_t *key, const void *val, dx_hash_handle_t **handle);
dx_error_t dx_hash_retrieve(dx_hash_t *h, dx_field_iterator_t *key, void **val);
dx_error_t dx_hash_retrieve_const(dx_hash_t *h, dx_field_iterator_t *key, const void **val);
dx_error_t dx_hash_remove(dx_hash_t *h, dx_field_iterator_t *key);

void dx_hash_handle_free(dx_hash_handle_t *handle);
const unsigned char *dx_hash_key_by_handle(const dx_hash_handle_t *handle);
dx_error_t dx_hash_remove_by_handle(dx_hash_t *h, dx_hash_handle_t *handle);


#endif
