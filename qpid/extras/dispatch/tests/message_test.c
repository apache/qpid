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
#include "message_private.h"
#include <qpid/dispatch/iterator.h>
#include <proton/message.h>

static char buffer[10000];

static size_t flatten_bufs(dx_message_content_t *content)
{
    char        *cursor = buffer;
    dx_buffer_t *buf    = DEQ_HEAD(content->buffers);

    while (buf) {
        memcpy(cursor, dx_buffer_base(buf), dx_buffer_size(buf));
        cursor += dx_buffer_size(buf);
        buf = buf->next;
    }

    return (size_t) (cursor - buffer);
}


static void set_content(dx_message_content_t *content, size_t len)
{
    char        *cursor = buffer;
    dx_buffer_t *buf;

    while (len > (size_t) (cursor - buffer)) {
        buf = dx_allocate_buffer();
        size_t segment   = dx_buffer_capacity(buf);
        size_t remaining = len - (size_t) (cursor - buffer);
        if (segment > remaining)
            segment = remaining;
        memcpy(dx_buffer_base(buf), cursor, segment);
        cursor += segment;
        dx_buffer_insert(buf, segment);
        DEQ_INSERT_TAIL(content->buffers, buf);
    }
}


static char* test_send_to_messenger(void *context)
{
    dx_message_t         *msg     = dx_allocate_message();
    dx_message_content_t *content = MSG_CONTENT(msg);

    dx_message_compose_1(msg, "test_addr_0", 0);
    dx_buffer_t *buf = DEQ_HEAD(content->buffers);
    if (buf == 0) return "Expected a buffer in the test message";

    pn_message_t *pn_msg = pn_message();
    size_t len = flatten_bufs(content);
    int result = pn_message_decode(pn_msg, buffer, len);
    if (result != 0) return "Error in pn_message_decode";

    if (strcmp(pn_message_get_address(pn_msg), "test_addr_0") != 0)
        return "Address mismatch in received message";

    pn_message_free(pn_msg);
    dx_free_message(msg);

    return 0;
}


static char* test_receive_from_messenger(void *context)
{
    pn_message_t *pn_msg = pn_message();
    pn_message_set_address(pn_msg, "test_addr_1");

    size_t       size = 10000;
    int result = pn_message_encode(pn_msg, buffer, &size);
    if (result != 0) return "Error in pn_message_encode";

    dx_message_t         *msg     = dx_allocate_message();
    dx_message_content_t *content = MSG_CONTENT(msg);

    set_content(content, size);

    int valid = dx_message_check(msg, DX_DEPTH_ALL);
    if (!valid) return "dx_message_check returns 'invalid'";

    dx_field_iterator_t *iter = dx_message_field_iterator(msg, DX_FIELD_TO);
    if (iter == 0) return "Expected an iterator for the 'to' field";

    if (!dx_field_iterator_equal(iter, (unsigned char*) "test_addr_1"))
        return "Mismatched 'to' field contents";

    ssize_t test_len = dx_message_field_length(msg, DX_FIELD_TO);
    if (test_len != 11) return "Incorrect field length";

    char test_field[15];
    test_len = dx_message_field_copy(msg, DX_FIELD_TO, test_field);
    if (test_len != 11) return "Incorrect length returned from field_copy";
    test_field[test_len] = '\0';
    if (strcmp(test_field, "test_addr_1") != 0)
        return "Incorrect field content returned from field_copy";

    pn_message_free(pn_msg);
    dx_free_message(msg);

    return 0;
}


static char* test_insufficient_check_depth(void *context)
{
    pn_message_t *pn_msg = pn_message();
    pn_message_set_address(pn_msg, "test_addr_2");

    size_t       size = 10000;
    int result = pn_message_encode(pn_msg, buffer, &size);
    if (result != 0) return "Error in pn_message_encode";

    dx_message_t         *msg     = dx_allocate_message();
    dx_message_content_t *content = MSG_CONTENT(msg);

    set_content(content, size);

    int valid = dx_message_check(msg, DX_DEPTH_DELIVERY_ANNOTATIONS);
    if (!valid) return "dx_message_check returns 'invalid'";

    dx_field_iterator_t *iter = dx_message_field_iterator(msg, DX_FIELD_TO);
    if (iter) return "Expected no iterator for the 'to' field";

    dx_free_message(msg);

    return 0;
}


int message_tests(void)
{
    int result = 0;

    TEST_CASE(test_send_to_messenger, 0);
    TEST_CASE(test_receive_from_messenger, 0);
    TEST_CASE(test_insufficient_check_depth, 0);

    return result;
}

