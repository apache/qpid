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

#include <qpid/nexus/iterator.h>
#include <qpid/nexus/message.h>
#include <qpid/nexus/ctools.h>
#include <qpid/nexus/alloc.h>
#include <stdio.h>
#include <string.h>

typedef enum {
MODE_TO_END,
MODE_TO_SLASH
} parse_mode_t;

struct nx_field_iterator_t {
    nx_buffer_t        *start_buffer;
    unsigned char      *start_cursor;
    int                 start_length;
    nx_buffer_t        *buffer;
    unsigned char      *cursor;
    int                 length;
    nx_iterator_view_t  view;
    parse_mode_t        mode;
};


ALLOC_DECLARE(nx_field_iterator_t);
ALLOC_DEFINE(nx_field_iterator_t);


typedef enum {
STATE_START,
STATE_SLASH_LEFT,
STATE_SKIPPING_TO_NEXT_SLASH,
STATE_SCANNING,
STATE_COLON,
STATE_COLON_SLASH,
STATE_AT_NODE_ID
} state_t;


static void view_initialize(nx_field_iterator_t *iter)
{
    if (iter->view == ITER_VIEW_ALL) {
        iter->mode = MODE_TO_END;
        return;
    }

    //
    // Advance to the node-id.
    //
    state_t      state = STATE_START;
    unsigned int octet;
    while (!nx_field_iterator_end(iter) && state != STATE_AT_NODE_ID) {
        octet = nx_field_iterator_octet(iter);
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
            if (octet == '/')
                state = STATE_COLON_SLASH;
            else
                state = STATE_SCANNING;
            break;

        case STATE_COLON_SLASH :
            if (octet == '/')
                state = STATE_SKIPPING_TO_NEXT_SLASH;
            else
                state = STATE_SCANNING;
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
        iter->buffer = iter->start_buffer;
        iter->cursor = iter->start_cursor;
        iter->length = iter->start_length;
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

    if (iter->view == ITER_VIEW_NODE_SPECIFIC) {
        iter->mode = MODE_TO_END;
        while (!nx_field_iterator_end(iter)) {
            octet = nx_field_iterator_octet(iter);
            if (octet == '/')
                break;
        }
        return;
    }
}


nx_field_iterator_t* nx_field_iterator_string(const char *text, nx_iterator_view_t view)
{
    nx_field_iterator_t *iter = new_nx_field_iterator_t();
    if (!iter)
        return 0;

    iter->start_buffer = 0;
    iter->start_cursor = (unsigned char*) text;
    iter->start_length = strlen(text);

    nx_field_iterator_reset(iter, view);

    return iter;
}


nx_field_iterator_t *nx_field_iterator_buffer(nx_buffer_t *buffer, int offset, int length, nx_iterator_view_t view)
{
    nx_field_iterator_t *iter = new_nx_field_iterator_t();
    if (!iter)
        return 0;

    iter->start_buffer = buffer;
    iter->start_cursor = nx_buffer_base(buffer) + offset;
    iter->start_length = length;

    nx_field_iterator_reset(iter, view);

    return iter;
}


void nx_field_iterator_free(nx_field_iterator_t *iter)
{
    free_nx_field_iterator_t(iter);
}


void nx_field_iterator_reset(nx_field_iterator_t *iter, nx_iterator_view_t  view)
{
    iter->buffer = iter->start_buffer;
    iter->cursor = iter->start_cursor;
    iter->length = iter->start_length;
    iter->view   = view;

    view_initialize(iter);
}


unsigned char nx_field_iterator_octet(nx_field_iterator_t *iter)
{
    if (iter->length == 0)
        return (unsigned char) 0;

    unsigned char result = *(iter->cursor);

    iter->cursor++;
    iter->length--;

    if (iter->length > 0) {
        if (iter->buffer) {
            if (iter->cursor - nx_buffer_base(iter->buffer) == nx_buffer_size(iter->buffer)) {
                iter->buffer = iter->buffer->next;
                if (iter->buffer == 0)
                    iter->length = 0;
                iter->cursor = nx_buffer_base(iter->buffer);
            }
        }
    }

    if (iter->length && iter->mode == MODE_TO_SLASH && *(iter->cursor) == '/')
        iter->length = 0;

    return result;
}


int nx_field_iterator_end(nx_field_iterator_t *iter)
{
    return iter->length == 0;
}


int nx_field_iterator_equal(nx_field_iterator_t *iter, unsigned char *string)
{
    nx_field_iterator_reset(iter, iter->view);
    while (!nx_field_iterator_end(iter) && *string) {
        if (*string != nx_field_iterator_octet(iter))
            return 0;
        string++;
    }

    return (nx_field_iterator_end(iter) && (*string == 0));
}


unsigned char *nx_field_iterator_copy(nx_field_iterator_t *iter)
{
    int            length = 0;
    int            idx    = 0;
    unsigned char *copy;

    nx_field_iterator_reset(iter, iter->view);
    while (!nx_field_iterator_end(iter)) {
        nx_field_iterator_octet(iter);
        length++;
    }

    nx_field_iterator_reset(iter, iter->view);
    copy = (unsigned char*) malloc(length + 1);
    while (!nx_field_iterator_end(iter))
        copy[idx++] = nx_field_iterator_octet(iter);
    copy[idx] = '\0';

    return copy;
}

