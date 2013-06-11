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
#include <qpid/dispatch.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>

static int            exit_with_sigint = 0;
static dx_dispatch_t *dispatch;

/**
 * The thread_start_handler is invoked once for each server thread at thread startup.
 */
static void thread_start_handler(void* context, int thread_id)
{
}


/**
 * This is the OS signal handler, invoked on an undetermined thread at a completely
 * arbitrary point of time.  It is not safe to do anything here but signal the dispatch
 * server with the signal number.
 */
static void signal_handler(int signum)
{
    dx_server_signal(dispatch, signum);
}


/**
 * This signal handler is called cleanly by one of the server's worker threads in
 * response to an earlier call to dx_server_signal.
 */
static void server_signal_handler(void* context, int signum)
{
    dx_server_pause(dispatch);

    switch (signum) {
    case SIGINT:
        exit_with_sigint = 1;

    case SIGQUIT:
    case SIGTERM:
        fflush(stdout);
        dx_server_stop(dispatch);
        break;

    case SIGHUP:
        break;

    default:
        break;
    }

    dx_server_resume(dispatch);
}


static void startup(void *context)
{
    dx_server_pause(dispatch);
    dx_dispatch_configure(dispatch);
    dx_server_resume(dispatch);
}


int main(int argc, char **argv)
{
    dx_log_set_mask(LOG_INFO | LOG_TRACE | LOG_ERROR);

    dispatch = dx_dispatch("../etc/qpid-dispatch.conf");

    dx_server_set_signal_handler(dispatch, server_signal_handler, 0);
    dx_server_set_start_handler(dispatch, thread_start_handler, 0);

    dx_timer_t *startup_timer = dx_timer(dispatch, startup, 0);
    dx_timer_schedule(startup_timer, 0);

    signal(SIGHUP,  signal_handler);
    signal(SIGQUIT, signal_handler);
    signal(SIGTERM, signal_handler);
    signal(SIGINT,  signal_handler);

    dx_server_run(dispatch);
    dx_dispatch_free(dispatch);

    if (exit_with_sigint) {
	signal(SIGINT, SIG_DFL);
	kill(getpid(), SIGINT);
    }

    return 0;
}

