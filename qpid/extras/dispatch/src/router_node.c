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
#include <qpid/dispatch.h>
#include "dispatch_private.h"

static char *module = "ROUTER";

static void dx_router_python_setup(dx_router_t *router);
static void dx_pyrouter_tick(dx_router_t *router);

static char *router_address = "_local/qdxrouter";
static char *local_prefix   = "_local/";
//static char *topo_prefix    = "_topo/";

/**
 * Address Types and Processing:
 *
 *   Address                              Hash Key       onReceive         onEmit
 *   =============================================================================
 *   _local/<local>                       L<local>       handler           forward
 *   _topo/<area>/<router>/<local>        A<area>        forward           forward
 *   _topo/<my-area>/<router>/<local>     R<router>      forward           forward
 *   _topo/<my-area>/<my-router>/<local>  L<local>       forward+handler   forward
 *   _topo/<area>/all/<local>             A<area>        forward           forward
 *   _topo/<my-area>/all/<local>          L<local>       forward+handler   forward
 *   _topo/all/all/<local>                L<local>       forward+handler   forward
 *   <mobile>                             M<mobile>      forward+handler   forward
 */


typedef struct dx_router_link_t dx_router_link_t;
typedef struct dx_router_node_t dx_router_node_t;


typedef enum {
    DX_LINK_ENDPOINT,   // A link to a connected endpoint
    DX_LINK_ROUTER,     // A link to a peer router in the same area
    DX_LINK_AREA        // A link to a peer router in a different area (area boundary)
} dx_link_type_t;


struct dx_router_link_t {
    DEQ_LINKS(dx_router_link_t);
    dx_direction_t     link_direction;
    dx_link_type_t     link_type;
    dx_address_t      *owning_addr;     // [ref] Address record that owns this link
    dx_link_t         *link;            // [own] Link pointer
    dx_router_link_t  *connected_link;  // [ref] If this is a link-route, reference the connected link
    dx_router_link_t  *peer_link;       // [ref] If this is a bidirectional link-route, reference the peer link
    dx_message_list_t  out_fifo;        // Message FIFO for outgoing messages
};

ALLOC_DECLARE(dx_router_link_t);
ALLOC_DEFINE(dx_router_link_t);
DEQ_DECLARE(dx_router_link_t, dx_router_link_list_t);

struct dx_router_node_t {
    DEQ_LINKS(dx_router_node_t);
    const char       *id;
    dx_router_node_t *next_hop;   // Next hop node _if_ this is not a neighbor node
    dx_router_link_t *peer_link;  // Outgoing link _if_ this is a neighbor node
    // list of valid origins (pointers to router_node) - (bit masks?)
};

ALLOC_DECLARE(dx_router_node_t);
ALLOC_DEFINE(dx_router_node_t);
DEQ_DECLARE(dx_router_node_t, dx_router_node_list_t);


struct dx_address_t {
    dx_router_message_cb   handler;          // In-Process Consumer
    void                  *handler_context;
    dx_router_link_list_t  rlinks;           // Locally-Connected Consumers
    dx_router_node_list_t  rnodes;           // Remotely-Connected Consumers
};

ALLOC_DECLARE(dx_address_t);
ALLOC_DEFINE(dx_address_t);


struct dx_router_t {
    dx_dispatch_t         *dx;
    const char            *router_area;
    const char            *router_id;
    dx_node_t             *node;
    dx_router_link_list_t  in_links;
    dx_router_node_list_t  routers;
    dx_message_list_t      in_fifo;
    sys_mutex_t           *lock;
    dx_timer_t            *timer;
    hash_t                *out_hash;
    uint64_t               dtag;
    PyObject              *pyRouter;
    PyObject              *pyTick;
};


/**
 * Outbound Delivery Handler
 */
