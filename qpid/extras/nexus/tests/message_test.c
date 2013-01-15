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
#include <qpid/nexus/message.h>
#include <qpid/nexus/iterator.h>
#include <proton/message.h>


static char* test_init(void *context)
{
    nx_allocator_initialize(nx_allocator_default_config());
    nx_allocator_finalize();
    return 0;
}


static char* test_send_to_messenger(void *context)
{
    nx_allocator_initialize(nx_allocator_default_config());

    nx_message_t *msg = nx_allocate_message();
    nx_message_compose_1(msg, "test_addr_0", 0);
    nx_buffer_t *buf = DEQ_HEAD(msg->buffers);
    if (buf == 0) return "Expected a buffer in the test message";

    pn_message_t *pn_msg = pn_message();
    int result = pn_message_decode(pn_msg, (const char*) nx_buffer_base(buf), nx_buffer_size(buf));
    if (result != 0) return "Error in pn_message_decode";

    if (strcmp(pn_message_get_address(pn_msg), "test_addr_0") != 0)
        return "Address mismatch in received message";

    pn_message_free(pn_msg);
    nx_free_message(msg);

    nx_allocator_finalize();
    return 0;
}


static char* test_receive_from_messenger(void *context)
{
    nx_allocator_initialize(nx_allocator_default_config());

    pn_message_t *pn_msg = pn_message();
    pn_message_set_address(pn_msg, "test_addr_1");

    nx_buffer_t *buf  = nx_allocate_buffer();
    size_t       size = nx_buffer_capacity(buf);
    int result = pn_message_encode(pn_msg, (char*) nx_buffer_cursor(buf), &size);
    if (result != 0) return "Error in pn_message_encode";
    nx_buffer_insert(buf, size);

    nx_message_t *msg = nx_allocate_message();
    DEQ_INSERT_TAIL(msg->buffers, buf);
    int valid = nx_message_check(msg, NX_DEPTH_ALL);
    if (!valid) return "nx_message_check returns 'invalid'";

    nx_field_iterator_t *iter = nx_message_field_to(msg);
    if (iter == 0) return "Expected an iterator for the 'to' field";

    if (!nx_field_iterator_equal(iter, (unsigned char*) "test_addr_1"))
        return "Mismatched 'to' field contents";

    pn_message_free(pn_msg);
    nx_free_message(msg);

    nx_allocator_finalize();
    return 0;
}


static char* test_insufficient_check_depth(void *context)
{
    nx_allocator_initialize(nx_allocator_default_config());

    pn_message_t *pn_msg = pn_message();
    pn_message_set_address(pn_msg, "test_addr_2");

    nx_buffer_t *buf  = nx_allocate_buffer();
    size_t       size = nx_buffer_capacity(buf);
    int result = pn_message_encode(pn_msg, (char*) nx_buffer_cursor(buf), &size);
    if (result != 0) return "Error in pn_message_encode";
    nx_buffer_insert(buf, size);

    nx_message_t *msg = nx_allocate_message();
    DEQ_INSERT_TAIL(msg->buffers, buf);
    int valid = nx_message_check(msg, NX_DEPTH_DELIVERY_ANNOTATIONS);
    if (!valid) return "nx_message_check returns 'invalid'";

    nx_field_iterator_t *iter = nx_message_field_to(msg);
    if (iter) return "Expected no iterator for the 'to' field";

    nx_free_message(msg);

    nx_allocator_finalize();
    return 0;
}


int message_tests(void)
{
    int result = 0;

    TEST_CASE(test_init, 0);
    TEST_CASE(test_send_to_messenger, 0);
    TEST_CASE(test_receive_from_messenger, 0);
    TEST_CASE(test_insufficient_check_depth, 0);

    return result;
}

