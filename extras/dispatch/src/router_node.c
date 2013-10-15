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
#include "dispatch_private.h"
#include "router_private.h"

static char *module = "ROUTER";

static char *router_role   = "inter-router";
static char *local_prefix  = "_local/";
static char *topo_prefix   = "_topo/";
static char *direct_prefix;

/**
 * Address Types and Processing:
 *
 *   Address                              Hash Key       onReceive
 *   ===================================================================
 *   _local/<local>                       L<local>               handler
 *   _topo/<area>/<router>/<local>        A<area>        forward
 *   _topo/<my-area>/<router>/<local>     R<router>      forward
 *   _topo/<my-area>/<my-router>/<local>  L<local>               handler
 *   _topo/<area>/all/<local>             A<area>        forward
 *   _topo/<my-area>/all/<local>          L<local>       forward handler
 *   _topo/all/all/<local>                L<local>       forward handler
 *   <mobile>                             M<mobile>      forward handler
 */

ALLOC_DEFINE(dx_routed_event_t);
ALLOC_DEFINE(dx_router_link_t);
ALLOC_DEFINE(dx_router_node_t);
ALLOC_DEFINE(dx_router_ref_t);
ALLOC_DEFINE(dx_router_link_ref_t);
ALLOC_DEFINE(dx_address_t);
ALLOC_DEFINE(dx_router_conn_t);


void dx_router_add_link_ref_LH(dx_router_link_ref_list_t *ref_list, dx_router_link_t *link)
{
    dx_router_link_ref_t *ref = new_dx_router_link_ref_t();
    DEQ_ITEM_INIT(ref);
    ref->link = link;
    link->ref = ref;
    DEQ_INSERT_TAIL(*ref_list, ref);
}


void dx_router_del_link_ref_LH(dx_router_link_ref_list_t *ref_list, dx_router_link_t *link)
{
    if (link->ref) {
        DEQ_REMOVE(*ref_list, link->ref);
        free_dx_router_link_ref_t(link->ref);
        link->ref = 0;
    }
}


void dx_router_add_node_ref_LH(dx_router_ref_list_t *ref_list, dx_router_node_t *rnode)
{
    dx_router_ref_t *ref = new_dx_router_ref_t();
    DEQ_ITEM_INIT(ref);
    ref->router = rnode;
    rnode->ref_count++;
    DEQ_INSERT_TAIL(*ref_list, ref);
}


void dx_router_del_node_ref_LH(dx_router_ref_list_t *ref_list, dx_router_node_t *rnode)
{
    dx_router_ref_t *ref = DEQ_HEAD(*ref_list);
    while (ref) {
        if (ref->router == rnode) {
            DEQ_REMOVE(*ref_list, ref);
            free_dx_router_ref_t(ref);
            rnode->ref_count--;
            break;
        }
        ref = DEQ_NEXT(ref);
    }
}


/**
 * Check an address to see if it no longer has any associated destinations.
 * Depending on its policy, the address may be eligible for being closed out
 * (i.e. Logging its terminal statistics and freeing its resources).
 */
void dx_router_check_addr_LH(dx_router_t *router, dx_address_t *addr)
{
    if (addr == 0)
        return;

    if (addr->handler || DEQ_SIZE(addr->rlinks) > 0 || DEQ_SIZE(addr->rnodes) > 0)
        return;

    dx_hash_remove_by_handle(router->addr_hash, addr->hash_handle);
    DEQ_REMOVE(router->addrs, addr);
    dx_hash_handle_free(addr->hash_handle);
    free_dx_address_t(addr);
}


/**
 * Determine whether a connection is configured in the inter-router role.
 */
static int dx_router_connection_is_inter_router(const dx_connection_t *conn)
{
    if (!conn)
        return 0;

    const dx_server_config_t *cf = dx_connection_config(conn);
    if (cf && strcmp(cf->role, router_role) == 0)
        return 1;

    return 0;
}


/**
 * Determine whether a terminus has router capability
 */
static int dx_router_terminus_is_router(pn_terminus_t *term)
{
    pn_data_t *cap = pn_terminus_capabilities(term);

    pn_data_rewind(cap);
    pn_data_next(cap);
    if (cap && pn_data_type(cap) == PN_SYMBOL) {
        pn_bytes_t sym = pn_data_get_symbol(cap);
        if (sym.size == strlen(DX_CAPABILITY_ROUTER) &&
            strcmp(sym.start, DX_CAPABILITY_ROUTER) == 0)
            return 1;
    }

    return 0;
}


static void dx_router_generate_temp_addr(dx_router_t *router, char *buffer, size_t length)
{
    static const char *table = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+_";
    char     discriminator[11];
    long int rnd = random();
    int      idx;

    for (idx = 0; idx < 6; idx++)
        discriminator[idx] = table[(rnd >> (idx * 6)) & 63];
    discriminator[idx] = '\0';

    snprintf(buffer, length, "amqp:/%s%s/%s/temp.%s", topo_prefix, router->router_area, router->router_id, discriminator);
}


