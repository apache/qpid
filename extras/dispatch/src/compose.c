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
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/buffer.h>
#include <qpid/dispatch/amqp.h>
#include "message_private.h"
#include "compose_private.h"
#include <memory.h>

typedef struct dx_composite_t {
    DEQ_LINKS(struct dx_composite_t);
    int                 isMap;
    uint32_t            count;
    uint32_t            length;
    dx_field_location_t length_location;
    dx_field_location_t count_location;
} dx_composite_t;

ALLOC_DECLARE(dx_composite_t);
ALLOC_DEFINE(dx_composite_t);
DEQ_DECLARE(dx_composite_t, dx_field_stack_t);


struct dx_composed_field_t {
    dx_buffer_list_t buffers;
    dx_field_stack_t fieldStack;
};

ALLOC_DECLARE(dx_composed_field_t);
ALLOC_DEFINE(dx_composed_field_t);


static void bump_count(dx_composed_field_t *field)
{
    dx_composite_t *comp = DEQ_HEAD(field->fieldStack);
    if (comp)
        comp->count++;
}


static void dx_insert(dx_composed_field_t *field, const uint8_t *seq, size_t len)
{
    dx_buffer_t    *buf  = DEQ_TAIL(field->buffers);
    dx_composite_t *comp = DEQ_HEAD(field->fieldStack);

    while (len > 0) {
        if (buf == 0 || dx_buffer_capacity(buf) == 0) {
            buf = dx_allocate_buffer();
            if (buf == 0)
                return;
            DEQ_INSERT_TAIL(field->buffers, buf);
        }

        size_t to_copy = dx_buffer_capacity(buf);
        if (to_copy > len)
            to_copy = len;
        memcpy(dx_buffer_cursor(buf), seq, to_copy);
        dx_buffer_insert(buf, to_copy);
        len -= to_copy;
        seq += to_copy;
        if (comp)
            comp->length += to_copy;
    }
}


static void dx_insert_8(dx_composed_field_t *field, uint8_t value)
{
    dx_insert(field, &value, 1);
}


static void dx_insert_32(dx_composed_field_t *field, uint32_t value)
{
    uint8_t buf[4];
    buf[0] = (uint8_t) ((value & 0xFF000000) >> 24);
    buf[1] = (uint8_t) ((value & 0x00FF0000) >> 16);
    buf[2] = (uint8_t) ((value & 0x0000FF00) >> 8);
    buf[3] = (uint8_t)  (value & 0x000000FF);
    dx_insert(field, buf, 4);
}


static void dx_insert_64(dx_composed_field_t *field, uint64_t value)
{
    uint8_t buf[8];
    buf[0] = (uint8_t) ((value & 0xFF00000000000000L) >> 56);
    buf[1] = (uint8_t) ((value & 0x00FF000000000000L) >> 48);
    buf[2] = (uint8_t) ((value & 0x0000FF0000000000L) >> 40);
    buf[3] = (uint8_t) ((value & 0x000000FF00000000L) >> 32);
    buf[4] = (uint8_t) ((value & 0x00000000FF000000L) >> 24);
    buf[5] = (uint8_t) ((value & 0x0000000000FF0000L) >> 16);
    buf[6] = (uint8_t) ((value & 0x000000000000FF00L) >> 8);
    buf[7] = (uint8_t)  (value & 0x00000000000000FFL);
    dx_insert(field, buf, 8);
}


static void dx_overwrite(dx_buffer_t **buf, size_t *cursor, uint8_t value)
{
    while (*buf) {
        if (*cursor >= dx_buffer_size(*buf)) {
            *buf = (*buf)->next;
            *cursor = 0;
        } else {
            dx_buffer_base(*buf)[*cursor] = value;
            (*cursor)++;
            return;
        }
    }
}


static void dx_overwrite_32(dx_field_location_t *field, uint32_t value)
{
    dx_buffer_t *buf    = field->buffer;
    size_t       cursor = field->offset;

    dx_overwrite(&buf, &cursor, (uint8_t) ((value & 0xFF000000) >> 24));
    dx_overwrite(&buf, &cursor, (uint8_t) ((value & 0x00FF0000) >> 24));
    dx_overwrite(&buf, &cursor, (uint8_t) ((value & 0x0000FF00) >> 24));
    dx_overwrite(&buf, &cursor, (uint8_t)  (value & 0x000000FF));
}


