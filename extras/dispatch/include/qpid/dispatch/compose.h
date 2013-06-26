#ifndef __dispatch_compose_h__
#define __dispatch_compose_h__ 1
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

typedef struct dx_composed_field_t dx_composed_field_t;

#define DX_PERFORMATIVE_HEADER                  0x70
#define DX_PERFORMATIVE_DELIVERY_ANNOTATIONS    0x71
#define DX_PERFORMATIVE_MESSAGE_ANNOTATIONS     0x72  
#define DX_PERFORMATIVE_PROPERTIES              0x73
#define DX_PERFORMATIVE_APPLICATION_PROPERTIES  0x74
#define DX_PERFORMATIVE_BODY_DATA               0x75
#define DX_PERFORMATIVE_BODY_AMQP_SEQUENCE      0x76
#define DX_PERFORMATIVE_BODY_AMQP_VALUE         0x77
#define DX_PERFORMATIVE_FOOTER                  0x78

/**
 * Begin composing a new field for a message.  The new field can be standalone or
 * appended onto an existing field.
 *
 * @param performative The performative for the message section being composed.
 * @param extend An existing field onto which to append the new field or NULL to
 *        create a standalone field.
 * @return A pointer to the newly created field.
 */
dx_composed_field_t *dx_compose(uint8_t performative, dx_composed_field_t *extend);

/**
 * Free the resources associated with a composed field.
 *
 * @param A field pointer returned by dx_compose.
 */
void dx_compose_free(dx_composed_field_t *field);

/**
 * Begin to compose the elements of a list in the field.  This is called before inserting
 * the first list element.
 *
 * @param field A field created by dx_compose.
 */
void dx_compose_start_list(dx_composed_field_t *field);

/**
 * Complete the composition of a list in the field.  This is called after the last
 * list element has been inserted.
 *
 * @param field A field created by dx_compose.
 */
void dx_compose_end_list(dx_composed_field_t *field);

/**
 * Begin to compose the elements os a map in the field.  This is called before
 * inserting the first element-pair into the map.
 *
 * @param field A field created by dx_compose.
 */
void dx_compose_start_map(dx_composed_field_t *field);

/**
 * Complete the composition of a map in the field.  This is called after the last
 * element-pair has been inserted.
 *
 * @param field A field created by dx_compose.
 */
void dx_compose_end_map(dx_composed_field_t *field);

/**
 * Insert a null element into the field.
 *
 * @param field A field created by dx_compose.
 */
void dx_compose_insert_null(dx_composed_field_t *field);

/**
 * Insert a boolean value into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The boolean (zero or non-zero) value to insert.
 */
void dx_compose_insert_bool(dx_composed_field_t *field, int value);

/**
 * Insert an unsigned integer (up to 32 bits) into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The unsigned integer value to be inserted.
 */
void dx_compose_insert_uint(dx_composed_field_t *field, uint32_t value);

/**
 * Insert a long (64-bit) unsigned value into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The unsigned integer value to be inserted.
 */
void dx_compose_insert_ulong(dx_composed_field_t *field, uint64_t value);

/**
 * Insert a signed integer (up to 32 bits) into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The integer value to be inserted.
 */
void dx_compose_insert_int(dx_composed_field_t *field, int32_t value);

/**
 * Insert a long signed integer (64 bits) into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The integer value to be inserted.
 */
void dx_compose_insert_long(dx_composed_field_t *field, int64_t value);

/**
 * Insert a timestamp into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The timestamp value to be inserted.
 */
void dx_compose_insert_timestamp(dx_composed_field_t *field, uint64_t value);

/**
 * Insert a UUID into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The pointer to the first octet in the UUID to be inserted.
 */
void dx_compose_insert_uuid(dx_composed_field_t *field, const char *value);

/**
 * Insert a binary blob into the field.
 *
 * @param field A field created by dx_compose.
 * @param value The pointer to the first octet to be inserted.
 * @param len The length, in octets, of the binary blob.
 */
void dx_compose_insert_binary(dx_composed_field_t *field, const uint8_t *value, uint32_t len);

/**
 * Insert a binary blob from a list of buffers.
 *
 * @param field A field created by dx_compose.
 * @param buffers A pointer to a list of buffers to be inserted as binary data.  Note that
 *        the buffer list will be left empty by this function.
 */
void dx_compose_insert_binary_buffers(dx_composed_field_t *field, dx_buffer_list_t *buffers);

/**
 * Insert a null-terminated utf8-encoded string into the field.
 *
 * @param field A field created by dx_compose.
 * @param value A pointer to a null-terminated string.
 */
void dx_compose_insert_string(dx_composed_field_t *field, const char *value);

/**
 * Insert a symbol into the field.
 *
 * @param field A field created by dx_compose.
 * @param value A pointer to a null-terminated ASCII string.
 */
void dx_compose_insert_symbol(dx_composed_field_t *field, const char *value);

#endif

