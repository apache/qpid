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

#include <stdio.h>
#include <string.h>
#include "dispatch_private.h"
#include <qpid/dispatch/container.h>
#include <qpid/dispatch/server.h>
#include <qpid/dispatch/message.h>
#include <proton/engine.h>
#include <proton/message.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/hash.h>
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/iterator.h>
#include <qpid/dispatch/log.h>
#include <qpid/dispatch/agent.h>

static char *module="CONTAINER";

struct dx_node_t {
    dx_container_t       *container;
    const dx_node_type_t *ntype;
    char                 *name;
    void                 *context;
    dx_dist_mode_t        supported_dist;
    dx_lifetime_policy_t  life_policy;
};

ALLOC_DECLARE(dx_node_t);
ALLOC_DEFINE(dx_node_t);
ALLOC_DEFINE(dx_link_item_t);


struct dx_link_t {
    pn_link_t *pn_link;
    void      *context;
    dx_node_t *node;
};

ALLOC_DECLARE(dx_link_t);
ALLOC_DEFINE(dx_link_t);


struct dx_delivery_t {
    pn_delivery_t *pn_delivery;
    dx_delivery_t *peer;
    void          *context;
    uint64_t       disposition;
    dx_link_t     *link;
};

ALLOC_DECLARE(dx_delivery_t);
ALLOC_DEFINE(dx_delivery_t);


typedef struct dxc_node_type_t {
    DEQ_LINKS(struct dxc_node_type_t);
    const dx_node_type_t *ntype;
} dxc_node_type_t;
DEQ_DECLARE(dxc_node_type_t, dxc_node_type_list_t);

static int DX_CONTAINER_CLASS_CONTAINER = 1;
static int DX_CONTAINER_CLASS_NODE_TYPE = 2;
static int DX_CONTAINER_CLASS_NODE      = 3;

typedef struct container_class_t {
    dx_container_t *container;
    int             class_id;
} container_class_t;

struct dx_container_t {
    dx_dispatch_t        *dx;
    dx_server_t          *server;
    dx_hash_t            *node_type_map;
    dx_hash_t            *node_map;
    sys_mutex_t          *lock;
    dx_node_t            *default_node;
    dxc_node_type_list_t  node_type_list;
    dx_agent_class_t     *class_container;
    dx_agent_class_t     *class_node_type;
    dx_agent_class_t     *class_node;
};

static void setup_outgoing_link(dx_container_t *container, pn_link_t *pn_link)
{
    sys_mutex_lock(container->lock);
    dx_node_t  *node = 0;
    const char *source = pn_terminus_get_address(pn_link_remote_source(pn_link));
    dx_field_iterator_t *iter;
    // TODO - Extract the name from the structured source

    if (source) {
        iter   = dx_field_iterator_string(source, ITER_VIEW_NODE_ID);
        dx_hash_retrieve(container->node_map, iter, (void*) &node);
        dx_field_iterator_free(iter);
    }
    sys_mutex_unlock(container->lock);

    if (node == 0) {
        if (container->default_node)
            node = container->default_node;
        else {
            // Reject the link
            // TODO - When the API allows, add an error message for "no available node"
            pn_link_close(pn_link);
            return;
        }
    }

    dx_link_t *link = new_dx_link_t();
    if (!link) {
        pn_link_close(pn_link);
        return;
    }

    link->pn_link = pn_link;
    link->context = 0;
    link->node    = node;

    pn_link_set_context(pn_link, link);
    node->ntype->outgoing_handler(node->context, link);
}


static void setup_incoming_link(dx_container_t *container, pn_link_t *pn_link)
{
    sys_mutex_lock(container->lock);
    dx_node_t   *node = 0;
    const char  *target = pn_terminus_get_address(pn_link_remote_target(pn_link));
    dx_field_iterator_t *iter;
    // TODO - Extract the name from the structured target

    if (target) {
        iter   = dx_field_iterator_string(target, ITER_VIEW_NODE_ID);
        dx_hash_retrieve(container->node_map, iter, (void*) &node);
        dx_field_iterator_free(iter);
    }
    sys_mutex_unlock(container->lock);

    if (node == 0) {
        if (container->default_node)
            node = container->default_node;
        else {
            // Reject the link
            // TODO - When the API allows, add an error message for "no available node"
            pn_link_close(pn_link);
            return;
        }
    }

    dx_link_t *link = new_dx_link_t();
    if (!link) {
        pn_link_close(pn_link);
        return;
    }

    link->pn_link = pn_link;
    link->context = 0;
    link->node    = node;

    pn_link_set_context(pn_link, link);
    node->ntype->incoming_handler(node->context, link);
}


