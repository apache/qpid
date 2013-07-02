#ifndef __dispatch_parse_h__
#define __dispatch_parse_h__ 1
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
#include <qpid/dispatch/iterator.h>

typedef struct dx_parsed_field_t dx_parsed_field_t;

/**
 * Parse a field delimited by a field iterator.
 *
 * @param iter Field iterator for the field being parsed
 * @return A pointer to the newly created field.
 */
dx_parsed_field_t *dx_parse(dx_field_iterator_t *iter);

/**
 * Free the resources associated with a parsed field.
 *
 * @param A field pointer returned by dx_parse.
 */
void dx_parse_free(dx_parsed_field_t *field);

/**
 * Check to see if the field parse was successful (i.e. the field was
 * well-formed).
 *
 * @param field The field pointer returned by dx_parse.
 * @return true iff the field was well-formed and successfully parsed.
 */
int dx_parse_ok(dx_parsed_field_t *field);

/**
 * Return the text of the error describing the parse error if the field
 * is not well-formed.
 *
 * @param field The field pointer returned by dx_parse.
 * @return a null-terminated string describing the parse failure.
 */
const char *dx_parse_error(dx_parsed_field_t *field);

/**
 * Return the AMQP tag for the parsed (and well-formed) field.
 *
 * @param field The field pointer returned by dx_parse.
 * @return The tag (see amqp.h) that indicates the type of the field.
 */
uint8_t dx_parse_tag(dx_parsed_field_t *field);

/**
 * Return an iterator for the raw content of the field.  This is useful
 * only for scalar fields.  It is not appropriate for compound fields.
 * For compound fields, use the sub-field functions instead.
 *
 * The returned iterator describes the raw content of the field, and can be
 * used for comparison, indexing, or copying.
 *
 * IMPORTANT: The returned iterator is owned by the field and *must not* be
 * freed by the caller of this function.
 *
 * @param field The field pointer returned by dx_parse.
 * @return A field iterator that describes the field's raw content.
 */
dx_field_iterator_t *dx_parse_raw(dx_parsed_field_t *field);

/**
 * Return the raw content as an unsigned integer up to 32-bits.  This is
 * valid only for scalar fields of a fixed size of 4-octets or fewer.
 *
 * @param field The field pointer returned by dx_parse.
 * @return The raw content of the field cast as a uint32_t.
 */
uint32_t dx_parse_as_uint(dx_parsed_field_t *field);

/**
 * Return the raw content as an unsigned integer up to 64-bits.  This is
 * valid only for scalar fields of a fixed size of 8-octets or fewer.
 *
 * @param field The field pointer returned by dx_parse.
 * @return The raw content of the field cast as a uint64_t.
 */
uint64_t dx_parse_as_ulong(dx_parsed_field_t *field);

/**
 * Return the raw content as a signed integer up to 32-bits.  This is
 * valid only for scalar fields of a fixed size of 4-octets or fewer.
 *
 * @param field The field pointer returned by dx_parse.
 * @return The raw content of the field cast as an int32_t.
 */
int32_t dx_parse_as_int(dx_parsed_field_t *field);

/**
 * Return the raw content as a signed integer up to 64-bits.  This is
 * valid only for scalar fields of a fixed size of 8-octets or fewer.
 *
 * @param field The field pointer returned by dx_parse.
 * @return The raw content of the field cast as an int64_t.
 */
int64_t dx_parse_as_long(dx_parsed_field_t *field);

/**
 * Return the number of sub-field in a compound field.  If the field is
 * a list or array, this is the number of items in the list/array.  If
 * the field is a map, this is the number of key/value pairs in the map
 * (i.e. half the number of actual sub-field in the map).
 *
 * For scalar fields, this function will return zero.
 *
 * @param field The field pointer returned by dx_parse.
 * @return The number of sub-fields in the field.
 */
uint32_t dx_parse_sub_count(dx_parsed_field_t *field);

/**
 * Return a dx_parsed_field_t for the idx'th key in a map field.
 * If 'field' is not a map, or idx is equal-to or greater-than the number
 * of sub-fields in field, this function will return NULL.
 *
 * IMPORTANT: The pointer returned by this function remains owned by the
 * parent field.  It *must not* be freed by the caller.
 *
 * @param field The field pointer returned by dx_parse.
 * @param idx The index of the desired sub-field (in range 0..sub_count)
 * @return A pointer to the parsed sub-field
 */
dx_parsed_field_t *dx_parse_sub_key(dx_parsed_field_t *field, uint32_t idx);

/**
 * Return a dx_parsed_field_t for the idx'th value in a compound field.
 * If idx is equal-to or greater-than the number of sub-fields in field,
 * this function will return NULL.
 *
 * IMPORTANT: The pointer returned by this function remains owned by the
 * parent field.  It *must not* be freed by the caller.
 *
 * @param field The field pointer returned by dx_parse.
 * @param idx The index of the desired sub-field (in range 0..sub_count)
 * @return A pointer to the parsed sub-field
 */
dx_parsed_field_t *dx_parse_sub_value(dx_parsed_field_t *field, uint32_t idx);

/**
 * Convenience Function - Return true iff the field is a map.
 *
 * @param field The field pointer returned by dx_parse[_sub_{value,key}]
 * @return non-zero if the condition is mat.
 */
int dx_parse_is_map(dx_parsed_field_t *field);

/**
 * Convenience Function - Return true iff the field is a list.
 *
 * @param field The field pointer returned by dx_parse[_sub_{value,key}]
 * @return non-zero if the condition is mat.
 */
int dx_parse_is_list(dx_parsed_field_t *field);

/**
 * Convenience Function - Return true iff the field is a scalar type.
 *
 * @param field The field pointer returned by dx_parse[_sub_{value,key}]
 * @return non-zero if the condition is mat.
 */
int dx_parse_is_scalar(dx_parsed_field_t *field);

/**
 * Convenience Function - Return the value for a key in a map.
 *
 * @param field The field pointer returned by dx_parse[_sub_{value,key}]
 * @param key The key to search for in the map.
 * @return The value field corresponding to the key or NULL.
 */
dx_parsed_field_t *dx_parse_value_by_key(dx_parsed_field_t *field, const char *key);

#endif

