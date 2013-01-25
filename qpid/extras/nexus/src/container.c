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
#include <qpid/nexus/container.h>
#include <qpid/nexus/message.h>
#include <proton/engine.h>
#include <proton/message.h>
#include <qpid/nexus/ctools.h>
#include <qpid/nexus/hash.h>
#include <qpid/nexus/threading.h>
#include <qpid/nexus/iterator.h>
#include <qpid/nexus/log.h>

static char *module="CONTAINER";

struct nx_node_t {
    const nx_node_type_t *ntype;
    char                 *name;
    void                 *context;
    nx_dist_mode_t        supported_dist;
    nx_lifetime_policy_t  life_policy;
};

ALLOC_DECLARE(nx_node_t);
ALLOC_DEFINE(nx_node_t);
ALLOC_DEFINE(nx_link_item_t);

struct nx_link_t {
    pn_link_t *pn_link;
    void      *context;
    nx_node_t *node;
};

ALLOC_DECLARE(nx_link_t);
ALLOC_DEFINE(nx_link_t);

typedef struct nxc_node_type_t {
    DEQ_LINKS(struct nxc_node_type_t);
    const nx_node_type_t *ntype;
} nxc_node_type_t;
DEQ_DECLARE(nxc_node_type_t, nxc_node_type_list_t);


static hash_t               *node_type_map;
static hash_t               *node_map;
static sys_mutex_t          *lock;
static nx_node_t            *default_node;
static nxc_node_type_list_t  node_type_list;

