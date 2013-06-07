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

#include "python_embedded.h"
#include <qpid/dispatch.h>
#include "dispatch_private.h"
#include "alloc_private.h"
#include "log_private.h"
#include "config_private.h"

/**
 * Private Function Prototypes
 */
dx_server_t    *dx_server(int tc, const char *container_name);
void            dx_server_setup_agent(dx_dispatch_t *dx);
void            dx_server_free(dx_server_t *server);
dx_container_t *dx_container(dx_dispatch_t *dx);
void            dx_container_setup_agent(dx_dispatch_t *dx);
void            dx_container_free(dx_container_t *container);
dx_router_t    *dx_router(dx_dispatch_t *dx, const char *area, const char *id);
void            dx_router_setup_agent(dx_dispatch_t *dx);
void            dx_router_free(dx_router_t *router);
dx_agent_t     *dx_agent(dx_dispatch_t *dx);
void            dx_agent_free(dx_agent_t *agent);


static const char *CONF_CONTAINER = "container";
static const char *CONF_ROUTER    = "router";


dx_dispatch_t *dx_dispatch()
{
    dx_dispatch_t *dx = NEW(dx_dispatch_t);

    int         thread_count   = 0;
    const char *container_name = 0;
    const char *router_area    = 0;
    const char *router_id      = 0;

    dx_python_initialize();
    dx_log_initialize();
    dx_alloc_initialize();

    dx_config_initialize();
    dx_config_t *config = dx_config("../etc/qpid-dispatch.conf");

    if (config) {
        int count = dx_config_item_count(config, CONF_CONTAINER);
        if (count == 1) {
            thread_count   = dx_config_item_value_int(config, CONF_CONTAINER, 0, "worker-threads");
            container_name = dx_config_item_value_string(config, CONF_CONTAINER, 0, "container-name");
        }

        count = dx_config_item_count(config, CONF_ROUTER);
        if (count == 1) {
            router_area = dx_config_item_value_string(config, CONF_ROUTER, 0, "area");
            router_id   = dx_config_item_value_string(config, CONF_ROUTER, 0, "router-id");
        }
    }

    if (thread_count == 0)
        thread_count = 1;

    if (!container_name)
        container_name = "00000000-0000-0000-0000-000000000000";  // TODO - gen a real uuid

    if (!router_area)
        router_area = "area";

    if (!router_id)
        router_id = container_name;

    dx->server    = dx_server(thread_count, container_name);
    dx->container = dx_container(dx);
    dx->router    = dx_router(dx, router_area, router_id);
    dx->agent     = dx_agent(dx);

    dx_server_setup_agent(dx);
    dx_container_setup_agent(dx);
    dx_router_setup_agent(dx);

    dx_config_free(config);

    return dx;
}


void dx_dispatch_free(dx_dispatch_t *dx)
{
    dx_config_finalize();
    dx_agent_free(dx->agent);
    dx_router_free(dx->router);
    dx_container_free(dx->container);
    dx_server_free(dx->server);
    dx_log_finalize();
    dx_python_finalize();
}

