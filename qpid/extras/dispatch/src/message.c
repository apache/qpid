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
#include "compose_private.h"
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
                                const unsigned char  *pattern,
                                int                   pattern_length,
                                const unsigned char  *expected_tags,
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
    int pre_consume = 1;  // Count the already extracted tag
    int consume     = 0;
    unsigned char tag = next_octet(&test_cursor, &test_buffer);
    if (!test_cursor) return 0;
    switch (tag) {
    case 0x45 : // list0
        break;

    case 0xd0 : // list32
    case 0xd1 : // map32
    case 0xb0 : // vbin32
        pre_consume += 3;
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
        pre_consume += 1;
        consume |= (int) next_octet(&test_cursor, &test_buffer);
        if (!test_cursor) return 0;
        break;
    }

    location->length = pre_consume + consume;
    if (consume)
        advance(&test_cursor, &test_buffer, consume);

    *cursor = test_cursor;
    *buffer = test_buffer;
    return 1;
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

    case DX_FIELD_REPLY_TO:
        while (1) {
            if (content->field_reply_to.parsed)
                return &content->field_reply_to;

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
            result = traverse_field(&cursor, &buffer, 0); // to
            if (!result) return 0;
            result = traverse_field(&cursor, &buffer, 0); // subject
            if (!result) return 0;
            result = traverse_field(&cursor, &buffer, &content->field_reply_to); // reply_to
            if (!result) return 0;
        }
        break;

    case DX_FIELD_BODY:
        if (content->section_body.parsed)
            return &content->section_body;
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
    msg->content->lock        = sys_mutex();
    msg->content->ref_count   = 1;
    msg->content->parse_depth = DX_DEPTH_NONE;

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


static int dx_check_field_LH(dx_message_content_t *content,
                             dx_message_depth_t    depth,
                             const unsigned char  *long_pattern,
                             const unsigned char  *short_pattern,
                             const unsigned char  *expected_tags,
                             dx_field_location_t  *location,
                             int                   more)
{
#define LONG  10
#define SHORT 3
    if (depth > content->parse_depth) {
        if (0 == dx_check_and_advance(&content->parse_buffer, &content->parse_cursor, long_pattern,  LONG,  expected_tags, location))
            return 0;
        if (0 == dx_check_and_advance(&content->parse_buffer, &content->parse_cursor, short_pattern, SHORT, expected_tags, location))
            return 0;
        if (!more)
            content->parse_depth = depth;
    }
    return 1;
}


