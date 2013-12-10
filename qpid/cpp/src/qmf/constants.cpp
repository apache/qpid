/*
 *
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
 *
 */

#include "constants.h"

using namespace std;
using namespace qmf;

/**
 * Header key strings
 */
const string protocol::HEADER_KEY_APP_ID  = "x-amqp-0-10.app-id";
const string protocol::HEADER_KEY_METHOD  = "method";
const string protocol::HEADER_KEY_OPCODE  = "qmf.opcode";
const string protocol::HEADER_KEY_AGENT   = "qmf.agent";
const string protocol::HEADER_KEY_CONTENT = "qmf.content";
const string protocol::HEADER_KEY_PARTIAL = "partial";

/**
 * Header values per-key
 */
const string protocol::HEADER_APP_ID_QMF = "qmf2";

const string protocol::HEADER_METHOD_REQUEST    = "request";
const string protocol::HEADER_METHOD_RESPONSE   = "response";
const string protocol::HEADER_METHOD_INDICATION = "indication";

const string protocol::HEADER_OPCODE_EXCEPTION                    = "_exception";
const string protocol::HEADER_OPCODE_AGENT_LOCATE_REQUEST         = "_agent_locate_request";
const string protocol::HEADER_OPCODE_AGENT_LOCATE_RESPONSE        = "_agent_locate_response";
const string protocol::HEADER_OPCODE_AGENT_HEARTBEAT_INDICATION   = "_agent_heartbeat_indication";
const string protocol::HEADER_OPCODE_QUERY_REQUEST                = "_query_request";
const string protocol::HEADER_OPCODE_QUERY_RESPONSE               = "_query_response";
const string protocol::HEADER_OPCODE_SUBSCRIBE_REQUEST            = "_subscribe_request";
const string protocol::HEADER_OPCODE_SUBSCRIBE_RESPONSE           = "_subscribe_response";
const string protocol::HEADER_OPCODE_SUBSCRIBE_CANCEL_INDICATION  = "_subscribe_cancel_indication";
const string protocol::HEADER_OPCODE_SUBSCRIBE_REFRESH_INDICATION = "_subscribe_refresh_indication";
const string protocol::HEADER_OPCODE_DATA_INDICATION              = "_data_indication";
const string protocol::HEADER_OPCODE_METHOD_REQUEST               = "_method_request";
const string protocol::HEADER_OPCODE_METHOD_RESPONSE              = "_method_response";

const string protocol::HEADER_CONTENT_SCHEMA_ID    = "_schema_id";
const string protocol::HEADER_CONTENT_SCHEMA_CLASS = "_schema_class";
const string protocol::HEADER_CONTENT_OBJECT_ID    = "_object_id";
const string protocol::HEADER_CONTENT_DATA         = "_data";
const string protocol::HEADER_CONTENT_EVENT        = "_event";
const string protocol::HEADER_CONTENT_QUERY        = "_query";

/**
 * Keywords for Agent attributes
 */
const string protocol::AGENT_ATTR_VENDOR                   = "_vendor";
const string protocol::AGENT_ATTR_PRODUCT                  = "_product";
const string protocol::AGENT_ATTR_INSTANCE                 = "_instance";
const string protocol::AGENT_ATTR_NAME                     = "_name";
const string protocol::AGENT_ATTR_TIMESTAMP                = "_timestamp";
const string protocol::AGENT_ATTR_HEARTBEAT_INTERVAL       = "_heartbeat_interval";
const string protocol::AGENT_ATTR_EPOCH                    = "_epoch";
const string protocol::AGENT_ATTR_SCHEMA_UPDATED_TIMESTAMP = "_schema_updated";