static int dx_router_find_mask_bit_LH(dx_router_t *router, dx_link_t *link)
{
    dx_router_conn_t *shared = (dx_router_conn_t*) dx_link_get_conn_context(link);
    if (shared)
        return shared->mask_bit;

    int mask_bit;
    if (dx_bitmask_first_set(router->neighbor_free_mask, &mask_bit)) {
        dx_bitmask_clear_bit(router->neighbor_free_mask, mask_bit);
    } else {
        dx_log(module, LOG_CRITICAL, "Exceeded maximum inter-router link count");
        return -1;
    }

    shared = new_dx_router_conn_t();
    shared->mask_bit = mask_bit;
    dx_link_set_conn_context(link, shared);
    return mask_bit;
}


/**
 * Outgoing Link Writable Handler
 */
static int router_writable_link_handler(void* context, dx_link_t *link)
{
    dx_router_t            *router = (dx_router_t*) context;
    dx_delivery_t          *delivery;
    dx_router_link_t       *rlink = (dx_router_link_t*) dx_link_get_context(link);
    pn_link_t              *pn_link = dx_link_pn(link);
    uint64_t                tag;
    int                     link_credit = pn_link_credit(pn_link);
    dx_routed_event_list_t  to_send;
    dx_routed_event_list_t  events;
    dx_routed_event_t      *re;
    size_t                  offer;
    int                     event_count = 0;

    DEQ_INIT(to_send);
    DEQ_INIT(events);

    sys_mutex_lock(router->lock);

    //
    // Pull the non-delivery events into a local list so they can be processed without
    // the lock being held.
    //
    re = DEQ_HEAD(rlink->event_fifo);
    while (re) {
        DEQ_REMOVE_HEAD(rlink->event_fifo);
        DEQ_INSERT_TAIL(events, re);
        re = DEQ_HEAD(rlink->event_fifo);
    }

    //
    // Under lock, move available deliveries from the msg_fifo to the local to_send
    // list.  Don't move more than we have credit to send.
    //
    if (link_credit > 0) {
        tag = router->dtag;
        re = DEQ_HEAD(rlink->msg_fifo);
        while (re) {
            DEQ_REMOVE_HEAD(rlink->msg_fifo);
            DEQ_INSERT_TAIL(to_send, re);
            if (DEQ_SIZE(to_send) == link_credit)
                break;
            re = DEQ_HEAD(rlink->msg_fifo);
        }
        router->dtag += DEQ_SIZE(to_send);
    }

    offer = DEQ_SIZE(rlink->msg_fifo);
    sys_mutex_unlock(router->lock);

    //
    // Deliver all the to_send messages downrange
    //
    re = DEQ_HEAD(to_send);
    while (re) {
        DEQ_REMOVE_HEAD(to_send);

        //
        // Get a delivery for the send.  This will be the current delivery on the link.
        //
        tag++;
        delivery = dx_delivery(link, pn_dtag((char*) &tag, 8));

        //
        // Send the message
        //
        dx_message_send(re->message, link);

        //
        // If there is an incoming delivery associated with this message, link it
        // with the outgoing delivery.  Otherwise, the message arrived pre-settled
        // and should be sent presettled.
        //
        if (re->delivery) {
            dx_delivery_set_peer(re->delivery, delivery);
            dx_delivery_set_peer(delivery, re->delivery);
        } else
            dx_delivery_free(delivery, 0);  // settle and free

        pn_link_advance(pn_link);
        event_count++;

        dx_message_free(re->message);
        free_dx_routed_event_t(re);
        re = DEQ_HEAD(to_send);
    }

    //
    // Process the non-delivery events.
    //
    re = DEQ_HEAD(events);
    while (re) {
        DEQ_REMOVE_HEAD(events);

        if (re->delivery) {
            if (re->disposition) {
                pn_delivery_update(dx_delivery_pn(re->delivery), re->disposition);
                event_count++;
            }
            if (re->settle) {
                dx_delivery_free(re->delivery, 0);
                event_count++;
            }
        }

        free_dx_routed_event_t(re);
        re = DEQ_HEAD(events);
    }

    //
    // Set the offer to the number of messages remaining to be sent.
    //
    pn_link_offered(pn_link, offer);
    return event_count;
}


