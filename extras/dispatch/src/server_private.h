#ifndef __server_private_h__
#define __server_private_h__ 1
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

#include <qpid/dispatch/server.h>
#include <qpid/dispatch/user_fd.h>
#include <qpid/dispatch/timer.h>
#include <qpid/dispatch/alloc.h>
#include <proton/driver.h>
#include <proton/driver_extras.h>

void dx_server_timer_pending_LH(dx_timer_t *timer);
void dx_server_timer_cancel_LH(dx_timer_t *timer);


typedef enum {
    CONN_STATE_CONNECTING = 0,
    CONN_STATE_SASL_CLIENT,
    CONN_STATE_SASL_SERVER,
    CONN_STATE_OPENING,
    CONN_STATE_OPERATIONAL,
    CONN_STATE_FAILED,
    CONN_STATE_USER
} conn_state_t;

#define CONTEXT_NO_OWNER -1

typedef enum {
    CXTR_STATE_CONNECTING = 0,
    CXTR_STATE_OPEN,
    CXTR_STATE_FAILED
} cxtr_state_t;

typedef struct dx_server_t dx_server_t;

struct dx_listener_t {
    dx_server_t              *server;
    const dx_server_config_t *config;
    void                     *context;
    pn_listener_t            *pn_listener;
};


struct dx_connector_t {
    dx_server_t              *server;
    cxtr_state_t              state;
    const dx_server_config_t *config;
    void                     *context;
    dx_connection_t          *ctx;
    dx_timer_t               *timer;
    long                      delay;
};


struct dx_connection_t {
    dx_server_t     *server;
    conn_state_t     state;
    int              owner_thread;
    int              enqueued;
    pn_connector_t  *pn_cxtr;
    pn_connection_t *pn_conn;
    dx_listener_t   *listener;
    dx_connector_t  *connector;
    void            *context; // Copy of context from listener or connector
    void            *user_context;
    dx_user_fd_t    *ufd;
};


struct dx_user_fd_t {
    dx_server_t    *server;
    void           *context;
    int             fd;
    pn_connector_t *pn_conn;
};


ALLOC_DECLARE(dx_listener_t);
ALLOC_DECLARE(dx_connector_t);
ALLOC_DECLARE(dx_connection_t);
ALLOC_DECLARE(dx_user_fd_t);


#endif
