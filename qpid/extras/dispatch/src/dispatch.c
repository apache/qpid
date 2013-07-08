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
#include <qpid/dispatch.h>
#include <qpid/dispatch/server.h>
#include <qpid/dispatch/ctools.h>
#include "dispatch_private.h"
#include "alloc_private.h"
#include "log_private.h"

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

ALLOC_DEFINE(dx_config_listener_t);
ALLOC_DEFINE(dx_config_connector_t);

static const char *CONF_CONTAINER   = "container";
static const char *CONF_ROUTER      = "router";
static const char *CONF_LISTENER    = "listener";
static const char *CONF_CONNECTOR   = "connector";


dx_dispatch_t *dx_dispatch(const char *config_path)
{
    dx_dispatch_t *dx = NEW(dx_dispatch_t);

    int         thread_count   = 0;
    const char *container_name = 0;
    const char *router_area    = 0;
    const char *router_id      = 0;

    DEQ_INIT(dx->config_listeners);
    DEQ_INIT(dx->config_connectors);

    dx_python_initialize(dx);
    dx_log_initialize();
    dx_alloc_initialize();

    dx_config_initialize();
    dx->config = dx_config(config_path);
    dx_config_read(dx->config);

    if (dx->config) {
        int count = dx_config_item_count(dx->config, CONF_CONTAINER);
        if (count == 1) {
            thread_count   = dx_config_item_value_int(dx->config, CONF_CONTAINER, 0, "worker-threads");
            container_name = dx_config_item_value_string(dx->config, CONF_CONTAINER, 0, "container-name");
        }

        count = dx_config_item_count(dx->config, CONF_ROUTER);
        if (count == 1) {
            router_area = dx_config_item_value_string(dx->config, CONF_ROUTER, 0, "area");
            router_id   = dx_config_item_value_string(dx->config, CONF_ROUTER, 0, "router-id");
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

    dx_alloc_setup_agent(dx);
    dx_server_setup_agent(dx);
    dx_container_setup_agent(dx);
    dx_router_setup_agent(dx);

    return dx;
}


void dx_dispatch_free(dx_dispatch_t *dx)
{
    dx_config_free(dx->config);
    dx_config_finalize();
    dx_agent_free(dx->agent);
    dx_router_free(dx->router);
    dx_container_free(dx->container);
    dx_server_free(dx->server);
    dx_log_finalize();
    dx_python_finalize();
}


static void load_server_config(dx_dispatch_t *dx, dx_server_config_t *config, const char *section, int i)
{
    config->host = dx_config_item_value_string(dx->config, section, i, "addr");
    config->port = dx_config_item_value_string(dx->config, section, i, "port");
    config->sasl_mechanisms =
        dx_config_item_value_string(dx->config, section, i, "sasl-mechanisms");
    config->ssl_enabled =
        dx_config_item_value_bool(dx->config, section, i, "ssl-profile");
    if (config->ssl_enabled) {
        config->ssl_server = 1;
        config->ssl_allow_unsecured_client =
            dx_config_item_value_bool(dx->config, section, i, "allow-unsecured");
        config->ssl_certificate_file =
            dx_config_item_value_string(dx->config, section, i, "cert-file");
        config->ssl_private_key_file =
            dx_config_item_value_string(dx->config, section, i, "key-file");
        config->ssl_password =
            dx_config_item_value_string(dx->config, section, i, "password");
        config->ssl_trusted_certificate_db =
            dx_config_item_value_string(dx->config, section, i, "cert-db");
        config->ssl_require_peer_authentication =
            dx_config_item_value_bool(dx->config, section, i, "require-peer-auth");
    }
}


static void configure_listeners(dx_dispatch_t *dx)
{
    int count;

    if (!dx->config)
        return;

    count = dx_config_item_count(dx->config, CONF_LISTENER);
    for (int i = 0; i < count; i++) {
        dx_config_listener_t *cl = new_dx_config_listener_t();
        load_server_config(dx, &cl->configuration, CONF_LISTENER, i);

        printf("\nListener   : %s:%s\n", cl->configuration.host, cl->configuration.port);
        printf("       SASL: %s\n", cl->configuration.sasl_mechanisms);
        printf("        SSL: %d\n", cl->configuration.ssl_enabled);
        if (cl->configuration.ssl_enabled) {
            printf("      unsec: %d\n", cl->configuration.ssl_allow_unsecured_client);
            printf("  cert-file: %s\n", cl->configuration.ssl_certificate_file);
            printf("   key-file: %s\n", cl->configuration.ssl_private_key_file);
            printf("    cert-db: %s\n", cl->configuration.ssl_trusted_certificate_db);
            printf("  peer-auth: %d\n", cl->configuration.ssl_require_peer_authentication);
        }

        cl->listener = dx_server_listen(dx, &cl->configuration, cl);
        DEQ_ITEM_INIT(cl);
        DEQ_INSERT_TAIL(dx->config_listeners, cl);
    }
}


static void configure_connectors(dx_dispatch_t *dx)
{
    int count;

    if (!dx->config)
        return;

    count = dx_config_item_count(dx->config, CONF_CONNECTOR);
    for (int i = 0; i < count; i++) {
        dx_config_connector_t *cc = new_dx_config_connector_t();
        load_server_config(dx, &cc->configuration, CONF_CONNECTOR, i);

        printf("\nConnector  : %s:%s\n", cc->configuration.host, cc->configuration.port);
        printf("       SASL: %s\n", cc->configuration.sasl_mechanisms);
        printf("        SSL: %d\n", cc->configuration.ssl_enabled);
        if (cc->configuration.ssl_enabled) {
            printf("  cert-file: %s\n", cc->configuration.ssl_certificate_file);
            printf("   key-file: %s\n", cc->configuration.ssl_private_key_file);
            printf("    cert-db: %s\n", cc->configuration.ssl_trusted_certificate_db);
            printf("  peer-auth: %d\n", cc->configuration.ssl_require_peer_authentication);
        }

        cc->connector = dx_server_connect(dx, &cc->configuration, cc);
        DEQ_ITEM_INIT(cc);
        DEQ_INSERT_TAIL(dx->config_connectors, cc);
    }
}


void dx_dispatch_configure(dx_dispatch_t *dx)
{
    configure_listeners(dx);
    configure_connectors(dx);
}