static void setup_outgoing_link(pn_link_t *pn_link)
{
    sys_mutex_lock(lock);
    nx_node_t  *node;
    int         result;
    const char *source = pn_terminus_get_address(pn_link_remote_source(pn_link));
    nx_field_iterator_t *iter;
    // TODO - Extract the name from the structured source

    if (source) {
        iter   = nx_field_iterator_string(source, ITER_VIEW_NODE_ID);
        result = hash_retrieve(node_map, iter, (void*) &node);
        nx_field_iterator_free(iter);
    } else
        result = -1;
    sys_mutex_unlock(lock);

    if (result < 0) {
        if (default_node)
            node = default_node;
        else {
            // Reject the link
            // TODO - When the API allows, add an error message for "no available node"
            pn_link_close(pn_link);
            return;
        }
    }

    nx_link_t *link = new_nx_link_t();
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


static void setup_incoming_link(pn_link_t *pn_link)
{
    sys_mutex_lock(lock);
    nx_node_t   *node;
    int          result;
    const char  *target = pn_terminus_get_address(pn_link_remote_target(pn_link));
    nx_field_iterator_t *iter;
    // TODO - Extract the name from the structured target

    if (target) {
        iter   = nx_field_iterator_string(target, ITER_VIEW_NODE_ID);
        result = hash_retrieve(node_map, iter, (void*) &node);
        nx_field_iterator_free(iter);
    } else
        result = -1;
    sys_mutex_unlock(lock);

    if (result < 0) {
        if (default_node)
            node = default_node;
        else {
            // Reject the link
            // TODO - When the API allows, add an error message for "no available node"
            pn_link_close(pn_link);
            return;
        }
    }

    nx_link_t *link = new_nx_link_t();
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
    nx_link_t *link = (nx_link_t*) pn_link_get_context(pn_link);
    if (!link)
        return 0;

    nx_node_t *node = link->node;
    if (!node)
        return 0;

    return node->ntype->writable_handler(node->context, link);
}


static void process_receive(pn_delivery_t *delivery)
{
    pn_link_t *pn_link = pn_delivery_link(delivery);
    nx_link_t *link    = (nx_link_t*) pn_link_get_context(pn_link);

    if (link) {
        nx_node_t *node = link->node;
        if (node) {
            node->ntype->rx_handler(node->context, link, delivery);
            return;
        }
    }

    //
    // Reject the delivery if we couldn't find a node to handle it
    //
    pn_link_advance(pn_link);
    pn_link_flow(pn_link, 1);
    pn_delivery_update(delivery, PN_REJECTED);
    pn_delivery_settle(delivery);
}


static void do_send(pn_delivery_t *delivery)
{
    pn_link_t *pn_link = pn_delivery_link(delivery);
    nx_link_t *link    = (nx_link_t*) pn_link_get_context(pn_link);

    if (link) {
        nx_node_t *node = link->node;
        if (node) {
            node->ntype->tx_handler(node->context, link, delivery);
            return;
        }
    }

    // TODO - Cancel the delivery
}


static void do_updated(pn_delivery_t *delivery)
{
    pn_link_t *pn_link = pn_delivery_link(delivery);
    nx_link_t *link    = (nx_link_t*) pn_link_get_context(pn_link);

    if (link) {
        nx_node_t *node = link->node;
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
    pn_link_t *pn_link = pn_link_head(conn, 0);
    while (pn_link) {
        nx_link_t *link = (nx_link_t*) pn_link_get_context(pn_link);
        nx_node_t *node = link->node;
        if (node)
            node->ntype->link_detach_handler(node->context, link, 0);
        pn_link_close(pn_link);
        free_nx_link_t(link);
        pn_link = pn_link_next(pn_link, 0);
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


static int process_handler(void* unused, pn_connection_t *conn)
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
            setup_outgoing_link(pn_link);
        else
            setup_incoming_link(pn_link);
        pn_link = pn_link_next(pn_link, PN_LOCAL_UNINIT);
        event_count++;
    }


    // Step 2: Now drain all the pending deliveries from the connection's
    // work queue and process them

    delivery = pn_work_head(conn);
    while (delivery) {
        if      (pn_delivery_readable(delivery))
            process_receive(delivery);
        else if (pn_delivery_writable(delivery))
            do_send(delivery);

        if (pn_delivery_updated(delivery)) 
            do_updated(delivery);

        delivery = pn_work_next(delivery);
        event_count++;
    }

    //
    // Step 2.5: Traverse all of the links on the connection looking for
    // outgoing links with non-zero credit.  Call the attached node's
    // writable handler for such links.
    //
    pn_link = pn_link_head(conn, PN_LOCAL_ACTIVE | PN_REMOTE_ACTIVE);
    while (pn_link) {
        assert(pn_session_connection(pn_link_session(pn_link)) == conn);
        if (pn_link_is_sender(pn_link) && pn_link_credit(pn_link) > 0)
            event_count += do_writable(pn_link);
        pn_link = pn_link_next(pn_link, PN_LOCAL_ACTIVE | PN_REMOTE_ACTIVE);
    }

    // Step 3: Clean up any links or sessions that have been closed by the
    // remote.  If the connection has been closed remotely, clean that up
    // also.

    // teardown any terminating links
    pn_link = pn_link_head(conn, PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED);
    while (pn_link) {
        nx_link_t *link = (nx_link_t*) pn_link_get_context(pn_link);
        nx_node_t *node = link->node;
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


static void open_handler(nx_connection_t *conn, nx_direction_t dir)
{
    const nx_node_type_t *nt;

    //
    // Note the locking structure in this function.  Generally this would be unsafe, but since
    // this particular list is only ever appended to and never has items inserted or deleted,
    // this usage is safe in this case.
    //
    sys_mutex_lock(lock);
    nxc_node_type_t *nt_item = DEQ_HEAD(node_type_list);
    sys_mutex_unlock(lock);

    pn_connection_open(nx_connection_pn(conn));

    while (nt_item) {
        nt = nt_item->ntype;
        if (dir == NX_INCOMING) {
            if (nt->inbound_conn_open_handler)
                nt->inbound_conn_open_handler(nt->type_context, conn);
        } else {
            if (nt->outbound_conn_open_handler)
                nt->outbound_conn_open_handler(nt->type_context, conn);
        }

        sys_mutex_lock(lock);
        nt_item = DEQ_NEXT(nt_item);
        sys_mutex_unlock(lock);
    }
}


static int handler(void* context, nx_conn_event_t event, nx_connection_t *nx_conn)
{
    pn_connection_t *conn = nx_connection_pn(nx_conn);

    switch (event) {
    case NX_CONN_EVENT_LISTENER_OPEN:  open_handler(nx_conn, NX_INCOMING);   break;
    case NX_CONN_EVENT_CONNECTOR_OPEN: open_handler(nx_conn, NX_OUTGOING);   break;
    case NX_CONN_EVENT_CLOSE:          return close_handler(context, conn);
    case NX_CONN_EVENT_PROCESS:        return process_handler(context, conn);
    }

    return 0;
}


void nx_container_initialize(void)
{
    nx_log(module, LOG_TRACE, "Container Initializing");

    // TODO - move allocator init to server?
    const nx_allocator_config_t *alloc_config = nx_allocator_default_config();
    nx_allocator_initialize(alloc_config);

    node_type_map = hash(6,  4, 1);  // 64 buckets, item batches of 4
    node_map      = hash(10, 32, 0); // 1K buckets, item batches of 32
    lock          = sys_mutex();
    default_node  = 0;
    DEQ_INIT(node_type_list);

    nx_server_set_conn_handler(handler);
}


void nx_container_finalize(void)
{
}


int nx_container_register_node_type(const nx_node_type_t *nt)
{
    int result;
    nx_field_iterator_t *iter = nx_field_iterator_string(nt->type_name, ITER_VIEW_ALL);
    nxc_node_type_t     *nt_item = NEW(nxc_node_type_t);
    DEQ_ITEM_INIT(nt_item);
    nt_item->ntype = nt;

    sys_mutex_lock(lock);
    result = hash_insert_const(node_type_map, iter, nt);
    DEQ_INSERT_TAIL(node_type_list, nt_item);
    sys_mutex_unlock(lock);

    nx_field_iterator_free(iter);
    if (result < 0)
        return result;
    nx_log(module, LOG_TRACE, "Node Type Registered - %s", nt->type_name);

    return 0;
}


void nx_container_set_default_node_type(const nx_node_type_t *nt,
                                        void                 *context,
                                        nx_dist_mode_t        supported_dist)
{
    if (default_node)
        nx_container_destroy_node(default_node);

    if (nt) {
        default_node = nx_container_create_node(nt, 0, context, supported_dist, NX_LIFE_PERMANENT);
        nx_log(module, LOG_TRACE, "Node of type '%s' installed as default node", nt->type_name);
    } else {
        default_node = 0;
        nx_log(module, LOG_TRACE, "Default node removed");
    }
}


nx_node_t *nx_container_create_node(const nx_node_type_t *nt,
                                    const char           *name,
                                    void                 *context,
                                    nx_dist_mode_t        supported_dist,
                                    nx_lifetime_policy_t  life_policy)
{
    int result;
    nx_node_t *node = new_nx_node_t();
    if (!node)
        return 0;

    node->ntype          = nt;
    node->name           = 0;
    node->context        = context;
    node->supported_dist = supported_dist;
    node->life_policy    = life_policy;

    if (name) {
        nx_field_iterator_t *iter = nx_field_iterator_string(name, ITER_VIEW_ALL);
        sys_mutex_lock(lock);
        result = hash_insert(node_map, iter, node);
        sys_mutex_unlock(lock);
        nx_field_iterator_free(iter);
        if (result < 0) {
            free_nx_node_t(node);
            return 0;
        }

        node->name = (char*) malloc(strlen(name) + 1);
        strcpy(node->name, name);
    }

    if (name)
        nx_log(module, LOG_TRACE, "Node of type '%s' created with name '%s'", nt->type_name, name);

    return node;
}


void nx_container_destroy_node(nx_node_t *node)
{
    if (node->name) {
        nx_field_iterator_t *iter = nx_field_iterator_string(node->name, ITER_VIEW_ALL);
        sys_mutex_lock(lock);
        hash_remove(node_map, iter);
        sys_mutex_unlock(lock);
        nx_field_iterator_free(iter);
        free(node->name);
    }

    free_nx_node_t(node);
}


void nx_container_node_set_context(nx_node_t *node, void *node_context)
{
    node->context = node_context;
}


nx_dist_mode_t nx_container_node_get_dist_modes(const nx_node_t *node)
{
    return node->supported_dist;
}


nx_lifetime_policy_t nx_container_node_get_life_policy(const nx_node_t *node)
{
    return node->life_policy;
}


nx_link_t *nx_link(nx_node_t *node, nx_connection_t *conn, nx_direction_t dir, const char* name)
{
    pn_session_t *sess = pn_session(nx_connection_pn(conn));
    nx_link_t    *link = new_nx_link_t();

    if (dir == NX_OUTGOING)
        link->pn_link = pn_sender(sess, name);
    else
        link->pn_link = pn_receiver(sess, name);
    link->context = node->context;
    link->node    = node;

    pn_link_set_context(link->pn_link, link);

    pn_session_open(sess);

    return link;
}


void nx_link_set_context(nx_link_t *link, void *context)
{
    link->context = context;
}


void *nx_link_get_context(nx_link_t *link)
{
    return link->context;
}


pn_link_t *nx_link_pn(nx_link_t *link)
{
    return link->pn_link;
}


pn_terminus_t *nx_link_source(nx_link_t *link)
{
    return pn_link_source(link->pn_link);
}


pn_terminus_t *nx_link_target(nx_link_t *link)
{
    return pn_link_target(link->pn_link);
}


pn_terminus_t *nx_link_remote_source(nx_link_t *link)
{
    return pn_link_remote_source(link->pn_link);
}


pn_terminus_t *nx_link_remote_target(nx_link_t *link)
{
    return pn_link_remote_target(link->pn_link);
}


void nx_link_activate(nx_link_t *link)
{
    if (!link || !link->pn_link)
        return;

    pn_session_t *sess = pn_link_session(link->pn_link);
    if (!sess)
        return;

    pn_connection_t *conn = pn_session_connection(sess);
    if (!conn)
        return;

    nx_connection_t *ctx = pn_connection_get_context(conn);
    if (!ctx)
        return;

    nx_server_activate(ctx);
}


void nx_link_close(nx_link_t *link)
{
    pn_link_close(link->pn_link);
}


