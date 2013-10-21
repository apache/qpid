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

#include "dispatch_private.h"
#include <qpid/dispatch/error.h>
#include <qpid/dispatch/agent.h>
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/hash.h>
#include <qpid/dispatch/container.h>
#include <qpid/dispatch/message.h>
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/timer.h>
#include <qpid/dispatch/router.h>
#include <qpid/dispatch/log.h>
#include <qpid/dispatch/compose.h>
#include <qpid/dispatch/parse.h>
#include <qpid/dispatch/amqp.h>
#include <string.h>
#include <stdio.h>

struct dx_agent_class_t {
    DEQ_LINKS(dx_agent_class_t);
    dx_hash_handle_t     *hash_handle;
    void                 *context;
    dx_agent_schema_cb_t  schema_handler;
    dx_agent_query_cb_t   query_handler;  // 0 iff class is an event.
};

DEQ_DECLARE(dx_agent_class_t, dx_agent_class_list_t);


struct dx_agent_t {
    dx_dispatch_t         *dx;
    dx_hash_t             *class_hash;
    dx_agent_class_list_t  class_list;
    dx_message_list_t      in_fifo;
    dx_message_list_t      out_fifo;
    sys_mutex_t           *lock;
    dx_timer_t            *timer;
    dx_address_t          *address;
    dx_agent_class_t      *container_class;
};


typedef struct {
    dx_agent_t          *agent;
    dx_composed_field_t *response;
} dx_agent_request_t;


static char *log_module = "AGENT";


static dx_composed_field_t *dx_agent_setup_response(dx_field_iterator_t *reply_to)
{
    //
    // Compose the header
    //
    dx_composed_field_t *field = dx_compose(DX_PERFORMATIVE_HEADER, 0);
    dx_compose_start_list(field);
    dx_compose_insert_bool(field, 0);     // durable
    dx_compose_end_list(field);

    //
    // Compose the Properties
    //
    field = dx_compose(DX_PERFORMATIVE_PROPERTIES, field);
    dx_compose_start_list(field);
    dx_compose_insert_null(field);                       // message-id
    dx_compose_insert_null(field);                       // user-id
    dx_compose_insert_string_iterator(field, reply_to);  // to
    dx_compose_insert_null(field);                       // subject
    dx_compose_insert_null(field);                       // reply-to
    dx_compose_insert_string(field, "1");                // correlation-id   // TODO - fix
    dx_compose_end_list(field);

    //
    // Compose the Application Properties
    //
    field = dx_compose(DX_PERFORMATIVE_APPLICATION_PROPERTIES, field);
    dx_compose_start_map(field);
    dx_compose_insert_string(field, "status-code");
    dx_compose_insert_uint(field, 200);

    dx_compose_insert_string(field, "status-descriptor");
    dx_compose_insert_string(field, "OK");
    dx_compose_end_map(field);

    return field;
}


static void dx_agent_process_get(dx_agent_t *agent, dx_parsed_field_t *map, dx_field_iterator_t *reply_to)
{
    dx_parsed_field_t *cls = dx_parse_value_by_key(map, "type");
    if (cls == 0)
        return;

    dx_field_iterator_t    *cls_string = dx_parse_raw(cls);
    const dx_agent_class_t *cls_record;
    dx_hash_retrieve_const(agent->class_hash, cls_string, (const void**) &cls_record);
    if (cls_record == 0)
        return;

    dx_log(log_module, LOG_TRACE, "Received GET request for type: %s", dx_hash_key_by_handle(cls_record->hash_handle));

    dx_composed_field_t *field = dx_agent_setup_response(reply_to);

    //
    // Open the Body (AMQP Value) to be filled in by the handler.
    //
    field = dx_compose(DX_PERFORMATIVE_BODY_AMQP_VALUE, field);
    dx_compose_start_list(field);
    dx_compose_start_map(field);

    //
    // The request record is allocated locally because the entire processing of the request
    // will be done synchronously.
    //
    dx_agent_request_t request;
    request.agent    = agent;
    request.response = field;

    cls_record->query_handler(cls_record->context, 0, &request);

    //
    // The response is complete, close the list.
    //
    dx_compose_end_list(field);

    //
    // Create a message and send it.
    //
    dx_message_t *msg = dx_message();
    dx_message_compose_2(msg, field);
    dx_router_send(agent->dx, reply_to, msg);

    dx_message_free(msg);
    dx_compose_free(field);
}


