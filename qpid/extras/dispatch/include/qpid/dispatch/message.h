#ifndef __dispatch_message_h__
#define __dispatch_message_h__ 1
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
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/iterator.h>
#include <qpid/dispatch/buffer.h>
#include <qpid/dispatch/compose.h>
#include <qpid/dispatch/parse.h>
#include <qpid/dispatch/container.h>

// Callback for status change (confirmed persistent, loaded-in-memory, etc.)

typedef struct dx_message_t dx_message_t;

DEQ_DECLARE(dx_message_t, dx_message_list_t);

struct dx_message_t {
    DEQ_LINKS(dx_message_t);
    // Private members not listed here.
};

typedef enum {
    DX_DEPTH_NONE,
    DX_DEPTH_HEADER,
    DX_DEPTH_DELIVERY_ANNOTATIONS,
    DX_DEPTH_MESSAGE_ANNOTATIONS,
    DX_DEPTH_PROPERTIES,
    DX_DEPTH_APPLICATION_PROPERTIES,
    DX_DEPTH_BODY,
    DX_DEPTH_ALL
} dx_message_depth_t;


typedef enum {
    //
    // Message Sections
    //
    DX_FIELD_HEADER,
    DX_FIELD_DELIVERY_ANNOTATION,
    DX_FIELD_MESSAGE_ANNOTATION,
    DX_FIELD_PROPERTIES,
    DX_FIELD_APPLICATION_PROPERTIES,
    DX_FIELD_BODY,
    DX_FIELD_FOOTER,

    //
    // Fields of the Header Section
    //
    DX_FIELD_DURABLE,
    DX_FIELD_PRIORITY,
    DX_FIELD_TTL,
    DX_FIELD_FIRST_ACQUIRER,
    DX_FIELD_DELIVERY_COUNT,

    //
    // Fields of the Properties Section
    //
    DX_FIELD_MESSAGE_ID,
    DX_FIELD_USER_ID,
    DX_FIELD_TO,
    DX_FIELD_SUBJECT,
    DX_FIELD_REPLY_TO,
    DX_FIELD_CORRELATION_ID,
    DX_FIELD_CONTENT_TYPE,
    DX_FIELD_CONTENT_ENCODING,
    DX_FIELD_ABSOLUTE_EXPIRY_TIME,
    DX_FIELD_CREATION_TIME,
    DX_FIELD_GROUP_ID,
    DX_FIELD_GROUP_SEQUENCE,
    DX_FIELD_REPLY_TO_GROUP_ID
} dx_message_field_t;


/**
 * Allocate a new message.
 *
 * @return A pointer to a dx_message_t that is the sole reference to a newly allocated
 *         message.
 */
dx_message_t *dx_message(void);

/**
 * Free a message reference.  If this is the last reference to the message, free the
 * message as well.
 *
 * @param msg A pointer to a dx_message_t that is no longer needed.
 */
void dx_message_free(dx_message_t *msg);

/**
 * Make a new reference to an existing message.
 *
 * @param msg A pointer to a dx_message_t referencing a message.
 * @return A new pointer to the same referenced message.
 */
dx_message_t *dx_message_copy(dx_message_t *msg);

/**
 * Retrieve the delivery annotations from a message.
 *
 * IMPORTANT: The pointer returned by this function remains owned by the message.
 *            The caller MUST NOT free the parsed field.
 *
 * @param msg Pointer to a received message.
 * @return Pointer to the parsed field for the delivery annotations.  If the message doesn't
 *         have delivery annotations, the return value shall be NULL.
 */
dx_parsed_field_t *dx_message_delivery_annotations(dx_message_t *msg);

/**
 * Set the delivery annotations for the message.  If the message already has delivery annotations,
 * they will be overwritten/replaced by the new field.
 *
 * @param msg Pointer to a receiver message.
 * @param da Pointer to a composed field representing the new delivery annotations of the message.
 *           If null, the message will not have a delivery annotations field.
 *           IMPORTANT: The message will not take ownership of the composed field.  The
 *                      caller is responsible for freeing it after this call.  Since the contents
 *                      are copied into the message, it is safe to free the composed field
 *                      any time after the call to this function.
 */
void dx_message_set_delivery_annotations(dx_message_t *msg, dx_composed_field_t *da);

/**
 * Receive message data via a delivery.  This function may be called more than once on the same
 * delivery if the message spans multiple frames.  Once a complete message has been received, this
 * function shall return the message.
 *
 * @param delivery An incoming delivery from a link
 * @return A pointer to the complete message or 0 if the message is not yet complete.
 */
dx_message_t *dx_message_receive(dx_delivery_t *delivery);

/**
 * Send the message outbound on an outgoing link.
 *
 * @param msg A pointer to a message to be sent.
 * @param link The outgoing link on which to send the message.
 */
void dx_message_send(dx_message_t *msg, dx_link_t *link);

/**
 * Check that the message is well-formed up to a certain depth.  Any part of the message that is
 * beyond the specified depth is not checked for validity.
 */
int dx_message_check(dx_message_t *msg, dx_message_depth_t depth);

/**
 * Return an iterator for the requested message field.  If the field is not in the message,
 * return NULL.
 *
 * @param msg A pointer to a message.
 * @param field The field to be returned via iterator.
 * @return A field iterator that spans the requested field.
 */
dx_field_iterator_t *dx_message_field_iterator(dx_message_t *msg, dx_message_field_t field);

ssize_t dx_message_field_length(dx_message_t *msg, dx_message_field_t field);
ssize_t dx_message_field_copy(dx_message_t *msg, dx_message_field_t field, void *buffer);

//
// Functions for composed messages
//

// Convenience Functions
void dx_message_compose_1(dx_message_t *msg, const char *to, dx_buffer_list_t *buffers);
void dx_message_compose_2(dx_message_t *msg, dx_composed_field_t *content);

#endif