static int do_writable(pn_link_t *pn_link)
{
    dx_link_t *link = (dx_link_t*) pn_link_get_context(pn_link);
    if (!link)
        return 0;

    dx_node_t *node = link->node;
    if (!node)
        return 0;

    return node->ntype->writable_handler(node->context, link);
}


static void do_receive(pn_delivery_t *pnd)
{
    pn_link_t     *pn_link  = pn_delivery_link(pnd);
    dx_link_t     *link     = (dx_link_t*) pn_link_get_context(pn_link);
    dx_delivery_t *delivery = (dx_delivery_t*) pn_delivery_get_context(pnd);

    if (link) {
        dx_node_t *node = link->node;
        if (node) {
            if (!delivery) {
                delivery = new_dx_delivery_t();
                delivery->pn_delivery = pnd;
                delivery->peer        = 0;
                delivery->context     = 0;
                delivery->disposition = 0;
                delivery->link        = link;
                pn_delivery_set_context(pnd, delivery);
            }

            node->ntype->rx_handler(node->context, link, delivery);
            return;
        }
    }

    //
    // Reject the delivery if we couldn't find a node to handle it
    //
    pn_link_advance(pn_link);
    pn_link_flow(pn_link, 1);
    pn_delivery_update(pnd, PN_REJECTED);
    pn_delivery_settle(pnd);
}


static void do_updated(pn_delivery_t *pnd)
{
    pn_link_t     *pn_link  = pn_delivery_link(pnd);
    dx_link_t     *link     = (dx_link_t*) pn_link_get_context(pn_link);
    dx_delivery_t *delivery = (dx_delivery_t*) pn_delivery_get_context(pnd);

    if (link && delivery) {
        dx_node_t *node = link->node;
        if (node)
            node->ntype->disp_handler(node->context, link, delivery);
    }
}


static int close_handler(void* unused, pn_connection_t *conn)
{
    //
    // Close all links, passing False as the 'closed' argument.  These links are not
    // being properly 'detached'.  They are being orphaned.
    //
    pn_link_t *pn_link = pn_link_head(conn, PN_LOCAL_ACTIVE);
    while (pn_link) {
        dx_link_t *link = (dx_link_t*) pn_link_get_context(pn_link);
        dx_node_t *node = link->node;
        if (node && link)
            node->ntype->link_detach_handler(node->context, link, 0);
        pn_link_close(pn_link);
        free_dx_link_t(link);
        pn_link = pn_link_next(pn_link, PN_LOCAL_ACTIVE);
    }

    // teardown all sessions
    pn_session_t *ssn = pn_session_head(conn, 0);
    while (ssn) {
        pn_session_close(ssn);
        ssn = pn_session_next(ssn, 0);
    }

    // teardown the connection
    pn_connection_close(conn);
    return 0;
}


