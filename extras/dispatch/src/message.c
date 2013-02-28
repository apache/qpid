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
#include <qpid/dispatch/threading.h>
#include "message_private.h"
#include <string.h>
#include <stdio.h>

ALLOC_DEFINE_CONFIG(dx_message_t, sizeof(dx_message_pvt_t), 0, 0);
ALLOC_DEFINE(dx_message_content_t);


static void advance(unsigned char **cursor, dx_buffer_t **buffer, int consume)
{
    unsigned char *local_cursor = *cursor;
    dx_buffer_t   *local_buffer = *buffer;

    int remaining = dx_buffer_size(local_buffer) - (local_cursor - dx_buffer_base(local_buffer));
    while (consume > 0) {
        if (consume < remaining) {
            local_cursor += consume;
            consume = 0;
        } else {
            consume -= remaining;
            local_buffer = local_buffer->next;
            if (local_buffer == 0){
                local_cursor = 0;
                break;
            }
            local_cursor = dx_buffer_base(local_buffer);
            remaining = dx_buffer_size(local_buffer) - (local_cursor - dx_buffer_base(local_buffer));
        }
    }

    *cursor = local_cursor;
    *buffer = local_buffer;
}


static unsigned char next_octet(unsigned char **cursor, dx_buffer_t **buffer)
{
    unsigned char result = **cursor;
    advance(cursor, buffer, 1);
    return result;
}


static int traverse_field(unsigned char **cursor, dx_buffer_t **buffer, dx_field_location_t *field)
{
    unsigned char tag = next_octet(cursor, buffer);
    if (!(*cursor)) return 0;
    int consume = 0;
    switch (tag & 0xF0) {
    case 0x40 : consume = 0;  break;
    case 0x50 : consume = 1;  break;
    case 0x60 : consume = 2;  break;
    case 0x70 : consume = 4;  break;
    case 0x80 : consume = 8;  break;
    case 0x90 : consume = 16; break;

    case 0xB0 :
    case 0xD0 :
    case 0xF0 :
        consume |= ((int) next_octet(cursor, buffer)) << 24;
        if (!(*cursor)) return 0;
        consume |= ((int) next_octet(cursor, buffer)) << 16;
        if (!(*cursor)) return 0;
        consume |= ((int) next_octet(cursor, buffer)) << 8;
        if (!(*cursor)) return 0;
        // Fall through to the next case...

    case 0xA0 :
    case 0xC0 :
    case 0xE0 :
        consume |= (int) next_octet(cursor, buffer);
        if (!(*cursor)) return 0;
        break;
    }

    if (field) {
        field->buffer = *buffer;
        field->offset = *cursor - dx_buffer_base(*buffer);
        field->length = consume;
        field->parsed = 1;
    }

    advance(cursor, buffer, consume);
    return 1;
}


static int start_list(unsigned char **cursor, dx_buffer_t **buffer)
{
    unsigned char tag = next_octet(cursor, buffer);
    if (!(*cursor)) return 0;
    int length = 0;
    int count  = 0;

    switch (tag) {
    case 0x45 :     // list0
        break;
    case 0xd0 :     // list32
        length |= ((int) next_octet(cursor, buffer)) << 24;
        if (!(*cursor)) return 0;
        length |= ((int) next_octet(cursor, buffer)) << 16;
        if (!(*cursor)) return 0;
        length |= ((int) next_octet(cursor, buffer)) << 8;
        if (!(*cursor)) return 0;
        length |=  (int) next_octet(cursor, buffer);
        if (!(*cursor)) return 0;

        count |= ((int) next_octet(cursor, buffer)) << 24;
        if (!(*cursor)) return 0;
        count |= ((int) next_octet(cursor, buffer)) << 16;
        if (!(*cursor)) return 0;
        count |= ((int) next_octet(cursor, buffer)) << 8;
        if (!(*cursor)) return 0;
        count |=  (int) next_octet(cursor, buffer);
        if (!(*cursor)) return 0;

        break;

    case 0xc0 :     // list8
        length |= (int) next_octet(cursor, buffer);
        if (!(*cursor)) return 0;

        count |= (int) next_octet(cursor, buffer);
        if (!(*cursor)) return 0;
        break;
    }

    return count;
}


