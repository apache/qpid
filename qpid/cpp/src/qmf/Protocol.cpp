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

#include "qmf/Protocol.h"

using namespace std;
using namespace qmf;

const string Protocol::SCHEMA_ELT_TYPE("type");
const string Protocol::SCHEMA_ELT_DIR("dir");
const string Protocol::SCHEMA_ELT_UNIT("unit");
const string Protocol::SCHEMA_ELT_DESC("desc");
const string Protocol::SCHEMA_ELT_ACCESS("access");
const string Protocol::SCHEMA_ELT_OPTIONAL("optional");
const string Protocol::SCHEMA_ARGS("_arguments");
const string Protocol::SCHEMA_PACKAGE("_package_name");
const string Protocol::SCHEMA_CLASS_KIND("_type");
const string Protocol::SCHEMA_CLASS_KIND_DATA("_data");
const string Protocol::SCHEMA_CLASS_KIND_EVENT("_event");
const string Protocol::SCHEMA_CLASS("_class_name");
const string Protocol::SCHEMA_HASH("_hash_str");
const string Protocol::AGENT_NAME("_agent_name");
const string Protocol::OBJECT_NAME("_object_name");
const string Protocol::SCHEMA_ID("_schema_id");
const string Protocol::VALUES("_values");
const string Protocol::SUBTYPES("_subtypes");
const string Protocol::SUBTYPE_SCHEMA_PROPERTY("qmfProperty");
const string Protocol::SUBTYPE_SCHEMA_METHOD("qmfMethod");

const string Protocol::AMQP_CONTENT_MAP("amqp/map");
const string Protocol::AMQP_CONTENT_LIST("amqp/list");

const string Protocol::APP_OPCODE("qmf.opcode");
const string Protocol::APP_PARTIAL("partial");
const string Protocol::APP_CONTENT("qmf.content");

const string Protocol::OP_EXCEPTION("_exception");
const string Protocol::OP_AGENT_LOCATE_REQUEST("_agent_locate_request");
const string Protocol::OP_AGENT_LOCATE_RESPONSE("_agent_locate_response");
const string Protocol::OP_AGENT_HEARTBEAT_INDICATION("_agent_heartbeat_indication");
const string Protocol::OP_QUERY_REQUEST("_query_request");
const string Protocol::OP_QUERY_RESPONSE("_query_response");
const string Protocol::OP_SUBSCRIBE_REQUEST("_subscribe_request");
const string Protocol::OP_SUBSCRIBE_RESPONSE("_subscribe_response");
const string Protocol::OP_SUBSCRIBE_CANCEL_INDICATION("_subscribe_cancel_indication");
const string Protocol::OP_SUBSCRIBE_REFRESH_REQUEST("_subscribe_refresh_request");
const string Protocol::OP_DATA_INDICATION("_data_indication");
const string Protocol::OP_METHOD_REQUEST("_method_request");
const string Protocol::OP_METHOD_RESPONSE("_method_response");

const string Protocol::CONTENT_NONE("");
const string Protocol::CONTENT_PACKAGE("_schema_package");
const string Protocol::CONTENT_SCHEMA_ID("_schema_id");
const string Protocol::CONTENT_SCHEMA_CLASS("_schema_class");
const string Protocol::CONTENT_OBJECT_ID("_object_id");
const string Protocol::CONTENT_DATA("_data");
const string Protocol::CONTENT_EVENT("_event");

#if 0
bool Protocol::checkHeader(const Message& /*msg*/, string& /*opcode*/, uint32_t* /*seq*/)
{
    // TODO
    return true;
}

void Protocol::encodeHeader(Message& /*msg*/, const string& /*opcode*/, uint32_t /*seq*/)
{
    // TODO
}
#endif