static int process_handler(dx_container_t *container, void* unused, pn_connection_t *conn)
{
    pn_session_t    *ssn;
    pn_link_t       *pn_link;
    pn_delivery_t   *delivery;
    int              event_count = 0;

    // Step 1: setup the engine's connection, and any sessions and links
    // that may be pending.

    // initialize the connection if it's new
    if (pn_connection_state(conn) & PN_LOCAL_UNINIT) {
        pn_connection_open(conn);
        event_count++;
    }

    // open all pending sessions
    ssn = pn_session_head(conn, PN_LOCAL_UNINIT);
    while (ssn) {
        pn_session_open(ssn);
        ssn = pn_session_next(ssn, PN_LOCAL_UNINIT);
        event_count++;
    }

    // configure and open any pending links
    pn_link = pn_link_head(conn, PN_LOCAL_UNINIT);
    while (pn_link) {
        if (pn_link_is_sender(pn_link))
            setup_outgoing_link(container, pn_link);
        else
            setup_incoming_link(container, pn_link);
        pn_link = pn_link_next(pn_link, PN_LOCAL_UNINIT);
        event_count++;
    }


    // Step 2: Now drain all the pending deliveries from the connection's
    // work queue and process them

    delivery = pn_work_head(conn);
    while (delivery) {
        if (pn_delivery_readable(delivery))
            do_receive(delivery);

        if (pn_delivery_updated(delivery)) {
            do_updated(delivery);
            pn_delivery_clear(delivery);
        }
        delivery = pn_work_next(delivery);
        event_count++;
    }

    //
    // Step 2.5: Call the attached node's writable handler for all active links
    // on the connection.  Note that in Dispatch, links are considered
    // bidirectional.  Incoming and outgoing only pertains to deliveries and
    // deliveries are a subset of the traffic that flows both directions on links.
    //
    pn_link = pn_link_head(conn, PN_LOCAL_ACTIVE | PN_REMOTE_ACTIVE);
    while (pn_link) {
        assert(pn_session_connection(pn_link_session(pn_link)) == conn);
        event_count += do_writable(pn_link);
        pn_link = pn_link_next(pn_link, PN_LOCAL_ACTIVE | PN_REMOTE_ACTIVE);
    }

    // Step 3: Clean up any links or sessions that have been closed by the
    // remote.  If the connection has been closed remotely, clean that up
    // also.

    // teardown any terminating links
    pn_link = pn_link_head(conn, PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED);
    while (pn_link) {
        dx_link_t *link = (dx_link_t*) pn_link_get_context(pn_link);
        dx_node_t *node = link->node;
        if (node)
            node->ntype->link_detach_handler(node->context, link, 1); // TODO - get 'closed' from detach message
        pn_link_close(pn_link);
        pn_link = pn_link_next(pn_link, PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED);
        event_count++;
    }

    // teardown any terminating sessions
    ssn = pn_session_head(conn, PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED);
    while (ssn) {
        pn_session_close(ssn);
        ssn = pn_session_next(ssn, PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED);
        event_count++;
    }

    // teardown the connection if it's terminating
    if (pn_connection_state(conn) == (PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED)) {
        pn_connection_close(conn);
        event_count++;
    }

    return event_count;
}


static void open_handler(dx_container_t *container, dx_connection_t *conn, dx_direction_t dir)
{
    const dx_node_type_t *nt;

    //
    // Note the locking structure in this function.  Generally this would be unsafe, but since
    // this particular list is only ever appended to and never has items inserted or deleted,
    // this usage is safe in this case.
    //
    sys_mutex_lock(container->lock);
    dxc_node_type_t *nt_item = DEQ_HEAD(container->node_type_list);
    sys_mutex_unlock(container->lock);

    pn_connection_open(dx_connection_pn(conn));

    while (nt_item) {
        nt = nt_item->ntype;
        if (dir == DX_INCOMING) {
            if (nt->inbound_conn_open_handler)
                nt->inbound_conn_open_handler(nt->type_context, conn);
        } else {
            if (nt->outbound_conn_open_handler)
                nt->outbound_conn_open_handler(nt->type_context, conn);
        }

        sys_mutex_lock(container->lock);
        nt_item = DEQ_NEXT(nt_item);
        sys_mutex_unlock(container->lock);
    }
}


static int handler(void *handler_context, void *conn_context, dx_conn_event_t event, dx_connection_t *dx_conn)
{
    dx_container_t  *container = (dx_container_t*) handler_context;
    pn_connection_t *conn      = dx_connection_pn(dx_conn);

    switch (event) {
    case DX_CONN_EVENT_LISTENER_OPEN:  open_handler(container, dx_conn, DX_INCOMING);   break;
    case DX_CONN_EVENT_CONNECTOR_OPEN: open_handler(container, dx_conn, DX_OUTGOING);   break;
    case DX_CONN_EVENT_CLOSE:          return close_handler(conn_context, conn);
    case DX_CONN_EVENT_PROCESS:        return process_handler(container, conn_context, conn);
    }

    return 0;
}