//
// Check the buffer chain, starting at cursor to see if it matches the pattern.
// If the pattern matches, check the next tag to see if it's in the set of expected
// tags.  If not, return zero.  If so, set the location descriptor to the good
// tag and advance the cursor (and buffer, if needed) to the end of the matched section.
//
// If there is no match, don't advance the cursor.
//
// Return 0 if the pattern matches but the following tag is unexpected
// Return 0 if the pattern matches and the location already has a pointer (duplicate section)
// Return 1 if the pattern matches and we've advanced the cursor/buffer
// Return 1 if the pattern does not match
//
static int dx_check_and_advance(dx_buffer_t         **buffer,
                                unsigned char       **cursor,
                                unsigned char        *pattern,
                                int                   pattern_length,
                                unsigned char        *expected_tags,
                                dx_field_location_t  *location)
{
    dx_buffer_t   *test_buffer = *buffer;
    unsigned char *test_cursor = *cursor;

    if (!test_cursor)
        return 1; // no match

    unsigned char *end_of_buffer = dx_buffer_base(test_buffer) + dx_buffer_size(test_buffer);
    int idx = 0;

    while (idx < pattern_length && *test_cursor == pattern[idx]) {
        idx++;
        test_cursor++;
        if (test_cursor == end_of_buffer) {
            test_buffer = test_buffer->next;
            if (test_buffer == 0)
                return 1; // Pattern didn't match
            test_cursor = dx_buffer_base(test_buffer);
            end_of_buffer = test_cursor + dx_buffer_size(test_buffer);
        }
    }

    if (idx < pattern_length)
        return 1; // Pattern didn't match

    //
    // Pattern matched, check the tag
    //
    while (*expected_tags && *test_cursor != *expected_tags)
        expected_tags++;
    if (*expected_tags == 0)
        return 0;  // Unexpected tag

    if (location->parsed)
        return 0;  // Duplicate section

    //
    // Pattern matched and tag is expected.  Mark the beginning of the section.
    //
    location->parsed = 1;
    location->buffer = test_buffer;
    location->offset = test_cursor - dx_buffer_base(test_buffer);
    location->length = 0;

    //
    // Advance the pointers to consume the whole section.
    //
    int consume = 0;
    unsigned char tag = next_octet(&test_cursor, &test_buffer);
    if (!test_cursor) return 0;
    switch (tag) {
    case 0x45 : // list0
        break;

    case 0xd0 : // list32
    case 0xd1 : // map32
    case 0xb0 : // vbin32
        consume |= ((int) next_octet(&test_cursor, &test_buffer)) << 24;
        if (!test_cursor) return 0;
        consume |= ((int) next_octet(&test_cursor, &test_buffer)) << 16;
        if (!test_cursor) return 0;
        consume |= ((int) next_octet(&test_cursor, &test_buffer)) << 8;
        if (!test_cursor) return 0;
        // Fall through to the next case...

    case 0xc0 : // list8
    case 0xc1 : // map8
    case 0xa0 : // vbin8
        consume |= (int) next_octet(&test_cursor, &test_buffer);
        if (!test_cursor) return 0;
        break;
    }

    if (consume)
        advance(&test_cursor, &test_buffer, consume);

    *cursor = test_cursor;
    *buffer = test_buffer;
    return 1;
}


static void dx_insert(dx_message_content_t *msg, const uint8_t *seq, size_t len)
{
    dx_buffer_t *buf = DEQ_TAIL(msg->buffers);

    while (len > 0) {
        if (buf == 0 || dx_buffer_capacity(buf) == 0) {
            buf = dx_allocate_buffer();
            if (buf == 0)
                return;
            DEQ_INSERT_TAIL(msg->buffers, buf);
        }

        size_t to_copy = dx_buffer_capacity(buf);
        if (to_copy > len)
            to_copy = len;
        memcpy(dx_buffer_cursor(buf), seq, to_copy);
        dx_buffer_insert(buf, to_copy);
        len -= to_copy;
        seq += to_copy;
        msg->length += to_copy;
    }
}


