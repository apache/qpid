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

#define _GNU_SOURCE
#include <stdio.h>
#include <assert.h>
#include <string.h>
#include <inttypes.h>
#include "test_case.h"
#include <qpid/dispatch.h>
#include "compose_private.h"


static char *vector1 =
    "\x00\x53\x71"                             // delivery annotations
    "\xd1\x00\x00\x00\x3d\x00\x00\x00\x04"     // map32 with two item pairs
    "\xa1\x06key001"                           // str8-utf8
    "\x52\x0a"                                 // smalluint
    "\xa1\x06key002"                           // str8-utf8
    "\xd0\x00\x00\x00\x22\x00\x00\x00\x04"     // list32 with four items
    "\xa1\x05item1"                            // str8-utf8
    "\xa1\x05item2"                            // str8-utf8
    "\xa1\x05item3"                            // str8-utf8
    "\xd0\x00\x00\x00\x04\x00\x00\x00\x00"     // list32 empty
    ;

static int vector1_length = 69;

static char *test_compose_nested_composites(void *context)
{
    dx_composed_field_t *field = dx_compose(DX_PERFORMATIVE_DELIVERY_ANNOTATIONS, 0);

    dx_compose_start_map(field);

    dx_compose_insert_string(field, "key001");
    dx_compose_insert_uint(field, 10);

    dx_compose_insert_string(field, "key002");
    dx_compose_start_list(field);

    dx_compose_insert_string(field, "item1");
    dx_compose_insert_string(field, "item2");
    dx_compose_insert_string(field, "item3");

    dx_compose_start_list(field);
    dx_compose_end_list(field);
   
    dx_compose_end_list(field);
    dx_compose_end_map(field);

    dx_buffer_t *buf = DEQ_HEAD(field->buffers);

    if (dx_buffer_size(buf) != vector1_length) return "Incorrect Length of Buffer";

    char *left  = vector1;
    char *right = (char*) dx_buffer_base(buf);
    int   idx;

    for (idx = 0; idx < vector1_length; idx++) {
        if (*left != *right) return "Pattern Mismatch";
        left++;
        right++;
    }

    dx_compose_free(field);
    return 0;
}

static char *vector2 =
    "\x00\x53\x73"                             // properties
    "\xd0\x00\x00\x00\x83\x00\x00\x00\x1c"     // list32 with 28 items
    "\x40"                                     // null
    "\x42"                                     // false
    "\x41"                                     // true
    "\x43"                                     // uint0
    "\x52\x01"                                 // smalluint
    "\x52\xff"                                 // smalluint
    "\x70\x00\x00\x01\x00"                     // uint
    "\x70\x10\x00\x00\x00"                     // uint
    "\x44"                                     // ulong0
    "\x53\x01"                                 // smallulong
    "\x53\xff"                                 // smallulong
    "\x80\x00\x00\x00\x00\x00\x00\x01\x00"     // ulong
    "\x80\x00\x00\x00\x00\x20\x00\x00\x00"     // ulong
    "\x54\x00"                                 // smallint
    "\x54\x01"                                 // smallint
    "\x54\xff"                                 // smallint
    "\x71\x00\x00\x00\xff"                     // int
    "\x71\x00\x00\x01\x00"                     // int
    "\x55\x00"                                 // smalllong
    "\x55\x01"                                 // smalllong
    "\x55\xff"                                 // smalllong
    "\x81\x00\x00\x00\x00\x00\x00\x00\xff"     // long
    "\x81\x00\x00\x00\x00\x00\x00\x01\x00"     // long
    "\x83\x00\x11\x22\x33\x44\x55\x66\x77"     // timestamp
    "\x98\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x00"  // uuid
    "\xa0\x02\x00\x11"                         // vbin8
    "\xa1\x06string"                           // str8-utf8
    "\xa3\x06symbol"                           // sym8
    ;

static int vector2_length = 139;

static char *test_compose_scalars(void *context)
{
    dx_composed_field_t *field = dx_compose(DX_PERFORMATIVE_PROPERTIES, 0);

    dx_compose_start_list(field);

    dx_compose_insert_null(field);

    dx_compose_insert_bool(field, 0);
    dx_compose_insert_bool(field, 1);

    dx_compose_insert_uint(field, 0);
    dx_compose_insert_uint(field, 1);
    dx_compose_insert_uint(field, 255);
    dx_compose_insert_uint(field, 256);
    dx_compose_insert_uint(field, 0x10000000);

    dx_compose_insert_ulong(field, 0);
    dx_compose_insert_ulong(field, 1);
    dx_compose_insert_ulong(field, 255);
    dx_compose_insert_ulong(field, 256);
    dx_compose_insert_ulong(field, 0x20000000);

    dx_compose_insert_int(field, 0);
    dx_compose_insert_int(field, 1);
    dx_compose_insert_int(field, -1);
    dx_compose_insert_int(field, 255);
    dx_compose_insert_int(field, 256);

    dx_compose_insert_long(field, 0);
    dx_compose_insert_long(field, 1);
    dx_compose_insert_long(field, -1);
    dx_compose_insert_long(field, 255);
    dx_compose_insert_long(field, 256);

    dx_compose_insert_timestamp(field, 0x0011223344556677);
    dx_compose_insert_uuid(field, (uint8_t*) "\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x00");
    dx_compose_insert_binary(field, (uint8_t*) "\x00\x11", 2);
    dx_compose_insert_string(field, "string");
    dx_compose_insert_symbol(field, "symbol");

    dx_compose_end_list(field);

    dx_buffer_t *buf = DEQ_HEAD(field->buffers);

    if (dx_buffer_size(buf) != vector2_length) return "Incorrect Length of Buffer";

    char *left  = vector2;
    char *right = (char*) dx_buffer_base(buf);
    int   idx;

    for (idx = 0; idx < vector2_length; idx++) {
        if (*left != *right) return "Pattern Mismatch";
        left++;
        right++;
    }

    dx_compose_free(field);
    return 0;
}


int compose_tests()
{
    int result = 0;
    dx_log_set_mask(LOG_NONE);

    TEST_CASE(test_compose_nested_composites, 0);
    TEST_CASE(test_compose_scalars, 0);

    return result;
}