static void router_tx_handler(void* context, dx_link_t *link, pn_delivery_t *delivery)
{
    dx_router_t      *router  = (dx_router_t*) context;
    pn_link_t        *pn_link = pn_delivery_link(delivery);
    dx_router_link_t *rlink   = (dx_router_link_t*) dx_link_get_context(link);
    dx_message_t     *msg;
    size_t            size;

    sys_mutex_lock(router->lock);
    msg = DEQ_HEAD(rlink->out_fifo);
    if (!msg) {
        // TODO - Recind the delivery
        sys_mutex_unlock(router->lock);
        return;
    }

    DEQ_REMOVE_HEAD(rlink->out_fifo);
    size = (DEQ_SIZE(rlink->out_fifo));
    sys_mutex_unlock(router->lock);

    dx_message_send(msg, pn_link);

    //
    // If there is no incoming delivery, it was pre-settled.  In this case,
    // we must pre-settle the outgoing delivery as well.
    //
    if (dx_message_in_delivery(msg)) {
        pn_delivery_set_context(delivery, (void*) msg);
        dx_message_set_out_delivery(msg, delivery);
    } else {
        pn_delivery_settle(delivery);
        dx_free_message(msg);
    }

    pn_link_advance(pn_link);
    pn_link_offered(pn_link, size);
}


/**
 * Inbound Delivery Handler
 */
static void router_rx_handler(void* context, dx_link_t *link, pn_delivery_t *delivery)
{
    dx_router_t      *router  = (dx_router_t*) context;
    pn_link_t        *pn_link = pn_delivery_link(delivery);
    dx_router_link_t *rlink   = (dx_router_link_t*) dx_link_get_context(link);
    dx_message_t     *msg;
    int               valid_message = 0;

    //
    // Receive the message into a local representation.  If the returned message
    // pointer is NULL, we have not yet received a complete message.
    //
    sys_mutex_lock(router->lock);
    msg = dx_message_receive(delivery);
    sys_mutex_unlock(router->lock);

    if (!msg)
        return;

    //
    // Consume the delivery and issue a replacement credit
    //
    pn_link_advance(pn_link);
    pn_link_flow(pn_link, 1);

    sys_mutex_lock(router->lock);

    //
    // Handle the Link-Routing case.  If this incoming link is associated with a connected
    // link, simply deliver the message to the outgoing link.  There is no need to validate
    // the message in this case.
    //
    if (rlink->connected_link) {
        dx_router_link_t *clink      = rlink->connected_link;
        pn_link_t        *pn_outlink = dx_link_pn(clink->link);
        DEQ_INSERT_TAIL(clink->out_fifo, msg);
        sys_mutex_unlock(router->lock);

        pn_link_offered(pn_outlink, DEQ_SIZE(clink->out_fifo));
        dx_link_activate(clink->link);
        sys_mutex_unlock(router->lock);

        return;
    }

    //
    // We are performing Message-Routing, therefore we will need to validate the message
    // through the Properties section so we can access the TO field.
    //
    dx_message_t         *in_process_copy = 0;
    dx_router_message_cb  handler         = 0;
    void                 *handler_context = 0;

    valid_message = dx_message_check(msg, DX_DEPTH_PROPERTIES);

    if (valid_message) {
        dx_field_iterator_t *iter = dx_message_field_iterator(msg, DX_FIELD_TO);
        dx_address_t        *addr;
        if (iter) {
            dx_field_iterator_reset_view(iter, ITER_VIEW_ADDRESS_HASH);
            hash_retrieve(router->out_hash, iter, (void*) &addr);
            dx_field_iterator_reset_view(iter, ITER_VIEW_NO_HOST);
            int is_local = dx_field_iterator_prefix(iter, local_prefix);
            dx_field_iterator_free(iter);

            if (addr) {
                //
                // To field is valid and contains a known destination.  Handle the various
                // cases for forwarding.
                //

                //
                // Forward to the in-process handler for this message if there is one.  The
                // actual invocation of the handler will occur later after we've released
                // the lock.
                //
                if (addr->handler) {
                    in_process_copy = dx_message_copy(msg);
                    handler         = addr->handler;
                    handler_context = addr->handler_context;
                }

                //
                // If the address form is local (i.e. is prefixed by _local), don't forward
                // outside of the router process.
                //
                if (!is_local) {
                    //
                    // Forward to all of the local links receiving this address.
                    //
                    dx_router_link_t *dest_link = DEQ_HEAD(addr->rlinks);
                    while (dest_link) {
                        pn_link_t    *pn_outlink = dx_link_pn(dest_link->link);
                        dx_message_t *copy       = dx_message_copy(msg);
                        DEQ_INSERT_TAIL(dest_link->out_fifo, copy);
                        pn_link_offered(pn_outlink, DEQ_SIZE(dest_link->out_fifo));
                        dx_link_activate(dest_link->link);
                        dest_link = DEQ_NEXT(dest_link);
                    }

                    //
                    // Forward to the next-hops for remote destinations.
                    //
                    dx_router_node_t *dest_node = DEQ_HEAD(addr->rnodes);
                    while (dest_node) {
                        if (dest_node->next_hop)
                            dest_link = dest_node->next_hop->peer_link;
                        else
                            dest_link = dest_node->peer_link;
                        if (dest_link) {
                            pn_link_t    *pn_outlink = dx_link_pn(dest_link->link);
                            dx_message_t *copy       = dx_message_copy(msg);
                            DEQ_INSERT_TAIL(dest_link->out_fifo, copy);
                            pn_link_offered(pn_outlink, DEQ_SIZE(dest_link->out_fifo));
                            dx_link_activate(dest_link->link);
                        }
                        dest_node = DEQ_NEXT(dest_node);
                    }
                }

            } else {
                //
                // To field contains an unknown address.  Release the message.
                //
                // TODO - Undeliverable processing
                pn_delivery_update(delivery, PN_RELEASED);
                pn_delivery_settle(delivery);
            }

            //
            // Since we are message-routing, there is no end-to-end disposition or
            // settlement.  Accept and settle the delivery now.
            //
            pn_delivery_update(delivery, PN_ACCEPTED);
            pn_delivery_settle(delivery);
        }
    } else {
        //
        // Message is invalid.  Reject the message.
        //
        pn_delivery_update(delivery, PN_REJECTED);
        pn_delivery_settle(delivery);
        pn_delivery_set_context(delivery, 0);
    }

    sys_mutex_unlock(router->lock);
    dx_free_message(msg);

    //
    // Invoke the in-process handler now that the lock is released.
    //
    if (handler)
        handler(handler_context, in_process_copy);
}