static void dx_insert_8(dx_message_content_t *msg, uint8_t value)
{
    dx_insert(msg, &value, 1);
}


static void dx_insert_32(dx_message_content_t *msg, uint32_t value)
{
    uint8_t buf[4];
    buf[0] = (uint8_t) ((value & 0xFF000000) >> 24);
    buf[1] = (uint8_t) ((value & 0x00FF0000) >> 16);
    buf[2] = (uint8_t) ((value & 0x0000FF00) >> 8);
    buf[3] = (uint8_t)  (value & 0x000000FF);
    dx_insert(msg, buf, 4);
}


static void dx_insert_64(dx_message_content_t *msg, uint64_t value)
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
    dx_insert(msg, buf, 8);
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


static void dx_start_list_performative(dx_message_content_t *msg, uint8_t code)
{
    //
    // Insert the short-form performative tag
    //
    dx_insert(msg, (const uint8_t*) "\x00\x53", 2);
    dx_insert_8(msg, code);

    //
    // Open the list with a list32 tag
    //
    dx_insert_8(msg, 0xd0);

    //
    // Mark the current location to later overwrite the length
    //
    msg->compose_length.buffer = DEQ_TAIL(msg->buffers);
    msg->compose_length.offset = dx_buffer_size(msg->compose_length.buffer);
    msg->compose_length.length = 4;
    msg->compose_length.parsed = 1;

    dx_insert(msg, (const uint8_t*) "\x00\x00\x00\x00", 4);

    //
    // Mark the current location to later overwrite the count
    //
    msg->compose_count.buffer = DEQ_TAIL(msg->buffers);
    msg->compose_count.offset = dx_buffer_size(msg->compose_count.buffer);
    msg->compose_count.length = 4;
    msg->compose_count.parsed = 1;

    dx_insert(msg, (const uint8_t*) "\x00\x00\x00\x00", 4);

    msg->length = 4; // Include the length of the count field
    msg->count = 0;
}


static void dx_end_list(dx_message_content_t *msg)
{
    dx_overwrite_32(&msg->compose_length, msg->length);
    dx_overwrite_32(&msg->compose_count,  msg->count);
}


static dx_field_location_t *dx_message_field_location(dx_message_t *msg, dx_message_field_t field)
{
    dx_message_content_t *content = MSG_CONTENT(msg);

    switch (field) {
    case DX_FIELD_TO:
        while (1) {
            if (content->field_to.parsed)
                return &content->field_to;

            if (content->section_message_properties.parsed == 0)
                break;

            dx_buffer_t   *buffer = content->section_message_properties.buffer;
            unsigned char *cursor = dx_buffer_base(buffer) + content->section_message_properties.offset;

            int count = start_list(&cursor, &buffer);
            int result;

            if (count < 3)
                break;

            result = traverse_field(&cursor, &buffer, 0); // message_id
            if (!result) return 0;
            result = traverse_field(&cursor, &buffer, 0); // user_id
            if (!result) return 0;
            result = traverse_field(&cursor, &buffer, &content->field_to); // to
            if (!result) return 0;
        }
        break;

    case DX_FIELD_BODY:
        while (1) {
            if (content->body.parsed)
                return &content->body;

            if (content->section_body.parsed == 0)
                break;

            dx_buffer_t   *buffer = content->section_body.buffer;
            unsigned char *cursor = dx_buffer_base(buffer) + content->section_body.offset;
            int result;

            result = traverse_field(&cursor, &buffer, &content->body);
            if (!result) return 0;
        }
        break;

    default:
        break;
    }

    return 0;
}


dx_message_t *dx_allocate_message()
{
    dx_message_pvt_t *msg = (dx_message_pvt_t*) new_dx_message_t();
    if (!msg)
        return 0;

    DEQ_ITEM_INIT(msg);
    msg->content      = new_dx_message_content_t();
    msg->out_delivery = 0;

    if (msg->content == 0) {
        free_dx_message_t((dx_message_t*) msg);
        return 0;
    }

    memset(msg->content, 0, sizeof(dx_message_content_t));
    msg->content->lock      = sys_mutex();
    msg->content->ref_count = 1;

    return (dx_message_t*) msg;
}


