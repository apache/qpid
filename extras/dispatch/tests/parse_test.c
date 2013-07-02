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
#include "test_case.h"
#include <qpid/dispatch.h>

struct vector_t {
    const char *data;
    int         length;
    uint8_t     expected_tag;
    int         check_uint;
    uint32_t    expected_uint;
} vectors[] = {
{"\x40",                 1, DX_AMQP_NULL,      0, 0},
{"\x41",                 1, DX_AMQP_TRUE,      1, 1},
{"\x42",                 1, DX_AMQP_FALSE,     1, 0},
{"\x56\x00",             2, DX_AMQP_BOOLEAN,   1, 0},
{"\x56\x01",             2, DX_AMQP_BOOLEAN,   1, 1},
{"\x50\x45",             2, DX_AMQP_UBYTE,     1, 0x45},
{"\x60\x02\x04",         3, DX_AMQP_USHORT,    1, 0x0204},
{"\x70\x01\x02\x03\x04", 5, DX_AMQP_UINT,      1, 0x01020304},
{"\x52\x06",             2, DX_AMQP_SMALLUINT, 1, 0x00000006},
{"\x43",                 1, DX_AMQP_UINT0,     1, 0x00000000},
{0, 0, 0, 0, 0}
};


static char *test_parser_fixed_scalars(void *context)
{
    int idx = 0;

    while (vectors[idx].data) {
        dx_field_iterator_t *field  = dx_field_iterator_binary(vectors[idx].data,
                                                               vectors[idx].length,
                                                               ITER_VIEW_ALL);
        dx_parsed_field_t *parsed = dx_parse(field);
        if (!dx_parse_ok(parsed)) return "Unexpected Parse Error";
        if (dx_parse_tag(parsed) != vectors[idx].expected_tag) return "Mismatched Tag";
        if (vectors[idx].check_uint &&
            dx_parse_as_uint(parsed) != vectors[idx].expected_uint) return "Mismatched Uint";
        idx++;
    }

    return 0;
}


int parse_tests()
{
    int result = 0;
    dx_log_set_mask(LOG_NONE);

    TEST_CASE(test_parser_fixed_scalars, 0);

    return result;
}