static void dx_agent_process_discover_types(dx_agent_t *agent, dx_parsed_field_t *map, dx_field_iterator_t *reply_to)
{
    dx_log(log_module, LOG_TRACE, "Received DISCOVER-TYPES request");

    dx_composed_field_t *field = dx_agent_setup_response(reply_to);

    //
    // Open the Body (AMQP Value) to be filled in by the handler.
    //
    field = dx_compose(DX_PERFORMATIVE_BODY_AMQP_VALUE, field);
    dx_compose_start_map(field);

    //
    // Put entries into the map for each known entity type
    //
    sys_mutex_lock(agent->lock);
    dx_agent_class_t *cls = DEQ_HEAD(agent->class_list);
    while (cls) {
        dx_compose_insert_string(field, (const char*) dx_hash_key_by_handle(cls->hash_handle));
        dx_compose_insert_null(field); // TODO - https://tools.oasis-open.org/issues/browse/AMQP-87
        cls = DEQ_NEXT(cls);
    }
    sys_mutex_unlock(agent->lock);
    dx_compose_end_map(field);

    //
    // Create a message and send it.
    //
    dx_message_t *msg = dx_message();
    dx_message_compose_2(msg, field);
    dx_router_send(agent->dx, reply_to, msg);

    dx_message_free(msg);
    dx_compose_free(field);
}


static void dx_agent_process_discover_operations(dx_agent_t *agent, dx_parsed_field_t *map, dx_field_iterator_t *reply_to)
{
    dx_log(log_module, LOG_TRACE, "Received DISCOVER-OPERATIONS request");

    dx_composed_field_t *field = dx_agent_setup_response(reply_to);

    //
    // Open the Body (AMQP Value) to be filled in by the handler.
    //
    field = dx_compose(DX_PERFORMATIVE_BODY_AMQP_VALUE, field);
    dx_compose_start_map(field);

    //
    // Put entries into the map for each known entity type
    //
    sys_mutex_lock(agent->lock);
    dx_agent_class_t *cls = DEQ_HEAD(agent->class_list);
    while (cls) {
        dx_compose_insert_string(field, (const char*) dx_hash_key_by_handle(cls->hash_handle));
        dx_compose_start_list(field);
        dx_compose_insert_string(field, "READ");
        dx_compose_end_list(field);
        cls = DEQ_NEXT(cls);
    }
    sys_mutex_unlock(agent->lock);
    dx_compose_end_map(field);

    //
    // Create a message and send it.
    //
    dx_message_t *msg = dx_message();
    dx_message_compose_2(msg, field);
    dx_router_send(agent->dx, reply_to, msg);

    dx_message_free(msg);
    dx_compose_free(field);
}


static void dx_agent_process_discover_nodes(dx_agent_t *agent, dx_parsed_field_t *map, dx_field_iterator_t *reply_to)
{
    dx_log(log_module, LOG_TRACE, "Received DISCOVER-MGMT-NODES request");

    dx_composed_field_t *field = dx_agent_setup_response(reply_to);

    //
    // Open the Body (AMQP Value) to be filled in by the handler.
    //
    field = dx_compose(DX_PERFORMATIVE_BODY_AMQP_VALUE, field);

    //
    // Put entries into the list for each known management node
    //
    dx_compose_start_list(field);
    dx_compose_insert_string(field, "amqp:/_local/$management");
    dx_router_build_node_list(agent->dx, field);
    dx_compose_end_list(field);

    //
    // Create a message and send it.
    //
    dx_message_t *msg = dx_message();
    dx_message_compose_2(msg, field);
    dx_router_send(agent->dx, reply_to, msg);

    dx_message_free(msg);
    dx_compose_free(field);
}