void dx_free_message(dx_message_t *in_msg)
{
    uint32_t rc;
    dx_message_pvt_t     *msg     = (dx_message_pvt_t*) in_msg;
    dx_message_content_t *content = msg->content;

    sys_mutex_lock(content->lock);
    rc = --content->ref_count;
    sys_mutex_unlock(content->lock);

    if (rc == 0) {
        dx_buffer_t *buf = DEQ_HEAD(content->buffers);

        while (buf) {
            DEQ_REMOVE_HEAD(content->buffers);
            dx_free_buffer(buf);
            buf = DEQ_HEAD(content->buffers);
        }

        sys_mutex_free(content->lock);
        free_dx_message_content_t(content);
    }

    free_dx_message_t((dx_message_t*) msg);
}


dx_message_t *dx_message_copy(dx_message_t *in_msg)
{
    dx_message_pvt_t     *msg     = (dx_message_pvt_t*) in_msg;
    dx_message_content_t *content = msg->content;
    dx_message_pvt_t     *copy    = (dx_message_pvt_t*) new_dx_message_t();

    if (!copy)
        return 0;

    DEQ_ITEM_INIT(copy);
    copy->content      = content;
    copy->out_delivery = 0;

    sys_mutex_lock(content->lock);
    content->ref_count++;
    sys_mutex_unlock(content->lock);

    return (dx_message_t*) copy;
}


void dx_message_set_out_delivery(dx_message_t *msg, pn_delivery_t *delivery)
{
    ((dx_message_pvt_t*) msg)->out_delivery = delivery;
}


pn_delivery_t *dx_message_out_delivery(dx_message_t *msg)
{
    return ((dx_message_pvt_t*) msg)->out_delivery;
}


void dx_message_set_in_delivery(dx_message_t *msg, pn_delivery_t *delivery)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    content->in_delivery = delivery;
}


pn_delivery_t *dx_message_in_delivery(dx_message_t *msg)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    return content->in_delivery;
}


dx_message_t *dx_message_receive(pn_delivery_t *delivery)
{
    pn_link_t        *link = pn_delivery_link(delivery);
    dx_message_pvt_t *msg  = (dx_message_pvt_t*) pn_delivery_get_context(delivery);
    ssize_t           rc;
    dx_buffer_t      *buf;

    //
    // If there is no message associated with the delivery, this is the first time
    // we've received anything on this delivery.  Allocate a message descriptor and 
    // link it and the delivery together.
    //
    if (!msg) {
        msg = (dx_message_pvt_t*) dx_allocate_message();
        pn_delivery_set_context(delivery, (void*) msg);

        //
        // Record the incoming delivery only if it is not settled.  If it is 
        // settled, it should not be recorded as no future operations on it are
        // permitted.
        //
        if (!pn_delivery_settled(delivery))
            msg->content->in_delivery = delivery;
    }

    //
    // Get a reference to the tail buffer on the message.  This is the buffer into which
    // we will store incoming message data.  If there is no buffer in the message, allocate
    // an empty one and add it to the message.
    //
    buf = DEQ_TAIL(msg->content->buffers);
    if (!buf) {
        buf = dx_allocate_buffer();
        DEQ_INSERT_TAIL(msg->content->buffers, buf);
    }

    while (1) {
        //
        // Try to receive enough data to fill the remaining space in the tail buffer.
        //
        rc = pn_link_recv(link, (char*) dx_buffer_cursor(buf), dx_buffer_capacity(buf));

        //
        // If we receive PN_EOS, we have come to the end of the message.
        //
        if (rc == PN_EOS) {
            //
            // If the last buffer in the list is empty, remove it and free it.  This
            // will only happen if the size of the message content is an exact multiple
            // of the buffer size.
            //
            if (dx_buffer_size(buf) == 0) {
                DEQ_REMOVE_TAIL(msg->content->buffers);
                dx_free_buffer(buf);
            }
            return (dx_message_t*) msg;
        }

        if (rc > 0) {
            //
            // We have received a positive number of bytes for the message.  Advance
            // the cursor in the buffer.
            //
            dx_buffer_insert(buf, rc);

            //
            // If the buffer is full, allocate a new empty buffer and append it to the
            // tail of the message's list.
            //
            if (dx_buffer_capacity(buf) == 0) {
                buf = dx_allocate_buffer();
                DEQ_INSERT_TAIL(msg->content->buffers, buf);
            }
        } else
            //
            // We received zero bytes, and no PN_EOS.  This means that we've received
            // all of the data available up to this point, but it does not constitute
            // the entire message.  We'll be back later to finish it up.
            //
            break;
    }

    return 0;
}


