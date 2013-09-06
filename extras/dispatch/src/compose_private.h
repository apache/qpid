#ifndef __compose_private_h__
#define __compose_private_h__ 1
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

#include <qpid/dispatch/compose.h>
#include "message_private.h"

dx_buffer_list_t *dx_compose_buffers(dx_composed_field_t *field);

typedef struct dx_composite_t {
    DEQ_LINKS(struct dx_composite_t);
    int                 isMap;
    uint32_t            count;
    uint32_t            length;
    dx_field_location_t length_location;
    dx_field_location_t count_location;
} dx_composite_t;

ALLOC_DECLARE(dx_composite_t);
DEQ_DECLARE(dx_composite_t, dx_field_stack_t);


struct dx_composed_field_t {
    dx_buffer_list_t buffers;
    dx_field_stack_t fieldStack;
};

ALLOC_DECLARE(dx_composed_field_t);

#endif
