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

#include <qpid/nexus/message.h>
#include <qpid/nexus/ctools.h>
#include <qpid/nexus/threading.h>
#include <string.h>
#include <stdio.h>


//
// Per-Thread allocator
//
typedef struct nx_allocator_t {
    nx_message_list_t  message_free_list;
    nx_buffer_list_t   buffer_free_list;
} nx_allocator_t;

//
// Global allocator (protected by a global lock)
//
typedef struct {
    nx_message_list_t  message_free_list;
    nx_buffer_list_t   buffer_free_list;
    sys_mutex_t       *lock;
} nx_global_allocator_t;

static nx_global_allocator_t  global;
static nx_allocator_config_t  default_config;
static const nx_allocator_config_t *config;


static nx_allocator_t *nx_get_allocator(void)
{
    static __thread nx_allocator_t *alloc = 0;

    if (!alloc) {
        alloc = NEW(nx_allocator_t);

        if (!alloc)
            return 0;

        DEQ_INIT(alloc->message_free_list);
        DEQ_INIT(alloc->buffer_free_list);
    }

    return alloc;
}


static void advance(unsigned char **cursor, nx_buffer_t **buffer, int consume)
{
    unsigned char *local_cursor = *cursor;
    nx_buffer_t   *local_buffer = *buffer;

    int remaining = nx_buffer_size(local_buffer) - (local_cursor - nx_buffer_base(local_buffer));
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
            local_cursor = nx_buffer_base(local_buffer);
            remaining = nx_buffer_size(local_buffer) - (local_cursor - nx_buffer_base(local_buffer));
        }
    }

    *cursor = local_cursor;
    *buffer = local_buffer;
}


static unsigned char next_octet(unsigned char **cursor, nx_buffer_t **buffer)
{
    unsigned char result = **cursor;
    advance(cursor, buffer, 1);
    return result;
}


static int traverse_field(unsigned char **cursor, nx_buffer_t **buffer, nx_field_location_t *field)
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
        field->offset = *cursor - nx_buffer_base(*buffer);
        field->length = consume;
        field->parsed = 1;
    }

    advance(cursor, buffer, consume);
    return 1;
}