void dx_message_send(dx_message_t *in_msg, pn_link_t *link)
{
    dx_message_pvt_t *msg = (dx_message_pvt_t*) in_msg;
    dx_buffer_t      *buf = DEQ_HEAD(msg->content->buffers);

    // TODO - Handle cases where annotations have been added or modified
    while (buf) {
        pn_link_send(link, (char*) dx_buffer_base(buf), dx_buffer_size(buf));
        buf = DEQ_NEXT(buf);
    }
}


int dx_message_check(dx_message_t *in_msg, dx_message_depth_t depth)
{

#define LONG  10
#define SHORT 3
#define MSG_HDR_LONG                  (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x70"
#define MSG_HDR_SHORT                 (unsigned char*) "\x00\x53\x70"
#define DELIVERY_ANNOTATION_LONG      (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x71"
#define DELIVERY_ANNOTATION_SHORT     (unsigned char*) "\x00\x53\x71"
#define MESSAGE_ANNOTATION_LONG       (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x72"
#define MESSAGE_ANNOTATION_SHORT      (unsigned char*) "\x00\x53\x72"
#define PROPERTIES_LONG               (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x73"
#define PROPERTIES_SHORT              (unsigned char*) "\x00\x53\x73"
#define APPLICATION_PROPERTIES_LONG   (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x74"
#define APPLICATION_PROPERTIES_SHORT  (unsigned char*) "\x00\x53\x74"
#define BODY_DATA_LONG                (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x75"
#define BODY_DATA_SHORT               (unsigned char*) "\x00\x53\x75"
#define BODY_SEQUENCE_LONG            (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x76"
#define BODY_SEQUENCE_SHORT           (unsigned char*) "\x00\x53\x76"
#define FOOTER_LONG                   (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x78"
#define FOOTER_SHORT                  (unsigned char*) "\x00\x53\x78"
#define TAGS_LIST                     (unsigned char*) "\x45\xc0\xd0"
#define TAGS_MAP                      (unsigned char*) "\xc1\xd1"
#define TAGS_BINARY                   (unsigned char*) "\xa0\xb0"

    dx_message_pvt_t     *msg = (dx_message_pvt_t*) in_msg;
    dx_message_content_t *content = msg->content;
    dx_buffer_t          *buffer = DEQ_HEAD(content->buffers);
    unsigned char        *cursor;

    if (!buffer)
        return 0; // Invalid - No data in the message

    if (depth == DX_DEPTH_NONE)
        return 1;

    cursor = dx_buffer_base(buffer);

    //
    // MESSAGE HEADER
    //
    if (0 == dx_check_and_advance(&buffer, &cursor, MSG_HDR_LONG,  LONG,  TAGS_LIST, &content->section_message_header))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, MSG_HDR_SHORT, SHORT, TAGS_LIST, &content->section_message_header))
        return 0;

    if (depth == DX_DEPTH_HEADER)
        return 1;

    //
    // DELIVERY ANNOTATION
    //
    if (0 == dx_check_and_advance(&buffer, &cursor, DELIVERY_ANNOTATION_LONG,  LONG,  TAGS_MAP,  &content->section_delivery_annotation))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, DELIVERY_ANNOTATION_SHORT, SHORT, TAGS_MAP,  &content->section_delivery_annotation))
        return 0;

    if (depth == DX_DEPTH_DELIVERY_ANNOTATIONS)
        return 1;

    //
    // MESSAGE ANNOTATION
    //
    if (0 == dx_check_and_advance(&buffer, &cursor, MESSAGE_ANNOTATION_LONG,  LONG,  TAGS_MAP,  &content->section_message_annotation))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, MESSAGE_ANNOTATION_SHORT, SHORT, TAGS_MAP,  &content->section_message_annotation))
        return 0;

    if (depth == DX_DEPTH_MESSAGE_ANNOTATIONS)
        return 1;

    //
    // PROPERTIES
    //
    if (0 == dx_check_and_advance(&buffer, &cursor, PROPERTIES_LONG,  LONG,  TAGS_LIST, &content->section_message_properties))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, PROPERTIES_SHORT, SHORT, TAGS_LIST, &content->section_message_properties))
        return 0;

    if (depth == DX_DEPTH_PROPERTIES)
        return 1;

    //
    // APPLICATION PROPERTIES
    //
    if (0 == dx_check_and_advance(&buffer, &cursor, APPLICATION_PROPERTIES_LONG,  LONG,  TAGS_MAP, &content->section_application_properties))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, APPLICATION_PROPERTIES_SHORT, SHORT, TAGS_MAP, &content->section_application_properties))
        return 0;

    if (depth == DX_DEPTH_APPLICATION_PROPERTIES)
        return 1;

    //
    // BODY  (Note that this function expects a single data section or a single AMQP sequence)
    //
    if (0 == dx_check_and_advance(&buffer, &cursor, BODY_DATA_LONG,      LONG,  TAGS_BINARY, &content->section_body))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, BODY_DATA_SHORT,     SHORT, TAGS_BINARY, &content->section_body))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, BODY_SEQUENCE_LONG,  LONG,  TAGS_LIST,   &content->section_body))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, BODY_SEQUENCE_SHORT, SHORT, TAGS_LIST,   &content->section_body))
        return 0;

    if (depth == DX_DEPTH_BODY)
        return 1;

    //
    // FOOTER
    //
    if (0 == dx_check_and_advance(&buffer, &cursor, FOOTER_LONG,  LONG,  TAGS_MAP, &content->section_footer))
        return 0;
    if (0 == dx_check_and_advance(&buffer, &cursor, FOOTER_SHORT, SHORT, TAGS_MAP, &content->section_footer))
        return 0;

    return 1;
}


