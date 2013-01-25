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
#include <qpid/nexus/iterator.h>

typedef struct nx_message_t nx_message_t;
typedef struct nx_buffer_t  nx_buffer_t;

DEQ_DECLARE(nx_buffer_t, nx_buffer_list_t);
DEQ_DECLARE(nx_message_t, nx_message_list_t);

typedef struct {
    nx_buffer_t *buffer;  // Buffer that contains the first octet of the field, null if the field is not present
    size_t       offset;  // Offset in the buffer to the first octet
    size_t       length;  // Length of the field or zero if unneeded
    int          parsed;  // non-zero iff the buffer chain has been parsed to find this field
} nx_field_location_t;


// TODO - consider using pointers to nx_field_location_t below to save memory
struct nx_message_t {
    DEQ_LINKS(nx_message_t);
    nx_buffer_list_t     buffers;                         // The buffer chain containing the message
    pn_delivery_t       *in_delivery;                     // The delivery on which the message arrived
    pn_delivery_t       *out_delivery;                    // The delivery on which the message was last sent
    nx_field_location_t  section_message_header;          // The message header list
    nx_field_location_t  section_delivery_annotation;     // The delivery annotation map
    nx_field_location_t  section_message_annotation;      // The message annotation map
    nx_field_location_t  section_message_properties;      // The message properties list
    nx_field_location_t  section_application_properties;  // The application properties list
    nx_field_location_t  section_body;                    // The message body: Data
    nx_field_location_t  section_footer;                  // The footer
    nx_field_location_t  field_user_id;                   // The string value of the user-id
    nx_field_location_t  field_to;                        // The string value of the to field
    nx_field_location_t  body;                            // The body of the message
    nx_field_location_t  compose_length;
    nx_field_location_t  compose_count;
    uint32_t             length;
    uint32_t             count;
};

struct nx_buffer_t {
    DEQ_LINKS(nx_buffer_t);
    unsigned int size;
};

typedef struct {
    size_t        buffer_size;
    unsigned long buffer_preallocation_count;
    unsigned long buffer_rebalancing_batch_count;
    unsigned long buffer_local_storage_max;
    unsigned long buffer_free_list_max;
    unsigned long message_allocation_batch_count;
    unsigned long message_rebalancing_batch_count;
    unsigned long message_local_storage_max;
} nx_allocator_config_t;

const nx_allocator_config_t *nx_allocator_default_config(void);

void nx_allocator_initialize(const nx_allocator_config_t *config);
void nx_allocator_finalize(void);

//
// Functions for per-thread allocators.
//
nx_message_t *nx_allocate_message(void);
nx_buffer_t  *nx_allocate_buffer(void);
void          nx_free_message(nx_message_t *msg);
void          nx_free_buffer(nx_buffer_t *buf);


typedef enum {
    NX_DEPTH_NONE,
    NX_DEPTH_HEADER,
    NX_DEPTH_DELIVERY_ANNOTATIONS,
    NX_DEPTH_MESSAGE_ANNOTATIONS,
    NX_DEPTH_MESSAGE_PROPERTIES,     // Needed for 'user-id' and 'to'
    NX_DEPTH_APPLICATION_PROPERTIES,
    NX_DEPTH_BODY,
    NX_DEPTH_ALL
} nx_message_depth_t;

//
// Functions for received messages
//
nx_message_t *nx_message_receive(pn_delivery_t *delivery);
int nx_message_check(nx_message_t *msg, nx_message_depth_t depth);
nx_field_iterator_t *nx_message_field_to(nx_message_t *msg);
nx_field_iterator_t *nx_message_body(nx_message_t *msg);

//
// Functions for composed messages
//

// Convenience Functions
void nx_message_compose_1(nx_message_t *msg, const char *to, nx_buffer_t *buf_chain);

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

void nx_message_append_body_data(nx_message_t *msg, nx_buffer_t *buf_chain);

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

//
// Functions for buffers
//
unsigned char *nx_buffer_base(nx_buffer_t *buf);      // Pointer to the first octet in the buffer
unsigned char *nx_buffer_cursor(nx_buffer_t *buf);    // Pointer to the first free octet in the buffer
size_t         nx_buffer_capacity(nx_buffer_t *buf);  // Size of free space in the buffer in octets
size_t         nx_buffer_size(nx_buffer_t *buf);      // Number of octets in the buffer
void           nx_buffer_insert(nx_buffer_t *buf, size_t len);  // Notify the buffer that 'len' octets were written at cursor

#endif
