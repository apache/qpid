#ifndef __dispatch_agent_h__
#define __dispatch_agent_h__ 1
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

#include <stddef.h>
#include <stdbool.h>
#include <stdint.h>

typedef struct dx_dispatch_t dx_dispatch_t;

/**
 * \defgroup Container Management Agent
 * @{
 */

typedef struct dx_agent_class_t dx_agent_class_t;


/**
 * \brief Get Schema Data Handler
 *
 * @param context The handler context supplied in dx_agent_register.
 */
typedef void (*dx_agent_schema_cb_t)(void* context);


/**
 * \brief Query Handler
 *
 * @param context The handler context supplied in dx_agent_register.
 * @param id The identifier of the instance being queried or NULL for all instances.
 * @param correlator The correlation handle to be used in calls to dx_agent_value_*
 */
typedef void (*dx_agent_query_cb_t)(void* context, const char *id, const void *correlator);


/**
 * \brief Register a class/object-type with the agent.
 */
dx_agent_class_t *dx_agent_register_class(dx_dispatch_t        *dx,
                                          const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler,
                                          dx_agent_query_cb_t   query_handler);

/**
 * \brief Register an event-type with the agent.
 */
dx_agent_class_t *dx_agent_register_event(dx_dispatch_t        *dx,
                                          const char           *fqname,
                                          void                 *context,
                                          dx_agent_schema_cb_t  schema_handler);

/**
 *
 */
void dx_agent_value_string(dx_dispatch_t *dx, const void *correlator, const char *key, const char *value);
void dx_agent_value_uint(dx_dispatch_t *dx, const void *correlator, const char *key, uint64_t value);
void dx_agent_value_null(dx_dispatch_t *dx, const void *correlator, const char *key);
void dx_agent_value_boolean(dx_dispatch_t *dx, const void *correlator, const char *key, bool value);
void dx_agent_value_binary(dx_dispatch_t *dx, const void *correlator, const char *key, const uint8_t *value, size_t len);
void dx_agent_value_uuid(dx_dispatch_t *dx, const void *correlator, const char *key, const uint8_t *value);
void dx_agent_value_timestamp(dx_dispatch_t *dx, const void *correlator, const char *key, uint64_t value);


/**
 *
 */
void dx_agent_value_complete(dx_dispatch_t *dx, const void *correlator, bool more);


/**
 *
 */
void *dx_agent_raise_event(dx_dispatch_t *dx, dx_agent_class_t *event);


/**
 * @}
 */

#endif