dx_field_iterator_t *dx_message_field_iterator(dx_message_t *msg, dx_message_field_t field)
{
    dx_field_location_t *loc = dx_message_field_location(msg, field);
    if (!loc)
        return 0;

    return dx_field_iterator_buffer(loc->buffer, loc->offset, loc->length, ITER_VIEW_ALL);
}


dx_iovec_t *dx_message_field_iovec(dx_message_t *msg, dx_message_field_t field)
{
    dx_field_location_t *loc = dx_message_field_location(msg, field);
    if (!loc)
        return 0;

    //
    // Count the number of buffers this field straddles
    //
    int          bufcnt    = 1;
    dx_buffer_t *buf       = loc->buffer;
    size_t       bufsize   = dx_buffer_size(buf) - loc->offset;
    ssize_t      remaining = loc->length - bufsize;

    while (remaining > 0) {
        bufcnt++;
        buf = buf->next;
        if (!buf)
            return 0;
        remaining -= dx_buffer_size(buf);
    }

    //
    // Allocate an iovec object big enough to hold the number of buffers
    //
    dx_iovec_t *iov = dx_iovec(bufcnt);
    if (!iov)
        return 0;

    //
    // Build out the io vectors with pointers to the segments of the field in buffers
    //
    bufcnt     = 0;
    buf        = loc->buffer;
    bufsize    = dx_buffer_size(buf) - loc->offset;
    void *base = dx_buffer_base(buf) + loc->offset;
    remaining  = loc->length;

    while (remaining > 0) {
        if (bufsize > remaining)
            bufsize = remaining;
        dx_iovec_array(iov)[bufcnt].iov_base = base;
        dx_iovec_array(iov)[bufcnt].iov_len  = bufsize;
        bufcnt++;
        remaining -= bufsize;
        if (remaining > 0) {
            buf     = buf->next;
            base    = dx_buffer_base(buf);
            bufsize = dx_buffer_size(buf);
        }
    }

    return iov;
}


