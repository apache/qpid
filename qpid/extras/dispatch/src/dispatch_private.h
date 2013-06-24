#ifndef __dispatch_private_h__
#define __dispatch_private_h__
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

#include "server_private.h"
#include "config_private.h"
#include <qpid/dispatch/ctools.h>

typedef struct dx_container_t dx_container_t;
typedef struct dx_router_t    dx_router_t;
typedef struct dx_agent_t     dx_agent_t;

typedef struct dx_config_listener_t {
    DEQ_LINKS(struct dx_config_listener_t);
    dx_listener_t      *listener;
    dx_server_config_t  configuration;
} dx_config_listener_t;

DEQ_DECLARE(dx_config_listener_t, dx_config_listener_list_t);
ALLOC_DECLARE(dx_config_listener_t);


typedef struct dx_config_connector_t {
    DEQ_LINKS(struct dx_config_connector_t);
    dx_connector_t     *connector;
    dx_server_config_t  configuration;
} dx_config_connector_t;

DEQ_DECLARE(dx_config_connector_t, dx_config_connector_list_t);
ALLOC_DECLARE(dx_config_connector_t);

struct dx_dispatch_t {
    dx_server_t        *server;
    dx_container_t     *container;
    dx_router_t        *router;
    dx_agent_t         *agent;
    dx_config_t        *config;

    dx_config_listener_list_t   config_listeners;
    dx_config_connector_list_t  config_connectors;
};

#endif