static void dx_compose_start_composite(dx_composed_field_t *field, int isMap)
{
    if (isMap)
        dx_insert_8(field, DX_AMQP_MAP32);
    else
        dx_insert_8(field, DX_AMQP_LIST32);

    //
    // Push a composite descriptor on the field stack
    //
    dx_composite_t *comp = new_dx_composite_t();
    DEQ_ITEM_INIT(comp);
    comp->isMap = isMap;

    //
    // Mark the current location to later overwrite the length
    //
    comp->length_location.buffer = DEQ_TAIL(field->buffers);
    comp->length_location.offset = dx_buffer_size(comp->length_location.buffer);
    comp->length_location.length = 4;
    comp->length_location.parsed = 1;

    dx_insert(field, (const uint8_t*) "\x00\x00\x00\x00", 4);

    //
    // Mark the current location to later overwrite the count
    //
    comp->count_location.buffer = DEQ_TAIL(field->buffers);
    comp->count_location.offset = dx_buffer_size(comp->count_location.buffer);
    comp->count_location.length = 4;
    comp->count_location.parsed = 1;

    dx_insert(field, (const uint8_t*) "\x00\x00\x00\x00", 4);

    comp->length = 4; // Include the length of the count field
    comp->count = 0;

    DEQ_INSERT_HEAD(field->fieldStack, comp);
}


static void dx_compose_end_composite(dx_composed_field_t *field)
{
    dx_composite_t *comp = DEQ_HEAD(field->fieldStack);
    assert(comp);

    dx_overwrite_32(&comp->length_location, comp->length);
    dx_overwrite_32(&comp->count_location,  comp->count);

    DEQ_REMOVE_HEAD(field->fieldStack);

    //
    // If there is an enclosing composite, update its length and count
    //
    dx_composite_t *enclosing = DEQ_HEAD(field->fieldStack);
    if (enclosing) {
        enclosing->length += 4 + comp->length;
        enclosing->count++;
    }

    free_dx_composite_t(comp);
}


dx_composed_field_t *dx_compose(uint64_t performative, dx_composed_field_t *extend)
{
    dx_composed_field_t *field = extend;

    if (field) {
        assert(DEQ_SIZE(field->fieldStack) == 0);
    } else {
        field = new_dx_composed_field_t();
        if (!field)
            return 0;

        DEQ_INIT(field->buffers);
        DEQ_INIT(field->fieldStack);
    }

    dx_insert_8(field, 0x00);
    dx_compose_insert_ulong(field, performative);

    return field;
}


void dx_compose_free(dx_composed_field_t *field)
{
    dx_buffer_t *buf = DEQ_HEAD(field->buffers);
    while (buf) {
        DEQ_REMOVE_HEAD(field->buffers);
        dx_free_buffer(buf);
    }

    dx_composite_t *comp = DEQ_HEAD(field->fieldStack);
    while (comp) {
        DEQ_REMOVE_HEAD(field->fieldStack);
        free_dx_composite_t(comp);
    }

    free_dx_composed_field_t(field);
}


void dx_compose_start_list(dx_composed_field_t *field)
{
    dx_compose_start_composite(field, 0);
}


void dx_compose_end_list(dx_composed_field_t *field)
{
    dx_compose_end_composite(field);
}


void dx_compose_start_map(dx_composed_field_t *field)
{
    dx_compose_start_composite(field, 1);
}


void dx_compose_end_map(dx_composed_field_t *field)
{
    dx_compose_end_composite(field);
}


void dx_compose_insert_null(dx_composed_field_t *field)
{
    dx_insert_8(field, DX_AMQP_NULL);
    bump_count(field);
}


void dx_compose_insert_bool(dx_composed_field_t *field, int value)
{
    dx_insert_8(field, value ? DX_AMQP_TRUE : DX_AMQP_FALSE);
    bump_count(field);
}


void dx_compose_insert_uint(dx_composed_field_t *field, uint32_t value)
{
    if (value == 0) {
        dx_insert_8(field, DX_AMQP_UINT0);
    } else if (value < 256) {
        dx_insert_8(field, DX_AMQP_SMALLUINT);
        dx_insert_8(field, (uint8_t) value);
    } else {
        dx_insert_8(field, DX_AMQP_UINT);
        dx_insert_32(field, value);
    }
    bump_count(field);
}


void dx_compose_insert_ulong(dx_composed_field_t *field, uint64_t value)
{
    if (value == 0) {
        dx_insert_8(field, DX_AMQP_ULONG0);
    } else if (value < 256) {
        dx_insert_8(field, DX_AMQP_SMALLULONG);
        dx_insert_8(field, (uint8_t) value);
    } else {
        dx_insert_8(field, DX_AMQP_ULONG);
        dx_insert_64(field, value);
    }
    bump_count(field);
}


