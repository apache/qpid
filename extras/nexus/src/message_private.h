#ifndef __message_private_h__
#define __message_private_h__ 1
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
#include <qpid/nexus/alloc.h>
#include <qpid/nexus/threading.h>

/**
 * Architecture of the message module:
 *
 *     +--------------+            +----------------------+
 *     |              |            |                      |
 *     | nx_message_t |----------->| nx_message_content_t |
 *     |              |     +----->|                      |
 *     +--------------+     |      +----------------------+
 *                          |                |
 *     +--------------+     |                |    +-------------+   +-------------+   +-------------+
 *     |              |     |                +--->| nx_buffer_t |-->| nx_buffer_t |-->| nx_buffer_t |--/
 *     | nx_message_t |-----+                     +-------------+   +-------------+   +-------------+
 *     |              |
 *     +--------------+
 *
 * The message module provides chained-fixed-sized-buffer storage of message content with multiple
 * references.  If a message is received and is to be queued for multiple destinations, there is only
 * one copy of the message content in memory but multiple lightweight references to the content.
 *
 */

typedef struct {
    nx_buffer_t *buffer;  // Buffer that contains the first octet of the field, null if the field is not present
    size_t       offset;  // Offset in the buffer to the first octet
    size_t       length;  // Length of the field or zero if unneeded
    int          parsed;  // non-zero iff the buffer chain has been parsed to find this field
} nx_field_location_t;


// TODO - consider using pointers to nx_field_location_t below to save memory
// TODO - we need a second buffer list for modified annotations and header
//        There are three message scenarios:
//            1) Received message is held and forwarded unmodified - single buffer list
//            2) Received message is held and modified before forwarding - two buffer lists
//            3) Message is composed internally - single buffer list

typedef struct {
    sys_mutex_t         *lock;
    uint32_t             ref_count;                       // The number of qmessages referencing this
    nx_buffer_list_t     buffers;                         // The buffer chain containing the message
    pn_delivery_t       *in_delivery;                     // The delivery on which the message arrived
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
} nx_message_content_t;

typedef struct {
    DEQ_LINKS(nx_message_t);                              // Deq linkage that overlays the nx_message_t
    nx_message_content_t *content;
    pn_delivery_t        *out_delivery;
} nx_message_pvt_t;

ALLOC_DECLARE(nx_message_t);
ALLOC_DECLARE(nx_message_content_t);

#define MSG_CONTENT(m) (((nx_message_pvt_t*) m)->content)

#endif
