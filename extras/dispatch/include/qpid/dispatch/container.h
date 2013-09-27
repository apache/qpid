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
#include <qpid/dispatch/dispatch.h>
#include <qpid/dispatch/server.h>
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/ctools.h>

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


typedef struct dx_node_t     dx_node_t;
typedef struct dx_link_t     dx_link_t;
typedef struct dx_delivery_t dx_delivery_t;

typedef void (*dx_container_delivery_handler_t)    (void *node_context, dx_link_t *link, dx_delivery_t *delivery);
typedef int  (*dx_container_link_handler_t)        (void *node_context, dx_link_t *link);
typedef int  (*dx_container_link_detach_handler_t) (void *node_context, dx_link_t *link, int closed);
typedef void (*dx_container_node_handler_t)        (void *type_context, dx_node_t *node);
typedef void (*dx_container_conn_handler_t)        (void *type_context, dx_connection_t *conn);

typedef struct {
    char *type_name;
    void *type_context;
    int   allow_dynamic_creation;

    //=======================
    // Node-Instance Handlers
    //=======================

    //
    // rx_handler - Invoked when a new received delivery is avaliable for processing.
    //
    dx_container_delivery_handler_t rx_handler;

    //
    // disp_handler - Invoked when an existing delivery changes disposition
    //                or settlement state.
    //
    dx_container_delivery_handler_t disp_handler;

    //
    // incoming_handler - Invoked when an attach for a new incoming link is received.
    //
    dx_container_link_handler_t incoming_handler;

    //
    // outgoing_handler - Invoked when an attach for a new outgoing link is received.
    //
    dx_container_link_handler_t outgoing_handler;

    //
    // writable_handler - Invoked when an outgoing link is available for sending either
    //                    deliveries or disposition changes.  The handler must check the
    //                    link's credit to determine whether (and how many) message
    //                    deliveries may be sent.
    //
    dx_container_link_handler_t writable_handler;

    //
    // link_detach_handler - Invoked when a link is detached.
    //
    dx_container_link_detach_handler_t link_detach_handler;

    //===================
    // Node-Type Handlers
    //===================

    //
    // node_created_handler - Invoked when a new instance of the node-type is created.
    //
    dx_container_node_handler_t  node_created_handler;

    //
    // node_destroyed_handler - Invoked when an instance of the node type is destroyed.
    //
    dx_container_node_handler_t  node_destroyed_handler;

    //
    // inbound_conn_open_handler - Invoked when an incoming connection (via listener)
    //                             is established.
    //
    dx_container_conn_handler_t  inbound_conn_open_handler;

    //
    // outbound_conn_open_handler - Invoked when an outgoing connection (via connector)
    //                              is established.
    //
    dx_container_conn_handler_t  outbound_conn_open_handler;
} dx_node_type_t;


int dx_container_register_node_type(dx_dispatch_t *dispatch, const dx_node_type_t *nt);

dx_node_t *dx_container_set_default_node_type(dx_dispatch_t        *dispatch,
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

/**
 * Context associated with the link for storing link-specific state.
 */
void dx_link_set_context(dx_link_t *link, void *link_context);
void *dx_link_get_context(dx_link_t *link);

/**
 * Link context associated with the link's connection for storing state shared across
 * all links in a connection.
 */
void dx_link_set_conn_context(dx_link_t *link, void *link_context);
void *dx_link_get_conn_context(dx_link_t *link);

pn_link_t *dx_link_pn(dx_link_t *link);
pn_terminus_t *dx_link_source(dx_link_t *link);
pn_terminus_t *dx_link_target(dx_link_t *link);
pn_terminus_t *dx_link_remote_source(dx_link_t *link);
pn_terminus_t *dx_link_remote_target(dx_link_t *link);
void dx_link_activate(dx_link_t *link);
void dx_link_close(dx_link_t *link);

/**
 * Important: dx_delivery must never be called twice in a row without an intervening pn_link_advance.
 *            The Disatch architecture provides a hook for discovering when an outgoing link is writable
 *            and has credit.  When a link is writable, a delivery is allocated, written, and advanced
 *            in one operation.  If a backlog of pending deliveries is created, an assertion will be
 *            thrown.
 */
dx_delivery_t *dx_delivery(dx_link_t *link, pn_delivery_tag_t tag);
void dx_delivery_free(dx_delivery_t *delivery, uint64_t final_disposition);
void dx_delivery_set_peer(dx_delivery_t *delivery, dx_delivery_t *peer);
dx_delivery_t *dx_delivery_peer(dx_delivery_t *delivery);
void dx_delivery_set_context(dx_delivery_t *delivery, void *context);
void *dx_delivery_context(dx_delivery_t *delivery);
pn_delivery_t *dx_delivery_pn(dx_delivery_t *delivery);
void dx_delivery_settle(dx_delivery_t *delivery);
bool dx_delivery_settled(dx_delivery_t *delivery);
bool dx_delivery_disp_changed(dx_delivery_t *delivery);
uint64_t dx_delivery_disp(dx_delivery_t *delivery);
dx_link_t *dx_delivery_link(dx_delivery_t *delivery);


typedef struct dx_link_item_t dx_link_item_t;

struct dx_link_item_t {
    DEQ_LINKS(dx_link_item_t);
    dx_link_t *link;
};

ALLOC_DECLARE(dx_link_item_t);
DEQ_DECLARE(dx_link_item_t, dx_link_list_t);

#endif