void dx_compose_insert_int(dx_composed_field_t *field, int32_t value)
{
    if (value >= -128 && value <= 127) {
        dx_insert_8(field, DX_AMQP_SMALLINT);
        dx_insert_8(field, (uint8_t) value);
    } else {
        dx_insert_8(field, DX_AMQP_INT);
        dx_insert_32(field, (uint32_t) value);
    }
    bump_count(field);
}


void dx_compose_insert_long(dx_composed_field_t *field, int64_t value)
{
    if (value >= -128 && value <= 127) {
        dx_insert_8(field, DX_AMQP_SMALLLONG);
        dx_insert_8(field, (uint8_t) value);
    } else {
        dx_insert_8(field, DX_AMQP_LONG);
        dx_insert_64(field, (uint64_t) value);
    }
    bump_count(field);
}


void dx_compose_insert_timestamp(dx_composed_field_t *field, uint64_t value)
{
    dx_insert_8(field, DX_AMQP_TIMESTAMP);
    dx_insert_64(field, value);
    bump_count(field);
}


void dx_compose_insert_uuid(dx_composed_field_t *field, const uint8_t *value)
{
    dx_insert_8(field, DX_AMQP_UUID);
    dx_insert(field, value, 16);
    bump_count(field);
}


void dx_compose_insert_binary(dx_composed_field_t *field, const uint8_t *value, uint32_t len)
{
    if (len < 256) {
        dx_insert_8(field, DX_AMQP_VBIN8);
        dx_insert_8(field, (uint8_t) len);
    } else {
        dx_insert_8(field, DX_AMQP_VBIN32);
        dx_insert_32(field, len);
    }
    dx_insert(field, value, len);
    bump_count(field);
}


void dx_compose_insert_binary_buffers(dx_composed_field_t *field, dx_buffer_list_t *buffers)
{
    dx_buffer_t *buf = DEQ_HEAD(*buffers);
    uint32_t     len = 0;

    //
    // Calculate the size of the binary field to be appended.
    //
    while (buf) {
        len += dx_buffer_size(buf);
        buf = DEQ_NEXT(buf);
    }

    //
    // Supply the appropriate binary tag for the length.
    //
    if (len < 256) {
        dx_insert_8(field, DX_AMQP_VBIN8);
        dx_insert_8(field, (uint8_t) len);
    } else {
        dx_insert_8(field, DX_AMQP_VBIN32);
        dx_insert_32(field, len);
    }

    //
    // Move the supplied buffers to the tail of the field's buffer list.
    //
    buf = DEQ_HEAD(*buffers);
    while (buf) {
        DEQ_REMOVE_HEAD(*buffers);
        DEQ_INSERT_TAIL(field->buffers, buf);
        buf = DEQ_HEAD(*buffers);
    }
}


void dx_compose_insert_string(dx_composed_field_t *field, const char *value)
{
    uint32_t len = strlen(value);

    if (len < 256) {
        dx_insert_8(field, DX_AMQP_STR8_UTF8);
        dx_insert_8(field, (uint8_t) len);
    } else {
        dx_insert_8(field, DX_AMQP_STR32_UTF8);
        dx_insert_32(field, len);
    }
    dx_insert(field, (const uint8_t*) value, len);
    bump_count(field);
}


void dx_compose_insert_string_iterator(dx_composed_field_t *field, dx_field_iterator_t *iter)
{
    uint32_t len = 0;

    while (!dx_field_iterator_end(iter)) {
        dx_field_iterator_octet(iter);
        len++;
    }

    dx_field_iterator_reset(iter);

    if (len < 256) {
        dx_insert_8(field, DX_AMQP_STR8_UTF8);
        dx_insert_8(field, (uint8_t) len);
    } else {
        dx_insert_8(field, DX_AMQP_STR32_UTF8);
        dx_insert_32(field, len);
    }

    while (!dx_field_iterator_end(iter)) {
        uint8_t octet = dx_field_iterator_octet(iter);
        dx_insert_8(field, octet);
    }

    bump_count(field);
}


void dx_compose_insert_symbol(dx_composed_field_t *field, const char *value)
{
    uint32_t len = strlen(value);

    if (len < 256) {
        dx_insert_8(field, DX_AMQP_SYM8);
        dx_insert_8(field, (uint8_t) len);
    } else {
        dx_insert_8(field, DX_AMQP_SYM32);
        dx_insert_32(field, len);
    }
    dx_insert(field, (const uint8_t*) value, len);
    bump_count(field);
}


dx_buffer_list_t *dx_compose_buffers(dx_composed_field_t *field)
{
    return &field->buffers;
}