static int start_list(unsigned char **cursor, nx_buffer_t **buffer)
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
static int nx_check_and_advance(nx_buffer_t         **buffer,
                                unsigned char       **cursor,
                                unsigned char        *pattern,
                                int                   pattern_length,
                                unsigned char        *expected_tags,
                                nx_field_location_t  *location)
{
    nx_buffer_t   *test_buffer = *buffer;
    unsigned char *test_cursor = *cursor;

    if (!test_cursor)
        return 1; // no match

    unsigned char *end_of_buffer = nx_buffer_base(test_buffer) + nx_buffer_size(test_buffer);
    int idx = 0;

    while (idx < pattern_length && *test_cursor == pattern[idx]) {
        idx++;
        test_cursor++;
        if (test_cursor == end_of_buffer) {
            test_buffer = test_buffer->next;
            if (test_buffer == 0)
                return 1; // Pattern didn't match
            test_cursor = nx_buffer_base(test_buffer);
            end_of_buffer = test_cursor + nx_buffer_size(test_buffer);
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
    location->offset = test_cursor - nx_buffer_base(test_buffer);
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


static void nx_insert(nx_message_t *msg, const uint8_t *seq, size_t len)
{
    nx_buffer_t *buf = DEQ_TAIL(msg->buffers);

    while (len > 0) {
        if (buf == 0 || nx_buffer_capacity(buf) == 0) {
            buf = nx_allocate_buffer();
            if (buf == 0)
                return;
            DEQ_INSERT_TAIL(msg->buffers, buf);
        }

        size_t to_copy = nx_buffer_capacity(buf);
        if (to_copy > len)
            to_copy = len;
        memcpy(nx_buffer_cursor(buf), seq, to_copy);
        nx_buffer_insert(buf, to_copy);
        len -= to_copy;
        seq += to_copy;
        msg->length += to_copy;
    }
}


static void nx_insert_8(nx_message_t *msg, uint8_t value)
{
    nx_insert(msg, &value, 1);
}


static void nx_insert_32(nx_message_t *msg, uint32_t value)
{
    uint8_t buf[4];
    buf[0] = (uint8_t) ((value & 0xFF000000) >> 24);
    buf[1] = (uint8_t) ((value & 0x00FF0000) >> 16);
    buf[2] = (uint8_t) ((value & 0x0000FF00) >> 8);
    buf[3] = (uint8_t)  (value & 0x000000FF);
    nx_insert(msg, buf, 4);
}


static void nx_insert_64(nx_message_t *msg, uint64_t value)
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
    nx_insert(msg, buf, 8);
}


static void nx_overwrite(nx_buffer_t **buf, size_t *cursor, uint8_t value)
{
    while (*buf) {
        if (*cursor >= nx_buffer_size(*buf)) {
            *buf = (*buf)->next;
            *cursor = 0;
        } else {
            nx_buffer_base(*buf)[*cursor] = value;
            (*cursor)++;
            return;
        }
    }
}


static void nx_overwrite_32(nx_field_location_t *field, uint32_t value)
{
    nx_buffer_t *buf    = field->buffer;
    size_t       cursor = field->offset;

    nx_overwrite(&buf, &cursor, (uint8_t) ((value & 0xFF000000) >> 24));
    nx_overwrite(&buf, &cursor, (uint8_t) ((value & 0x00FF0000) >> 24));
    nx_overwrite(&buf, &cursor, (uint8_t) ((value & 0x0000FF00) >> 24));
    nx_overwrite(&buf, &cursor, (uint8_t)  (value & 0x000000FF));
}


static void nx_start_list_performative(nx_message_t *msg, uint8_t code)
{
    //
    // Insert the short-form performative tag
    //
    nx_insert(msg, (const uint8_t*) "\x00\x53", 2);
    nx_insert_8(msg, code);

    //
    // Open the list with a list32 tag
    //
    nx_insert_8(msg, 0xd0);

    //
    // Mark the current location to later overwrite the length
    //
    msg->compose_length.buffer = DEQ_TAIL(msg->buffers);
    msg->compose_length.offset = nx_buffer_size(msg->compose_length.buffer);
    msg->compose_length.length = 4;
    msg->compose_length.parsed = 1;

    nx_insert(msg, (const uint8_t*) "\x00\x00\x00\x00", 4);

    //
    // Mark the current location to later overwrite the count
    //
    msg->compose_count.buffer = DEQ_TAIL(msg->buffers);
    msg->compose_count.offset = nx_buffer_size(msg->compose_count.buffer);
    msg->compose_count.length = 4;
    msg->compose_count.parsed = 1;

    nx_insert(msg, (const uint8_t*) "\x00\x00\x00\x00", 4);

    msg->length = 4; // Include the length of the count field
    msg->count = 0;
}


static void nx_end_list(nx_message_t *msg)
{
    nx_overwrite_32(&msg->compose_length, msg->length);
    nx_overwrite_32(&msg->compose_count,  msg->count);
}


const nx_allocator_config_t *nx_allocator_default_config(void)
{
    default_config.buffer_size                     = 1024;
    default_config.buffer_preallocation_count      = 512;
    default_config.buffer_rebalancing_batch_count  = 16;
    default_config.buffer_local_storage_max        = 64;
    default_config.buffer_free_list_max            = 1000000;
    default_config.message_allocation_batch_count  = 256;
    default_config.message_rebalancing_batch_count = 64;
    default_config.message_local_storage_max       = 256;

    return &default_config;
}


void nx_allocator_initialize(const nx_allocator_config_t *c)
{
    config = c;

    // Initialize the fields in the global structure.
    DEQ_INIT(global.message_free_list);
    DEQ_INIT(global.buffer_free_list);
    global.lock = sys_mutex();

    // Pre-allocate buffers according to the configuration
    int          i;
    nx_buffer_t *buf;

    for (i = 0; i < config->buffer_preallocation_count; i++) {
        buf = (nx_buffer_t*) malloc (sizeof(nx_buffer_t) + config->buffer_size);
        DEQ_ITEM_INIT(buf);
        DEQ_INSERT_TAIL(global.buffer_free_list, buf);
    }
}


void nx_allocator_finalize(void)
{
    // TODO - Free buffers and messages
}


nx_message_t *nx_allocate_message(void)
{
    nx_allocator_t *alloc = nx_get_allocator();
    nx_message_t   *msg;
    int             i;

    if (DEQ_SIZE(alloc->message_free_list) == 0) {
        //
        // The local free list is empty, rebalance a batch of objects from the global
        // free list.
        //
        sys_mutex_lock(global.lock);
        if (DEQ_SIZE(global.message_free_list) >= config->message_rebalancing_batch_count) {
            for (i = 0; i < config->message_rebalancing_batch_count; i++) {
                msg = DEQ_HEAD(global.message_free_list);
                DEQ_REMOVE_HEAD(global.message_free_list);
                DEQ_INSERT_TAIL(alloc->message_free_list, msg);
            }
        }
        sys_mutex_unlock(global.lock);
    }

    if (DEQ_SIZE(alloc->message_free_list) == 0) {
        //
        // The local free list is still empty.  This means there were not enough objects on the
        // global free list to make up a batch.  Allocate new objects from the heap and store
        // them in the local free list.
        //
        nx_message_t *batch = NEW_ARRAY(nx_message_t, config->message_allocation_batch_count);
        memset(batch, 0, sizeof(nx_message_t) * config->message_allocation_batch_count);
        for (i = 0; i < config->message_allocation_batch_count; i++) {
            DEQ_INSERT_TAIL(alloc->message_free_list, &batch[i]);
        }
    }

    //
    // If the local free list is still empty, we're out of memory.
    //
    if (DEQ_SIZE(alloc->message_free_list) == 0)
        return 0;

    msg = DEQ_HEAD(alloc->message_free_list);
    DEQ_REMOVE_HEAD(alloc->message_free_list);

    DEQ_INIT(msg->buffers);
    msg->in_delivery = NULL;
    msg->out_delivery = NULL;
    msg->section_message_header.buffer = 0;
    msg->section_message_header.parsed = 0;
    msg->section_delivery_annotation.buffer = 0;
    msg->section_delivery_annotation.parsed = 0;
    msg->section_message_annotation.buffer = 0;
    msg->section_message_annotation.parsed = 0;
    msg->section_message_properties.buffer = 0;
    msg->section_message_properties.parsed = 0;
    msg->section_application_properties.buffer = 0;
    msg->section_application_properties.parsed = 0;
    msg->section_body.buffer = 0;
    msg->section_body.parsed = 0;
    msg->section_footer.buffer = 0;
    msg->section_footer.parsed = 0;
    msg->field_user_id.buffer = 0;
    msg->field_user_id.parsed = 0;
    msg->field_to.buffer = 0;
    msg->field_to.parsed = 0;
    msg->body.buffer = 0;
    msg->body.parsed = 0;
    return msg;
}


nx_buffer_t *nx_allocate_buffer(void)
{
    nx_allocator_t *alloc = nx_get_allocator();
    nx_buffer_t    *buf;
    int             i;

    if (DEQ_SIZE(alloc->buffer_free_list) == 0) {
        sys_mutex_lock(global.lock);
        if (DEQ_SIZE(global.buffer_free_list) >= config->buffer_rebalancing_batch_count) {
            // Rebalance a batch of free descriptors to the local free list.
            for (i = 0; i < config->buffer_rebalancing_batch_count; i++) {
                buf = DEQ_HEAD(global.buffer_free_list);
                DEQ_REMOVE_HEAD(global.buffer_free_list);
                DEQ_INSERT_TAIL(alloc->buffer_free_list, buf);
            }
        }
        sys_mutex_unlock(global.lock);
    }

    if (DEQ_SIZE(alloc->buffer_free_list) == 0) {
        // Allocate a buffer from the heap
        buf = (nx_buffer_t*) malloc (sizeof(nx_buffer_t) + config->buffer_size);
        DEQ_ITEM_INIT(buf);
        DEQ_INSERT_TAIL(alloc->buffer_free_list, buf);
    }

    if (DEQ_SIZE(alloc->buffer_free_list) == 0)
        return 0;

    buf = DEQ_HEAD(alloc->buffer_free_list);
    DEQ_REMOVE_HEAD(alloc->buffer_free_list);

    buf->size = 0;

    return buf;
}


void nx_free_message(nx_message_t *msg)
{
    nx_allocator_t *alloc = nx_get_allocator();

    // Free any buffers in the message
    int          i;
    nx_buffer_t *buf = DEQ_HEAD(msg->buffers);
    while (buf) {
        DEQ_REMOVE_HEAD(msg->buffers);
        nx_free_buffer(buf);
        buf = DEQ_HEAD(msg->buffers);
    }

    DEQ_INSERT_TAIL(alloc->message_free_list, msg);
    if (DEQ_SIZE(alloc->message_free_list) > config->message_local_storage_max) {
        //
        // The local free list has exceeded the threshold for local storage.
        // Rebalance a batch of free objects to the global free list.
        //
        sys_mutex_lock(global.lock);
        for (i = 0; i < config->message_rebalancing_batch_count; i++) {
            msg = DEQ_HEAD(alloc->message_free_list);
            DEQ_REMOVE_HEAD(alloc->message_free_list);
            DEQ_INSERT_TAIL(global.message_free_list, msg);
        }
        sys_mutex_unlock(global.lock);
    }
}


void nx_free_buffer(nx_buffer_t *buf)
{
    nx_allocator_t *alloc = nx_get_allocator();
    int             i;

    DEQ_INSERT_TAIL(alloc->buffer_free_list, buf);
    if (DEQ_SIZE(alloc->buffer_free_list) > config->buffer_local_storage_max) {
        // Rebalance a batch of free descriptors to the global free list.
        sys_mutex_lock(global.lock);
        for (i = 0; i < config->buffer_rebalancing_batch_count; i++) {
            buf = DEQ_HEAD(alloc->buffer_free_list);
            DEQ_REMOVE_HEAD(alloc->buffer_free_list);
            DEQ_INSERT_TAIL(global.buffer_free_list, buf);
        }
        sys_mutex_unlock(global.lock);
    }
}


nx_message_t *nx_message_receive(pn_delivery_t *delivery)
{
    pn_link_t    *link = pn_delivery_link(delivery);
    nx_message_t *msg  = (nx_message_t*) pn_delivery_get_context(delivery);
    ssize_t       rc;
    nx_buffer_t  *buf;

    //
    // If there is no message associated with the delivery, this is the first time
    // we've received anything on this delivery.  Allocate a message descriptor and 
    // link it and the delivery together.
    //
    if (!msg) {
        msg = nx_allocate_message();
        pn_delivery_set_context(delivery, (void*) msg);

        //
        // Record the incoming delivery only if it is not settled.  If it is 
        // settled, there's no need to propagate disposition back to the sender.
        //
        if (!pn_delivery_settled(delivery))
            msg->in_delivery = delivery;
    }

    //
    // Get a reference to the tail buffer on the message.  This is the buffer into which
    // we will store incoming message data.  If there is no buffer in the message, allocate
    // an empty one and add it to the message.
    //
    buf = DEQ_TAIL(msg->buffers);
    if (!buf) {
        buf = nx_allocate_buffer();
        DEQ_INSERT_TAIL(msg->buffers, buf);
    }

    while (1) {
        //
        // Try to receive enough data to fill the remaining space in the tail buffer.
        //
        rc = pn_link_recv(link, (char*) nx_buffer_cursor(buf), nx_buffer_capacity(buf));

        //
        // If we receive PN_EOS, we have come to the end of the message.
        //
        if (rc == PN_EOS) {
            //
            // If the last buffer in the list is empty, remove it and free it.  This
            // will only happen if the size of the message content is an exact multiple
            // of the buffer size.
            //
            if (nx_buffer_size(buf) == 0) {
                DEQ_REMOVE_TAIL(msg->buffers);
                nx_free_buffer(buf);
            }
            return msg;
        }

        if (rc > 0) {
            //
            // We have received a positive number of bytes for the message.  Advance
            // the cursor in the buffer.
            //
            nx_buffer_insert(buf, rc);

            //
            // If the buffer is full, allocate a new empty buffer and append it to the
            // tail of the message's list.
            //
            if (nx_buffer_capacity(buf) == 0) {
                buf = nx_allocate_buffer();
                DEQ_INSERT_TAIL(msg->buffers, buf);
            }
        } else
            //
            // We received zero bytes, and no PN_EOS.  This means that we've received
            // all of the data available up to this point, but it does not constitute
            // the entire message.  We'll be back later to finish it up.
            //
            break;
    }

    return NULL;
}


int nx_message_check(nx_message_t *msg, nx_message_depth_t depth)
{

#define LONG  10
#define SHORT 3
#define MSG_HDR_LONG                  (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x70"
#define MSG_HDR_SHORT                 (unsigned char*) "\x00\x53\x70"
#define DELIVERY_ANNOTATION_LONG      (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x71"
#define DELIVERY_ANNOTATION_SHORT     (unsigned char*) "\x00\x53\x71"
#define MESSAGE_ANNOTATION_LONG       (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x72"
#define MESSAGE_ANNOTATION_SHORT      (unsigned char*) "\x00\x53\x72"
#define MESSAGE_PROPERTIES_LONG       (unsigned char*) "\x00\x80\x00\x00\x00\x00\x00\x00\x00\x73"
#define MESSAGE_PROPERTIES_SHORT      (unsigned char*) "\x00\x53\x73"
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

    nx_buffer_t   *buffer = DEQ_HEAD(msg->buffers);
    unsigned char *cursor;

    if (!buffer)
        return 0; // Invalid - No data in the message

    if (depth == NX_DEPTH_NONE)
        return 1;

    cursor = nx_buffer_base(buffer);

    //
    // MESSAGE HEADER
    //
    if (0 == nx_check_and_advance(&buffer, &cursor, MSG_HDR_LONG,  LONG,  TAGS_LIST, &msg->section_message_header))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, MSG_HDR_SHORT, SHORT, TAGS_LIST, &msg->section_message_header))
        return 0;

    if (depth == NX_DEPTH_HEADER)
        return 1;

    //
    // DELIVERY ANNOTATION
    //
    if (0 == nx_check_and_advance(&buffer, &cursor, DELIVERY_ANNOTATION_LONG,  LONG,  TAGS_MAP,  &msg->section_delivery_annotation))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, DELIVERY_ANNOTATION_SHORT, SHORT, TAGS_MAP,  &msg->section_delivery_annotation))
        return 0;

    if (depth == NX_DEPTH_DELIVERY_ANNOTATIONS)
        return 1;

    //
    // MESSAGE ANNOTATION
    //
    if (0 == nx_check_and_advance(&buffer, &cursor, MESSAGE_ANNOTATION_LONG,  LONG,  TAGS_MAP,  &msg->section_message_annotation))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, MESSAGE_ANNOTATION_SHORT, SHORT, TAGS_MAP,  &msg->section_message_annotation))
        return 0;

    if (depth == NX_DEPTH_MESSAGE_ANNOTATIONS)
        return 1;

    //
    // MESSAGE PROPERTIES
    //
    if (0 == nx_check_and_advance(&buffer, &cursor, MESSAGE_PROPERTIES_LONG,  LONG,  TAGS_LIST, &msg->section_message_properties))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, MESSAGE_PROPERTIES_SHORT, SHORT, TAGS_LIST, &msg->section_message_properties))
        return 0;

    if (depth == NX_DEPTH_MESSAGE_PROPERTIES)
        return 1;

    //
    // APPLICATION PROPERTIES
    //
    if (0 == nx_check_and_advance(&buffer, &cursor, APPLICATION_PROPERTIES_LONG,  LONG,  TAGS_MAP, &msg->section_application_properties))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, APPLICATION_PROPERTIES_SHORT, SHORT, TAGS_MAP, &msg->section_application_properties))
        return 0;

    if (depth == NX_DEPTH_APPLICATION_PROPERTIES)
        return 1;

    //
    // BODY  (Note that this function expects a single data section or a single AMQP sequence)
    //
    if (0 == nx_check_and_advance(&buffer, &cursor, BODY_DATA_LONG,      LONG,  TAGS_BINARY, &msg->section_body))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, BODY_DATA_SHORT,     SHORT, TAGS_BINARY, &msg->section_body))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, BODY_SEQUENCE_LONG,  LONG,  TAGS_LIST,   &msg->section_body))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, BODY_SEQUENCE_SHORT, SHORT, TAGS_LIST,   &msg->section_body))
        return 0;

    if (depth == NX_DEPTH_BODY)
        return 1;

    //
    // FOOTER
    //
    if (0 == nx_check_and_advance(&buffer, &cursor, FOOTER_LONG,  LONG,  TAGS_MAP, &msg->section_footer))
        return 0;
    if (0 == nx_check_and_advance(&buffer, &cursor, FOOTER_SHORT, SHORT, TAGS_MAP, &msg->section_footer))
        return 0;

    return 1;
}


