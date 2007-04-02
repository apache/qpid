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
#include "../QpidError.h"
#include "BodyHandler.h"
#include "AMQRequestBody.h"
#include "AMQResponseBody.h"
#include "AMQMethodBody.h"
#include "AMQHeaderBody.h"
#include "AMQContentBody.h"
#include "AMQHeartbeatBody.h"

using namespace qpid::framing;
using namespace boost;

BodyHandler::~BodyHandler() {}

void BodyHandler::handleBody(shared_ptr<AMQBody> body) {
    switch(body->type())
    {
      case REQUEST_BODY:
        handleRequest(shared_polymorphic_cast<AMQRequestBody>(body));
        break; 
      case RESPONSE_BODY:
        handleResponse(shared_polymorphic_cast<AMQResponseBody>(body));
        break;
      case METHOD_BODY:
	handleMethod(shared_polymorphic_cast<AMQMethodBody>(body));
	break;
      case HEADER_BODY:
	handleHeader(shared_polymorphic_cast<AMQHeaderBody>(body));
	break;
      case CONTENT_BODY:
	handleContent(shared_polymorphic_cast<AMQContentBody>(body));
	break;
      case HEARTBEAT_BODY:
	handleHeartbeat(shared_polymorphic_cast<AMQHeartbeatBody>(body));
	break;
      default:
        QPID_ERROR(PROTOCOL_ERROR, "Unknown frame type "+body->type());
    }
}

