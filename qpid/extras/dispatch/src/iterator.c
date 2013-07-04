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

#include <qpid/dispatch/iterator.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/log.h>
#include "message_private.h"
#include <stdio.h>
#include <string.h>

//static const char *log_module = "FIELD";

typedef enum {
    MODE_TO_END,
    MODE_TO_SLASH
} parse_mode_t;

typedef struct {
    dx_buffer_t   *buffer;
    unsigned char *cursor;
    int            length;
} pointer_t;

struct dx_field_iterator_t {
    pointer_t           start_pointer;
    pointer_t           view_start_pointer;
    pointer_t           pointer;
    dx_iterator_view_t  view;
    parse_mode_t        mode;
    unsigned char       prefix;
    int                 at_prefix;
    int                 view_prefix;
};

ALLOC_DECLARE(dx_field_iterator_t);
ALLOC_DEFINE(dx_field_iterator_t);


typedef enum {
    STATE_START,
    STATE_SLASH_LEFT,
    STATE_SKIPPING_TO_NEXT_SLASH,
    STATE_SCANNING,
    STATE_COLON,
    STATE_COLON_SLASH,
    STATE_AT_NODE_ID
} state_t;


static char *my_area    = "";
static char *my_router  = "";


static void parse_address_view(dx_field_iterator_t *iter)
{
    //
    // This function starts with an iterator view that is identical to
    // ITER_VIEW_NO_HOST.  We will now further refine the view in order
    // to aid the router in looking up addresses.
    //

    if (dx_field_iterator_prefix(iter, "_")) {
        if (dx_field_iterator_prefix(iter, "local/")) {
            iter->prefix      = 'L';
            iter->at_prefix   = 1;
            iter->view_prefix = 1;
            return;
        }

        if (dx_field_iterator_prefix(iter, "topo/")) {
            if (dx_field_iterator_prefix(iter, "all/") || dx_field_iterator_prefix(iter, my_area)) {
                if (dx_field_iterator_prefix(iter, "all/") || dx_field_iterator_prefix(iter, my_router)) {
                    iter->prefix      = 'L';
                    iter->at_prefix   = 1;
                    iter->view_prefix = 1;
                    return;
                }

                iter->prefix      = 'R';
                iter->at_prefix   = 1;
                iter->view_prefix = 1;
                iter->mode        = MODE_TO_SLASH;
                return;
            }

            iter->prefix      = 'A';
            iter->at_prefix   = 1;
            iter->view_prefix = 1;
            iter->mode        = MODE_TO_SLASH;
            return;
        }
    }

    iter->prefix      = 'M';
    iter->at_prefix   = 1;
    iter->view_prefix = 1;
}


