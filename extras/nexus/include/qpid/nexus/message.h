#ifndef __nexus_message_h__
#define __nexus_message_h__ 1
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
#include <qpid/nexus/ctools.h>
#include <qpid/nexus/alloc.h>
#include <qpid/nexus/iterator.h>
#include <qpid/nexus/buffer.h>
#include <qpid/nexus/iovec.h>

// Callback for status change (confirmed persistent, loaded-in-memory, etc.)

typedef struct nx_message_t nx_message_t;

DEQ_DECLARE(nx_message_t, nx_message_list_t);

struct nx_message_t {
    DEQ_LINKS(nx_message_t);
    // Private members not listed here.
};

typedef enum {
    NX_DEPTH_NONE,
    NX_DEPTH_HEADER,
    NX_DEPTH_DELIVERY_ANNOTATIONS,
    NX_DEPTH_MESSAGE_ANNOTATIONS,
    NX_DEPTH_PROPERTIES,
    NX_DEPTH_APPLICATION_PROPERTIES,
    NX_DEPTH_BODY,
    NX_DEPTH_ALL
} nx_message_depth_t;


typedef enum {
    //
    // Message Sections
    //
    NX_FIELD_HEADER,
    NX_FIELD_DELIVERY_ANNOTATION,
    NX_FIELD_MESSAGE_ANNOTATION,
    NX_FIELD_PROPERTIES,
    NX_FIELD_APPLICATION_PROPERTIES,
    NX_FIELD_BODY,
    NX_FIELD_FOOTER,

    //
    // Fields of the Header Section
    //
    NX_FIELD_DURABLE,
    NX_FIELD_PRIORITY,
    NX_FIELD_TTL,
    NX_FIELD_FIRST_ACQUIRER,
    NX_FIELD_DELIVERY_COUNT,

    //
    // Fields of the Properties Section
    //
    NX_FIELD_MESSAGE_ID,
    NX_FIELD_USER_ID,
    NX_FIELD_TO,
    NX_FIELD_SUBJECT,
    NX_FIELD_REPLY_TO,
    NX_FIELD_CORRELATION_ID,
    NX_FIELD_CONTENT_TYPE,
    NX_FIELD_CONTENT_ENCODING,
    NX_FIELD_ABSOLUTE_EXPIRY_TIME,
    NX_FIELD_CREATION_TIME,
    NX_FIELD_GROUP_ID,
    NX_FIELD_GROUP_SEQUENCE,
    NX_FIELD_REPLY_TO_GROUP_ID
} nx_message_field_t;

//
// Functions for allocation
//
nx_message_t *nx_allocate_message(void);
void nx_free_message(nx_message_t *qm);
nx_message_t *nx_message_copy(nx_message_t *qm);
int nx_message_persistent(nx_message_t *qm);
int nx_message_in_memory(nx_message_t *qm);

void nx_message_set_out_delivery(nx_message_t *msg, pn_delivery_t *delivery);
pn_delivery_t *nx_message_out_delivery(nx_message_t *msg);
void nx_message_set_in_delivery(nx_message_t *msg, pn_delivery_t *delivery);
pn_delivery_t *nx_message_in_delivery(nx_message_t *msg);

//
// Functions for received messages
//
nx_message_t *nx_message_receive(pn_delivery_t *delivery);
void nx_message_send(nx_message_t *msg, pn_link_t *link);

int nx_message_check(nx_message_t *msg, nx_message_depth_t depth);
nx_field_iterator_t *nx_message_field_iterator(nx_message_t *msg, nx_message_field_t field);
nx_iovec_t *nx_message_field_iovec(nx_message_t *msg, nx_message_field_t field);

pn_delivery_t *nx_message_inbound_delivery(nx_message_t *qm);

//
// Functions for composed messages
//

// Convenience Functions
void nx_message_compose_1(nx_message_t *msg, const char *to, nx_buffer_list_t *buffers);
void nx_message_copy_header(nx_message_t *msg); // Copy received header into send-header (prior to adding annotations)
void nx_message_copy_message_annotations(nx_message_t *msg);

// Raw Functions
void nx_message_begin_header(nx_message_t *msg);
void nx_message_end_header(nx_message_t *msg);

void nx_message_begin_delivery_annotations(nx_message_t *msg);
void nx_message_end_delivery_annotations(nx_message_t *msg);

void nx_message_begin_message_annotations(nx_message_t *msg);
void nx_message_end_message_annotations(nx_message_t *msg);

void nx_message_begin_message_properties(nx_message_t *msg);
void nx_message_end_message_properties(nx_message_t *msg);

void nx_message_begin_application_properties(nx_message_t *msg);
void nx_message_end_application_properties(nx_message_t *msg);

void nx_message_append_body_data(nx_message_t *msg, nx_buffer_list_t *buffers);

void nx_message_begin_body_sequence(nx_message_t *msg);
void nx_message_end_body_sequence(nx_message_t *msg);

void nx_message_begin_footer(nx_message_t *msg);
void nx_message_end_footer(nx_message_t *msg);

void nx_message_insert_null(nx_message_t *msg);
void nx_message_insert_boolean(nx_message_t *msg, int value);
void nx_message_insert_ubyte(nx_message_t *msg, uint8_t value);
void nx_message_insert_uint(nx_message_t *msg, uint32_t value);
void nx_message_insert_ulong(nx_message_t *msg, uint64_t value);
void nx_message_insert_binary(nx_message_t *msg, const uint8_t *start, size_t len);
void nx_message_insert_string(nx_message_t *msg, const char *start);
void nx_message_insert_uuid(nx_message_t *msg, const uint8_t *value);
void nx_message_insert_symbol(nx_message_t *msg, const char *start, size_t len);
void nx_message_insert_timestamp(nx_message_t *msg, uint64_t value);
void nx_message_begin_list(nx_message_t* msg);
void nx_message_end_list(nx_message_t* msg);
void nx_message_begin_map(nx_message_t* msg);
void nx_message_end_map(nx_message_t* msg);

#endif