ssize_t dx_message_field_length(dx_message_t *msg, dx_message_field_t field)
{
    dx_field_location_t *loc = dx_message_field_location(msg, field);
    if (!loc)
        return -1;

    return loc->length;
}


ssize_t dx_message_field_copy(dx_message_t *msg, dx_message_field_t field, void *buffer)
{
    dx_field_location_t *loc = dx_message_field_location(msg, field);
    if (!loc)
        return -1;

    dx_buffer_t *buf       = loc->buffer;
    size_t       bufsize   = dx_buffer_size(buf) - loc->offset;
    void        *base      = dx_buffer_base(buf) + loc->offset;
    size_t       remaining = loc->length;

    while (remaining > 0) {
        if (bufsize > remaining)
            bufsize = remaining;
        memcpy(buffer, base, bufsize);
        buffer    += bufsize;
        remaining -= bufsize;
        if (remaining > 0) {
            buf     = buf->next;
            base    = dx_buffer_base(buf);
            bufsize = dx_buffer_size(buf);
        }
    }

    return loc->length;
}


void dx_message_compose_1(dx_message_t *msg, const char *to, dx_buffer_list_t *buffers)
{
    dx_message_begin_header(msg);
    dx_message_insert_boolean(msg, 0);  // durable
    //dx_message_insert_null(msg);        // priority
    //dx_message_insert_null(msg);        // ttl
    //dx_message_insert_boolean(msg, 0);  // first-acquirer
    //dx_message_insert_uint(msg, 0);     // delivery-count
    dx_message_end_header(msg);

    dx_message_begin_message_properties(msg);
    dx_message_insert_null(msg);          // message-id
    dx_message_insert_null(msg);          // user-id
    dx_message_insert_string(msg, to);    // to
    //dx_message_insert_null(msg);          // subject
    //dx_message_insert_null(msg);          // reply-to
    //dx_message_insert_null(msg);          // correlation-id
    //dx_message_insert_null(msg);          // content-type
    //dx_message_insert_null(msg);          // content-encoding
    //dx_message_insert_timestamp(msg, 0);  // absolute-expiry-time
    //dx_message_insert_timestamp(msg, 0);  // creation-time
    //dx_message_insert_null(msg);          // group-id
    //dx_message_insert_uint(msg, 0);       // group-sequence
    //dx_message_insert_null(msg);          // reply-to-group-id
    dx_message_end_message_properties(msg);

    if (buffers)
        dx_message_append_body_data(msg, buffers);
}


void dx_message_begin_header(dx_message_t *msg)
{
    dx_start_list_performative(MSG_CONTENT(msg), 0x70);
}


void dx_message_end_header(dx_message_t *msg)
{
    dx_end_list(MSG_CONTENT(msg));
}