nx_field_iterator_t *nx_message_field_to(nx_message_t *msg)
{
    while (1) {
        if (msg->field_to.parsed)
            return nx_field_iterator_buffer(msg->field_to.buffer, msg->field_to.offset, msg->field_to.length, ITER_VIEW_ALL);

        if (msg->section_message_properties.parsed == 0)
            break;

        nx_buffer_t   *buffer = msg->section_message_properties.buffer;
        unsigned char *cursor = nx_buffer_base(buffer) + msg->section_message_properties.offset;

        int count = start_list(&cursor, &buffer);
        int result;

        if (count < 3)
            break;

        result = traverse_field(&cursor, &buffer, 0); // message_id
        if (!result) return 0;
        result = traverse_field(&cursor, &buffer, 0); // user_id
        if (!result) return 0;
        result = traverse_field(&cursor, &buffer, &msg->field_to); // to
        if (!result) return 0;
    }

    return 0;
}


nx_field_iterator_t *nx_message_body(nx_message_t *msg)
{
    while (1) {
        if (msg->body.parsed)
            return nx_field_iterator_buffer(msg->body.buffer, msg->body.offset, msg->body.length, ITER_VIEW_ALL);

        if (msg->section_body.parsed == 0)
            break;

        nx_buffer_t   *buffer = msg->section_body.buffer;
        unsigned char *cursor = nx_buffer_base(buffer) + msg->section_body.offset;
        int result;

        result = traverse_field(&cursor, &buffer, &msg->body);
        if (!result) return 0;
    }

    return 0;
}


