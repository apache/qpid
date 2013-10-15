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

#include <qpid/dispatch/python_embedded.h>
#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include <stdlib.h>
#include <qpid/dispatch.h>
#include <qpid/dispatch/agent.h>
#include "dispatch_private.h"
#include "router_private.h"

//static char *module = "router.agent";

#define DX_ROUTER_CLASS_ROUTER  1
#define DX_ROUTER_CLASS_LINK    2
#define DX_ROUTER_CLASS_NODE    3
#define DX_ROUTER_CLASS_ADDRESS 4

typedef struct dx_router_class_t {
    dx_router_t *router;
    int          class_id;
} dx_router_class_t;


static void dx_router_schema_handler(void *context, void *correlator)
{
}


static const char *dx_router_addr_text(dx_address_t *addr)
{
    if (addr) {
        const unsigned char *text = dx_hash_key_by_handle(addr->hash_handle);
        if (text)
            return (const char*) text;
    }
    return 0;
}


static void dx_router_query_router(dx_router_t *router, void *cor)
{
    dx_agent_value_string(cor, "area",      router->router_area);
    dx_agent_value_string(cor, "router_id", router->router_id);

    sys_mutex_lock(router->lock);
    dx_agent_value_uint(cor, "addr_count", DEQ_SIZE(router->addrs));
    dx_agent_value_uint(cor, "link_count", DEQ_SIZE(router->links));
    dx_agent_value_uint(cor, "node_count", DEQ_SIZE(router->routers));
    sys_mutex_unlock(router->lock);

    dx_agent_value_complete(cor, 0);
}


static void dx_router_query_link(dx_router_t *router, void *cor)
{
    sys_mutex_lock(router->lock);
    dx_router_link_t *link = DEQ_HEAD(router->links);
    const char       *link_type = "?";
    const char       *link_dir;

    while (link) {
        dx_agent_value_uint(cor, "index", link->mask_bit);
        switch (link->link_type) {
        case DX_LINK_ENDPOINT: link_type = "endpoint";     break;
        case DX_LINK_ROUTER:   link_type = "inter-router"; break;
        case DX_LINK_AREA:     link_type = "inter-area";   break;
        }
        dx_agent_value_string(cor, "link-type", link_type);

        if (link->link_direction == DX_INCOMING)
            link_dir = "in";
        else
            link_dir = "out";
        dx_agent_value_string(cor, "link-dir", link_dir);

        const char *text = dx_router_addr_text(link->owning_addr);
        if (text)
            dx_agent_value_string(cor, "owning-addr", text);
        else
            dx_agent_value_null(cor, "owning-addr");

        link = DEQ_NEXT(link);
        dx_agent_value_complete(cor, link != 0);
    }
    sys_mutex_unlock(router->lock);
}


static void dx_router_query_node(dx_router_t *router, void *cor)
{
    sys_mutex_lock(router->lock);
    dx_router_node_t *node = DEQ_HEAD(router->routers);
    while (node) {
        dx_agent_value_uint(cor, "index", node->mask_bit);
        dx_agent_value_string(cor, "addr", dx_router_addr_text(node->owning_addr));
        if (node->next_hop)
            dx_agent_value_uint(cor, "next-hop", node->next_hop->mask_bit);
        else
            dx_agent_value_null(cor, "next-hop");
        if (node->peer_link)
            dx_agent_value_uint(cor, "router-link", node->peer_link->mask_bit);
        else
            dx_agent_value_null(cor, "router-link");
        node = DEQ_NEXT(node);
        dx_agent_value_complete(cor, node != 0);
    }
    sys_mutex_unlock(router->lock);
}


static void dx_router_query_address(dx_router_t *router, void *cor)
{
    sys_mutex_lock(router->lock);
    dx_address_t *addr = DEQ_HEAD(router->addrs);
    while (addr) {
        dx_agent_value_string(cor, "addr", dx_router_addr_text(addr));
        dx_agent_value_boolean(cor, "in-process", addr->handler != 0);
        dx_agent_value_uint(cor, "subscriber-count", DEQ_SIZE(addr->rlinks));
        dx_agent_value_uint(cor, "remote-count", DEQ_SIZE(addr->rnodes));
        dx_agent_value_uint(cor, "deliveries-ingress", addr->deliveries_ingress);
        dx_agent_value_uint(cor, "deliveries-egress", addr->deliveries_egress);
        dx_agent_value_uint(cor, "deliveries-transit", addr->deliveries_transit);
        dx_agent_value_uint(cor, "deliveries-to-container", addr->deliveries_to_container);
        dx_agent_value_uint(cor, "deliveries-from-container", addr->deliveries_from_container);
        addr = DEQ_NEXT(addr);
        dx_agent_value_complete(cor, addr != 0);
    }
    sys_mutex_unlock(router->lock);
}


static void dx_router_query_handler(void* context, const char *id, void *correlator)
{
    dx_router_class_t *cls = (dx_router_class_t*) context;
    switch (cls->class_id) {
    case DX_ROUTER_CLASS_ROUTER:  dx_router_query_router(cls->router, correlator); break;
    case DX_ROUTER_CLASS_LINK:    dx_router_query_link(cls->router, correlator); break;
    case DX_ROUTER_CLASS_NODE:    dx_router_query_node(cls->router, correlator); break;
    case DX_ROUTER_CLASS_ADDRESS: dx_router_query_address(cls->router, correlator); break;
    }
}


static dx_agent_class_t *dx_router_setup_class(dx_router_t *router, const char *fqname, int id)
{
    dx_router_class_t *cls = NEW(dx_router_class_t);
    cls->router   = router;
    cls->class_id = id;

    return dx_agent_register_class(router->dx, fqname, cls,
                                   dx_router_schema_handler,
                                   dx_router_query_handler);
}


void dx_router_agent_setup(dx_router_t *router)
{
    router->class_router =
        dx_router_setup_class(router, "org.apache.qpid.dispatch.router", DX_ROUTER_CLASS_ROUTER);
    router->class_link =
        dx_router_setup_class(router, "org.apache.qpid.dispatch.router.link", DX_ROUTER_CLASS_LINK);
    router->class_node =
        dx_router_setup_class(router, "org.apache.qpid.dispatch.router.node", DX_ROUTER_CLASS_NODE);
    router->class_address =
        dx_router_setup_class(router, "org.apache.qpid.dispatch.router.address", DX_ROUTER_CLASS_ADDRESS);
}

