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

#include <proton/engine.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/iterator.h>
#include <qpid/dispatch/buffer.h>
#include <qpid/dispatch/iovec.h>

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

//
// Functions for allocation
//
dx_message_t *dx_allocate_message(void);
void dx_free_message(dx_message_t *qm);
dx_message_t *dx_message_copy(dx_message_t *qm);
int dx_message_persistent(dx_message_t *qm);
int dx_message_in_memory(dx_message_t *qm);

void dx_message_set_out_delivery(dx_message_t *msg, pn_delivery_t *delivery);
pn_delivery_t *dx_message_out_delivery(dx_message_t *msg);
void dx_message_set_in_delivery(dx_message_t *msg, pn_delivery_t *delivery);
pn_delivery_t *dx_message_in_delivery(dx_message_t *msg);

//
// Functions for received messages
//
dx_message_t *dx_message_receive(pn_delivery_t *delivery);
void dx_message_send(dx_message_t *msg, pn_link_t *link);

int dx_message_check(dx_message_t *msg, dx_message_depth_t depth);
dx_field_iterator_t *dx_message_field_iterator(dx_message_t *msg, dx_message_field_t field);
dx_iovec_t *dx_message_field_iovec(dx_message_t *msg, dx_message_field_t field);

pn_delivery_t *dx_message_inbound_delivery(dx_message_t *qm);

//
// Functions for composed messages
//

// Convenience Functions
void dx_message_compose_1(dx_message_t *msg, const char *to, dx_buffer_list_t *buffers);
void dx_message_copy_header(dx_message_t *msg); // Copy received header into send-header (prior to adding annotations)
void dx_message_copy_message_annotations(dx_message_t *msg);

// Raw Functions
void dx_message_begin_header(dx_message_t *msg);
void dx_message_end_header(dx_message_t *msg);

void dx_message_begin_delivery_annotations(dx_message_t *msg);
void dx_message_end_delivery_annotations(dx_message_t *msg);

void dx_message_begin_message_annotations(dx_message_t *msg);
void dx_message_end_message_annotations(dx_message_t *msg);

void dx_message_begin_message_properties(dx_message_t *msg);
void dx_message_end_message_properties(dx_message_t *msg);

void dx_message_begin_application_properties(dx_message_t *msg);
void dx_message_end_application_properties(dx_message_t *msg);

void dx_message_append_body_data(dx_message_t *msg, dx_buffer_list_t *buffers);

void dx_message_begin_body_sequence(dx_message_t *msg);
void dx_message_end_body_sequence(dx_message_t *msg);

void dx_message_begin_footer(dx_message_t *msg);
void dx_message_end_footer(dx_message_t *msg);

void dx_message_insert_null(dx_message_t *msg);
void dx_message_insert_boolean(dx_message_t *msg, int value);
void dx_message_insert_ubyte(dx_message_t *msg, uint8_t value);
void dx_message_insert_uint(dx_message_t *msg, uint32_t value);
void dx_message_insert_ulong(dx_message_t *msg, uint64_t value);
void dx_message_insert_binary(dx_message_t *msg, const uint8_t *start, size_t len);
void dx_message_insert_string(dx_message_t *msg, const char *start);
void dx_message_insert_uuid(dx_message_t *msg, const uint8_t *value);
void dx_message_insert_symbol(dx_message_t *msg, const char *start, size_t len);
void dx_message_insert_timestamp(dx_message_t *msg, uint64_t value);
void dx_message_begin_list(dx_message_t* msg);
void dx_message_end_list(dx_message_t* msg);
void dx_message_begin_map(dx_message_t* msg);
void dx_message_end_map(dx_message_t* msg);

#endif