void nx_message_compose_1(nx_message_t *msg, const char *to, nx_buffer_t *buf_chain)
{
    nx_message_begin_header(msg);
    nx_message_insert_boolean(msg, 0);  // durable
    //nx_message_insert_null(msg);        // priority
    //nx_message_insert_null(msg);        // ttl
    //nx_message_insert_boolean(msg, 0);  // first-acquirer
    //nx_message_insert_uint(msg, 0);     // delivery-count
    nx_message_end_header(msg);

    nx_message_begin_message_properties(msg);
    nx_message_insert_null(msg);          // message-id
    nx_message_insert_null(msg);          // user-id
    nx_message_insert_string(msg, to);    // to
    //nx_message_insert_null(msg);          // subject
    //nx_message_insert_null(msg);          // reply-to
    //nx_message_insert_null(msg);          // correlation-id
    //nx_message_insert_null(msg);          // content-type
    //nx_message_insert_null(msg);          // content-encoding
    //nx_message_insert_timestamp(msg, 0);  // absolute-expiry-time
    //nx_message_insert_timestamp(msg, 0);  // creation-time
    //nx_message_insert_null(msg);          // group-id
    //nx_message_insert_uint(msg, 0);       // group-sequence
    //nx_message_insert_null(msg);          // reply-to-group-id
    nx_message_end_message_properties(msg);

    if (buf_chain)
        nx_message_append_body_data(msg, buf_chain);
}