static dx_field_iterator_t *router_annotate_message(dx_router_t *router, dx_message_t *msg, int *drop)
{
    dx_parsed_field_t   *in_da        = dx_message_delivery_annotations(msg);
    dx_composed_field_t *out_da       = dx_compose(DX_PERFORMATIVE_DELIVERY_ANNOTATIONS, 0);
    dx_field_iterator_t *ingress_iter = 0;

    dx_parsed_field_t *trace   = 0;
    dx_parsed_field_t *ingress = 0;

    if (in_da) {
        trace   = dx_parse_value_by_key(in_da, DX_DA_TRACE);
        ingress = dx_parse_value_by_key(in_da, DX_DA_INGRESS);
    }

    dx_compose_start_map(out_da);

    //
    // If there is a trace field, append this router's ID to the trace.
    //
    dx_compose_insert_string(out_da, DX_DA_TRACE);
    dx_compose_start_list(out_da);
    if (trace) {
        if (dx_parse_is_list(trace)) {
            uint32_t idx = 0;
            dx_parsed_field_t *trace_item = dx_parse_sub_value(trace, idx);
            while (trace_item) {
                dx_field_iterator_t *iter = dx_parse_raw(trace_item);
                if (dx_field_iterator_equal(iter, (unsigned char*) direct_prefix))
                    *drop = 1;
                dx_field_iterator_reset(iter);
                dx_compose_insert_string_iterator(out_da, iter);
                idx++;
                trace_item = dx_parse_sub_value(trace, idx);
            }
        }
    }

    dx_compose_insert_string(out_da, direct_prefix);
    dx_compose_end_list(out_da);

    //
    // If there is no ingress field, annotate the ingress as this router else
    // keep the original field.
    //
    dx_compose_insert_string(out_da, DX_DA_INGRESS);
    if (ingress && dx_parse_is_scalar(ingress)) {
        ingress_iter = dx_parse_raw(ingress);
        dx_compose_insert_string_iterator(out_da, ingress_iter);
    } else
        dx_compose_insert_string(out_da, direct_prefix);

    dx_compose_end_map(out_da);

    dx_message_set_delivery_annotations(msg, out_da);
    dx_compose_free(out_da);

    //
    // Return the iterator to the ingress field _if_ it was present.
    // If we added the ingress, return NULL.
    //
    return ingress_iter;
}


/**
 * Inbound Delivery Handler
 */