/**
 * Delivery Disposition Handler
 */
static void router_disp_handler(void* context, dx_link_t *link, pn_delivery_t *delivery)
{
    pn_link_t        *pn_link = pn_delivery_link(delivery);
    //dx_router_link_t *rlink   = (dx_router_link_t*) dx_link_get_context(link);

    //
    // TODO - Propagate disposition and settlement between deliveries on a link-routed
    //        link pair.
    //
    return;

    if (pn_link_is_sender(pn_link)) {
        uint64_t       disp     = pn_delivery_remote_state(delivery);
        dx_message_t  *msg      = pn_delivery_get_context(delivery);
        pn_delivery_t *activate = 0;

        if (msg) {
            assert(delivery == dx_message_out_delivery(msg));
            if (disp != 0) {
                activate = dx_message_in_delivery(msg);
                pn_delivery_update(activate, disp);
                // TODO - handling of the data accompanying RECEIVED/MODIFIED
            }

            if (pn_delivery_settled(delivery)) {
                //
                // Downstream delivery has been settled.  Propagate the settlement
                // upstream.
                //
                activate = dx_message_in_delivery(msg);
                pn_delivery_settle(activate);
                pn_delivery_settle(delivery);
                dx_free_message(msg);
            }

            if (activate) {
                //
                // Activate the upstream/incoming link so that the settlement will
                // get pushed out.
                //
                dx_link_t *act_link = (dx_link_t*) pn_link_get_context(pn_delivery_link(activate));
                dx_link_activate(act_link);
            }

            return;
        }
    } else {
        // TODO - Handle disposition updates from upstream
    }
}


/**
 * New Incoming Link Handler
 */