void nx_message_begin_header(nx_message_t *msg)
{
    nx_start_list_performative(msg, 0x70);
}


void nx_message_end_header(nx_message_t *msg)
{
    nx_end_list(msg);
}


void nx_message_begin_delivery_annotations(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_end_delivery_annotations(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_begin_message_annotations(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_end_message_annotations(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_begin_message_properties(nx_message_t *msg)
{
    nx_start_list_performative(msg, 0x73);
}


void nx_message_end_message_properties(nx_message_t *msg)
{
    nx_end_list(msg);
}


void nx_message_begin_application_properties(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_end_application_properties(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_append_body_data(nx_message_t *msg, nx_buffer_t *buf_chain)
{
    uint32_t     len   = 0;
    nx_buffer_t *buf   = buf_chain;
    nx_buffer_t *last  = 0;
    size_t       count = 0;

    while (buf) {
        len += nx_buffer_size(buf);
        count++;
        last = buf;
        buf = DEQ_NEXT(buf);
    }

    nx_insert(msg, (const uint8_t*) "\x00\x53\x75", 3);
    if (len < 256) {
        nx_insert_8(msg, 0xa0);  // vbin8
        nx_insert_8(msg, (uint8_t) len);
    } else {
        nx_insert_8(msg, 0xb0);  // vbin32
        nx_insert_32(msg, len);
    }

    if (len > 0) {
        buf_chain->prev         = msg->buffers.tail;
        msg->buffers.tail->next = buf_chain;
        msg->buffers.tail       = last;
        msg->buffers.size      += count;
    }
}


void nx_message_begin_body_sequence(nx_message_t *msg)
{
}


void nx_message_end_body_sequence(nx_message_t *msg)
{
}


void nx_message_begin_footer(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_end_footer(nx_message_t *msg)
{
    assert(0); // Not Implemented
}


void nx_message_insert_null(nx_message_t *msg)
{
    nx_insert_8(msg, 0x40);
    msg->count++;
}


void nx_message_insert_boolean(nx_message_t *msg, int value)
{
    if (value)
        nx_insert(msg, (const uint8_t*) "\x56\x01", 2);
    else
        nx_insert(msg, (const uint8_t*) "\x56\x00", 2);
    msg->count++;
}


void nx_message_insert_ubyte(nx_message_t *msg, uint8_t value)
{
    nx_insert_8(msg, 0x50);
    nx_insert_8(msg, value);
    msg->count++;
}


void nx_message_insert_uint(nx_message_t *msg, uint32_t value)
{
    if (value == 0) {
        nx_insert_8(msg, 0x43);  // uint0
    } else if (value < 256) {
        nx_insert_8(msg, 0x52);  // smalluint
        nx_insert_8(msg, (uint8_t) value);
    } else {
        nx_insert_8(msg, 0x70);  // uint
        nx_insert_32(msg, value);
    }
    msg->count++;
}


void nx_message_insert_ulong(nx_message_t *msg, uint64_t value)
{
    if (value == 0) {
        nx_insert_8(msg, 0x44);  // ulong0
    } else if (value < 256) {
        nx_insert_8(msg, 0x53);  // smallulong
        nx_insert_8(msg, (uint8_t) value);
    } else {
        nx_insert_8(msg, 0x80);  // ulong
        nx_insert_64(msg, value);
    }
    msg->count++;
}


void nx_message_insert_binary(nx_message_t *msg, const uint8_t *start, size_t len)
{
    if (len < 256) {
        nx_insert_8(msg, 0xa0);  // vbin8
        nx_insert_8(msg, (uint8_t) len);
    } else {
        nx_insert_8(msg, 0xb0);  // vbin32
        nx_insert_32(msg, len);
    }
    nx_insert(msg, start, len);
    msg->count++;
}


void nx_message_insert_string(nx_message_t *msg, const char *start)
{
    uint32_t len = strlen(start);

    if (len < 256) {
        nx_insert_8(msg, 0xa1);  // str8-utf8
        nx_insert_8(msg, (uint8_t) len);
        nx_insert(msg, (const uint8_t*) start, len);
    } else {
        nx_insert_8(msg, 0xb1);  // str32-utf8
        nx_insert_32(msg, len);
        nx_insert(msg, (const uint8_t*) start, len);
    }
    msg->count++;
}


void nx_message_insert_uuid(nx_message_t *msg, const uint8_t *value)
{
    nx_insert_8(msg, 0x98);  // uuid
    nx_insert(msg, value, 16);
    msg->count++;
}


void nx_message_insert_symbol(nx_message_t *msg, const char *start, size_t len)
{
    if (len < 256) {
        nx_insert_8(msg, 0xa3);  // sym8
        nx_insert_8(msg, (uint8_t) len);
        nx_insert(msg, (const uint8_t*) start, len);
    } else {
        nx_insert_8(msg, 0xb3);  // sym32
        nx_insert_32(msg, len);
        nx_insert(msg, (const uint8_t*) start, len);
    }
    msg->count++;
}


void nx_message_insert_timestamp(nx_message_t *msg, uint64_t value)
{
    nx_insert_8(msg, 0x83);  // timestamp
    nx_insert_64(msg, value);
    msg->count++;
}


unsigned char *nx_buffer_base(nx_buffer_t *buf)
{
    return (unsigned char*) &buf[1];
}


unsigned char *nx_buffer_cursor(nx_buffer_t *buf)
{
    return ((unsigned char*) &buf[1]) + buf->size;
}


size_t nx_buffer_capacity(nx_buffer_t *buf)
{
    return config->buffer_size - buf->size;
}


size_t nx_buffer_size(nx_buffer_t *buf)
{
    return buf->size;
}


void nx_buffer_insert(nx_buffer_t *buf, size_t len)
{
    buf->size += len;
    assert(buf->size <= config->buffer_size);
}