static void router_rx_handler(void* context, dx_link_t *link, dx_delivery_t *delivery)
{
    dx_router_t      *router  = (dx_router_t*) context;
    pn_link_t        *pn_link = dx_link_pn(link);
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
        dx_router_link_t  *clink = rlink->connected_link;
        dx_routed_event_t *re    = new_dx_routed_event_t();

        DEQ_ITEM_INIT(re);
        re->delivery    = 0;
        re->message     = msg;
        re->settle      = false;
        re->disposition = 0;
        DEQ_INSERT_TAIL(clink->msg_fifo, re);

        //
        // If the incoming delivery is settled (pre-settled), don't link it into the routed
        // event.  If it's not settled, link it into the event for later handling.
        //
        if (dx_delivery_settled(delivery))
            dx_delivery_free(delivery, 0);
        else
            re->delivery = delivery;

        sys_mutex_unlock(router->lock);
        dx_link_activate(clink->link);
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
        int                  fanout = 0;

        if (iter) {
            dx_field_iterator_reset_view(iter, ITER_VIEW_ADDRESS_HASH);
            dx_hash_retrieve(router->addr_hash, iter, (void*) &addr);
            dx_field_iterator_reset_view(iter, ITER_VIEW_NO_HOST);
            int is_local  = dx_field_iterator_prefix(iter, local_prefix);
            int is_direct = dx_field_iterator_prefix(iter, direct_prefix);
            dx_field_iterator_free(iter);

            if (addr) {
                //
                // If the incoming link is an endpoint link, count this as an ingress delivery.
                //
                if (rlink->link_type == DX_LINK_ENDPOINT)
                    addr->deliveries_ingress++;

                //
                // To field is valid and contains a known destination.  Handle the various
                // cases for forwarding.
                //

                //
                // Interpret and update the delivery annotations of the message.  As a convenience,
                // this function returns the iterator to the ingress field (if it exists).
                //
                int drop = 0;
                dx_field_iterator_t *ingress_iter = router_annotate_message(router, msg, &drop);

                //
                // Forward to the in-process handler for this message if there is one.  The
                // actual invocation of the handler will occur later after we've released
                // the lock.
                //
                if (!drop && addr->handler) {
                    in_process_copy = dx_message_copy(msg);
                    handler         = addr->handler;
                    handler_context = addr->handler_context;
                    addr->deliveries_to_container++;
                }

                //
                // If the address form is local (i.e. is prefixed by _local), don't forward
                // outside of the router process.
                //
                if (!drop && !is_local) {
                    //
                    // Forward to all of the local links receiving this address.
                    //
                    dx_router_link_ref_t *dest_link_ref = DEQ_HEAD(addr->rlinks);
                    while (dest_link_ref) {
                        dx_routed_event_t *re = new_dx_routed_event_t();
                        DEQ_ITEM_INIT(re);
                        re->delivery    = 0;
                        re->message     = dx_message_copy(msg);
                        re->settle      = 0;
                        re->disposition = 0;
                        DEQ_INSERT_TAIL(dest_link_ref->link->msg_fifo, re);

                        fanout++;
                        if (fanout == 1 && !dx_delivery_settled(delivery))
                            re->delivery = delivery;

                        addr->deliveries_egress++;
                        dx_link_activate(dest_link_ref->link->link);
                        dest_link_ref = DEQ_NEXT(dest_link_ref);
                    }

                    //
                    // If the address form is direct to this router node, don't relay it on
                    // to any other part of the network.
                    //
                    if (!is_direct) {
                        //
                        // Get the mask bit associated with the ingress router for the message.
                        // This will be compared against the "valid_origin" masks for each
                        // candidate destination router.
                        //
                        int origin = -1;
                        if (ingress_iter) {
                            dx_field_iterator_reset_view(ingress_iter, ITER_VIEW_ADDRESS_HASH);
                            dx_address_t *origin_addr;
                            dx_hash_retrieve(router->addr_hash, ingress_iter, (void*) &origin_addr);
                            if (origin_addr && DEQ_SIZE(origin_addr->rnodes) == 1) {
                                dx_router_ref_t *rref = DEQ_HEAD(origin_addr->rnodes);
                                origin = rref->router->mask_bit;
                            }
                        } else
                            origin = 0;

                        //
                        // Forward to the next-hops for remote destinations.
                        //
                        if (origin >= 0) {
                            dx_router_ref_t  *dest_node_ref = DEQ_HEAD(addr->rnodes);
                            dx_router_link_t *dest_link;
                            dx_bitmask_t     *link_set = dx_bitmask(0);

                            //
                            // Loop over the target nodes for this address.  Build a set of outgoing links
                            // for which there are valid targets.  We do this to avoid sending more than one
                            // message down a given link.  It's possible that there are multiple destinations
                            // for this address that are all reachable over the same link.  In this case, we
                            // will send only one copy of the message over the link and allow a downstream
                            // router to fan the message out.
                            //
                            while (dest_node_ref) {
                                if (dest_node_ref->router->next_hop)
                                    dest_link = dest_node_ref->router->next_hop->peer_link;
                                else
                                    dest_link = dest_node_ref->router->peer_link;
                                if (dest_link && dx_bitmask_value(dest_node_ref->router->valid_origins, origin))
                                    dx_bitmask_set_bit(link_set, dest_link->mask_bit);
                                dest_node_ref = DEQ_NEXT(dest_node_ref);
                            }

                            //
                            // Send a copy of the message outbound on each identified link.
                            //
                            int link_bit;
                            while (dx_bitmask_first_set(link_set, &link_bit)) {
                                dx_bitmask_clear_bit(link_set, link_bit);
                                dest_link = router->out_links_by_mask_bit[link_bit];
                                if (dest_link) {
                                    dx_routed_event_t *re = new_dx_routed_event_t();
                                    DEQ_ITEM_INIT(re);
                                    re->delivery    = 0;
                                    re->message     = dx_message_copy(msg);
                                    re->settle      = 0;
                                    re->disposition = 0;
                                    DEQ_INSERT_TAIL(dest_link->msg_fifo, re);

                                    fanout++;
                                    if (fanout == 1 && !dx_delivery_settled(delivery))
                                        re->delivery = delivery;
                                
                                    addr->deliveries_transit++;
                                    dx_link_activate(dest_link->link);
                                }
                            }

                            dx_bitmask_free(link_set);
                        }
                    }
                }
            }

            //
            // In message-routing mode, the handling of the incoming delivery depends on the
            // number of copies of the received message that were forwarded.
            //
            if (handler) {
                dx_delivery_free(delivery, PN_ACCEPTED);
            } else if (fanout == 0) {
                dx_delivery_free(delivery, PN_RELEASED);
            }
        }
    } else {
        //
        // Message is invalid.  Reject the message.
        //
        dx_delivery_free(delivery, PN_REJECTED);
    }

    sys_mutex_unlock(router->lock);
    dx_message_free(msg);

    //
    // Invoke the in-process handler now that the lock is released.
    //
    if (handler) {
        handler(handler_context, in_process_copy, rlink->mask_bit);
        dx_message_free(in_process_copy);
    }
}


/**
 * Delivery Disposition Handler
 */