static int router_incoming_link_handler(void* context, dx_link_t *link)
{
    dx_router_t      *router  = (dx_router_t*) context;
    dx_router_link_t *rlink   = new_dx_router_link_t();
    pn_link_t        *pn_link = dx_link_pn(link);

    DEQ_ITEM_INIT(rlink);
    rlink->link_direction = DX_INCOMING;
    rlink->link_type      = DX_LINK_ENDPOINT;
    rlink->owning_addr    = 0;
    rlink->link           = link;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    DEQ_INIT(rlink->out_fifo);  // Won't be used

    dx_link_set_context(link, rlink);

    sys_mutex_lock(router->lock);
    DEQ_INSERT_TAIL(router->in_links, rlink);
    sys_mutex_unlock(router->lock);

    pn_terminus_copy(pn_link_source(pn_link), pn_link_remote_source(pn_link));
    pn_terminus_copy(pn_link_target(pn_link), pn_link_remote_target(pn_link));
    pn_link_flow(pn_link, 1000);
    pn_link_open(pn_link);

    //
    // TODO - If the address has link-route semantics, create all associated
    //        links needed to go with this one.
    //

    return 0;
}


/**
 * New Outgoing Link Handler
 */
static int router_outgoing_link_handler(void* context, dx_link_t *link)
{
    dx_router_t *router  = (dx_router_t*) context;
    pn_link_t   *pn_link = dx_link_pn(link);
    const char  *r_tgt   = pn_terminus_get_address(pn_link_remote_target(pn_link));

    if (!r_tgt) {
        pn_link_close(pn_link);
        return 0;
    }

    dx_field_iterator_t *iter  = dx_field_iterator_string(r_tgt, ITER_VIEW_NO_HOST);
    dx_router_link_t    *rlink = new_dx_router_link_t();

    int is_router = dx_field_iterator_equal(iter, (unsigned char*) router_address);

    DEQ_ITEM_INIT(rlink);
    rlink->link_direction = DX_OUTGOING;
    rlink->link_type      = is_router ? DX_LINK_ROUTER : DX_LINK_ENDPOINT;
    rlink->link           = link;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    DEQ_INIT(rlink->out_fifo);

    dx_link_set_context(link, rlink);

    dx_field_iterator_reset_view(iter, ITER_VIEW_ADDRESS_HASH);
    dx_address_t *addr;

    sys_mutex_lock(router->lock);
    hash_retrieve(router->out_hash, iter, (void**) &addr);
    if (!addr) {
        addr = new_dx_address_t();
        addr->handler         = 0;
        addr->handler_context = 0;
        DEQ_INIT(addr->rlinks);
        DEQ_INIT(addr->rnodes);
        hash_insert(router->out_hash, iter, addr);
    }
    dx_field_iterator_free(iter);

    rlink->owning_addr = addr;
    DEQ_INSERT_TAIL(addr->rlinks, rlink);

    pn_terminus_copy(pn_link_source(pn_link), pn_link_remote_source(pn_link));
    pn_terminus_copy(pn_link_target(pn_link), pn_link_remote_target(pn_link));
    pn_link_open(pn_link);
    sys_mutex_unlock(router->lock);
    dx_log(module, LOG_TRACE, "Registered new local address: %s", r_tgt);
    return 0;
}


/**
 * Outgoing Link Writable Handler
 */
static int router_writable_link_handler(void* context, dx_link_t *link)
{
    dx_router_t      *router = (dx_router_t*) context;
    int               grant_delivery = 0;
    pn_delivery_t    *delivery;
    dx_router_link_t *rlink = (dx_router_link_t*) dx_link_get_context(link);
    pn_link_t        *pn_link = dx_link_pn(link);
    uint64_t          tag;

    sys_mutex_lock(router->lock);
    if (DEQ_SIZE(rlink->out_fifo) > 0) {
        grant_delivery = 1;
        tag = router->dtag++;
    }
    sys_mutex_unlock(router->lock);

    if (grant_delivery) {
        pn_delivery(pn_link, pn_dtag((char*) &tag, 8));
        delivery = pn_link_current(pn_link);
        if (delivery) {
            router_tx_handler(context, link, delivery);
            return 1;
        }
    }

    return 0;
}