static void container_schema_handler(void *context, void *correlator)
{
}


static void container_query_handler(void* context, const char *id, void *correlator)
{
    container_class_t *cls = (container_class_t*) context;

    if (cls->class_id == DX_CONTAINER_CLASS_CONTAINER) {
        dx_agent_value_uint(correlator, "node_type_count", dx_hash_size(cls->container->node_type_map));
        dx_agent_value_uint(correlator, "node_count",      dx_hash_size(cls->container->node_map));
        if (cls->container->default_node)
            dx_agent_value_string(correlator, "default_node_type", cls->container->default_node->ntype->type_name);
        else
            dx_agent_value_null(correlator, "default_node_type");
        dx_agent_value_complete(correlator, false);

    } else if (cls->class_id == DX_CONTAINER_CLASS_NODE_TYPE) {

    } else if (cls->class_id == DX_CONTAINER_CLASS_NODE) {

    }
}


dx_agent_class_t *setup_class(dx_container_t *container, const char *fqname, int id)
{
    container_class_t *cls = NEW(container_class_t);
    cls->container = container;
    cls->class_id  = id;

    return dx_agent_register_class(container->dx, fqname, cls,
                                   container_schema_handler,
                                   container_query_handler);
}


dx_container_t *dx_container(dx_dispatch_t *dx)
{
    dx_container_t *container = NEW(dx_container_t);

    container->dx            = dx;
    container->server        = dx->server;
    container->node_type_map = dx_hash(6,  4, 1);  // 64 buckets, item batches of 4
    container->node_map      = dx_hash(10, 32, 0); // 1K buckets, item batches of 32
    container->lock          = sys_mutex();
    container->default_node  = 0;
    DEQ_INIT(container->node_type_list);

    dx_log(module, LOG_TRACE, "Container Initializing");
    dx_server_set_conn_handler(dx, handler, container);

    return container;
}


void dx_container_setup_agent(dx_dispatch_t *dx)
{
    dx->container->class_container =
        setup_class(dx->container, "org.apache.qpid.dispatch.container", DX_CONTAINER_CLASS_CONTAINER);
    dx->container->class_node_type =
        setup_class(dx->container, "org.apache.qpid.dispatch.container.node_type", DX_CONTAINER_CLASS_NODE_TYPE);
    dx->container->class_node =
        setup_class(dx->container, "org.apache.qpid.dispatch.container.node", DX_CONTAINER_CLASS_NODE);
}


void dx_container_free(dx_container_t *container)
{
    // TODO - Free the nodes
    // TODO - Free the node types
    sys_mutex_free(container->lock);
    free(container);
}


int dx_container_register_node_type(dx_dispatch_t *dx, const dx_node_type_t *nt)
{
    dx_container_t *container = dx->container;

    int result;
    dx_field_iterator_t *iter = dx_field_iterator_string(nt->type_name, ITER_VIEW_ALL);
    dxc_node_type_t     *nt_item = NEW(dxc_node_type_t);
    DEQ_ITEM_INIT(nt_item);
    nt_item->ntype = nt;

    sys_mutex_lock(container->lock);
    result = dx_hash_insert_const(container->node_type_map, iter, nt, 0);
    DEQ_INSERT_TAIL(container->node_type_list, nt_item);
    sys_mutex_unlock(container->lock);

    dx_field_iterator_free(iter);
    if (result < 0)
        return result;
    dx_log(module, LOG_TRACE, "Node Type Registered - %s", nt->type_name);

    return 0;
}


dx_node_t *dx_container_set_default_node_type(dx_dispatch_t        *dx,
                                              const dx_node_type_t *nt,
                                              void                 *context,
                                              dx_dist_mode_t        supported_dist)
{
    dx_container_t *container = dx->container;

    if (container->default_node)
        dx_container_destroy_node(container->default_node);

    if (nt) {
        container->default_node = dx_container_create_node(dx, nt, 0, context, supported_dist, DX_LIFE_PERMANENT);
        dx_log(module, LOG_TRACE, "Node of type '%s' installed as default node", nt->type_name);
    } else {
        container->default_node = 0;
        dx_log(module, LOG_TRACE, "Default node removed");
    }

    return container->default_node;
}


