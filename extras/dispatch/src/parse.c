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

#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/parse.h>
#include <qpid/dispatch/amqp.h>

DEQ_DECLARE(dx_parsed_field_t, dx_parsed_field_list_t);

struct dx_parsed_field_t {
    DEQ_LINKS(dx_parsed_field_t);
    dx_parsed_field_t      *parent;
    dx_parsed_field_list_t  children;
    uint8_t                 tag;
    dx_field_iterator_t    *raw_iter;
    const char             *parse_error;
};

ALLOC_DECLARE(dx_parsed_field_t);
ALLOC_DEFINE(dx_parsed_field_t);


static char *get_type_info(dx_field_iterator_t *iter, uint8_t *tag, uint32_t *length, uint32_t *count, uint32_t *clen)
{
    if (dx_field_iterator_end(iter))
        return "Insufficient Data to Determine Tag";
    *tag      = dx_field_iterator_octet(iter);
    *count    = 0;
    *length   = 0;
    *clen     = 0;

    switch (*tag & 0xF0) {
    case 0x40: *length = 0;  break;
    case 0x50: *length = 1;  break;
    case 0x60: *length = 2;  break;
    case 0x70: *length = 4;  break;
    case 0x80: *length = 8;  break;
    case 0x90: *length = 16; break;
    case 0xB0:
    case 0xD0:
    case 0xF0:
        *length += ((unsigned int) dx_field_iterator_octet(iter)) << 24;
        *length += ((unsigned int) dx_field_iterator_octet(iter)) << 16;
        *length += ((unsigned int) dx_field_iterator_octet(iter)) << 8;
        // fall through to the next case
        
    case 0xA0:
    case 0xC0:
    case 0xE0:
        if (dx_field_iterator_end(iter))
            return "Insufficient Data to Determine Length";
        *length += (unsigned int) dx_field_iterator_octet(iter);
        break;

    default:
        return "Invalid Tag - No Length Information";
    }

    switch (*tag & 0xF0) {
    case 0xD0:
    case 0xF0:
        *count += ((unsigned int) dx_field_iterator_octet(iter)) << 24;
        *count += ((unsigned int) dx_field_iterator_octet(iter)) << 16;
        *count += ((unsigned int) dx_field_iterator_octet(iter)) << 8;
        *clen = 3;
        // fall through to the next case
        
    case 0xC0:
    case 0xE0:
        if (dx_field_iterator_end(iter))
            return "Insufficient Data to Determine Count";
        *count += (unsigned int) dx_field_iterator_octet(iter);
        *clen += 1;
        break;
    }

    if ((*tag == DX_AMQP_MAP8 || *tag == DX_AMQP_MAP32) && (*count & 1))
        return "Odd Number of Elements in a Map";

    if (*clen > *length)
        return "Insufficient Length to Determine Count";

    return 0;
}


static dx_parsed_field_t *dx_parse_internal(dx_field_iterator_t *iter, dx_parsed_field_t *p)
{
    dx_parsed_field_t *field = new_dx_parsed_field_t();
    if (!field)
        return 0;

    DEQ_ITEM_INIT(field);
    DEQ_INIT(field->children);
    field->parent   = p;
    field->raw_iter = 0;

    uint32_t length;
    uint32_t count;
    uint32_t length_of_count;

    field->parse_error = get_type_info(iter, &field->tag, &length, &count, &length_of_count);

    if (!field->parse_error) {
        field->raw_iter = dx_field_iterator_sub(iter, length);
        dx_field_iterator_advance(iter, length - length_of_count);
        for (uint32_t idx = 0; idx < count; idx++) {
            dx_parsed_field_t *child = dx_parse_internal(field->raw_iter, field);
            DEQ_INSERT_TAIL(field->children, child);
            if (!dx_parse_ok(child)) {
                field->parse_error = child->parse_error;
                break;
            }
        }
    }

    return field;
}


dx_parsed_field_t *dx_parse(dx_field_iterator_t *iter)
{
    return dx_parse_internal(iter, 0);
}


void dx_parse_free(dx_parsed_field_t *field)
{
    if (!field)
        return;

    assert(field->parent == 0);
    if (field->raw_iter)
        dx_field_iterator_free(field->raw_iter);

    dx_parsed_field_t *sub_field = DEQ_HEAD(field->children);
    while (sub_field) {
        dx_parsed_field_t *next = DEQ_NEXT(sub_field);
        DEQ_REMOVE_HEAD(field->children);
        sub_field->parent = 0;
        dx_parse_free(sub_field);
        sub_field = next;
    }

    free_dx_parsed_field_t(field);
}


int dx_parse_ok(dx_parsed_field_t *field)
{
    return field->parse_error == 0;
}


const char *dx_parse_error(dx_parsed_field_t *field)
{
    return field->parse_error;
}


uint8_t dx_parse_tag(dx_parsed_field_t *field)
{
    return field->tag;
}


dx_field_iterator_t *dx_parse_raw(dx_parsed_field_t *field)
{
    return field->raw_iter;
}


uint32_t dx_parse_as_uint(dx_parsed_field_t *field)
{
    uint32_t result = 0;

    dx_field_iterator_reset(field->raw_iter);

    switch (field->tag) {
    case DX_AMQP_UINT:
        result |= ((uint32_t) dx_field_iterator_octet(field->raw_iter)) << 24;
        result |= ((uint32_t) dx_field_iterator_octet(field->raw_iter)) << 16;

    case DX_AMQP_USHORT:
        result |= ((uint32_t) dx_field_iterator_octet(field->raw_iter)) << 8;
        // Fall Through...

    case DX_AMQP_UBYTE:
    case DX_AMQP_SMALLUINT:
    case DX_AMQP_BOOLEAN:
        result |= (uint32_t) dx_field_iterator_octet(field->raw_iter);
        break;

    case DX_AMQP_TRUE:
        result = 1;
        break;
    }

    return result;
}