/**
 * Link Detached Handler
 */
static int router_link_detach_handler(void* context, dx_link_t *link, int closed)
{
    dx_router_t      *router  = (dx_router_t*) context;
    pn_link_t        *pn_link = dx_link_pn(link);
    dx_router_link_t *rlink   = (dx_router_link_t*) dx_link_get_context(link);
    const char       *r_tgt   = pn_terminus_get_address(pn_link_remote_target(pn_link));

    if (!rlink)
        return 0;

    sys_mutex_lock(router->lock);
    if (pn_link_is_sender(pn_link)) {
        DEQ_REMOVE(rlink->owning_addr->rlinks, rlink);

        if ((rlink->owning_addr->handler == 0) &&
            (DEQ_SIZE(rlink->owning_addr->rlinks) == 0) &&
            (DEQ_SIZE(rlink->owning_addr->rnodes) == 0)) {
            dx_field_iterator_t *iter = dx_field_iterator_string(r_tgt, ITER_VIEW_ADDRESS_HASH);
            dx_address_t        *addr;
            if (iter) {
                hash_retrieve(router->out_hash, iter, (void**) &addr);
                if (addr == rlink->owning_addr) {
                    hash_remove(router->out_hash, iter);
                    free_dx_router_link_t(rlink);
                    free_dx_address_t(addr);
                    dx_log(module, LOG_TRACE, "Removed local address: %s", r_tgt);
                }
                dx_field_iterator_free(iter);
            }
        }
    } else
        DEQ_REMOVE(router->in_links, rlink);

    sys_mutex_unlock(router->lock);
    return 0;
}


static void router_inbound_open_handler(void *type_context, dx_connection_t *conn)
{
}


static void router_outbound_open_handler(void *type_context, dx_connection_t *conn)
{
    // TODO - Make sure this connection is annotated as an inter-router transport.
    //        Ignore otherwise

    dx_router_t         *router = (dx_router_t*) type_context;
    dx_field_iterator_t *aiter  = dx_field_iterator_string(router_address, ITER_VIEW_ADDRESS_HASH);
    dx_link_t           *sender;
    dx_link_t           *receiver;
    dx_router_link_t    *rlink;

    //
    // Create an incoming link and put it in the in-links collection.  The address
    // of the remote source of this link is '_local/qdxrouter'.
    //
    receiver = dx_link(router->node, conn, DX_INCOMING, "inter-router-rx");
    pn_terminus_set_address(dx_link_remote_source(receiver), router_address);
    pn_terminus_set_address(dx_link_target(receiver), router_address);

    rlink = new_dx_router_link_t();

    DEQ_ITEM_INIT(rlink);
    rlink->link_direction = DX_INCOMING;
    rlink->link_type      = DX_LINK_ROUTER;
    rlink->owning_addr    = 0;
    rlink->link           = receiver;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    DEQ_INIT(rlink->out_fifo);  // Won't be used

    dx_link_set_context(receiver, rlink);

    sys_mutex_lock(router->lock);
    DEQ_INSERT_TAIL(router->in_links, rlink);
    sys_mutex_unlock(router->lock);

    //
    // Create an outgoing link with a local source of '_local/qdxrouter' and place
    // it in the routing table.
    //
    sender = dx_link(router->node, conn, DX_OUTGOING, "inter-router-tx");
    pn_terminus_set_address(dx_link_remote_target(sender), router_address);
    pn_terminus_set_address(dx_link_source(sender), router_address);

    rlink = new_dx_router_link_t();

    DEQ_ITEM_INIT(rlink);
    rlink->link_direction = DX_OUTGOING;
    rlink->link_type      = DX_LINK_ROUTER;
    rlink->link           = sender;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    DEQ_INIT(rlink->out_fifo);

    dx_link_set_context(sender, rlink);

    dx_address_t *addr;

    sys_mutex_lock(router->lock);
    hash_retrieve(router->out_hash, aiter, (void**) &addr);
    if (!addr) {
        addr = new_dx_address_t();
        addr->handler         = 0;
        addr->handler_context = 0;
        DEQ_INIT(addr->rlinks);
        DEQ_INIT(addr->rnodes);
        hash_insert(router->out_hash, aiter, addr);
    }

    rlink->owning_addr = addr;
    DEQ_INSERT_TAIL(addr->rlinks, rlink);
    sys_mutex_unlock(router->lock);

    pn_link_open(dx_link_pn(receiver));
    pn_link_open(dx_link_pn(sender));
    pn_link_flow(dx_link_pn(receiver), 1000);
    dx_field_iterator_free(aiter);
}