static void dx_agent_process_request(dx_agent_t *agent, dx_message_t *msg)
{
    //
    // Parse the message through the body and exit if the message is not well formed.
    //
    if (!dx_message_check(msg, DX_DEPTH_BODY))
        return;

    //
    // Get an iterator for the application-properties.  Exit if the message has none.
    //
    dx_field_iterator_t *ap = dx_message_field_iterator(msg, DX_FIELD_APPLICATION_PROPERTIES);
    if (ap == 0)
        return;

    //
    // Get an iterator for the reply-to.  Exit if not found.
    //
    dx_field_iterator_t *reply_to = dx_message_field_iterator(msg, DX_FIELD_REPLY_TO);
    if (reply_to == 0)
        return;

    //
    // Try to get a map-view of the application-properties.
    //
    dx_parsed_field_t *map = dx_parse(ap);
    if (map == 0) {
        dx_field_iterator_free(ap);
        return;
    }

    //
    // Exit if there was a parsing error.
    //
    if (!dx_parse_ok(map)) {
        dx_log(log_module, LOG_TRACE, "Received unparsable App Properties: %s", dx_parse_error(map));
        dx_field_iterator_free(ap);
        dx_parse_free(map);
        return;
    }

    //
    // Exit if it is not a map.
    //
    if (!dx_parse_is_map(map)) {
        dx_field_iterator_free(ap);
        dx_parse_free(map);
        return;
    }

    //
    // Get an iterator for the "operation" field in the map.  Exit if the key is not found.
    //
    dx_parsed_field_t *operation = dx_parse_value_by_key(map, "operation");
    if (operation == 0) {
        dx_parse_free(map);
        dx_field_iterator_free(ap);
        return;
    }

    //
    // Dispatch the operation to the appropriate handler
    //
    dx_field_iterator_t *operation_string = dx_parse_raw(operation);
    if (dx_field_iterator_equal(operation_string, (unsigned char*) "GET"))
        dx_agent_process_get(agent, map, reply_to);
    if (dx_field_iterator_equal(operation_string, (unsigned char*) "DISCOVER-TYPES"))
        dx_agent_process_discover_types(agent, map, reply_to);
    if (dx_field_iterator_equal(operation_string, (unsigned char*) "DISCOVER-OPERATIONS"))
        dx_agent_process_discover_operations(agent, map, reply_to);
    if (dx_field_iterator_equal(operation_string, (unsigned char*) "DISCOVER-MGMT-NODES"))
        dx_agent_process_discover_nodes(agent, map, reply_to);

    dx_parse_free(map);
    dx_field_iterator_free(ap);
    dx_field_iterator_free(reply_to);
}


static void dx_agent_deferred_handler(void *context)
{
    dx_agent_t   *agent = (dx_agent_t*) context;
    dx_message_t *msg;

    do {
        sys_mutex_lock(agent->lock);
        msg = DEQ_HEAD(agent->in_fifo);
        if (msg)
            DEQ_REMOVE_HEAD(agent->in_fifo);
        sys_mutex_unlock(agent->lock);

        if (msg) {
            dx_agent_process_request(agent, msg);
            dx_message_free(msg);
        }
    } while (msg);
}


static void dx_agent_rx_handler(void *context, dx_message_t *msg, int unused_link_id)
{
    dx_agent_t   *agent = (dx_agent_t*) context;
    dx_message_t *copy  = dx_message_copy(msg);

    sys_mutex_lock(agent->lock);
    DEQ_INSERT_TAIL(agent->in_fifo, copy);
    sys_mutex_unlock(agent->lock);

    dx_timer_schedule(agent->timer, 0);
}


