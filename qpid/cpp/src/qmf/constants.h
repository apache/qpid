#ifndef QMF_CONSTANTS_H
#define QMF_CONSTANTS_H
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

#include <string>

namespace qmf {

    struct protocol {
        /**
         * Header key strings
         */
        static const std::string HEADER_KEY_APP_ID;
        static const std::string HEADER_KEY_METHOD;
        static const std::string HEADER_KEY_OPCODE;
        static const std::string HEADER_KEY_AGENT;
        static const std::string HEADER_KEY_CONTENT;
        static const std::string HEADER_KEY_PARTIAL;

        /**
         * Header values per-key
         */
        static const std::string HEADER_APP_ID_QMF;

        static const std::string HEADER_METHOD_REQUEST;
        static const std::string HEADER_METHOD_RESPONSE;
        static const std::string HEADER_METHOD_INDICATION;

        static const std::string HEADER_OPCODE_EXCEPTION;
        static const std::string HEADER_OPCODE_AGENT_LOCATE_REQUEST;
        static const std::string HEADER_OPCODE_AGENT_LOCATE_RESPONSE;
        static const std::string HEADER_OPCODE_AGENT_HEARTBEAT_INDICATION;
        static const std::string HEADER_OPCODE_QUERY_REQUEST;
        static const std::string HEADER_OPCODE_QUERY_RESPONSE;
        static const std::string HEADER_OPCODE_SUBSCRIBE_REQUEST;
        static const std::string HEADER_OPCODE_SUBSCRIBE_RESPONSE;
        static const std::string HEADER_OPCODE_SUBSCRIBE_CANCEL_INDICATION;
        static const std::string HEADER_OPCODE_SUBSCRIBE_REFRESH_INDICATION;
        static const std::string HEADER_OPCODE_DATA_INDICATION;
        static const std::string HEADER_OPCODE_METHOD_REQUEST;
        static const std::string HEADER_OPCODE_METHOD_RESPONSE;

        static const std::string HEADER_CONTENT_SCHEMA_ID;
        static const std::string HEADER_CONTENT_SCHEMA_CLASS;
        static const std::string HEADER_CONTENT_OBJECT_ID;
        static const std::string HEADER_CONTENT_DATA;
        static const std::string HEADER_CONTENT_EVENT;
        static const std::string HEADER_CONTENT_QUERY;

        /**
         * Keywords for Agent attributes
         */
        static const std::string AGENT_ATTR_VENDOR;
        static const std::string AGENT_ATTR_PRODUCT;
        static const std::string AGENT_ATTR_INSTANCE;
        static const std::string AGENT_ATTR_NAME;
        static const std::string AGENT_ATTR_TIMESTAMP;
        static const std::string AGENT_ATTR_HEARTBEAT_INTERVAL;
        static const std::string AGENT_ATTR_EPOCH;
        static const std::string AGENT_ATTR_SCHEMA_UPDATED_TIMESTAMP;
    };
}

#endif