uint64_t dx_parse_as_ulong(dx_parsed_field_t *field)
{
    uint64_t result = 0;

    dx_field_iterator_reset(field->raw_iter);

    switch (field->tag) {
    case DX_AMQP_ULONG:
    case DX_AMQP_TIMESTAMP:
        result |= ((uint64_t) dx_field_iterator_octet(field->raw_iter)) << 56;
        result |= ((uint64_t) dx_field_iterator_octet(field->raw_iter)) << 48;
        result |= ((uint64_t) dx_field_iterator_octet(field->raw_iter)) << 40;
        result |= ((uint64_t) dx_field_iterator_octet(field->raw_iter)) << 32;
        result |= ((uint64_t) dx_field_iterator_octet(field->raw_iter)) << 24;
        result |= ((uint64_t) dx_field_iterator_octet(field->raw_iter)) << 16;
        result |= ((uint64_t) dx_field_iterator_octet(field->raw_iter)) << 8;
        // Fall Through...

    case DX_AMQP_SMALLULONG:
        result |= (uint64_t) dx_field_iterator_octet(field->raw_iter);
        // Fall Through...

    case DX_AMQP_ULONG0:
        break;
    }

    return result;
}


int32_t dx_parse_as_int(dx_parsed_field_t *field)
{
    int32_t result = 0;

    dx_field_iterator_reset(field->raw_iter);

    switch (field->tag) {
    case DX_AMQP_INT:
        result |= ((int32_t) dx_field_iterator_octet(field->raw_iter)) << 24;
        result |= ((int32_t) dx_field_iterator_octet(field->raw_iter)) << 16;

    case DX_AMQP_SHORT:
        result |= ((int32_t) dx_field_iterator_octet(field->raw_iter)) << 8;
        // Fall Through...

    case DX_AMQP_BYTE:
    case DX_AMQP_SMALLINT:
    case DX_AMQP_BOOLEAN:
        result |= (int32_t) dx_field_iterator_octet(field->raw_iter);
        break;

    case DX_AMQP_TRUE:
        result = 1;
        break;
    }

    return result;
}


int64_t dx_parse_as_long(dx_parsed_field_t *field)
{
    int64_t result = 0;

    dx_field_iterator_reset(field->raw_iter);

    switch (field->tag) {
    case DX_AMQP_LONG:
        result |= ((int64_t) dx_field_iterator_octet(field->raw_iter)) << 56;
        result |= ((int64_t) dx_field_iterator_octet(field->raw_iter)) << 48;
        result |= ((int64_t) dx_field_iterator_octet(field->raw_iter)) << 40;
        result |= ((int64_t) dx_field_iterator_octet(field->raw_iter)) << 32;
        result |= ((int64_t) dx_field_iterator_octet(field->raw_iter)) << 24;
        result |= ((int64_t) dx_field_iterator_octet(field->raw_iter)) << 16;
        result |= ((int64_t) dx_field_iterator_octet(field->raw_iter)) << 8;
        // Fall Through...

    case DX_AMQP_SMALLLONG:
        result |= (uint64_t) dx_field_iterator_octet(field->raw_iter);
        break;
    }

    return result;
}


uint32_t dx_parse_sub_count(dx_parsed_field_t *field)
{
    uint32_t count = DEQ_SIZE(field->children);

    if (field->tag == DX_AMQP_MAP8 || field->tag == DX_AMQP_MAP32)
        count = count >> 1;

    return count;
}


dx_parsed_field_t *dx_parse_sub_key(dx_parsed_field_t *field, uint32_t idx)
{
    if (field->tag != DX_AMQP_MAP8 && field->tag != DX_AMQP_MAP32)
        return 0;

    idx = idx << 1;
    dx_parsed_field_t *key = DEQ_HEAD(field->children);
    while (idx && key) {
        idx--;
        key = DEQ_NEXT(key);
    }

    return key;
}


dx_parsed_field_t *dx_parse_sub_value(dx_parsed_field_t *field, uint32_t idx)
{
    if (field->tag == DX_AMQP_MAP8 || field->tag == DX_AMQP_MAP32)
        idx = (idx << 1) + 1;

    dx_parsed_field_t *key = DEQ_HEAD(field->children);
    while (idx && key) {
        idx--;
        key = DEQ_NEXT(key);
    }

    return key;
}


int dx_parse_is_map(dx_parsed_field_t *field)
{
    return field->tag == DX_AMQP_MAP8 || field->tag == DX_AMQP_MAP32;
}


int dx_parse_is_list(dx_parsed_field_t *field)
{
    return field->tag == DX_AMQP_LIST8 || field->tag == DX_AMQP_LIST32;
}


int dx_parse_is_scalar(dx_parsed_field_t *field)
{
    return DEQ_SIZE(field->children) == 0;
}


dx_parsed_field_t *dx_parse_value_by_key(dx_parsed_field_t *field, const char *key)
{
    uint32_t count = dx_parse_sub_count(field);

    for (uint32_t idx = 0; idx < count; idx++) {
        dx_parsed_field_t *sub  = dx_parse_sub_key(field, idx);
        if (!sub)
            return 0;

        dx_field_iterator_t *iter = dx_parse_raw(sub);
        if (!iter)
            return 0;

        if (dx_field_iterator_equal(iter, (const unsigned char*) key)) {
            return dx_parse_sub_value(field, idx);
        }
    }

    return 0;
}

