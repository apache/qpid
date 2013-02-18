#ifndef __nexus_buffer_h__
#define __nexus_buffer_h__ 1
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

#include <qpid/nexus/ctools.h>

typedef struct nx_buffer_t nx_buffer_t;

DEQ_DECLARE(nx_buffer_t, nx_buffer_list_t);

struct nx_buffer_t {
    DEQ_LINKS(nx_buffer_t);
    unsigned int size;
};

/**
 */
void nx_buffer_set_size(size_t size);

/**
 */
nx_buffer_t *nx_allocate_buffer(void);

/**
 * @param buf A pointer to an allocated buffer
 */
void nx_free_buffer(nx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return A pointer to the first octet in the buffer
 */
unsigned char *nx_buffer_base(nx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return A pointer to the first free octet in the buffer, the insert point for new data.
 */
unsigned char *nx_buffer_cursor(nx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return The number of octets in the buffer's free space, how many octets may be inserted.
 */
size_t nx_buffer_capacity(nx_buffer_t *buf);

/**
 * @param buf A pointer to an allocated buffer
 * @return The number of octets of data in the buffer
 */
size_t nx_buffer_size(nx_buffer_t *buf);

/**
 * Notify the buffer that octets have been inserted at the buffer's cursor.  This will advance the
 * cursor by len octets.
 *
 * @param buf A pointer to an allocated buffer
 * @param len The number of octets that have been appended to the buffer
 */
void nx_buffer_insert(nx_buffer_t *buf, size_t len);

#endif