static void view_initialize(dx_field_iterator_t *iter)
{
    //
    // The default behavior is for the view to *not* have a prefix.
    // We'll add one if it's needed later.
    //
    iter->at_prefix   = 0;
    iter->view_prefix = 0;
    iter->mode        = MODE_TO_END;

    if (iter->view == ITER_VIEW_ALL)
        return;

    //
    // Advance to the node-id.
    //
    state_t        state = STATE_START;
    unsigned int   octet;
    pointer_t      save_pointer = {0,0,0};

    while (!dx_field_iterator_end(iter) && state != STATE_AT_NODE_ID) {
        octet = dx_field_iterator_octet(iter);
        switch (state) {
        case STATE_START :
            if (octet == '/')
                state = STATE_SLASH_LEFT;
            else
                state = STATE_SCANNING;
            break;

        case STATE_SLASH_LEFT :
            if (octet == '/')
                state = STATE_SKIPPING_TO_NEXT_SLASH;
            else
                state = STATE_AT_NODE_ID;
            break;

        case STATE_SKIPPING_TO_NEXT_SLASH :
            if (octet == '/')
                state = STATE_AT_NODE_ID;
            break;

        case STATE_SCANNING :
            if (octet == ':')
                state = STATE_COLON;
            break;

        case STATE_COLON :
            if (octet == '/') {
                state = STATE_COLON_SLASH;
                save_pointer = iter->pointer;
            } else
                state = STATE_SCANNING;
            break;

        case STATE_COLON_SLASH :
            if (octet == '/')
                state = STATE_SKIPPING_TO_NEXT_SLASH;
            else {
                state = STATE_AT_NODE_ID;
                iter->pointer = save_pointer;
            }
            break;

        case STATE_AT_NODE_ID :
            break;
        }
    }

    if (state != STATE_AT_NODE_ID) {
        //
        // The address string was relative, not absolute.  The node-id
        // is at the beginning of the string.
        //
        iter->pointer = iter->start_pointer;
    }

    //
    // Cursor is now on the first octet of the node-id
    //
    if (iter->view == ITER_VIEW_NODE_ID) {
        iter->mode = MODE_TO_SLASH;
        return;
    }

    if (iter->view == ITER_VIEW_NO_HOST) {
        iter->mode = MODE_TO_END;
        return;
    }

    if (iter->view == ITER_VIEW_ADDRESS_HASH) {
        iter->mode = MODE_TO_END;
        parse_address_view(iter);
        return;
    }

    if (iter->view == ITER_VIEW_NODE_SPECIFIC) {
        iter->mode = MODE_TO_END;
        while (!dx_field_iterator_end(iter)) {
            octet = dx_field_iterator_octet(iter);
            if (octet == '/')
                break;
        }
        return;
    }
}


void dx_field_iterator_set_address(const char *area, const char *router)
{
    my_area = (char*) malloc(strlen(area) + 2);
    strcpy(my_area, area);
    strcat(my_area, "/");

    my_router = (char*) malloc(strlen(router) + 2);
    strcpy(my_router, router);
    strcat(my_router, "/");
}


dx_field_iterator_t* dx_field_iterator_string(const char *text, dx_iterator_view_t view)
{
    dx_field_iterator_t *iter = new_dx_field_iterator_t();
    if (!iter)
        return 0;

    iter->start_pointer.buffer = 0;
    iter->start_pointer.cursor = (unsigned char*) text;
    iter->start_pointer.length = strlen(text);

    dx_field_iterator_reset_view(iter, view);

    return iter;
}


dx_field_iterator_t* dx_field_iterator_binary(const char *text, int length, dx_iterator_view_t view)
{
    dx_field_iterator_t *iter = new_dx_field_iterator_t();
    if (!iter)
        return 0;

    iter->start_pointer.buffer = 0;
    iter->start_pointer.cursor = (unsigned char*) text;
    iter->start_pointer.length = length;

    dx_field_iterator_reset_view(iter, view);

    return iter;
}


dx_field_iterator_t *dx_field_iterator_buffer(dx_buffer_t *buffer, int offset, int length, dx_iterator_view_t view)
{
    dx_field_iterator_t *iter = new_dx_field_iterator_t();
    if (!iter)
        return 0;

    iter->start_pointer.buffer = buffer;
    iter->start_pointer.cursor = dx_buffer_base(buffer) + offset;
    iter->start_pointer.length = length;

    dx_field_iterator_reset_view(iter, view);

    return iter;
}


void dx_field_iterator_free(dx_field_iterator_t *iter)
{
    free_dx_field_iterator_t(iter);
}


void dx_field_iterator_reset(dx_field_iterator_t *iter)
{
    iter->pointer   = iter->view_start_pointer;
    iter->at_prefix = iter->view_prefix;
}


void dx_field_iterator_reset_view(dx_field_iterator_t *iter, dx_iterator_view_t  view)
{
    iter->pointer = iter->start_pointer;
    iter->view    = view;

    view_initialize(iter);

    iter->view_start_pointer = iter->pointer;
}