static void router_disp_handler(void* context, dx_link_t *link, dx_delivery_t *delivery)
{
    dx_router_t   *router  = (dx_router_t*) context;
    bool           changed = dx_delivery_disp_changed(delivery);
    uint64_t       disp    = dx_delivery_disp(delivery);
    bool           settled = dx_delivery_settled(delivery);
    dx_delivery_t *peer    = dx_delivery_peer(delivery);

    if (peer) {
        //
        // The case where this delivery has a peer.
        //
        if (changed || settled) {
            dx_link_t         *peer_link = dx_delivery_link(peer);
            dx_router_link_t  *prl       = (dx_router_link_t*) dx_link_get_context(peer_link);
            dx_routed_event_t *re        = new_dx_routed_event_t();
            DEQ_ITEM_INIT(re);
            re->delivery    = peer;
            re->message     = 0;
            re->settle      = settled;
            re->disposition = changed ? disp : 0;

            sys_mutex_lock(router->lock);
            DEQ_INSERT_TAIL(prl->event_fifo, re);
            sys_mutex_unlock(router->lock);

            dx_link_activate(peer_link);
        }

    } else {
        //
        // The no-peer case.  Ignore status changes and echo settlement.
        //
        if (settled)
            dx_delivery_free(delivery, 0);
    }
}


/**
 * New Incoming Link Handler
 */
static int router_incoming_link_handler(void* context, dx_link_t *link)
{
    dx_router_t *router    = (dx_router_t*) context;
    pn_link_t   *pn_link   = dx_link_pn(link);
    int          is_router = dx_router_terminus_is_router(dx_link_remote_source(link));

    if (is_router && !dx_router_connection_is_inter_router(dx_link_connection(link))) {
        dx_log(module, LOG_WARNING, "Incoming link claims router capability but is not on an inter-router connection");
        pn_link_close(pn_link);
        return 0;
    }

    dx_router_link_t *rlink = new_dx_router_link_t();
    DEQ_ITEM_INIT(rlink);
    rlink->link_type      = is_router ? DX_LINK_ROUTER : DX_LINK_ENDPOINT;
    rlink->link_direction = DX_INCOMING;
    rlink->owning_addr    = 0;
    rlink->link           = link;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    rlink->ref            = 0;
    DEQ_INIT(rlink->event_fifo);
    DEQ_INIT(rlink->msg_fifo);

    dx_link_set_context(link, rlink);

    sys_mutex_lock(router->lock);
    rlink->mask_bit = is_router ? dx_router_find_mask_bit_LH(router, link) : 0;
    DEQ_INSERT_TAIL(router->links, rlink);
    sys_mutex_unlock(router->lock);

    pn_terminus_copy(dx_link_source(link), dx_link_remote_source(link));
    pn_terminus_copy(dx_link_target(link), dx_link_remote_target(link));
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
    const char  *r_src   = pn_terminus_get_address(dx_link_remote_source(link));
    int is_dynamic       = pn_terminus_is_dynamic(dx_link_remote_source(link));
    int is_router        = dx_router_terminus_is_router(dx_link_remote_target(link));
    int propagate        = 0;
    dx_field_iterator_t *iter = 0;

    if (is_router && !dx_router_connection_is_inter_router(dx_link_connection(link))) {
        dx_log(module, LOG_WARNING, "Outgoing link claims router capability but is not on an inter-router connection");
        pn_link_close(pn_link);
        return 0;
    }

    //
    // If this link is not a router link and it has no source address, we can't
    // accept it.
    //
    if (r_src == 0 && !is_router && !is_dynamic) {
        pn_link_close(pn_link);
        return 0;
    }


    //
    // If this is an endpoint link with a source address, make sure the address is
    // appropriate for endpoint links.  If it is not mobile address, it cannot be
    // bound to an endpoint link.
    //
    if(r_src && !is_router && !is_dynamic) {
        iter = dx_field_iterator_string(r_src, ITER_VIEW_ADDRESS_HASH);
        unsigned char prefix = dx_field_iterator_octet(iter);
        dx_field_iterator_reset(iter);

        if (prefix != 'M') {
            dx_field_iterator_free(iter);
            pn_link_close(pn_link);
            dx_log(module, LOG_WARNING, "Rejected an outgoing endpoint link with a router address: %s", r_src);
            return 0;
        }
    }

    //
    // Create a router_link record for this link.  Some of the fields will be
    // modified in the different cases below.
    //
    dx_router_link_t *rlink = new_dx_router_link_t();
    DEQ_ITEM_INIT(rlink);
    rlink->link_type      = is_router ? DX_LINK_ROUTER : DX_LINK_ENDPOINT;
    rlink->link_direction = DX_OUTGOING;
    rlink->owning_addr    = 0;
    rlink->link           = link;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    rlink->ref            = 0;
    DEQ_INIT(rlink->event_fifo);
    DEQ_INIT(rlink->msg_fifo);

    dx_link_set_context(link, rlink);
    pn_terminus_copy(dx_link_source(link), dx_link_remote_source(link));
    pn_terminus_copy(dx_link_target(link), dx_link_remote_target(link));

    sys_mutex_lock(router->lock);
    rlink->mask_bit = is_router ? dx_router_find_mask_bit_LH(router, link) : 0;

    if (is_router) {
        //
        // If this is a router link, put it in the hello_address link-list.
        //
        dx_router_add_link_ref_LH(&router->hello_addr->rlinks, rlink);
        rlink->owning_addr = router->hello_addr;
        router->out_links_by_mask_bit[rlink->mask_bit] = rlink;

    } else {
        //
        // If this is an endpoint link, check the source.  If it is dynamic, we will
        // assign it an ephemeral and routable address.  If it has a non-dymanic
        // address, that address needs to be set up in the address list.
        //
        char          temp_addr[1000]; // FIXME
        dx_address_t *addr;

        if (is_dynamic) {
            dx_router_generate_temp_addr(router, temp_addr, 1000);
            iter = dx_field_iterator_string(temp_addr, ITER_VIEW_ADDRESS_HASH);
            pn_terminus_set_address(dx_link_source(link), temp_addr);
            dx_log(module, LOG_INFO, "Assigned temporary routable address: %s", temp_addr);
        } else
            dx_log(module, LOG_INFO, "Registered local address: %s", r_src);

        dx_hash_retrieve(router->addr_hash, iter, (void**) &addr);
        if (!addr) {
            addr = new_dx_address_t();
            memset(addr, 0, sizeof(dx_address_t));
            DEQ_ITEM_INIT(addr);
            DEQ_INIT(addr->rlinks);
            DEQ_INIT(addr->rnodes);
            dx_hash_insert(router->addr_hash, iter, addr, &addr->hash_handle);
            DEQ_INSERT_TAIL(router->addrs, addr);
        }

        rlink->owning_addr = addr;
        dx_router_add_link_ref_LH(&addr->rlinks, rlink);

        //
        // If this is not a dynamic address and it is the first local subscription
        // to the address, supply the address to the router module for propagation
        // to other nodes.
        //
        propagate = (!is_dynamic) && (DEQ_SIZE(addr->rlinks) == 1);
    }

    DEQ_INSERT_TAIL(router->links, rlink);
    sys_mutex_unlock(router->lock);

    if (propagate)
        dx_router_global_added(router, iter);

    if (iter)
        dx_field_iterator_free(iter);
    pn_link_open(pn_link);
    return 0;
}


