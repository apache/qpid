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

#include <qpid/dispatch/buffer.h>
#include <qpid/dispatch/alloc.h>

static size_t buffer_size = 512;
static int    size_locked = 0;

ALLOC_DECLARE(dx_buffer_t);
ALLOC_DEFINE_CONFIG(dx_buffer_t, sizeof(dx_buffer_t), &buffer_size, 0);


void dx_buffer_set_size(size_t size)
{
    assert(!size_locked);
    buffer_size = size;
}


dx_buffer_t *dx_allocate_buffer(void)
{
    size_locked = 1;
    dx_buffer_t *buf = new_dx_buffer_t();

    DEQ_ITEM_INIT(buf);
    buf->size = 0;
    return buf;
}


void dx_free_buffer(dx_buffer_t *buf)
{
    free_dx_buffer_t(buf);
}


unsigned char *dx_buffer_base(dx_buffer_t *buf)
{
    return (unsigned char*) &buf[1];
}


unsigned char *dx_buffer_cursor(dx_buffer_t *buf)
{
    return ((unsigned char*) &buf[1]) + buf->size;
}


size_t dx_buffer_capacity(dx_buffer_t *buf)
{
    return buffer_size - buf->size;
}


size_t dx_buffer_size(dx_buffer_t *buf)
{
    return buf->size;
}


void dx_buffer_insert(dx_buffer_t *buf, size_t len)
{
    buf->size += len;
    assert(buf->size <= buffer_size);
}

