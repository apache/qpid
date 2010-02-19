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

const string Protocol::SCHEMA_ELT_NAME("name");
const string Protocol::SCHEMA_ELT_TYPE("type");
const string Protocol::SCHEMA_ELT_DIR("dir");
const string Protocol::SCHEMA_ELT_UNIT("unit");
const string Protocol::SCHEMA_ELT_DESC("desc");
const string Protocol::SCHEMA_ELT_ACCESS("access");
const string Protocol::SCHEMA_ELT_OPTIONAL("optional");
const string Protocol::SCHEMA_ARGS("args");
const string Protocol::SCHEMA_PACKAGE("_package_name");
const string Protocol::SCHEMA_CLASS_KIND("_type");
const string Protocol::SCHEMA_CLASS_KIND_DATA("_data");
const string Protocol::SCHEMA_CLASS_KIND_EVENT("_event");
const string Protocol::SCHEMA_CLASS("_class_name");
const string Protocol::SCHEMA_HASH("_hash_str");
const string Protocol::AGENT_NAME("_agent_name");
const string Protocol::OBJECT_NAME("_object_name");
const string Protocol::SCHEMA_ID("_schema_id");

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