dx_node_t *dx_container_create_node(dx_dispatch_t        *dx,
                                    const dx_node_type_t *nt,
                                    const char           *name,
                                    void                 *context,
                                    dx_dist_mode_t        supported_dist,
                                    dx_lifetime_policy_t  life_policy)
{
    dx_container_t *container = dx->container;
    int result;
    dx_node_t *node = new_dx_node_t();
    if (!node)
        return 0;

    node->container      = container;
    node->ntype          = nt;
    node->name           = 0;
    node->context        = context;
    node->supported_dist = supported_dist;
    node->life_policy    = life_policy;

    if (name) {
        dx_field_iterator_t *iter = dx_field_iterator_string(name, ITER_VIEW_ALL);
        sys_mutex_lock(container->lock);
        result = dx_hash_insert(container->node_map, iter, node, 0);
        sys_mutex_unlock(container->lock);
        dx_field_iterator_free(iter);
        if (result < 0) {
            free_dx_node_t(node);
            return 0;
        }

        node->name = (char*) malloc(strlen(name) + 1);
        strcpy(node->name, name);
    }

    if (name)
        dx_log(module, LOG_TRACE, "Node of type '%s' created with name '%s'", nt->type_name, name);

    return node;
}


void dx_container_destroy_node(dx_node_t *node)
{
    dx_container_t *container = node->container;

    if (node->name) {
        dx_field_iterator_t *iter = dx_field_iterator_string(node->name, ITER_VIEW_ALL);
        sys_mutex_lock(container->lock);
        dx_hash_remove(container->node_map, iter);
        sys_mutex_unlock(container->lock);
        dx_field_iterator_free(iter);
        free(node->name);
    }

    free_dx_node_t(node);
}


void dx_container_node_set_context(dx_node_t *node, void *node_context)
{
    node->context = node_context;
}


dx_dist_mode_t dx_container_node_get_dist_modes(const dx_node_t *node)
{
    return node->supported_dist;
}


dx_lifetime_policy_t dx_container_node_get_life_policy(const dx_node_t *node)
{
    return node->life_policy;
}


dx_link_t *dx_link(dx_node_t *node, dx_connection_t *conn, dx_direction_t dir, const char* name)
{
    pn_session_t *sess = pn_session(dx_connection_pn(conn));
    dx_link_t    *link = new_dx_link_t();

    if (dir == DX_OUTGOING)
        link->pn_link = pn_sender(sess, name);
    else
        link->pn_link = pn_receiver(sess, name);
    link->context = node->context;
    link->node    = node;

    pn_link_set_context(link->pn_link, link);

    pn_session_open(sess);

    return link;
}


void dx_link_set_context(dx_link_t *link, void *context)
{
    link->context = context;
}


void *dx_link_get_context(dx_link_t *link)
{
    return link->context;
}


void dx_link_set_conn_context(dx_link_t *link, void *context)
{
    pn_session_t *pn_sess = pn_link_session(link->pn_link);
    if (!pn_sess)
        return;
    pn_connection_t *pn_conn = pn_session_connection(pn_sess);
    if (!pn_conn)
        return;
    dx_connection_t *conn = (dx_connection_t*) pn_connection_get_context(pn_conn);
    if (!conn)
        return;
    dx_connection_set_link_context(conn, context);
}


void *dx_link_get_conn_context(dx_link_t *link)
{
    pn_session_t *pn_sess = pn_link_session(link->pn_link);
    if (!pn_sess)
        return 0;
    pn_connection_t *pn_conn = pn_session_connection(pn_sess);
    if (!pn_conn)
        return 0;
    dx_connection_t *conn = (dx_connection_t*) pn_connection_get_context(pn_conn);
    if (!conn)
        return 0;
    return dx_connection_get_link_context(conn);
}


pn_link_t *dx_link_pn(dx_link_t *link)
{
    return link->pn_link;
}