void dx_message_begin_delivery_annotations(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_end_delivery_annotations(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_begin_message_annotations(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_end_message_annotations(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_begin_message_properties(dx_message_t *msg)
{
    dx_start_list_performative(MSG_CONTENT(msg), 0x73);
}


void dx_message_end_message_properties(dx_message_t *msg)
{
    dx_end_list(MSG_CONTENT(msg));
}


void dx_message_begin_application_properties(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_end_application_properties(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_append_body_data(dx_message_t *msg, dx_buffer_list_t *buffers)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    dx_buffer_t          *buf     = DEQ_HEAD(*buffers);
    uint32_t              len     = 0;

    //
    // Calculate the size of the body to be appended.
    //
    while (buf) {
        len += dx_buffer_size(buf);
        buf = DEQ_NEXT(buf);
    }

    //
    // Insert a DATA section performative header.
    //
    dx_insert(content, (const uint8_t*) "\x00\x53\x75", 3);
    if (len < 256) {
        dx_insert_8(content, 0xa0);  // vbin8
        dx_insert_8(content, (uint8_t) len);
    } else {
        dx_insert_8(content, 0xb0);  // vbin32
        dx_insert_32(content, len);
    }

    //
    // Move the supplied buffers to the tail of the message's buffer list.
    //
    buf = DEQ_HEAD(*buffers);
    while (buf) {
        DEQ_REMOVE_HEAD(*buffers);
        DEQ_INSERT_TAIL(content->buffers, buf);
        buf = DEQ_HEAD(*buffers);
    }
}


void dx_message_begin_body_sequence(dx_message_t *msg)
{
}


void dx_message_end_body_sequence(dx_message_t *msg)
{
}


void dx_message_begin_footer(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_end_footer(dx_message_t *msg)
{
    assert(0); // Not Implemented
}


void dx_message_insert_null(dx_message_t *msg)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    dx_insert_8(content, 0x40);
    content->count++;
}


void dx_message_insert_boolean(dx_message_t *msg, int value)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    if (value)
        dx_insert(content, (const uint8_t*) "\x56\x01", 2);
    else
        dx_insert(content, (const uint8_t*) "\x56\x00", 2);
    content->count++;
}


void dx_message_insert_ubyte(dx_message_t *msg, uint8_t value)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    dx_insert_8(content, 0x50);
    dx_insert_8(content, value);
    content->count++;
}


void dx_message_insert_uint(dx_message_t *msg, uint32_t value)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    if (value == 0) {
        dx_insert_8(content, 0x43);  // uint0
    } else if (value < 256) {
        dx_insert_8(content, 0x52);  // smalluint
        dx_insert_8(content, (uint8_t) value);
    } else {
        dx_insert_8(content, 0x70);  // uint
        dx_insert_32(content, value);
    }
    content->count++;
}


void dx_message_insert_ulong(dx_message_t *msg, uint64_t value)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    if (value == 0) {
        dx_insert_8(content, 0x44);  // ulong0
    } else if (value < 256) {
        dx_insert_8(content, 0x53);  // smallulong
        dx_insert_8(content, (uint8_t) value);
    } else {
        dx_insert_8(content, 0x80);  // ulong
        dx_insert_64(content, value);
    }
    content->count++;
}


void dx_message_insert_binary(dx_message_t *msg, const uint8_t *start, size_t len)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    if (len < 256) {
        dx_insert_8(content, 0xa0);  // vbin8
        dx_insert_8(content, (uint8_t) len);
    } else {
        dx_insert_8(content, 0xb0);  // vbin32
        dx_insert_32(content, len);
    }
    dx_insert(content, start, len);
    content->count++;
}


void dx_message_insert_string(dx_message_t *msg, const char *start)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    uint32_t len = strlen(start);

    if (len < 256) {
        dx_insert_8(content, 0xa1);  // str8-utf8
        dx_insert_8(content, (uint8_t) len);
        dx_insert(content, (const uint8_t*) start, len);
    } else {
        dx_insert_8(content, 0xb1);  // str32-utf8
        dx_insert_32(content, len);
        dx_insert(content, (const uint8_t*) start, len);
    }
    content->count++;
}


void dx_message_insert_uuid(dx_message_t *msg, const uint8_t *value)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    dx_insert_8(content, 0x98);  // uuid
    dx_insert(content, value, 16);
    content->count++;
}


void dx_message_insert_symbol(dx_message_t *msg, const char *start, size_t len)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    if (len < 256) {
        dx_insert_8(content, 0xa3);  // sym8
        dx_insert_8(content, (uint8_t) len);
        dx_insert(content, (const uint8_t*) start, len);
    } else {
        dx_insert_8(content, 0xb3);  // sym32
        dx_insert_32(content, len);
        dx_insert(content, (const uint8_t*) start, len);
    }
    content->count++;
}


void dx_message_insert_timestamp(dx_message_t *msg, uint64_t value)
{
    dx_message_content_t *content = MSG_CONTENT(msg);
    dx_insert_8(content, 0x83);  // timestamp
    dx_insert_64(content, value);
    content->count++;
}


void dx_message_begin_list(dx_message_t* msg)
{
    assert(0); // Not Implemented
}


void dx_message_end_list(dx_message_t* msg)
{
    assert(0); // Not Implemented
}


void dx_message_begin_map(dx_message_t* msg)
{
    assert(0); // Not Implemented
}


void dx_message_end_map(dx_message_t* msg)
{
    assert(0); // Not Implemented
}

