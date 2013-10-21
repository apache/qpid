#ifndef __dispatch_router_h__
#define __dispatch_router_h__ 1
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

#include <qpid/dispatch/dispatch.h>
#include <qpid/dispatch/message.h>
#include <qpid/dispatch/iterator.h>
#include <stdbool.h>

typedef struct dx_address_t dx_address_t;


typedef void (*dx_router_message_cb)(void *context, dx_message_t *msg, int link_id);

const char *dx_router_id(const dx_dispatch_t *dx);

dx_address_t *dx_router_register_address(dx_dispatch_t        *dx,
                                         const char           *address,
                                         dx_router_message_cb  handler,
                                         void                 *context);

void dx_router_unregister_address(dx_address_t *address);


void dx_router_send(dx_dispatch_t       *dx,
                    dx_field_iterator_t *address,
                    dx_message_t        *msg);

void dx_router_send2(dx_dispatch_t *dx,
                     const char    *address,
                     dx_message_t  *msg);

void dx_router_build_node_list(dx_dispatch_t *dx, dx_composed_field_t *field);

#endif
