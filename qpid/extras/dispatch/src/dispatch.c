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

#include <qpid/dispatch.h>
#include "dispatch_private.h"
#include "alloc_private.h"

/**
 * Private Function Prototypes
 */
dx_server_t    *dx_server(int tc);
void            dx_server_free(dx_server_t *server);
dx_container_t *dx_container(dx_dispatch_t *dx);
void            dx_container_free(dx_container_t *container);
dx_router_t    *dx_router(dx_dispatch_t *dx);
void            dx_router_free(dx_router_t *router);
dx_agent_t     *dx_agent(dx_dispatch_t *dx);
void            dx_agent_free(dx_agent_t *agent);


dx_dispatch_t *dx_dispatch(int thread_count)
{
    dx_dispatch_t *dx = NEW(dx_dispatch_t);

    dx_alloc_initialize();

    dx->server    = dx_server(thread_count);
    dx->container = dx_container(dx);
    dx->router    = dx_router(dx);
    dx->agent     = dx_agent(dx);

    return dx;
}


void dx_dispatch_free(dx_dispatch_t *dx)
{
    dx_agent_free(dx->agent);
    dx_router_free(dx->router);
    dx_container_free(dx->container);
    dx_server_free(dx->server);
}