dx_connection_t *dx_link_connection(dx_link_t *link)
{
    if (!link || !link->pn_link)
        return 0;

    pn_session_t *sess = pn_link_session(link->pn_link);
    if (!sess)
        return 0;

    pn_connection_t *conn = pn_session_connection(sess);
    if (!conn)
        return 0;

    dx_connection_t *ctx = pn_connection_get_context(conn);
    if (!ctx)
        return 0;

    return ctx;
}


pn_terminus_t *dx_link_source(dx_link_t *link)
{
    return pn_link_source(link->pn_link);
}


pn_terminus_t *dx_link_target(dx_link_t *link)
{
    return pn_link_target(link->pn_link);
}


pn_terminus_t *dx_link_remote_source(dx_link_t *link)
{
    return pn_link_remote_source(link->pn_link);
}


pn_terminus_t *dx_link_remote_target(dx_link_t *link)
{
    return pn_link_remote_target(link->pn_link);
}


void dx_link_activate(dx_link_t *link)
{
    if (!link || !link->pn_link)
        return;

    pn_session_t *sess = pn_link_session(link->pn_link);
    if (!sess)
        return;

    pn_connection_t *conn = pn_session_connection(sess);
    if (!conn)
        return;

    dx_connection_t *ctx = pn_connection_get_context(conn);
    if (!ctx)
        return;

    dx_server_activate(ctx);
}


void dx_link_close(dx_link_t *link)
{
    pn_link_close(link->pn_link);
}


dx_delivery_t *dx_delivery(dx_link_t *link, pn_delivery_tag_t tag)
{
    pn_link_t *pnl = dx_link_pn(link);

    //
    // If there is a current delivery on this outgoing link, something
    // is wrong with the delivey algorithm.  We assume that the current
    // delivery ('pnd' below) is the one created by pn_delivery.  If it is
    // not, then my understanding of how proton works is incorrect.
    //
    assert(!pn_link_current(pnl));

    pn_delivery(pnl, tag);
    pn_delivery_t *pnd = pn_link_current(pnl);

    if (!pnd)
        return 0;

    dx_delivery_t *delivery = new_dx_delivery_t();
    delivery->pn_delivery = pnd;
    delivery->peer        = 0;
    delivery->context     = 0;
    delivery->disposition = 0;
    delivery->link        = link;
    pn_delivery_set_context(pnd, delivery);

    return delivery;
}


void dx_delivery_free(dx_delivery_t *delivery, uint64_t final_disposition)
{
    if (delivery->pn_delivery) {
        if (final_disposition > 0)
            pn_delivery_update(delivery->pn_delivery, final_disposition);
        pn_delivery_set_context(delivery->pn_delivery, 0);
        pn_delivery_settle(delivery->pn_delivery);
    }
    if (delivery->peer)
        delivery->peer->peer = 0;
    free_dx_delivery_t(delivery);
}


void dx_delivery_set_peer(dx_delivery_t *delivery, dx_delivery_t *peer)
{
    delivery->peer = peer;
}


void dx_delivery_set_context(dx_delivery_t *delivery, void *context)
{
    delivery->context = context;
}


void *dx_delivery_context(dx_delivery_t *delivery)
{
    return delivery->context;
}


dx_delivery_t *dx_delivery_peer(dx_delivery_t *delivery)
{
    return delivery->peer;
}


pn_delivery_t *dx_delivery_pn(dx_delivery_t *delivery)
{
    return delivery->pn_delivery;
}


void dx_delivery_settle(dx_delivery_t *delivery)
{
    if (delivery->pn_delivery) {
        pn_delivery_settle(delivery->pn_delivery);
        delivery->pn_delivery = 0;
    }
}


bool dx_delivery_settled(dx_delivery_t *delivery)
{
    return pn_delivery_settled(delivery->pn_delivery);
}


bool dx_delivery_disp_changed(dx_delivery_t *delivery)
{
    return delivery->disposition != pn_delivery_remote_state(delivery->pn_delivery);
}


uint64_t dx_delivery_disp(dx_delivery_t *delivery)
{
    delivery->disposition = pn_delivery_remote_state(delivery->pn_delivery);
    return delivery->disposition;
}


dx_link_t *dx_delivery_link(dx_delivery_t *delivery)
{
    return delivery->link;
}

