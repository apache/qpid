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

#include <stdio.h>
#include <proton/driver.h>
#include <qpid/nexus/server.h>
#include <qpid/nexus/container.h>
#include <qpid/nexus/timer.h>
#include <qpid/nexus/log.h>
#include <qpid/nexus/router.h>
#include <signal.h>

static int exit_with_sigint = 0;

static void thread_start_handler(void* context, int thread_id)
{
}


static void signal_handler(void* context, int signum)
{
    nx_server_pause();

    switch (signum) {
    case SIGINT:
        exit_with_sigint = 1;

    case SIGQUIT:
    case SIGTERM:
        fflush(stdout);
        nx_server_stop();
        break;

    case SIGHUP:
        break;

    default:
        break;
    }

    nx_server_resume();
}


static void startup(void *context)
{
    // TODO - Move this into a configuration framework

    nx_server_pause();

    static nx_server_config_t server_config;
    server_config.host            = "0.0.0.0";
    server_config.port            = "5672";
    server_config.sasl_mechanisms = "ANONYMOUS";
    server_config.ssl_enabled     = 0;

    nx_server_listen(&server_config, 0);

    /*
    static nx_server_config_t client_config;
    client_config.host            = "0.0.0.0";
    client_config.port            = "10000";
    client_config.sasl_mechanisms = "ANONYMOUS";
    client_config.ssl_enabled     = 0;

    nx_server_connect(&client_config, 0);
    */

    nx_server_resume();
}


int main(int argc, char **argv)
{
    nx_log_set_mask(LOG_INFO | LOG_TRACE | LOG_ERROR);

    nx_server_initialize(4);
    nx_container_initialize();

    nx_server_set_signal_handler(signal_handler, 0);
    nx_server_set_start_handler(thread_start_handler, 0);

    nx_router_t *router = nx_router(0);

    nx_timer_t *startup_timer = nx_timer(startup, 0);
    nx_timer_schedule(startup_timer, 0);

    nx_server_signal(SIGHUP);
    nx_server_signal(SIGQUIT);
    nx_server_signal(SIGTERM);
    nx_server_signal(SIGINT);

    nx_server_run();
    nx_router_free(router);
    nx_server_finalize();

    if (exit_with_sigint) {
	signal(SIGINT, SIG_DFL);
	kill(getpid(), SIGINT);
    }

    return 0;
}