static int dx_message_check_LH(dx_message_content_t *content, dx_message_depth_t depth)
{
    static const unsigned char * const MSG_HDR_LONG                 = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x70";
    static const unsigned char * const MSG_HDR_SHORT                = (unsigned char*) "\x00\x53\x70";
    static const unsigned char * const DELIVERY_ANNOTATION_LONG     = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x71";
    static const unsigned char * const DELIVERY_ANNOTATION_SHORT    = (unsigned char*) "\x00\x53\x71";
    static const unsigned char * const MESSAGE_ANNOTATION_LONG      = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x72";
    static const unsigned char * const MESSAGE_ANNOTATION_SHORT     = (unsigned char*) "\x00\x53\x72";
    static const unsigned char * const PROPERTIES_LONG              = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x73";
    static const unsigned char * const PROPERTIES_SHORT             = (unsigned char*) "\x00\x53\x73";
    static const unsigned char * const APPLICATION_PROPERTIES_LONG  = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x74";
    static const unsigned char * const APPLICATION_PROPERTIES_SHORT = (unsigned char*) "\x00\x53\x74";
    static const unsigned char * const BODY_DATA_LONG               = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x75";
    static const unsigned char * const BODY_DATA_SHORT              = (unsigned char*) "\x00\x53\x75";
    static const unsigned char * const BODY_SEQUENCE_LONG           = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x76";
    static const unsigned char * const BODY_SEQUENCE_SHORT          = (unsigned char*) "\x00\x53\x76";
    static const unsigned char * const BODY_VALUE_LONG              = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x77";
    static const unsigned char * const BODY_VALUE_SHORT             = (unsigned char*) "\x00\x53\x77";
    static const unsigned char * const FOOTER_LONG                  = (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x78";
    static const unsigned char * const FOOTER_SHORT                 = (unsigned char*) "\x00\x53\x78";
    static const unsigned char * const TAGS_LIST                    = (unsigned char*) "\x45\xc0\xd0";
    static const unsigned char * const TAGS_MAP                     = (unsigned char*) "\xc1\xd1";
    static const unsigned char * const TAGS_BINARY                  = (unsigned char*) "\xa0\xb0";
    static const unsigned char * const TAGS_ANY                     = (unsigned char*) "\x45\xc0\xd0\xc1\xd1\xa0\xb0";

    dx_buffer_t *buffer  = DEQ_HEAD(content->buffers);

    if (!buffer)
        return 0; // Invalid - No data in the message

    if (depth <= content->parse_depth)
        return 1; // We've already parsed at least this deep

    if (content->parse_buffer == 0) {
        content->parse_buffer = buffer;
        content->parse_cursor = dx_buffer_base(content->parse_buffer);
    }

    if (depth == DX_DEPTH_NONE)
        return 1;

    //
    // MESSAGE HEADER
    //
    if (0 == dx_check_field_LH(content, DX_DEPTH_HEADER,
                               MSG_HDR_LONG, MSG_HDR_SHORT, TAGS_LIST, &content->section_message_header, 0))
        return 0;
    if (depth == DX_DEPTH_HEADER)
        return 1;

    //
    // DELIVERY ANNOTATION
    //
    if (0 == dx_check_field_LH(content, DX_DEPTH_DELIVERY_ANNOTATIONS,
                               DELIVERY_ANNOTATION_LONG, DELIVERY_ANNOTATION_SHORT, TAGS_MAP, &content->section_delivery_annotation, 0))
        return 0;
    if (depth == DX_DEPTH_DELIVERY_ANNOTATIONS)
        return 1;

    //
    // MESSAGE ANNOTATION
    //
    if (0 == dx_check_field_LH(content, DX_DEPTH_MESSAGE_ANNOTATIONS,
                               MESSAGE_ANNOTATION_LONG, MESSAGE_ANNOTATION_SHORT, TAGS_MAP, &content->section_message_annotation, 0))
        return 0;
    if (depth == DX_DEPTH_MESSAGE_ANNOTATIONS)
        return 1;

    //
    // PROPERTIES
    //
    if (0 == dx_check_field_LH(content, DX_DEPTH_PROPERTIES,
                               PROPERTIES_LONG, PROPERTIES_SHORT, TAGS_LIST, &content->section_message_properties, 0))
        return 0;
    if (depth == DX_DEPTH_PROPERTIES)
        return 1;

    //
    // APPLICATION PROPERTIES
    //
    if (0 == dx_check_field_LH(content, DX_DEPTH_APPLICATION_PROPERTIES,
                               APPLICATION_PROPERTIES_LONG, APPLICATION_PROPERTIES_SHORT, TAGS_MAP, &content->section_application_properties, 0))
        return 0;
    if (depth == DX_DEPTH_APPLICATION_PROPERTIES)
        return 1;

    //
    // BODY
    // Note that this function expects a limited set of types in a VALUE section.  This is
    // not a problem for messages passing through Dispatch because through-only messages won't
    // be parsed to BODY-depth.
    //
    if (0 == dx_check_field_LH(content, DX_DEPTH_BODY,
                               BODY_DATA_LONG, BODY_DATA_SHORT, TAGS_BINARY, &content->section_body, 1))
        return 0;
    if (0 == dx_check_field_LH(content, DX_DEPTH_BODY,
                               BODY_SEQUENCE_LONG, BODY_SEQUENCE_SHORT, TAGS_LIST, &content->section_body, 1))
        return 0;
    if (0 == dx_check_field_LH(content, DX_DEPTH_BODY,
                               BODY_VALUE_LONG, BODY_VALUE_SHORT, TAGS_ANY, &content->section_body, 0))
        return 0;
    if (depth == DX_DEPTH_BODY)
        return 1;

    //
    // FOOTER
    //
    if (0 == dx_check_field_LH(content, DX_DEPTH_ALL,
                               FOOTER_LONG, FOOTER_SHORT, TAGS_MAP, &content->section_footer, 0))
        return 0;

    return 1;
}


int dx_message_check(dx_message_t *in_msg, dx_message_depth_t depth)
{
    dx_message_pvt_t     *msg     = (dx_message_pvt_t*) in_msg;
    dx_message_content_t *content = msg->content;
    int                   result;

    sys_mutex_lock(content->lock);
    result = dx_message_check_LH(content, depth);
    sys_mutex_unlock(content->lock);

    return result;
}


dx_field_iterator_t *dx_message_field_iterator(dx_message_t *msg, dx_message_field_t field)
{
    dx_field_location_t *loc = dx_message_field_location(msg, field);
    if (!loc)
        return 0;

    return dx_field_iterator_buffer(loc->buffer, loc->offset, loc->length, ITER_VIEW_ALL);
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
    dx_composed_field_t  *field   = dx_compose(DX_PERFORMATIVE_HEADER, 0);
    dx_message_content_t *content = MSG_CONTENT(msg);

    dx_compose_start_list(field);
    dx_compose_insert_bool(field, 0);     // durable
    //dx_compose_insert_null(field);        // priority
    //dx_compose_insert_null(field);        // ttl
    //dx_compose_insert_boolean(field, 0);  // first-acquirer
    //dx_compose_insert_uint(field, 0);     // delivery-count
    dx_compose_end_list(field);

    field = dx_compose(DX_PERFORMATIVE_PROPERTIES, field);
    dx_compose_start_list(field);
    dx_compose_insert_null(field);          // message-id
    dx_compose_insert_null(field);          // user-id
    dx_compose_insert_string(field, to);    // to
    //dx_compose_insert_null(field);          // subject
    //dx_compose_insert_null(field);          // reply-to
    //dx_compose_insert_null(field);          // correlation-id
    //dx_compose_insert_null(field);          // content-type
    //dx_compose_insert_null(field);          // content-encoding
    //dx_compose_insert_timestamp(field, 0);  // absolute-expiry-time
    //dx_compose_insert_timestamp(field, 0);  // creation-time
    //dx_compose_insert_null(field);          // group-id
    //dx_compose_insert_uint(field, 0);       // group-sequence
    //dx_compose_insert_null(field);          // reply-to-group-id
    dx_compose_end_list(field);

    if (buffers) {
        field = dx_compose(DX_PERFORMATIVE_BODY_DATA, field);
        dx_compose_insert_binary_buffers(field, buffers);
    }

    dx_buffer_list_t *field_buffers = dx_compose_buffers(field);
    content->buffers = *field_buffers;
    DEQ_INIT(*field_buffers); // Zero out the linkage to the now moved buffers.

    dx_compose_free(field);
}


void dx_message_compose_2(dx_message_t *msg, dx_composed_field_t *field)
{
    dx_message_content_t *content       = MSG_CONTENT(msg);
    dx_buffer_list_t     *field_buffers = dx_compose_buffers(field);

    content->buffers = *field_buffers;
    DEQ_INIT(*field_buffers); // Zero out the linkage to the now moved buffers.
}

