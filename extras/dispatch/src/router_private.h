#ifndef __router_private_h__
#define __router_private_h__ 1
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

typedef struct dx_router_link_t     dx_router_link_t;
typedef struct dx_router_node_t     dx_router_node_t;
typedef struct dx_router_ref_t      dx_router_ref_t;
typedef struct dx_router_link_ref_t dx_router_link_ref_t;


typedef enum {
    DX_LINK_ENDPOINT,   // A link to a connected endpoint
    DX_LINK_ROUTER,     // A link to a peer router in the same area
    DX_LINK_AREA        // A link to a peer router in a different area (area boundary)
} dx_link_type_t;


typedef struct dx_routed_event_t {
    DEQ_LINKS(struct dx_routed_event_t);
    dx_delivery_t *delivery;
    dx_message_t  *message;
    bool           settle;
    uint64_t       disposition;
} dx_routed_event_t;

ALLOC_DECLARE(dx_routed_event_t);
DEQ_DECLARE(dx_routed_event_t, dx_routed_event_list_t);


struct dx_router_link_t {
    DEQ_LINKS(dx_router_link_t);
    int                     mask_bit;        // Unique mask bit if this is an inter-router link
    dx_link_type_t          link_type;
    dx_direction_t          link_direction;
    dx_address_t           *owning_addr;     // [ref] Address record that owns this link
    dx_link_t              *link;            // [own] Link pointer
    dx_router_link_t       *connected_link;  // [ref] If this is a link-route, reference the connected link
    dx_router_link_t       *peer_link;       // [ref] If this is a bidirectional link-route, reference the peer link
    dx_router_link_ref_t   *ref;             // Pointer to a containing reference object
    dx_routed_event_list_t  event_fifo;      // FIFO of outgoing delivery/link events (no messages)
    dx_routed_event_list_t  msg_fifo;        // FIFO of outgoing message deliveries
};

ALLOC_DECLARE(dx_router_link_t);
DEQ_DECLARE(dx_router_link_t, dx_router_link_list_t);

struct dx_router_node_t {
    DEQ_LINKS(dx_router_node_t);
    const char       *id;
    int               mask_bit;
    dx_router_node_t *next_hop;   // Next hop node _if_ this is not a neighbor node
    dx_router_link_t *peer_link;  // Outgoing link _if_ this is a neighbor node
    dx_bitmask_t     *valid_origins;
};

ALLOC_DECLARE(dx_router_node_t);
DEQ_DECLARE(dx_router_node_t, dx_router_node_list_t);

struct dx_router_ref_t {
    DEQ_LINKS(dx_router_ref_t);
    dx_router_node_t *router;
};

ALLOC_DECLARE(dx_router_ref_t);
DEQ_DECLARE(dx_router_ref_t, dx_router_ref_list_t);


struct dx_router_link_ref_t {
    DEQ_LINKS(dx_router_link_ref_t);
    dx_router_link_t *link;
};

ALLOC_DECLARE(dx_router_link_ref_t);
DEQ_DECLARE(dx_router_link_ref_t, dx_router_link_ref_list_t);


struct dx_address_t {
    DEQ_LINKS(dx_address_t);
    dx_router_message_cb       handler;          // In-Process Consumer
    void                      *handler_context;  // In-Process Consumer context
    dx_router_link_ref_list_t  rlinks;           // Locally-Connected Consumers
    dx_router_ref_list_t       rnodes;           // Remotely-Connected Consumers
};

ALLOC_DECLARE(dx_address_t);
DEQ_DECLARE(dx_address_t, dx_address_list_t);


struct dx_router_t {
    dx_dispatch_t         *dx;
    const char            *router_area;
    const char            *router_id;
    dx_node_t             *node;

    dx_address_list_t      addrs;
    hash_t                *addr_hash;
    dx_address_t          *router_addr;

    dx_router_link_list_t  links;
    dx_router_node_list_t  routers;

    dx_bitmask_t          *neighbor_free_mask;
    sys_mutex_t           *lock;
    dx_timer_t            *timer;
    uint64_t               dtag;

    PyObject              *pyRouter;
    PyObject              *pyTick;
};

#endif