static void dx_router_timer_handler(void *context)
{
    dx_router_t *router = (dx_router_t*) context;

    //
    // Periodic processing.
    //
    dx_pyrouter_tick(router);

    dx_timer_schedule(router->timer, 1000);
}


static dx_node_type_t router_node = {"router", 0, 0,
                                     router_rx_handler,
                                     router_tx_handler,
                                     router_disp_handler,
                                     router_incoming_link_handler,
                                     router_outgoing_link_handler,
                                     router_writable_link_handler,
                                     router_link_detach_handler,
                                     0,   // node_created_handler
                                     0,   // node_destroyed_handler
                                     router_inbound_open_handler,
                                     router_outbound_open_handler };
static int type_registered = 0;


dx_router_t *dx_router(dx_dispatch_t *dx, const char *area, const char *id)
{
    if (!type_registered) {
        type_registered = 1;
        dx_container_register_node_type(dx, &router_node);
    }

    dx_router_t *router = NEW(dx_router_t);

    router_node.type_context = router;

    router->dx          = dx;
    router->router_area = area;
    router->router_id   = id;
    router->node        = dx_container_set_default_node_type(dx, &router_node, (void*) router, DX_DIST_BOTH);
    DEQ_INIT(router->in_links);
    DEQ_INIT(router->routers);
    DEQ_INIT(router->in_fifo);
    router->lock        = sys_mutex();
    router->timer       = dx_timer(dx, dx_router_timer_handler, (void*) router);
    router->out_hash    = hash(10, 32, 0);
    router->dtag        = 1;
    router->pyRouter    = 0;
    router->pyTick      = 0;


    //
    // Inform the field iterator module of this router's id and area.  The field iterator
    // uses this to offload some of the address-processing load from the router.
    //
    dx_field_iterator_set_address(area, id);

    //
    // Set up the usage of the embedded python router module.
    //
    dx_python_start();

    dx_log(module, LOG_INFO, "Router started, area=%s id=%s", area, id);

    return router;
}


void dx_router_setup_agent(dx_dispatch_t *dx)
{
    dx_router_python_setup(dx->router);
    dx_timer_schedule(dx->router->timer, 1000);

    // TODO
}


void dx_router_free(dx_router_t *router)
{
    dx_container_set_default_node_type(router->dx, 0, 0, DX_DIST_BOTH);
    sys_mutex_free(router->lock);
    free(router);
    dx_python_stop();
}


dx_address_t *dx_router_register_address(dx_dispatch_t        *dx,
                                         const char           *address,
                                         dx_router_message_cb  handler,
                                         void                 *context)
{
    char                 addr_string[1000];
    dx_router_t         *router = dx->router;
    dx_address_t        *addr;
    dx_field_iterator_t *iter;

    strcpy(addr_string, "L");  // Local Hash-Key Space
    strcat(addr_string, address);
    iter = dx_field_iterator_string(addr_string, ITER_VIEW_NO_HOST);

    sys_mutex_lock(router->lock);
    hash_retrieve(router->out_hash, iter, (void**) &addr);
    if (!addr) {
        addr = new_dx_address_t();
        addr->handler         = 0;
        addr->handler_context = 0;
        DEQ_INIT(addr->rlinks);
        DEQ_INIT(addr->rnodes);
        hash_insert(router->out_hash, iter, addr);
    }
    dx_field_iterator_free(iter);

    addr->handler         = handler;
    addr->handler_context = context;

    sys_mutex_unlock(router->lock);

    dx_log(module, LOG_TRACE, "In-Process Address Registered: %s", address);
    return addr;
}


