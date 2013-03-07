#ifndef __dispatch_container_h__
#define __dispatch_container_h__ 1
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
#include <qpid/dispatch/server.h>
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/ctools.h>

typedef struct dx_dispatch_t dx_dispatch_t;

typedef uint8_t dx_dist_mode_t;
#define DX_DIST_COPY 0x01
#define DX_DIST_MOVE 0x02
#define DX_DIST_BOTH 0x03

/**
 * Node Lifetime Policy (see AMQP 3.5.9)
 */
typedef enum {
    DX_LIFE_PERMANENT,
    DX_LIFE_DELETE_CLOSE,
    DX_LIFE_DELETE_NO_LINKS,
    DX_LIFE_DELETE_NO_MESSAGES,
    DX_LIFE_DELETE_NO_LINKS_MESSAGES
} dx_lifetime_policy_t;


/**
 * Link Direction
 */
typedef enum {
    DX_INCOMING,
    DX_OUTGOING
} dx_direction_t;


typedef struct dx_node_t dx_node_t;
typedef struct dx_link_t dx_link_t;

typedef void (*dx_container_delivery_handler_t)    (void *node_context, dx_link_t *link, pn_delivery_t *delivery);
typedef int  (*dx_container_link_handler_t)        (void *node_context, dx_link_t *link);
typedef int  (*dx_container_link_detach_handler_t) (void *node_context, dx_link_t *link, int closed);
typedef void (*dx_container_node_handler_t)        (void *type_context, dx_node_t *node);
typedef void (*dx_container_conn_handler_t)        (void *type_context, dx_connection_t *conn);

typedef struct {
    char *type_name;
    void *type_context;
    int   allow_dynamic_creation;

    //
    // Node-Instance Handlers
    //
    dx_container_delivery_handler_t     rx_handler;
    dx_container_delivery_handler_t     tx_handler;
    dx_container_delivery_handler_t     disp_handler;
    dx_container_link_handler_t         incoming_handler;
    dx_container_link_handler_t         outgoing_handler;
    dx_container_link_handler_t         writable_handler;
    dx_container_link_detach_handler_t  link_detach_handler;

    //
    // Node-Type Handlers
    //
    dx_container_node_handler_t  node_created_handler;
    dx_container_node_handler_t  node_destroyed_handler;
    dx_container_conn_handler_t  inbound_conn_open_handler;
    dx_container_conn_handler_t  outbound_conn_open_handler;
} dx_node_type_t;


int dx_container_register_node_type(dx_dispatch_t *dispatch, const dx_node_type_t *nt);

void dx_container_set_default_node_type(dx_dispatch_t        *dispatch,
                                        const dx_node_type_t *nt,
                                        void                 *node_context,
                                        dx_dist_mode_t        supported_dist);

dx_node_t *dx_container_create_node(dx_dispatch_t        *dispatch,
                                    const dx_node_type_t *nt,
                                    const char           *name,
                                    void                 *node_context,
                                    dx_dist_mode_t        supported_dist,
                                    dx_lifetime_policy_t  life_policy);
void dx_container_destroy_node(dx_node_t *node);

void dx_container_node_set_context(dx_node_t *node, void *node_context);
dx_dist_mode_t dx_container_node_get_dist_modes(const dx_node_t *node);
dx_lifetime_policy_t dx_container_node_get_life_policy(const dx_node_t *node);

dx_link_t *dx_link(dx_node_t *node, dx_connection_t *conn, dx_direction_t dir, const char *name);
void dx_link_set_context(dx_link_t *link, void *link_context);
void *dx_link_get_context(dx_link_t *link);
pn_link_t *dx_link_pn(dx_link_t *link);
pn_terminus_t *dx_link_source(dx_link_t *link);
pn_terminus_t *dx_link_target(dx_link_t *link);
pn_terminus_t *dx_link_remote_source(dx_link_t *link);
pn_terminus_t *dx_link_remote_target(dx_link_t *link);
void dx_link_activate(dx_link_t *link);
void dx_link_close(dx_link_t *link);


typedef struct dx_link_item_t dx_link_item_t;

struct dx_link_item_t {
    DEQ_LINKS(dx_link_item_t);
    dx_link_t *link;
};

ALLOC_DECLARE(dx_link_item_t);
DEQ_DECLARE(dx_link_item_t, dx_link_list_t);

#endif