unsigned char dx_field_iterator_octet(dx_field_iterator_t *iter)
{
    if (iter->at_prefix) {
        iter->at_prefix = 0;
        return iter->prefix;
    }

    if (iter->pointer.length == 0)
        return (unsigned char) 0;

    unsigned char result = *(iter->pointer.cursor);

    iter->pointer.cursor++;
    iter->pointer.length--;

    if (iter->pointer.length > 0) {
        if (iter->pointer.buffer) {
            if (iter->pointer.cursor - dx_buffer_base(iter->pointer.buffer) == dx_buffer_size(iter->pointer.buffer)) {
                iter->pointer.buffer = iter->pointer.buffer->next;
                if (iter->pointer.buffer == 0)
                    iter->pointer.length = 0;
                iter->pointer.cursor = dx_buffer_base(iter->pointer.buffer);
            }
        }
    }

    if (iter->pointer.length && iter->mode == MODE_TO_SLASH && *(iter->pointer.cursor) == '/')
        iter->pointer.length = 0;

    return result;
}


int dx_field_iterator_end(dx_field_iterator_t *iter)
{
    return iter->pointer.length == 0;
}


dx_field_iterator_t *dx_field_iterator_sub(dx_field_iterator_t *iter, uint32_t length)
{
    dx_field_iterator_t *sub = new_dx_field_iterator_t();
    if (!sub)
        return 0;

    sub->start_pointer        = iter->pointer;
    sub->start_pointer.length = length;
    sub->view_start_pointer   = sub->start_pointer;
    sub->pointer              = sub->start_pointer;
    sub->view                 = iter->view;
    sub->mode                 = iter->mode;
    sub->at_prefix            = 0;
    sub->view_prefix          = 0;

    return sub;
}


void dx_field_iterator_advance(dx_field_iterator_t *iter, uint32_t length)
{
    // TODO - Make this more efficient.
    for (uint8_t idx = 0; idx < length; idx++)
        dx_field_iterator_octet(iter);
}


uint32_t dx_field_iterator_remaining(dx_field_iterator_t *iter)
{
    return iter->pointer.length;
}


int dx_field_iterator_equal(dx_field_iterator_t *iter, const unsigned char *string)
{
    dx_field_iterator_reset(iter);
    while (!dx_field_iterator_end(iter) && *string) {
        if (*string != dx_field_iterator_octet(iter))
            return 0;
        string++;
    }

    return (dx_field_iterator_end(iter) && (*string == 0));
}


int dx_field_iterator_prefix(dx_field_iterator_t *iter, const char *prefix)
{
    pointer_t      save_pointer = iter->pointer;
    unsigned char *c            = (unsigned char*) prefix;

    while(*c) {
        if (*c != dx_field_iterator_octet(iter))
            break;
        c++;
    }

    if (*c) {
        iter->pointer = save_pointer;
        return 0;
    }

    return 1;
}


unsigned char *dx_field_iterator_copy(dx_field_iterator_t *iter)
{
    int            length = 0;
    int            idx    = 0;
    unsigned char *copy;

    dx_field_iterator_reset(iter);
    while (!dx_field_iterator_end(iter)) {
        dx_field_iterator_octet(iter);
        length++;
    }

    dx_field_iterator_reset(iter);
    copy = (unsigned char*) malloc(length + 1);
    while (!dx_field_iterator_end(iter))
        copy[idx++] = dx_field_iterator_octet(iter);
    copy[idx] = '\0';

    return copy;
}


dx_iovec_t *dx_field_iterator_iovec(const dx_field_iterator_t *iter)
{
    assert(!iter->view_prefix); // Not supported for views with a prefix

    //
    // Count the number of buffers this field straddles
    //
    pointer_t    pointer   = iter->view_start_pointer;
    int          bufcnt    = 1;
    dx_buffer_t *buf       = pointer.buffer;
    size_t       bufsize   = dx_buffer_size(buf) - (pointer.cursor - dx_buffer_base(pointer.buffer));
    ssize_t      remaining = pointer.length - bufsize;

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
    buf        = pointer.buffer;
    bufsize    = dx_buffer_size(buf) - (pointer.cursor - dx_buffer_base(pointer.buffer));
    void *base = pointer.cursor;
    remaining  = pointer.length;

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