void dx_router_unregister_address(dx_address_t *ad)
{
    //free_dx_address_t(ad);
}


void dx_router_send(dx_dispatch_t       *dx,
                    dx_field_iterator_t *address,
                    dx_message_t        *msg)
{
    dx_router_t  *router = dx->router;
    dx_address_t *addr;

    dx_field_iterator_reset_view(address, ITER_VIEW_ADDRESS_HASH);
    sys_mutex_lock(router->lock);
    hash_retrieve(router->out_hash, address, (void*) &addr);
    if (addr) {
        //
        // Forward to all of the local links receiving this address.
        //
        dx_router_link_t *dest_link = DEQ_HEAD(addr->rlinks);
        while (dest_link) {
            pn_link_t    *pn_outlink = dx_link_pn(dest_link->link);
            dx_message_t *copy       = dx_message_copy(msg);
            DEQ_INSERT_TAIL(dest_link->out_fifo, copy);
            pn_link_offered(pn_outlink, DEQ_SIZE(dest_link->out_fifo));
            dx_link_activate(dest_link->link);
            dest_link = DEQ_NEXT(dest_link);
        }

        //
        // Forward to the next-hops for remote destinations.
        //
        dx_router_node_t *dest_node = DEQ_HEAD(addr->rnodes);
        while (dest_node) {
            if (dest_node->next_hop)
                dest_link = dest_node->next_hop->peer_link;
            else
                dest_link = dest_node->peer_link;
            if (dest_link) {
                pn_link_t    *pn_outlink = dx_link_pn(dest_link->link);
                dx_message_t *copy       = dx_message_copy(msg);
                DEQ_INSERT_TAIL(dest_link->out_fifo, copy);
                pn_link_offered(pn_outlink, DEQ_SIZE(dest_link->out_fifo));
                dx_link_activate(dest_link->link);
            }
            dest_node = DEQ_NEXT(dest_node);
        }
    }
    sys_mutex_unlock(router->lock); // TOINVESTIGATE Move this higher?
}


void dx_router_send2(dx_dispatch_t *dx,
                     const char    *address,
                     dx_message_t  *msg)
{
    dx_field_iterator_t *iter = dx_field_iterator_string(address, ITER_VIEW_ADDRESS_HASH);
    dx_router_send(dx, iter, msg);
    dx_field_iterator_free(iter);
}


//===============================================================================
// Python Router Adapter
//===============================================================================

typedef struct {
    PyObject_HEAD
    dx_router_t *router;
} RouterAdapter;


