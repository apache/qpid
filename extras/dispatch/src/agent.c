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

#include <qpid/dispatch/agent.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/hash.h>
#include <qpid/dispatch/container.h>
#include <qpid/dispatch/message.h>
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/timer.h>
#include <string.h>


typedef struct dx_agent_t {
    hash_t            *class_hash;
    dx_message_list_t  in_fifo;
    dx_message_list_t  out_fifo;
    sys_mutex_t       *lock;
    dx_timer_t        *timer;
} dx_agent_t;

static dx_agent_t *agent = 0;


struct dx_agent_class_t {
    char                 *fqname;
    void                 *context;
    dx_agent_schema_cb_t  schema_handler;
    dx_agent_query_cb_t   query_handler;  // 0 iff class is an event.
};


static void dx_agent_timer_handler(void *context)
{
    // TODO - Process the in_fifo here
}


void dx_agent_initialize()
{
    assert(!agent);
    agent = NEW(dx_agent_t);
    agent->class_hash = hash(6, 10, 1);
    DEQ_INIT(agent->in_fifo);
    DEQ_INIT(agent->out_fifo);
    agent->lock  = sys_mutex();
    agent->timer = dx_timer(dx_agent_timer_handler, agent);
}


void dx_agent_finalize(void)
{
    sys_mutex_free(agent->lock);
    dx_timer_free(agent->timer);
    hash_free(agent->class_hash);
    free(agent);
    agent = 0;
}


dx_agent_class_t *dx_agent_register_class(const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler,
                                          dx_agent_query_cb_t   query_handler)
{
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
    assert(result >= 0);

    return cls;
}


dx_agent_class_t *dx_agent_register_event(const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler)
{
    return dx_agent_register_class(fqname, context, schema_handler, 0);
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


void *dx_agent_raise_event(dx_agent_class_t *event)
{
    return 0;
}