/**
 * Link Detached Handler
 */
static int router_link_detach_handler(void* context, dx_link_t *link, int closed)
{
    dx_router_t      *router = (dx_router_t*) context;
    dx_router_link_t *rlink  = (dx_router_link_t*) dx_link_get_context(link);
    dx_router_conn_t *shared = (dx_router_conn_t*) dx_link_get_conn_context(link);

    if (shared) {
        dx_link_set_conn_context(link, 0);
        free_dx_router_conn_t(shared);
    }

    if (!rlink)
        return 0;

    sys_mutex_lock(router->lock);

    //
    // If the link is outgoing, we must disassociate it from its address.
    //
    if (rlink->link_direction == DX_OUTGOING && rlink->owning_addr) {
        dx_router_del_link_ref_LH(&rlink->owning_addr->rlinks, rlink);
        dx_router_check_addr_LH(router, rlink->owning_addr);
    }

    //
    // If this is an outgoing inter-router link, we must remove the by-mask-bit
    // index reference to this link.
    //
    if (rlink->link_type == DX_LINK_ROUTER && rlink->link_direction == DX_OUTGOING) {
        if (router->out_links_by_mask_bit[rlink->mask_bit] == rlink)
            router->out_links_by_mask_bit[rlink->mask_bit] = 0;
        else
            dx_log(module, LOG_CRITICAL, "Outgoing router link closing but not in index: bit=%d", rlink->mask_bit);
    }

    //
    // If this is an incoming inter-router link, we must free the mask_bit.
    //
    if (rlink->link_type == DX_LINK_ROUTER && rlink->link_direction == DX_INCOMING)
        dx_bitmask_set_bit(router->neighbor_free_mask, rlink->mask_bit);

    //
    // Remove the link from the master list-of-links.
    //
    DEQ_REMOVE(router->links, rlink);
    sys_mutex_unlock(router->lock);

    // TODO - wrap the free to handle the recursive items
    free_dx_router_link_t(rlink);

    return 0;
}


static void router_inbound_open_handler(void *type_context, dx_connection_t *conn)
{
}


