#ifndef __dispatch_buffer_h__
#define __dispatch_buffer_h__ 1
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

typedef struct dx_buffer_t dx_buffer_t;

DEQ_DECLARE(dx_buffer_t, dx_buffer_list_t);

struct dx_buffer_t {
    DEQ_LINKS(dx_buffer_t);
    unsigned int size;
};

/**
 */
void dx_buffer_set_size(size_t size);

/**
 */
dx_buffer_t *dx_allocate_buffer(void);

/**
 * @param buf A pointer to an allocated buffer
 */
void dx_free_buffer(dx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return A pointer to the first octet in the buffer
 */
unsigned char *dx_buffer_base(dx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return A pointer to the first free octet in the buffer, the insert point for new data.
 */
unsigned char *dx_buffer_cursor(dx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return The number of octets in the buffer's free space, how many octets may be inserted.
 */
size_t dx_buffer_capacity(dx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return The number of octets of data in the buffer
 */
size_t dx_buffer_size(dx_buffer_t *buf);

/**
 * Notify the buffer that octets have been inserted at the buffer's cursor.  This will advance the
 * cursor by len octets.
 *
 * @param buf A pointer to an allocated buffer
 * @param len The number of octets that have been appended to the buffer
 */
void dx_buffer_insert(dx_buffer_t *buf, size_t len);

#endif
