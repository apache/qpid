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
#include <string.h>
#include <stdio.h>

typedef struct dx_agent_t {
    dx_server_t       *server;
    hash_t            *class_hash;
    dx_message_list_t  in_fifo;
    dx_message_list_t  out_fifo;
    sys_mutex_t       *lock;
    dx_timer_t        *timer;
    dx_address_t      *address;
} dx_agent_t;


struct dx_agent_class_t {
    char                 *fqname;
    void                 *context;
    dx_agent_schema_cb_t  schema_handler;
    dx_agent_query_cb_t   query_handler;  // 0 iff class is an event.
};


typedef struct {
    dx_agent_t   *agent;
    dx_message_t *response_msg;
} dx_agent_request_t;

ALLOC_DECLARE(dx_agent_request_t);
ALLOC_DEFINE(dx_agent_request_t);


static void dx_agent_process_request(dx_message_t *msg)
{
    if (!dx_message_check(msg, DX_DEPTH_BODY))
        return;
    printf("Processing Agent Request\n");
}


static void dx_agent_timer_handler(void *context)
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
            dx_agent_process_request(msg);
            dx_free_message(msg);
        }
    } while (msg);
}


static void dx_agent_rx_handler(void *context, dx_message_t *msg)
{
    dx_agent_t   *agent = (dx_agent_t*) context;
    dx_message_t *copy  = dx_message_copy(msg);

    sys_mutex_lock(agent->lock);
    DEQ_INSERT_TAIL(agent->in_fifo, copy);
    sys_mutex_unlock(agent->lock);

    dx_timer_schedule(agent->timer, 0);
}


dx_agent_t *dx_agent(dx_dispatch_t *dx)
{
    dx_agent_t *agent = NEW(dx_agent_t);
    agent->server     = dx->server;
    agent->class_hash = hash(6, 10, 1);
    DEQ_INIT(agent->in_fifo);
    DEQ_INIT(agent->out_fifo);
    agent->lock    = sys_mutex();
    agent->timer   = dx_timer(dx, dx_agent_timer_handler, agent);
    agent->address = dx_router_register_address(dx, true, "agent", dx_agent_rx_handler, agent);

    return agent;
}


void dx_agent_free(dx_agent_t *agent)
{
    dx_router_unregister_address(agent->address);
    sys_mutex_free(agent->lock);
    dx_timer_free(agent->timer);
    hash_free(agent->class_hash);
    free(agent);
}


dx_agent_class_t *dx_agent_register_class(dx_dispatch_t        *dx,
                                          const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler,
                                          dx_agent_query_cb_t   query_handler)
{
    dx_agent_t *agent = dx->agent;

    dx_agent_class_t *cls = NEW(dx_agent_class_t);
    assert(cls);
    cls->fqname = (char*) malloc(strlen(fqname) + 1);
    strcpy(cls->fqname, fqname);
    cls->context        = context;
    cls->schema_handler = schema_handler;
    cls->query_handler  = query_handler;

    dx_field_iterator_t *iter = dx_field_iterator_string(fqname, ITER_VIEW_ALL);
    int result = hash_insert_const(agent->class_hash, iter, cls);
    dx_field_iterator_free(iter);
    if (result < 0)
        assert(false);

    return cls;
}


dx_agent_class_t *dx_agent_register_event(dx_dispatch_t        *dx,
                                          const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler)
{
    return dx_agent_register_class(dx, fqname, context, schema_handler, 0);
}


void dx_agent_value_string(const void *correlator, const char *key, const char *value)
{
}


void dx_agent_value_uint(const void *correlator, const char *key, uint64_t value)
{
}


void dx_agent_value_null(const void *correlator, const char *key)
{
}


void dx_agent_value_boolean(const void *correlator, const char *key, bool value)
{
}


void dx_agent_value_binary(const void *correlator, const char *key, const uint8_t *value, size_t len)
{
}


void dx_agent_value_uuid(const void *correlator, const char *key, const uint8_t *value)
{
}


void dx_agent_value_timestamp(const void *correlator, const char *key, uint64_t value)
{
}


void dx_agent_value_complete(const void *correlator, bool more)
{
}


void *dx_agent_raise_event(dx_dispatch_t *dx, dx_agent_class_t *event)
{
    return 0;
}

