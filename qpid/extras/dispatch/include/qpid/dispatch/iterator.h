#ifndef __dispatch_iterator_h__
#define __dispatch_iterator_h__ 1
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

#include <qpid/dispatch/buffer.h>

/**
 * The field iterator is used to access fields within a buffer chain.
 * It shields the user from the fact that the field may be split across
 * one or more physical buffers.
 */
typedef struct dx_field_iterator_t dx_field_iterator_t;

/**
 * Iterator views allow the code traversing the field to see a transformed
 * view of the raw field.
 *
 * ITER_VIEW_ALL - No transformation of the raw field data
 *
 * ITER_VIEW_NO_HOST - Remove the scheme and host fields from the view
 *
 *    amqp://host.domain.com:port/node-id/node/specific
 *                                ^^^^^^^^^^^^^^^^^^^^^
 *    node-id/node/specific
 *    ^^^^^^^^^^^^^^^^^^^^^
 *
 * ITER_VIEW_NODE_ID - Isolate the node identifier from an address
 *
 *    amqp://host.domain.com:port/node-id/node/specific
 *                                ^^^^^^^
 *    node-id/node/specific
 *    ^^^^^^^
 *
 * ITER_VIEW_NODE_SPECIFIC - Isolate node-specific text from an address
 *
 *    amqp://host.domain.com:port/node-id/node/specific
 *                                        ^^^^^^^^^^^^^
 *    node-id/node/specific
 *            ^^^^^^^^^^^^^
 */
typedef enum {
    ITER_VIEW_ALL,
    ITER_VIEW_NO_HOST,
    ITER_VIEW_NODE_ID,
    ITER_VIEW_NODE_SPECIFIC
} dx_iterator_view_t;

/**
 * Create an iterator from a null-terminated string.
 *
 * The "text" string must stay intact for the whole life of the iterator.  The iterator
 * does not copy the string, it references it.
 */
dx_field_iterator_t* dx_field_iterator_string(const char         *text,
                                              dx_iterator_view_t  view);

/**
 * Create an iterator from a field in a buffer chain
 */
dx_field_iterator_t *dx_field_iterator_buffer(dx_buffer_t        *buffer,
                                              int                 offset,
                                              int                 length,
                                              dx_iterator_view_t  view);

/**
 * Free an iterator
 */
void dx_field_iterator_free(dx_field_iterator_t *iter);

/**
 * Reset the iterator to the first octet and set a new view
 */
void dx_field_iterator_reset(dx_field_iterator_t *iter,
                             dx_iterator_view_t   view);

/**
 * Return the current octet in the iterator's view and step to the next.
 */
unsigned char dx_field_iterator_octet(dx_field_iterator_t *iter);

/**
 * Return true iff the iterator has no more octets in the view.
 */
int dx_field_iterator_end(dx_field_iterator_t *iter);

/**
 * Compare an input string to the iterator's view.  Return true iff they are equal.
 */
int dx_field_iterator_equal(dx_field_iterator_t *iter, unsigned char *string);

/**
 * Return a copy of the iterator's view.
 */
unsigned char *dx_field_iterator_copy(dx_field_iterator_t *iter);

#endif