static void router_outbound_open_handler(void *type_context, dx_connection_t *conn)
{
    //
    // Check the configured role of this connection.  If it is not the inter-router
    // role, ignore it.
    //
    if (!dx_router_connection_is_inter_router(conn)) {
        dx_log(module, LOG_WARNING, "Outbound connection set up without inter-router role");
        return;
    }

    dx_router_t         *router = (dx_router_t*) type_context;
    dx_link_t           *sender;
    dx_link_t           *receiver;
    dx_router_link_t    *rlink;
    int                  mask_bit = 0;
    size_t               clen     = strlen(DX_CAPABILITY_ROUTER);

    //
    // Allocate a mask bit to designate the pair of links connected to the neighbor router
    //
    sys_mutex_lock(router->lock);
    if (dx_bitmask_first_set(router->neighbor_free_mask, &mask_bit)) {
        dx_bitmask_clear_bit(router->neighbor_free_mask, mask_bit);
    } else {
        sys_mutex_unlock(router->lock);
        dx_log(module, LOG_CRITICAL, "Exceeded maximum inter-router link count");
        return;
    }

    //
    // Create an incoming link with router source capability
    //
    receiver = dx_link(router->node, conn, DX_INCOMING, DX_INTERNODE_LINK_NAME_1);
    // TODO - We don't want to have to cast away the constness of the literal string here!
    //        See PROTON-429
    pn_data_put_symbol(pn_terminus_capabilities(dx_link_target(receiver)), pn_bytes(clen, (char*) DX_CAPABILITY_ROUTER));

    rlink = new_dx_router_link_t();
    DEQ_ITEM_INIT(rlink);
    rlink->mask_bit       = mask_bit;
    rlink->link_type      = DX_LINK_ROUTER;
    rlink->link_direction = DX_INCOMING;
    rlink->owning_addr    = 0;
    rlink->link           = receiver;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    DEQ_INIT(rlink->event_fifo);
    DEQ_INIT(rlink->msg_fifo);

    dx_link_set_context(receiver, rlink);
    DEQ_INSERT_TAIL(router->links, rlink);

    //
    // Create an outgoing link with router target capability
    //
    sender = dx_link(router->node, conn, DX_OUTGOING, DX_INTERNODE_LINK_NAME_2);
    // TODO - We don't want to have to cast away the constness of the literal string here!
    //        See PROTON-429
    pn_data_put_symbol(pn_terminus_capabilities(dx_link_source(sender)), pn_bytes(clen, (char *) DX_CAPABILITY_ROUTER));

    rlink = new_dx_router_link_t();
    DEQ_ITEM_INIT(rlink);
    rlink->mask_bit       = mask_bit;
    rlink->link_type      = DX_LINK_ROUTER;
    rlink->link_direction = DX_OUTGOING;
    rlink->owning_addr    = router->hello_addr;
    rlink->link           = sender;
    rlink->connected_link = 0;
    rlink->peer_link      = 0;
    DEQ_INIT(rlink->event_fifo);
    DEQ_INIT(rlink->msg_fifo);

    //
    // Add the new outgoing link to the hello_address's list of links.
    //
    dx_router_add_link_ref_LH(&router->hello_addr->rlinks, rlink);

    //
    // Index this link from the by-maskbit index so we can later find it quickly
    // when provided with the mask bit.
    //
    router->out_links_by_mask_bit[mask_bit] = rlink;

    dx_link_set_context(sender, rlink);
    DEQ_INSERT_TAIL(router->links, rlink);
    sys_mutex_unlock(router->lock);

    pn_link_open(dx_link_pn(receiver));
    pn_link_open(dx_link_pn(sender));
    pn_link_flow(dx_link_pn(receiver), 1000);
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


dx_router_t *dx_router(dx_dispatch_t *dx, dx_router_mode_t mode, const char *area, const char *id)
{
    if (!type_registered) {
        type_registered = 1;
        dx_container_register_node_type(dx, &router_node);
    }

    size_t dplen = 9 + strlen(area) + strlen(id);
    direct_prefix = (char*) malloc(dplen);
    strcpy(direct_prefix, "_topo/");
    strcat(direct_prefix, area);
    strcat(direct_prefix, "/");
    strcat(direct_prefix, id);
    strcat(direct_prefix, "/");

    dx_router_t *router = NEW(dx_router_t);

    router_node.type_context = router;

    dx->router = router;
    router->dx           = dx;
    router->router_mode  = mode;
    router->router_area  = area;
    router->router_id    = id;
    router->node         = dx_container_set_default_node_type(dx, &router_node, (void*) router, DX_DIST_BOTH);
    DEQ_INIT(router->addrs);
    router->addr_hash    = dx_hash(10, 32, 0);

    DEQ_INIT(router->links);
    DEQ_INIT(router->routers);

    router->out_links_by_mask_bit = NEW_PTR_ARRAY(dx_router_link_t, dx_bitmask_width());
    router->routers_by_mask_bit   = NEW_PTR_ARRAY(dx_router_node_t, dx_bitmask_width());
    for (int idx = 0; idx < dx_bitmask_width(); idx++) {
        router->out_links_by_mask_bit[idx] = 0;
        router->routers_by_mask_bit[idx]   = 0;
    }

    router->neighbor_free_mask = dx_bitmask(1);
    router->lock               = sys_mutex();
    router->timer              = dx_timer(dx, dx_router_timer_handler, (void*) router);
    router->dtag               = 1;
    router->pyRouter           = 0;
    router->pyTick             = 0;
    router->pyAdded            = 0;
    router->pyRemoved          = 0;

    //
    // Create addresses for all of the routers in the topology.  It will be registered
    // locally later in the initialization sequence.
    //
    if (router->router_mode == DX_ROUTER_MODE_INTERIOR) {
        router->router_addr = dx_router_register_address(dx, "qdxrouter", 0, 0);
        router->hello_addr  = dx_router_register_address(dx, "qdxhello", 0, 0);
    }

    //
    // Inform the field iterator module of this router's id and area.  The field iterator
    // uses this to offload some of the address-processing load from the router.
    //
    dx_field_iterator_set_address(area, id);

    //
    // Set up the usage of the embedded python router module.
    //
    dx_python_start();

    switch (router->router_mode) {
    case DX_ROUTER_MODE_STANDALONE:  dx_log(module, LOG_INFO, "Router started in Standalone mode");  break;
    case DX_ROUTER_MODE_INTERIOR:    dx_log(module, LOG_INFO, "Router started in Interior mode, area=%s id=%s", area, id);  break;
    case DX_ROUTER_MODE_EDGE:        dx_log(module, LOG_INFO, "Router started in Edge mode");  break;
    }

    return router;
}


void dx_router_setup_late(dx_dispatch_t *dx)
{
    dx_router_agent_setup(dx->router);
    dx_router_python_setup(dx->router);
    dx_timer_schedule(dx->router->timer, 1000);
}


void dx_router_free(dx_router_t *router)
{
    dx_container_set_default_node_type(router->dx, 0, 0, DX_DIST_BOTH);
    sys_mutex_free(router->lock);
    free(router);
    dx_python_stop();
}


const char *dx_router_id(const dx_dispatch_t *dx)
{
    return direct_prefix;
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
    dx_hash_retrieve(router->addr_hash, iter, (void**) &addr);
    if (!addr) {
        addr = new_dx_address_t();
        memset(addr, 0, sizeof(dx_address_t));
        DEQ_ITEM_INIT(addr);
        DEQ_INIT(addr->rlinks);
        DEQ_INIT(addr->rnodes);
        dx_hash_insert(router->addr_hash, iter, addr, &addr->hash_handle);
        DEQ_ITEM_INIT(addr);
        DEQ_INSERT_TAIL(router->addrs, addr);
    }
    dx_field_iterator_free(iter);

    addr->handler         = handler;
    addr->handler_context = context;

    sys_mutex_unlock(router->lock);

    if (handler)
        dx_log(module, LOG_INFO, "In-Process Address Registered: %s", address);
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
    dx_hash_retrieve(router->addr_hash, address, (void*) &addr);
    if (addr) {
        //
        // Forward to all of the local links receiving this address.
        //
        addr->deliveries_from_container++;
        dx_router_link_ref_t *dest_link_ref = DEQ_HEAD(addr->rlinks);
        while (dest_link_ref) {
            dx_routed_event_t *re = new_dx_routed_event_t();
            DEQ_ITEM_INIT(re);
            re->delivery    = 0;
            re->message     = dx_message_copy(msg);
            re->settle      = 0;
            re->disposition = 0;
            DEQ_INSERT_TAIL(dest_link_ref->link->msg_fifo, re);

            dx_link_activate(dest_link_ref->link->link);
            addr->deliveries_egress++;

            dest_link_ref = DEQ_NEXT(dest_link_ref);
        }

        //
        // Forward to the next-hops for remote destinations.
        //
        dx_router_ref_t  *dest_node_ref = DEQ_HEAD(addr->rnodes);
        dx_router_link_t *dest_link;
        dx_bitmask_t     *link_set = dx_bitmask(0);

        while (dest_node_ref) {
            if (dest_node_ref->router->next_hop)
                dest_link = dest_node_ref->router->next_hop->peer_link;
            else
                dest_link = dest_node_ref->router->peer_link;
            if (dest_link)
                dx_bitmask_set_bit(link_set, dest_link->mask_bit);
            dest_node_ref = DEQ_NEXT(dest_node_ref);
        }

        int link_bit;
        while (dx_bitmask_first_set(link_set, &link_bit)) {
            dx_bitmask_clear_bit(link_set, link_bit);
            dest_link = router->out_links_by_mask_bit[link_bit];
            if (dest_link) {
                dx_routed_event_t *re = new_dx_routed_event_t();
                DEQ_ITEM_INIT(re);
                re->delivery    = 0;
                re->message     = dx_message_copy(msg);
                re->settle      = 0;
                re->disposition = 0;
                DEQ_INSERT_TAIL(dest_link->msg_fifo, re);
                dx_link_activate(dest_link->link);
                addr->deliveries_transit++;
            }
        }

        dx_bitmask_free(link_set);
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

