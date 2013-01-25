#ifndef __container_h__
#define __container_h__ 1
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
#include <qpid/nexus/server.h>
#include <qpid/nexus/alloc.h>
#include <qpid/nexus/ctools.h>

typedef uint8_t nx_dist_mode_t;
#define NX_DIST_COPY 0x01
#define NX_DIST_MOVE 0x02
#define NX_DIST_BOTH 0x03

typedef enum {
    NX_LIFE_PERMANENT,
    NX_LIFE_DELETE_CLOSE,
    NX_LIFE_DELETE_NO_LINKS,
    NX_LIFE_DELETE_NO_MESSAGES,
    NX_LIFE_DELETE_NO_LINKS_MESSAGES
} nx_lifetime_policy_t;

typedef enum {
    NX_INCOMING,
    NX_OUTGOING
} nx_direction_t;


typedef struct nx_node_t nx_node_t;
typedef struct nx_link_t nx_link_t;

typedef void (*nx_container_delivery_handler_t)    (void *node_context, nx_link_t *link, pn_delivery_t *delivery);
typedef int  (*nx_container_link_handler_t)        (void *node_context, nx_link_t *link);
typedef int  (*nx_container_link_detach_handler_t) (void *node_context, nx_link_t *link, int closed);
typedef void (*nx_container_node_handler_t)        (void *type_context, nx_node_t *node);
typedef void (*nx_container_conn_handler_t)        (void *type_context, nx_connection_t *conn);

typedef struct {
    char *type_name;
    void *type_context;
    int   allow_dynamic_creation;

    //
    // Node-Instance Handlers
    //
    nx_container_delivery_handler_t     rx_handler;
    nx_container_delivery_handler_t     tx_handler;
    nx_container_delivery_handler_t     disp_handler;
    nx_container_link_handler_t         incoming_handler;
    nx_container_link_handler_t         outgoing_handler;
    nx_container_link_handler_t         writable_handler;
    nx_container_link_detach_handler_t  link_detach_handler;

    //
    // Node-Type Handlers
    //
    nx_container_node_handler_t  node_created_handler;
    nx_container_node_handler_t  node_destroyed_handler;
    nx_container_conn_handler_t  inbound_conn_open_handler;
    nx_container_conn_handler_t  outbound_conn_open_handler;
} nx_node_type_t;

void nx_container_initialize(void);
void nx_container_finalize(void);

int nx_container_register_node_type(const nx_node_type_t *nt);

void nx_container_set_default_node_type(const nx_node_type_t *nt,
                                        void                 *node_context,
                                        nx_dist_mode_t        supported_dist);

nx_node_t *nx_container_create_node(const nx_node_type_t *nt,
                                    const char           *name,
                                    void                 *node_context,
                                    nx_dist_mode_t        supported_dist,
                                    nx_lifetime_policy_t  life_policy);
void nx_container_destroy_node(nx_node_t *node);

void nx_container_node_set_context(nx_node_t *node, void *node_context);
nx_dist_mode_t nx_container_node_get_dist_modes(const nx_node_t *node);
nx_lifetime_policy_t nx_container_node_get_life_policy(const nx_node_t *node);

nx_link_t *nx_link(nx_node_t *node, nx_connection_t *conn, nx_direction_t dir, const char *name);
void nx_link_set_context(nx_link_t *link, void *link_context);
void *nx_link_get_context(nx_link_t *link);
pn_link_t *nx_link_pn(nx_link_t *link);
pn_terminus_t *nx_link_source(nx_link_t *link);
pn_terminus_t *nx_link_target(nx_link_t *link);
pn_terminus_t *nx_link_remote_source(nx_link_t *link);
pn_terminus_t *nx_link_remote_target(nx_link_t *link);
void nx_link_activate(nx_link_t *link);
void nx_link_close(nx_link_t *link);


typedef struct nx_link_item_t nx_link_item_t;

struct nx_link_item_t {
    DEQ_LINKS(nx_link_item_t);
    nx_link_t *link;
};

ALLOC_DECLARE(nx_link_item_t);
DEQ_DECLARE(nx_link_item_t, nx_link_list_t);

#endif
