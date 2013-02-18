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

#include <qpid/nexus/buffer.h>
#include <qpid/nexus/alloc.h>

static size_t buffer_size = 512;
static int    size_locked = 0;

ALLOC_DECLARE(nx_buffer_t);
ALLOC_DEFINE_CONFIG(nx_buffer_t, sizeof(nx_buffer_t), &buffer_size, 0);


void nx_buffer_set_size(size_t size)
{
    assert(!size_locked);
    buffer_size = size;
}


nx_buffer_t *nx_allocate_buffer(void)
{
    size_locked = 1;
    nx_buffer_t *buf = new_nx_buffer_t();

    DEQ_ITEM_INIT(buf);
    buf->size = 0;
    return buf;
}


void nx_free_buffer(nx_buffer_t *buf)
{
    free_nx_buffer_t(buf);
}


unsigned char *nx_buffer_base(nx_buffer_t *buf)
{
    return (unsigned char*) &buf[1];
}


unsigned char *nx_buffer_cursor(nx_buffer_t *buf)
{
    return ((unsigned char*) &buf[1]) + buf->size;
}


size_t nx_buffer_capacity(nx_buffer_t *buf)
{
    return buffer_size - buf->size;
}


size_t nx_buffer_size(nx_buffer_t *buf)
{
    return buf->size;
}


void nx_buffer_insert(nx_buffer_t *buf, size_t len)
{
    buf->size += len;
    assert(buf->size <= buffer_size);
}

