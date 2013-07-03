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
#include "test_case.h"
#include <qpid/dispatch.h>

struct fs_vector_t {
    const char *data;
    int         length;
    uint8_t     expected_tag;
    int         check_uint;
    int         check_ulong;
    int         check_int;
    int         check_long;
    uint64_t    expected_ulong;
    int64_t     expected_long;
} fs_vectors[] = {
{"\x40",                 1, DX_AMQP_NULL,       0, 0, 0, 0, 0, 0},           // 0
{"\x41",                 1, DX_AMQP_TRUE,       1, 0, 0, 0, 1, 0},           // 1
{"\x42",                 1, DX_AMQP_FALSE,      1, 0, 0, 0, 0, 0},           // 2
{"\x56\x00",             2, DX_AMQP_BOOLEAN,    1, 0, 0, 0, 0, 0},           // 3
{"\x56\x01",             2, DX_AMQP_BOOLEAN,    1, 0, 0, 0, 1, 0},           // 4
{"\x50\x45",             2, DX_AMQP_UBYTE,      1, 0, 0, 0, 0x45, 0},        // 5
{"\x60\x02\x04",         3, DX_AMQP_USHORT,     1, 0, 0, 0, 0x0204, 0},      // 6
{"\x70\x01\x02\x03\x04", 5, DX_AMQP_UINT,       1, 0, 0, 0, 0x01020304, 0},  // 7
{"\x52\x06",             2, DX_AMQP_SMALLUINT,  1, 0, 0, 0, 6, 0},           // 8
{"\x43",                 1, DX_AMQP_UINT0,      1, 0, 0, 0, 0, 0},           // 9
{"\x80\x01\x02\x03\x04\x05\x06\x07\x08",
                         9, DX_AMQP_ULONG,      0, 1, 0, 0, 0x0102030405060708, 0},  // 10
{"\x53\x08",             2, DX_AMQP_SMALLULONG, 0, 1, 0, 0, 0x08, 0},                // 11
{"\x44",                 1, DX_AMQP_ULONG0,     0, 1, 0, 0, 0, 0},                   // 12
{"\x71\x01\x02\x03\x04", 5, DX_AMQP_INT,        0, 0, 1, 0, 0, 0x01020304},          // 13
{"\x54\x02",             2, DX_AMQP_SMALLINT,   0, 0, 1, 0, 0, 2},                   // 14
{"\x81\x01\x02\x03\x04\x05\x06\x07\x08",
                         9, DX_AMQP_LONG,       0, 0, 0, 1, 0, 0x0102030405060708},  // 15
{"\x55\x08",             2, DX_AMQP_SMALLLONG,  0, 0, 0, 1, 0, 0x08},                // 16
{0, 0, 0, 0, 0}
};


static char *test_parser_fixed_scalars(void *context)
{
    int idx = 0;
    static char error[1024];

    while (fs_vectors[idx].data) {
        dx_field_iterator_t *field  = dx_field_iterator_binary(fs_vectors[idx].data,
                                                               fs_vectors[idx].length,
                                                               ITER_VIEW_ALL);
        dx_parsed_field_t *parsed = dx_parse(field);
        if (!dx_parse_ok(parsed)) return "Unexpected Parse Error";
        if (dx_parse_tag(parsed) != fs_vectors[idx].expected_tag) {
            sprintf(error, "(%d) Tag: Expected %02x, Got %02x", idx,
                    fs_vectors[idx].expected_tag, dx_parse_tag(parsed));
            return error;
        }
        if (fs_vectors[idx].check_uint &&
            dx_parse_as_uint(parsed) != fs_vectors[idx].expected_ulong) {
            sprintf(error, "(%d) UINT: Expected %08lx, Got %08x", idx,
                    fs_vectors[idx].expected_ulong, dx_parse_as_uint(parsed));
            return error;
        }
        if (fs_vectors[idx].check_ulong &&
            dx_parse_as_ulong(parsed) != fs_vectors[idx].expected_ulong) {
            sprintf(error, "(%d) ULONG: Expected %08lx, Got %08lx", idx,
                    fs_vectors[idx].expected_ulong, dx_parse_as_ulong(parsed));
            return error;
        }
        if (fs_vectors[idx].check_int &&
            dx_parse_as_int(parsed) != fs_vectors[idx].expected_long) {
            sprintf(error, "(%d) INT: Expected %08lx, Got %08x", idx,
                    fs_vectors[idx].expected_long, dx_parse_as_int(parsed));
            return error;
        }
        if (fs_vectors[idx].check_long &&
            dx_parse_as_long(parsed) != fs_vectors[idx].expected_long) {
            sprintf(error, "(%d) LONG: Expected %08lx, Got %08lx", idx,
                    fs_vectors[idx].expected_long, dx_parse_as_long(parsed));
            return error;
        }
        idx++;

        dx_field_iterator_free(field);
        dx_parse_free(parsed);
    }

    return 0;
}


struct err_vector_t {
    const char *data;
    int         length;
    const char *expected_error;
} err_vectors[] = {
{"",                 0, "Insufficient Data to Determine Tag"},  // 0
{"\x21",             1, "Invalid Tag - No Length Information"}, // 1
//{"\x56",             1, "w"},           // 2
{"\xa0",             1, "Insufficient Data to Determine Length"},        // 3
{"\xb0",             1, "Insufficient Data to Determine Length"},        // 4
{"\xb0\x00",         2, "Insufficient Data to Determine Length"},        // 5
{"\xb0\x00\x00",     3, "Insufficient Data to Determine Length"},        // 6
{"\xb0\x00\x00\x00", 4, "Insufficient Data to Determine Length"},        // 7
{"\xc0\x04",         2, "Insufficient Data to Determine Count"},         // 8
{"\xd0\x00\x00\x00\x00\x00\x00\x00\x01",  9, "Insufficient Data to Determine Tag"},         // 9
{0, 0, 0}
};

static char *test_parser_errors(void *context)
{
    int idx = 0;
    static char error[1024];

    while (err_vectors[idx].data) {
        dx_field_iterator_t *field  = dx_field_iterator_binary(err_vectors[idx].data,
                                                               err_vectors[idx].length,
                                                               ITER_VIEW_ALL);
        dx_parsed_field_t *parsed = dx_parse(field);
        if (dx_parse_ok(parsed)) {
            sprintf(error, "(%d) Unexpected Parse Success", idx);
            return error;
        }
        if (strcmp(dx_parse_error(parsed), err_vectors[idx].expected_error) != 0) {
            sprintf(error, "(%d) Error: Expected %s, Got %s", idx,
                    err_vectors[idx].expected_error, dx_parse_error(parsed));
            return error;
        }
        idx++;
    }

    return 0;
}


int parse_tests()
{
    int result = 0;
    dx_log_set_mask(LOG_NONE);

    TEST_CASE(test_parser_fixed_scalars, 0);
    TEST_CASE(test_parser_errors, 0);

    return result;
}