static dx_agent_class_t *dx_agent_register_class_LH(dx_agent_t           *agent,
                                                    const char           *fqname,
                                                    void                 *context,
                                                    dx_agent_schema_cb_t  schema_handler,
                                                    dx_agent_query_cb_t   query_handler)
{
    dx_agent_class_t *cls = NEW(dx_agent_class_t);
    assert(cls);
    DEQ_ITEM_INIT(cls);
    cls->context        = context;
    cls->schema_handler = schema_handler;
    cls->query_handler  = query_handler;

    dx_field_iterator_t *iter = dx_field_iterator_string(fqname, ITER_VIEW_ALL);
    int result = dx_hash_insert_const(agent->class_hash, iter, cls, &cls->hash_handle);
    dx_field_iterator_free(iter);
    if (result < 0)
        assert(false);

    DEQ_INSERT_TAIL(agent->class_list, cls);

    dx_log(log_module, LOG_INFO, "Manageable Entity Type (%s) %s", query_handler ? "object" : "event", fqname);
    return cls;
}


dx_agent_t *dx_agent(dx_dispatch_t *dx)
{
    dx_agent_t *agent = NEW(dx_agent_t);
    agent->dx         = dx;
    agent->class_hash = dx_hash(6, 10, 1);
    DEQ_INIT(agent->class_list);
    DEQ_INIT(agent->in_fifo);
    DEQ_INIT(agent->out_fifo);
    agent->lock    = sys_mutex();
    agent->timer   = dx_timer(dx, dx_agent_deferred_handler, agent);
    agent->address = dx_router_register_address(dx, "$management", dx_agent_rx_handler, agent);

    return agent;
}


void dx_agent_free(dx_agent_t *agent)
{
    dx_router_unregister_address(agent->address);
    sys_mutex_free(agent->lock);
    dx_timer_free(agent->timer);
    dx_hash_free(agent->class_hash);
    free(agent);
}


dx_agent_class_t *dx_agent_register_class(dx_dispatch_t        *dx,
                                          const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler,
                                          dx_agent_query_cb_t   query_handler)
{
    dx_agent_t       *agent = dx->agent;
    dx_agent_class_t *cls;

    sys_mutex_lock(agent->lock);
    cls = dx_agent_register_class_LH(agent, fqname, context, schema_handler, query_handler);
    sys_mutex_unlock(agent->lock);
    return cls;
}


dx_agent_class_t *dx_agent_register_event(dx_dispatch_t        *dx,
                                          const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler)
{
    return dx_agent_register_class(dx, fqname, context, schema_handler, 0);
}


void dx_agent_value_string(void *correlator, const char *key, const char *value)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_insert_string(request->response, key);
    dx_compose_insert_string(request->response, value);
}


void dx_agent_value_uint(void *correlator, const char *key, uint64_t value)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_insert_string(request->response, key);
    dx_compose_insert_uint(request->response, value);
}


void dx_agent_value_null(void *correlator, const char *key)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_insert_string(request->response, key);
    dx_compose_insert_null(request->response);
}


void dx_agent_value_boolean(void *correlator, const char *key, bool value)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_insert_string(request->response, key);
    dx_compose_insert_bool(request->response, value);
}


void dx_agent_value_binary(void *correlator, const char *key, const uint8_t *value, size_t len)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_insert_string(request->response, key);
    dx_compose_insert_binary(request->response, value, len);
}


void dx_agent_value_uuid(void *correlator, const char *key, const uint8_t *value)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_insert_string(request->response, key);
    dx_compose_insert_uuid(request->response, value);
}


void dx_agent_value_timestamp(void *correlator, const char *key, uint64_t value)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_insert_string(request->response, key);
    dx_compose_insert_timestamp(request->response, value);
}


void dx_agent_value_complete(void *correlator, bool more)
{
    dx_agent_request_t *request = (dx_agent_request_t*) correlator;
    dx_compose_end_map(request->response);
    if (more)
        dx_compose_start_map(request->response);
}


void *dx_agent_raise_event(dx_dispatch_t *dx, dx_agent_class_t *event)
{
    return 0;
}