static PyObject* dx_router_add_route(PyObject *self, PyObject *args)
{
    //RouterAdapter *adapter = (RouterAdapter*) self;
    const char    *addr;
    const char    *peer;

    if (!PyArg_ParseTuple(args, "ss", &addr, &peer))
        return 0;

    // TODO

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_router_del_route(PyObject *self, PyObject *args)
{
    //RouterAdapter *adapter = (RouterAdapter*) self;
    const char    *addr;
    const char    *peer;

    if (!PyArg_ParseTuple(args, "ss", &addr, &peer))
        return 0;

    // TODO

    Py_INCREF(Py_None);
    return Py_None;
}


static PyMethodDef RouterAdapter_methods[] = {
    {"add_route", dx_router_add_route, METH_VARARGS, "Add a newly discovered route"},
    {"del_route", dx_router_del_route, METH_VARARGS, "Delete a route"},
    {0, 0, 0, 0}
};

static PyTypeObject RouterAdapterType = {
    PyObject_HEAD_INIT(0)
    0,                         /* ob_size*/
    "dispatch.RouterAdapter",  /* tp_name*/
    sizeof(RouterAdapter),     /* tp_basicsize*/
    0,                         /* tp_itemsize*/
    0,                         /* tp_dealloc*/
    0,                         /* tp_print*/
    0,                         /* tp_getattr*/
    0,                         /* tp_setattr*/
    0,                         /* tp_compare*/
    0,                         /* tp_repr*/
    0,                         /* tp_as_number*/
    0,                         /* tp_as_sequence*/
    0,                         /* tp_as_mapping*/
    0,                         /* tp_hash */
    0,                         /* tp_call*/
    0,                         /* tp_str*/
    0,                         /* tp_getattro*/
    0,                         /* tp_setattro*/
    0,                         /* tp_as_buffer*/
    Py_TPFLAGS_DEFAULT,        /* tp_flags*/
    "Dispatch Router Adapter", /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    RouterAdapter_methods,     /* tp_methods */
    0,                         /* tp_members */
    0,                         /* tp_getset */
    0,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    0,                         /* tp_init */
    0,                         /* tp_alloc */
    0,                         /* tp_new */
    0,                         /* tp_free */
    0,                         /* tp_is_gc */
    0,                         /* tp_bases */
    0,                         /* tp_mro */
    0,                         /* tp_cache */
    0,                         /* tp_subclasses */
    0,                         /* tp_weaklist */
    0,                         /* tp_del */
    0                          /* tp_version_tag */
};


static void dx_router_python_setup(dx_router_t *router)
{
    PyObject *pDispatchModule = dx_python_module();

    RouterAdapterType.tp_new = PyType_GenericNew;
    if (PyType_Ready(&RouterAdapterType) < 0) {
        PyErr_Print();
        dx_log(module, LOG_CRITICAL, "Unable to initialize the Python Router Adapter");
        return;
    }

    Py_INCREF(&RouterAdapterType);
    PyModule_AddObject(pDispatchModule, "RouterAdapter", (PyObject*) &RouterAdapterType);

    //
    // Attempt to import the Python Router module
    //
    PyObject* pName;
    PyObject* pId;
    PyObject* pArea;
    PyObject* pModule;
    PyObject* pClass;
    PyObject* pArgs;

    pName   = PyString_FromString("router");
    pModule = PyImport_Import(pName);
    Py_DECREF(pName);
    if (!pModule) {
        dx_log(module, LOG_CRITICAL, "Can't Locate 'router' Python module");
        return;
    }

    pClass = PyObject_GetAttrString(pModule, "RouterEngine");
    if (!pClass || !PyClass_Check(pClass)) {
        dx_log(module, LOG_CRITICAL, "Can't Locate 'RouterEngine' class in the 'router' module");
        return;
    }

    PyObject *adapterType     = PyObject_GetAttrString(pDispatchModule, "RouterAdapter");
    PyObject *adapterInstance = PyObject_CallObject(adapterType, 0);
    assert(adapterInstance);

    ((RouterAdapter*) adapterInstance)->router = router;

    //
    // Constructor Arguments for RouterEngine
    //
    pArgs = PyTuple_New(3);

    // arg 0: adapter instance
    PyTuple_SetItem(pArgs, 0, adapterInstance);

    // arg 1: router_id
    pId = PyString_FromString(router->router_id);
    PyTuple_SetItem(pArgs, 1, pId);

    // arg 2: area id
    pArea = PyString_FromString(router->router_area);
    PyTuple_SetItem(pArgs, 2, pArea);

    //
    // Instantiate the router
    //
    router->pyRouter = PyInstance_New(pClass, pArgs, 0);
    Py_DECREF(pArgs);
    Py_DECREF(adapterType);

    if (!router->pyRouter) {
        PyErr_Print();
        dx_log(module, LOG_CRITICAL, "'RouterEngine' class cannot be instantiated");
        return;
    }

    router->pyTick = PyObject_GetAttrString(router->pyRouter, "handleTimerTick");
    if (!router->pyTick || !PyCallable_Check(router->pyTick)) {
        dx_log(module, LOG_CRITICAL, "'RouterEngine' class has no handleTimerTick method");
        return;
    }
}


static void dx_pyrouter_tick(dx_router_t *router)
{
    PyObject *pArgs;
    PyObject *pValue;

    pArgs  = PyTuple_New(0);
    pValue = PyObject_CallObject(router->pyTick, pArgs);
    if (PyErr_Occurred()) {
        PyErr_Print();
    }
    Py_DECREF(pArgs);
    if (pValue) {
        Py_DECREF(pValue);
    }
}

