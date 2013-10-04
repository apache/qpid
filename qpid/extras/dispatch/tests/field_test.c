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

#include "test_case.h"
#include <stdio.h>
#include <string.h>
#include <qpid/dispatch/iterator.h>

#define FAIL_TEXT_SIZE 10000
static char fail_text[FAIL_TEXT_SIZE];

static char* test_view_global_dns(void *context)
{
    dx_field_iterator_t *iter = dx_field_iterator_string("amqp://host/global/sub", ITER_VIEW_ALL);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "amqp://host/global/sub"))
        return "ITER_VIEW_ALL failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NO_HOST);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "global/sub"))
        return "ITER_VIEW_NO_HOST failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NODE_ID);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "global"))
        return "ITER_VIEW_NODE_ID failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NODE_SPECIFIC);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "sub"))
        return "ITER_VIEW_NODE_SPECIFIC failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_ADDRESS_HASH);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "Mglobal/sub"))
        return "ITER_VIEW_ADDRESS_HASH failed";

    return 0;
}


static char* test_view_global_non_dns(void *context)
{
    dx_field_iterator_t *iter = dx_field_iterator_string("amqp:/global/sub", ITER_VIEW_ALL);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "amqp:/global/sub"))
        return "ITER_VIEW_ALL failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NO_HOST);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "global/sub"))
        return "ITER_VIEW_NO_HOST failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NODE_ID);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "global"))
        return "ITER_VIEW_NODE_ID failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NODE_SPECIFIC);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "sub"))
        return "ITER_VIEW_NODE_SPECIFIC failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_ADDRESS_HASH);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "Mglobal/sub"))
        return "ITER_VIEW_ADDRESS_HASH failed";

    return 0;
}


static char* test_view_global_no_host(void *context)
{
    dx_field_iterator_t *iter = dx_field_iterator_string("global/sub", ITER_VIEW_ALL);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "global/sub"))
        return "ITER_VIEW_ALL failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NO_HOST);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "global/sub"))
        return "ITER_VIEW_NO_HOST failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NODE_ID);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "global"))
        return "ITER_VIEW_NODE_ID failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_NODE_SPECIFIC);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "sub"))
        return "ITER_VIEW_NODE_SPECIFIC failed";

    dx_field_iterator_reset_view(iter, ITER_VIEW_ADDRESS_HASH);
    if (!dx_field_iterator_equal(iter, (unsigned char*) "Mglobal/sub"))
        return "ITER_VIEW_ADDRESS_HASH failed";

    return 0;
}


static char* test_view_address_hash(void *context)
{
    struct {const char *addr; const char *view;} cases[] = {
    {"amqp:/_local/my-addr/sub",                "Lmy-addr/sub"},
    {"amqp:/_local/my-addr",                    "Lmy-addr"},
    {"amqp:/_topo/area/router/local/sub",       "Aarea"},
    {"amqp:/_topo/my-area/router/local/sub",    "Rrouter"},
    {"amqp:/_topo/my-area/my-router/local/sub", "Llocal/sub"},
    {"amqp:/_topo/area/all/local/sub",          "Aarea"},
    {"amqp:/_topo/my-area/all/local/sub",       "Llocal/sub"},
    {"amqp:/_topo/all/all/local/sub",           "Llocal/sub"},
    {"amqp://host:port/_local/my-addr",         "Lmy-addr"},
    {"_topo/area/router/my-addr",               "Aarea"},
    {"_topo/my-area/router/my-addr",            "Rrouter"},
    {"_topo/my-area/my-router/my-addr",         "Lmy-addr"},
    {"_topo/my-area/router",                    "Rrouter"},
    {0, 0}
    };
    int idx;

    for (idx = 0; cases[idx].addr; idx++) {
        dx_field_iterator_t *iter = dx_field_iterator_string(cases[idx].addr, ITER_VIEW_ADDRESS_HASH);
        if (!dx_field_iterator_equal(iter, (unsigned char*) cases[idx].view)) {
            char *got = (char*) dx_field_iterator_copy(iter);
            snprintf(fail_text, FAIL_TEXT_SIZE, "Addr '%s' failed.  Expected '%s', got '%s'",
                     cases[idx].addr, cases[idx].view, got);
            return fail_text;
        }
    }

    return 0;
}


int field_tests(void)
{
    int result = 0;

    dx_field_iterator_set_address("my-area", "my-router");

    TEST_CASE(test_view_global_dns, 0);
    TEST_CASE(test_view_global_non_dns, 0);
    TEST_CASE(test_view_global_no_host, 0);
    TEST_CASE(test_view_address_hash, 0);

    return result;
}

