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
#include <string.h>
#include "auth.h"
#include "server_private.h"
#include <proton/sasl.h>


void auth_client_handler(pn_connector_t *cxtr)
{
    pn_sasl_t       *sasl  = pn_connector_sasl(cxtr);
    pn_sasl_state_t  state = pn_sasl_state(sasl);
    nx_connection_t *ctx = (nx_connection_t*) pn_connector_context(cxtr);

    if (state == PN_SASL_CONF) {
        pn_sasl_mechanisms(sasl, "ANONYMOUS");
        pn_sasl_client(sasl);
    }

    state = pn_sasl_state(sasl);

    if        (state == PN_SASL_PASS) {
        ctx->state = CONN_STATE_OPENING;
    } else if (state == PN_SASL_FAIL) {
        ctx->state = CONN_STATE_FAILED;
    }
}


void auth_server_handler(pn_connector_t *cxtr)
{
    pn_sasl_t       *sasl  = pn_connector_sasl(cxtr);
    pn_sasl_state_t  state = pn_sasl_state(sasl);
    nx_connection_t *ctx = (nx_connection_t*) pn_connector_context(cxtr);

    while (state == PN_SASL_CONF || state == PN_SASL_STEP) {
        if        (state == PN_SASL_CONF) {
            pn_sasl_mechanisms(sasl, "ANONYMOUS");
            pn_sasl_server(sasl);
        } else if (state == PN_SASL_STEP) {
            const char* mechanisms = pn_sasl_remote_mechanisms(sasl);
            if (strcmp(mechanisms, "ANONYMOUS") == 0)
                pn_sasl_done(sasl, PN_SASL_OK);
            else
                pn_sasl_done(sasl, PN_SASL_AUTH);
        }
        state = pn_sasl_state(sasl);
    }

    if        (state == PN_SASL_PASS) {
        ctx->state = CONN_STATE_OPENING;
    } else if (state == PN_SASL_FAIL) {
        ctx->state = CONN_STATE_FAILED;
    }
}


